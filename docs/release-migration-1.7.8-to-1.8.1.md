# GShockAPI migration notes: 1.7.8 -> 1.8.1

This document summarizes the practical changes between the 1.7.8 release tag and the current 1.8.1 codebase in this repository, with an emphasis on what a TypeScript web client such as gshock-smart_sync-weball should account for when aligning to the latest library behavior.

> Note: the repository currently shows the 1.8.1 release notes on the main branch, but there is no explicit 1.8.1 git tag in the local repo snapshot. The comparison below therefore uses the 1.7.8 tag as the baseline and the current main branch as the target state.

## 1. Executive summary

The main changes since 1.7.8 are:

- Added support for the Casio BSA-B100 and related exercise/fitness handling.
- Added a formal exercise-profile API (`ExerciseProfile`, `ExerciseIO`, `getExerciseProfile()`, `setExerciseProfile()`).
- Improved alarm handling with one-time alarms and optional naming support.
- Tightened watch capability detection and model metadata (`WatchInfo`, `WatchModel`).
- Expanded step-counter and health data support (`getStepCount`, `getStepSummary`, richer `StepCounterData`).
- Improved initialization and connection reliability with timeouts and guarded startup logic.
- Improved time-setting behavior with a system-time offset option and safer protocol routing.

For a TypeScript consumer, this means the web app should expect new optional fields, more capability flags, and more robust retry behavior when watch communication is slow or model-specific.

## 2. Version boundary and release notes

The repository’s release notes record the following relevant progression:

- `1.7.8`: older baseline.
- `1.7.9`: added support for CASIO BSA-B100.
- `1.8.0`: step-counter and health-data improvements, BLE reliability work, connection safety fixes.
- `1.8.1`: release notes mention BSA-B100 support + exercise profile support and protocol / alarm enhancements.

This makes the effective upgrade path from 1.7.8 to the current state a combination of:

1. model-support upgrades,
2. API expansion for exercise-related features,
3. stricter handling of connection and alarm semantics,
4. more robust watch metadata and step data models.

## 3. Actual code-level changes from 1.7.8 to current main

### 3.1 New exercise profile feature

The largest API addition is the exercise profile system.

New model:

```kotlin
data class ExerciseProfile(
    val stepGoal: Int = 10000,
    val height: Int = 0,
    val weight: Int = 0,
)
```

New API members on `IGShockAPI`:

```kotlin
suspend fun getExerciseProfile(): ExerciseProfile
fun setExerciseProfile(profile: ExerciseProfile)
```

New IO implementation:

- `ExerciseIO.encode()` / `decode()`
- `ExerciseIO.request()`
- `ExerciseIO.set()`
- `ExerciseIO.sendToWatchSet()`

This indicates the library is now able to:

- read an exercise configuration from certain watch models,
- write `stepGoal`, `height`, and `weight`,
- encode and decode the Casio-specific payload format.

#### TypeScript impact

A TypeScript app should model this as:

```ts
export interface ExerciseProfile {
  stepGoal: number;
  height: number;
  weight: number;
}
```

If your frontend currently ignores exercise settings entirely, this is the main new capability to add for supported models.

### 3.2 Watch metadata model expanded

`WatchInfo` adds new capability flags and model entries. The model list now includes BSA-B100 and F_B100, and exposes booleans such as:

```kotlin
val hasExerciseProfile: Boolean
val hasStepCounter: Boolean
```

The model metadata also contains updated battery scaling and protocol assignments, notably for GW-BX5600 and similar hardware.

#### TypeScript impact

Your web app should not treat watch capability detection as a flat string lookup only. It should account for a capability object such as:

```ts
interface WatchCapabilities {
  hasStepCounter?: boolean;
  hasExerciseProfile?: boolean;
  hasWorldCities?: boolean;
  hasHomeTime?: boolean;
  batteryLowerLimit?: number;
  batteryUpperLimit?: number;
}
```

This is especially important for model-specific command routing and UI enablement.

### 3.3 Alarm API refinements

The alarm model now includes a one-time flag and optional name:

```kotlin
data class Alarm(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val hasHourlyChime: Boolean = false,
    val oneTime: Boolean = false,
    val name: String? = null
)
```

And the set logic explicitly supports `ONE_TIME_MASK`:

```kotlin
if (alarm.enabled) {
    flag = flag or Alarms.ENABLED_MASK
    if (alarm.oneTime) {
        flag = flag or Alarms.ONE_TIME_MASK
    }
}
```

This means alarm serialization must now preserve a distinction between:

- standard alarm,
- one-time alarm,
- hourly-chime alarm,
- named alarm.

#### TypeScript impact

If your app stores alarms in JSON, update the schema to include:

```ts
export interface Alarm {
  hour: number;
  minute: number;
  enabled: boolean;
  hasHourlyChime?: boolean;
  oneTime?: boolean;
  name?: string | null;
}
```

If your current TS app only stores `hour`, `minute`, and `enabled`, the newer Android code will receive missing `oneTime` state as `false` by default, which means one-time alarms may not round-trip correctly.

### 3.4 Time-setting behavior changed

The library now gives `setTime()` an additional offset parameter:

```kotlin
suspend fun setTime(
    timeZone: String = TimeZone.getDefault().id,
    timeMs: Long? = null,
    offsetFormSystemTime: Long? = null
)
```

The internal implementation uses:

```kotlin
val timeToSet = timeMs ?: (Clock.systemDefaultZone().millis() + (offset ?: 0L))
```

There is also explicit protection against issuing the legacy world-city setup path on watches that do not support it, as noted in `StandardProtocol.setTime()`.

#### TypeScript impact

Your webapp should be prepared for a more nuanced time-sync operation:

- some watches need local timezone or DST handling,
- some require no world-city register writes,
- some may benefit from a small timing offset for latency compensation.

If your current app sends a raw timestamp with no compensation, it may still work for many watches, but it will be less precise for network-delayed or remote-controlled syncing.

### 3.5 Initialization and BLE reliability changes

The current code adds more defensive startup handling:

- `GShockAPI.init()` wraps `getAppInfo()` and `getPressedButton()` in timeout-protected calls.
- startup continues even when initial app-info reads fail.
- `WaitForConnectionIO` includes timeout protection.
- BLE manager cleanup and re-creation logic have been hardened.

This is a functional change in how the library behaves under transient Bluetooth failures, not just a cosmetic improvement.

#### TypeScript impact

For the web app, this implies:

- treat connection initialization as noisy and retryable,
- expect temporary disconnects and partial initialization failures,
- do not assume app-info fetch always succeeds on first connection,
- prefer a resilient reconnect/retry flow rather than fail-fast behavior.

### 3.6 Step-counter and health data expansion

In the 1.8.0/1.8.1 range, the library adds a wider and more structured step counter model. The API includes:

```kotlin
suspend fun getStepCount(peek: Boolean = true): StepCounterData
suspend fun getStepSummary(): Int
```

The data model now covers:

- hourly aggregation,
- daily totals,
- activity intervals,
- distance metrics,
- diagnostics and warnings.

This is a major shift from older, simpler step counters toward richer health-data handling.

#### TypeScript impact

If your frontend displays or stores only a daily step total, you may now need to support a richer payload shape, for example:

```ts
interface StepCounterData {
  totalSteps?: number;
  summary?: number;
  hourlyByHour?: Record<number, number>;
  periods?: unknown[];
  distance?: number;
  warnings?: string[];
}
```

The exact shape may vary by watch model, but the key migration point is that the new code expects a richer health dataset and not just a flat integer.

## 4. Supported models and capability routing

The `WatchInfo` model table now contains explicit support for model families like:

- `BSA_B100`
- `F_B100`
- `ABL_100`
- `GW_BX5600`
- `MTG_B1000`
- `MTG_B3000`

The capability map is not just metadata: it affects whether the app should even invoke certain commands, especially exercise, step-counter, home-time, and world-city routines.

#### TypeScript implication

The web app should filter commands by capability rather than by device name string alone. A watch that is “BSA-B100” should not be treated like a generic digital watch if its capabilities say it has exercise/profile support or not.

## 5. Recommended TypeScript migration checklist

For gshock-smart_sync-weball, the recommended migration work is:

1. Add exercise profile support
   - Add `ExerciseProfile` model and `getExerciseProfile` / `setExerciseProfile` calls.
   - Gate these calls behind `hasExerciseProfile` checks.

2. Update alarm schema
   - Support `oneTime`, `hasHourlyChime`, and optional `name`.
   - Preserve these fields when reading or writing alarm state.

3. Improve watch capability detection
   - Add a typed capability object rather than raw model-name assumptions.
   - Include step-counter and exercise-profile flags.

4. Harden reconnect logic
   - Treat connection startup as retry-safe.
   - Do not crash on partial initialization or timeout during `getAppInfo()`/`getPressedButton()`.

5. Prepare step-data handling
   - Accept richer step history / summary payloads if the watch supports them.

6. Review time-sync behavior
   - Consider adding offset support when synchronizing time from a remote or network-delayed source.

## 6. Suggested TypeScript model set

A practical target for the web app could look like this:

```ts
export interface WatchCapabilities {
  hasExerciseProfile?: boolean;
  hasStepCounter?: boolean;
  hasWorldCities?: boolean;
  hasHomeTime?: boolean;
  batteryLevelLowerLimit?: number;
  batteryLevelUpperLimit?: number;
}

export interface Alarm {
  hour: number;
  minute: number;
  enabled: boolean;
  hasHourlyChime?: boolean;
  oneTime?: boolean;
  name?: string | null;
}

export interface ExerciseProfile {
  stepGoal: number;
  height: number;
  weight: number;
}

export interface StepCounterData {
  totalSteps?: number;
  summary?: number;
  hourlyByHour?: Record<number, number>;
  periods?: unknown[];
  distance?: number;
  warnings?: string[];
}
```

## 7. Bottom line

The changes from 1.7.8 to the current 1.8.1 codebase are not just bug fixes; they introduce a meaningful capability expansion. The library is now more model-aware, more health-data-aware, and more defensive in connection handling.

For gshock-smart_sync-weball, the most important migration work is:

- adding exercise-profile handling,
- preserving one-time and named alarms,
- treating watch capabilities as first-class data,
- accounting for richer step-counter data,
- handling connection retries and startup timeout behavior more gracefully.

This is the main set of differences to align the web app against the newest GShockAPI behavior.
