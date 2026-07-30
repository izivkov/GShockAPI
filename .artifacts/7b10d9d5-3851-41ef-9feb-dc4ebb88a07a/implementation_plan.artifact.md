# Implementation Plan: Protocol-Delegated HomeTime Logic

Refactor the Home Time retrieval logic to be delegated to the `WatchProtocol` implementations. This ensures that different watch generations use the correct hardware registers (`0x1F` vs `0x24`) and parsing offsets to avoid malformed ASCII output (the "yyyyyy" issue).

## User Review Required

> [!IMPORTANT]
> This change moves the hardware-specific details of Home City retrieval out of `HomeTimeIO.kt` and into the protocol generations.
> - **Standard Protocol** will continue using the World Cities register (`0x1F`).
> - **Analogue Protocol** (MTG models) will switch to the dedicated Home Time register (`0x24`) with a specialized offset.

## Proposed Changes

### Protocol Interface Extension
#### [MODIFY] [WatchProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/WatchProtocol.kt)
- Add `suspend fun getHomeTime(): String`.

### Standard Implementation
#### [MODIFY] [StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)
- Implement `getHomeTime()`:
    1. Request Register `0x1F`, Slot 0.
    2. Parse using `HomeTimeIOFunctional.parseHomeCity` with **offset 2**.

### Analogue (MTG) Implementation
#### [MODIFY] [AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)
- Implement `getHomeTime()`:
    1. Request Register `0x24`, Slot 0.
    2. Parse using `HomeTimeIOFunctional.parseHomeCity` with **offset 4** (matching B3000 log observations).

### IO Layer Refactoring
#### [MODIFY] [HomeTimeIO.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/io/HomeTimeIO.kt)
- Refactor `parseHomeCity` to accept a dynamic offset.
- Update `requestRaw` and `request` to be protocol-agnostic.

### Integration
#### [MODIFY] [GShockAPI.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/GShockAPI.kt)
- Update `getHomeTime()` to delegate to `WatchInfo.protocol.getHomeTime()`.

## Verification Plan

### Logic Verification
- Verify that MTG-B3000 now uses Register `0x24` and returns a valid city name.
- Verify that digital watches (B5600) still use Register `0x1F` and show the correct Home City.

### Manual Verification
- Review the logs to ensure no `FF FF FF` data is being parsed as ASCII for analogue models.
