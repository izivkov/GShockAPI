# Implementation Plan: Protocol-Delegated Watch Condition

Refactor the Watch Condition retrieval (battery level and temperature) to be delegated to the `WatchProtocol` implementations. This ensures that the correct request format (`28` vs `280000`) and scaling logic (calibrated vs direct) are used based on the watch generation.

## User Review Required

> [!IMPORTANT]
> This change moves the responsibility for requesting and parsing hardware status data out of the generic `WatchConditionIO.kt` and into the protocol generations.
> - **Standard Protocol** will use calibrated values based on `WatchInfo` limits.
> - **Analogue Protocol** will use the direct percentage/celsius values discovered in the MTG-B3000 logs.

## Proposed Changes

### Protocol Interface Extension
#### [MODIFY] [WatchProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/WatchProtocol.kt)
- Add `suspend fun getBatteryLevel(): Int`.
- Add `suspend fun getWatchTemperature(): Int`.

### Standard Implementation
#### [MODIFY] [StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)
- Implement status methods:
    1. Request using string `"28"`.
    2. Apply calibration logic from `WatchConditionIOFunctional`.

### Analogue (MTG) Implementation
#### [MODIFY] [AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)
- Implement status methods:
    1. Request using string `"280000"`.
    2. Use direct byte mapping (second byte = battery %, third byte = temp).

### IO Layer Refactoring
#### [MODIFY] [WatchConditionIO.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/io/WatchConditionIO.kt)
- Refactor to accept a `requestString` in `request()`.
- Expose pure decoding helpers that protocols can use.

### Integration
#### [MODIFY] [GShockAPI.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/GShockAPI.kt)
- Update `getBatteryLevel()` and `getWatchTemperature()` to delegate to `WatchInfo.protocol`.

## Verification Plan

### Logic Verification
- Verify that standard models continue to send `"28"` and return calibrated percentages.
- Verify that MTG-B3000 sends `"280000"` and returns direct percentages (e.g. `0x11` -> `17%`).

### Manual Verification
- Review logs to ensure the 0/0 filter in `WatchConditionIO` remains effective for both protocols.
