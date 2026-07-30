# Implementation Plan: Protocol-Owned Timer Logic

Refactor the timer logic to move payload size decisions and request formatting entirely into the `WatchProtocol` implementations. This removes implementation details from the `io` layer.

## User Review Required

> [!IMPORTANT]
> This change moves the `timerSize` logic out of the global `WatchInfo` feature set and into the specific protocol generations where it belongs.

## Proposed Changes

### Protocol Interface Extension
#### [MODIFY] [WatchProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/WatchProtocol.kt)
- Add `fun getTimerSize(): Int`.

### Protocol Implementations
#### [MODIFY] [StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)
- Implement `getTimerSize()`: returns `7`.
#### [MODIFY] [AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)
- Implement `getTimerSize()`: returns `15`.

### IO Layer Refactoring
#### [MODIFY] [TimerIO.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/io/TimerIO.kt)
- Update `TimerIOFunctional.encode(timerState, size)` to accept a size parameter.
- Update `TimerIOFunctional.buildSetCommand(message, size)` to pass the size.
- Update `TimerIO.sendToWatchSet(message)` to use `WatchInfo.protocol.getTimerSize()`.

### Cleanup
#### [MODIFY] [WatchInfo.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/WatchInfo.kt)
- Remove `timerSize` property from `ModelInfo` and the public accessor.

## Verification Plan

### Logic Verification
- Verify that standard models continue to produce 7-byte payloads.
- Verify that MTG models produce 15-byte payloads.
- Ensure `TimerIO` no longer depends on `WatchInfo` for internal protocol decisions.
