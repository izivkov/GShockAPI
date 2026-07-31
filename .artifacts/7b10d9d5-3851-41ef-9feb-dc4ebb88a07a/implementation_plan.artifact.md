# Implementation Plan - Fixing Home Time Routing Hang

Fix the app hang during `getHomeTime()` on digital watches (like GW-B5600) by ensuring requests for Register `0x1F` are handled by their designated IO component (`WorldCitiesIO`), while dedicated Home Time requests (`0x24`) remain in `HomeTimeIO`.

## User Review Required

> [!IMPORTANT]
> This change resolves a routing conflict where `HomeTimeIO` was requesting data that was being delivered to `WorldCitiesIO`, causing the app to wait indefinitely for a response that never arrived.

## Proposed Changes

### Protocol Logic Refinement
#### [MODIFY] [StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)
- Update `getHomeTime()` to use `WorldCitiesIO.request(0)`. This ensures that the response to Register `0x1F` is caught by the correct listener.

#### [MODIFY] [AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)
- Update `getHomeTime()` to call the simplified `HomeTimeIO.requestRaw(0)`.

### IO Layer Cleanup
#### [MODIFY] [HomeTimeIO.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/io/HomeTimeIO.kt)
- Simplify `requestRaw()` to focus strictly on Register `0x24`.
- Reset `deferredResult` to `null` in `onReceived` to prevent state leakage and ensure thread-safe cleanup.

#### [MODIFY] [WorldCitiesIO.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/io/WorldCitiesIO.kt)
- Update `onReceived` to reset `deferredResult` to `null` after completion, matching the standard robust IO pattern used elsewhere.

## Verification Plan

### Logic Verification
- **GW-B5600**: Verify `getHomeTime()` completes and returns the correct city name via `WorldCitiesIO`.
- **MTG-B3000**: Verify `getHomeTime()` completes and returns the correct city name via `HomeTimeIO` (Register `0x24`).

### Manual Verification
- Run the test app and confirm it proceeds past the "Home Time" log line on a GW-B5600.
