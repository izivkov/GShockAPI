# Adding support for a new watch

This guide shows how to add a new Casio watch to GShockAPI. The worked example is the
**Oceanus OCW-S400** (module 5728), added in the `ocw-s400-support` change. Read the
`OceanusProtocol` class alongside this guide.

## 1. Know what the watch is

Before writing any code, find out:

- **Model name and module number.** The module number is printed on the back of the
  watch and is also reported by the watch. Watches that share a module number usually
  share their protocol.
- **Which features it has.** Battery, temperature, time format, hourly chime, timer,
  light, operation tone, watch settings, time-adjustment minutes, scratchpad, step
  counter, and so on.
- **Which protocol it speaks.** Most watches use `StandardProtocol`. Some need a
  dedicated protocol (see step 4).

Keep the module number in your notes. The code comments use it to show which models share
a block.

## 2. Capture the official app's traffic

You need a Bluetooth HCI snoop log and the official app's behaviour, recorded with the
same watch:

1. Enable **Bluetooth HCI snoop log** in Developer options and reproduce each action
   (pairing, time sync, each setting you want to support).
2. Note the **button-info reply** (command `0x10`) and its byte 8. The library reads this
   byte as the connection kind (`ButtonPressedIO`). Record the value for each kind of
   connection you can make (pairing, normal, automatic time set). Do not assume a meaning
   for a value until you have seen it in more than one capture.
3. Save the captures. Keep only the small excerpts you need as test fixtures (see step 7).

## 3. Add the model

1. Add an entry to `WatchModel` in `WatchInfo.kt`. Reuse an existing entry only when the
   watch shares its module and feature set.
2. Add the exact name mapping to `exactModelMap`, with a comment giving the module number.
   **Matching is exact.** Do not use `startsWith` or `contains`.
3. Add a `ModelInfo` block to `modelList`. Set every `has...` flag explicitly, even when
   it is `false`. Set `protocol` to the protocol class for the watch.

```kotlin
ModelInfo(
    model = WatchModel.OCW_S400,
    hasBatteryLevel = true,
    hasTemperature = true,
    hasWatchSettings = false,
    hasScratchpad = false,
    // ... every other flag, set explicitly ...
    protocol = OceanusProtocol,
),
```

The Oceanus block disables the flags the watch doesn't have. The library then skips those
features instead of sending commands the watch won't answer.

## 4. Choose or write the protocol

Extend `StandardProtocol` and override only what differs. Look at how the existing
protocols do it:

- `H5600Protocol` for the DW-H5600 (its own LifeLog transfer and health data).
- `OceanusProtocol` for the S400. It overrides:
    - `onInitialized`, which runs after the connection's button-info reply. It decides
      what to do from the connection kind, and it does not write unless the kind allows it.
    - the time-setting path. It reads the home-time records, changes only the time-zone
      fields, and then writes the current time, in the order the official app uses.
    - the write helpers that send the commands in order (step 5).

Two rules apply to every protocol:

- **Don't write on an unknown connection kind.** If the kind doesn't match what the
  command needs, log it and skip the write. Surface the skip to the caller where you can.
- **Keep connection state per connection.** Set it once, when the connection starts, and
  clear it on disconnect.

## 5. Talk to the watch over BLE

- Characteristics and UUIDs are declared in `CasioConstants`.
- A request with `GetSetMode.GET` is written **without response**. Its completion only
  means the phone's stack has sent the packet.
- A request with `GetSetMode.SET` is written **with response**. Its completion means the
  watch's GATT layer acknowledged it, not that the watch has processed it.
- **Confirm state changes from the watch's reply** on the status characteristic, not from
  write completion.
- Use `Connection.writeInOrder` when the next write depends on the previous one. It waits
  for the completion. Wrap each call in `withTimeoutOrNull`, and handle `false` and the
  timeout.
- Writes are serialized by the BLE library's request queue. Don't assume that writes sent
  from separate coroutines keep their order unless you wait for each one.

## 6. Wire up the API

- If the watch needs a new capability, add it to `IGShockAPI` with a default
  implementation, and override it in `GShockAPI`.
- Keep watch-specific logic in the protocol or an IO class, not in `GShockAPI`.

## 7. Test with captured bytes

- Write unit tests for every decoder and encoder, using bytes from your captures.
- Put the fixtures under `api/src/test/resources/<watch>/`. Keep them small, and name them
  after the action they record.
- Test the boundary cases: an unknown connection kind, a missing reply, and a reply
  arriving after the timeout.

## 8. Update the documentation

- Add the watch to the table in the README's **Supported Watch Models** section.
- Note in the table what does not work yet. Honest notes help users and reviewers.
- Mention the module number in the pull request description.

## 9. Checklist for a pull request

- [ ] Module number and model name documented
- [ ] Exact name mapping added (no `startsWith` or `contains`)
- [ ] Every `has...` flag set explicitly
- [ ] Protocol extends `StandardProtocol`, and overrides only what differs
- [ ] Connection-kind decisions tested, including the unknown-kind case
- [ ] Writes that depend on each other use `writeInOrder` with a timeout
- [ ] Only small test fixtures committed, no full capture logs
- [ ] README table updated
- [ ] Unit tests pass (`./gradlew :api:test`)

## Reference

- `WatchInfo.kt`: `WatchModel`, `ModelInfo`, `exactModelMap`, and the per-model blocks
- `protocols/OceanusProtocol.kt`: the worked example
- `protocols/H5600Protocol.kt`: a protocol with its own data transfer
- `ble/Connection.kt` and `ble/IGShockManager.kt`: writes, completions, and the request queue
- `io/ButtonPressedIO.kt`: the button-info reply and the connection kind