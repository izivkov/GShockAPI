# GShockAPI — API Reference

This is a curated, human-organized companion to the auto-generated reference
(linked from the main [README](../README.md), built from KDoc). That one is
complete but flat — every method, alphabetized, with no sense of "what do I
actually need to call to do X." This one groups things by task instead.

Everything here lives on `IGShockAPI`, implemented by `GShockAPI`. Get an
instance once and keep it — it's meant to be a long-lived singleton, not
something you construct per-screen:

```kotlin
private val api = GShockAPI(context)
```

## 1. Connecting

```kotlin
api.waitForConnection(deviceId)   // suspends until connected, or fails fast
                                   // if a *different* watch is already connected
api.init()                        // call once, right after connection succeeds
api.isConnected(): Boolean
api.isConnectedTo(address: String): Boolean   // is *this specific* address connected?
api.disconnect()
api.close()                       // release all Bluetooth resources
```

`waitForConnection(deviceId)` is the one call most integrations start with.
Leave `deviceId` blank to accept whatever G-Shock is discovered first;
pass a specific MAC address to connect to that watch only. It suspends
until `"ConnectionSetupComplete"` fires (see §7), so you don't need to
listen for that event yourself just to know when to proceed.

## 2. Pairing and background presence (Companion Device Manager)

For letting a watch reconnect the app in the background, without the app
needing to keep scanning:

```kotlin
api.associate(context, delegate)                  // pair a new watch (system chooser UI)
api.disassociate(context, address)
api.getAssociations(context): List<String>         // paired MAC addresses
api.getAssociationsWithNames(context): List<Association>

// Android 12+ (API 31+) only:
api.startObservingDevicePresence(context, address)  // requires REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE
api.stopObservingDevicePresence(context, address)
```

`startObservingDevicePresence` is what makes `"DeviceAppeared"` /
`"DeviceDisappeared"` fire later when a previously-paired watch comes in
or out of range — call it once per address you care about (typically
right after `associate()` succeeds, and again at app startup for
already-paired devices).

## 3. Foreground scanning (finding a watch without pairing first)

```kotlin
api.scan(context, filter = { info -> true }, onDeviceFound = { info -> ... })
api.stopScan()
api.startFallbackScan(context, addresses, pendingIntent)  // PendingIntent-based background variant
```

## 4. Time

```kotlin
api.setTime(timeZone = TimeZone.getDefault().id, timeMs = null)
api.getHomeTime(): String
api.getWorldCities(cityNumber: Int): String        // 0-5
api.getDSTForWorldCities(cityNumber: Int): String
api.getDSTWatchState(state: IO.DstState): String    // ZERO / TWO / FOUR
api.getTimeAdjustment(): TimeAdjustmentInfo          // Auto-Time config
```

## 5. Alarms & timer

```kotlin
api.getAlarms(): ArrayList<Alarm>
api.setAlarms(alarms: ArrayList<Alarm>)              // always the full list (typically 5 slots)
api.getTimer(): Int                                  // seconds
api.setTimer(timerValue: Int)
```

## 6. Reminders (events)

```kotlin
api.getEventsFromWatch(): ArrayList<Event>
api.getEventFromWatch(eventNumber: Int): Event        // 1-5
api.setEvents(events: ArrayList<Event>)                // always the full list
api.clearEvents()
```

## 7. Settings

```kotlin
api.getSettings(): Settings           // full profile: basic settings + time adjustment
api.getBasicSettings(): Settings      // date format, language, button tones, etc.
api.setSettings(settings: Settings)
```

## 8. Watch info & diagnostics

```kotlin
api.getWatchName(): String
api.getBatteryLevel(): Int            // 0-100
api.getWatchTemperature(): Int        // °C
api.getStepCount(peek: Boolean = true): StepCounterData   // step-tracking models only
api.getStepSummary(): Int                                  // lightweight: today's total only
api.getAppInfo(): String
api.getError(): String
```

`getStepCount(peek = false)` finalizes and clears the watch's step
history — use `peek = true` (the default) for a non-destructive read.

## 9. What triggered this connection

After a connection completes, these tell you *why* it happened — was the
user pressing a button on the watch, or is this an automatic Auto-Time
sync? Feature code (which action to run) branches on these:

```kotlin
api.getPressedButton(): IO.WatchButton   // UPPER_LEFT, LOWER_LEFT, UPPER_RIGHT, LOWER_RIGHT, NO_BUTTON, FIND_PHONE, ...
api.isActionButtonPressed(): Boolean          // short-press, lower-right
api.isNormalButtonPressed(): Boolean          // long-press, lower-left
api.isFindPhoneButtonPressed(): Boolean
api.isAlwaysConnectedConnectionPressed(): Boolean
api.isAutoTimeStarted(): Boolean
```

## 10. Notifications to the watch

```kotlin
api.supportsAppNotifications(): Boolean
api.sendAppNotification(notification: AppNotification)
```

## 11. Escape hatches

```kotlin
api.sendMessage(message: String)         // raw JSON action, for anything not covered above
api.setScratchpadData(data: ByteArray)   // raw read/write to the watch's user-data area
api.getScratchpadData(): ByteArray
api.isScratchpadReset(): Boolean
api.resetHand()
api.validateBluetoothAddress(deviceAddress: String?): Boolean
api.isBluetoothEnabled(context: Context): Boolean
api.preventReconnection(): Boolean
```

## Listening for events instead of polling

Most of the calls above are request/response — you call them, you get a
result. For state changes that happen *to* you (connection established,
disconnect, data updated by some other trigger), subscribe to
`ProgressEvents` instead of polling:

```kotlin
ProgressEvents.runEventActions("MyUniqueSubscriberName", arrayOf(
    EventAction("ConnectionSetupComplete") { /* connected */ },
    EventAction("Disconnect") { /* lost connection */ },
    EventAction("AlarmsUpdated") { /* alarms changed - reload if you're showing them */ },
))
```

Two things worth knowing before you do this:

- **Names must be unique.** A second `runEventActions` call registered
  under a name already in use *replaces* the first (its old handler is
  cancelled) rather than adding a second listener — this is deliberate,
  not a bug, and it's what makes it safe to call this again from the same
  call site (a retry, a recreated component) without leaking handlers or
  silently no-op'ing. But two genuinely *different* subscribers must not
  share a name, or one will unintentionally replace the other.
- **Full event list:** `"Init"`, `"ConnectionStarted"`,
  `"ConnectionSetupComplete"`, `"Disconnect"`, `"AlarmDataLoaded"`,
  `"NotificationsEnabled"`, `"NotificationsDisabled"`,
  `"WatchInitializationCompleted"`, `"AllPermissionsAccepted"`,
  `"ButtonPressedInfoReceived"`, `"ConnectionFailed"`, `"SettingsLoaded"`,
  `"NeedToUpdateUI"`, `"CalendarUpdated"`, `"HomeTimeUpdated"`,
  `"ApiError"` — plus feature-specific ones fired by individual `IO`
  handlers (`"AlarmsUpdated"`, `"EventsUpdated"`, `"TimerUpdated"`,
  `"SettingsUpdated"` and others) that aren't in the built-in list but
  work the same way; any string is a valid event name.

For the full method-by-method generated reference (parameter types,
inherited members, source links), see the
[Dokka docs](https://izivkov.github.io/GShockAPI/api/org.avmedia.gshockapi/index.html).
