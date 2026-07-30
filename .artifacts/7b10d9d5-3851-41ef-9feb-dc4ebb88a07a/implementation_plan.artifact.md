# Implementation Plan - Protocol-Delegated Alarms

Refactor the alarm functionality to be delegated to the `WatchProtocol` implementations. This ensures that the library correctly handles watches with limited alarm slots (like MTG-B3000's single alarm) and those without hourly chime support.

## User Review Required

> [!IMPORTANT]
> This change moves the alarm execution sequence and chime handling out of `AlarmsIO.kt` and into the specific protocol generations.
> - **Standard Protocol** manages 5 alarms and the hourly chime.
> - **Analogue Protocol** (MTG models) manages a single alarm and suppresses chime data.

## Proposed Changes

### Protocol Interface Extension
#### [MODIFY] [WatchProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/WatchProtocol.kt)
- Add `suspend fun getAlarms(): ArrayList<Alarm>`.
- Add `fun setAlarms(alarms: ArrayList<Alarm>)`.

### Standard Implementation
#### [MODIFY] [StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)
- Implement `getAlarms()`:
    - Sequentially request Registers `0x15` and `0x16`.
    - Wait for all 5 alarms to be collected via `AlarmsIO.request()`.
- Implement `setAlarms()`:
    - Delegate to `AlarmsIO.set(alarms)` (standard 5-alarm encoding).

### Analogue (MTG) Implementation
#### [MODIFY] [AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)
- Implement `getAlarms()`:
    - Only request Register `0x15`.
    - Ensure `AlarmsIO.request()` completes after the first alarm is received (respecting `WatchInfo.alarmCount`).
- Implement `setAlarms()`:
    - Ensure only the first alarm in the list is encoded and sent to the watch.

### IO Layer Refactoring
#### [MODIFY] [AlarmsIO.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/io/AlarmsIO.kt)
- Refactor `AlarmsIOFunctional` to remove `WatchInfo` checks.
- Expose generic building blocks for protocols to construct their specific write commands.
- Update `onReceived()` to correctly handle the completion signal based on the active watch's `alarmCount`.

### Integration
#### [MODIFY] [GShockAPI.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/GShockAPI.kt)
- Update `getAlarms()` and `setAlarms()` to delegate entirely to `WatchInfo.protocol`.

## Verification Plan

### Logic Verification
- Verify that MTG-B3000 only makes one BLE request for alarms.
- Verify that standard models continue to make two requests and collect all 5 alarms.
- Ensure that the Hourly Chime UI remains disabled or hidden for models where `hasHourlyChime` is false.

### Manual Verification
- Review the generated BLE commands to ensure they match the Module-specific requirements in the HCI logs.
