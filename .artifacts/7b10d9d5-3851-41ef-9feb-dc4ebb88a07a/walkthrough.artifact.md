# Walkthrough: Protocol-Based Refactoring

I have successfully refactored the library's dispatching mechanism to support different watch protocols (Standard vs. Analogue/MTG) using a decoupled, table-driven architecture. This eliminates complex branching logic in the core `MessageDispatcher` and simplifies the addition of future watch models.

## Changes Made

### 1. Protocol Abstraction Layer
- **[WatchProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/WatchProtocol.kt)**: Introduced an interface that defines how each protocol generation should handle key extraction and payload unwrapping.
- **[StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)**: Implementation for digital models (B5600, etc.). It uses standard first-byte key extraction and no unwrapping.
- **[AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)**: Implementation for premium analogue models (MTG series). It contains the logic to detect and unwrap "enveloped" packets (like responses wrapped in `0x28` headers).

### 2. Table-Driven Dispatching
- **[MessageDispatcher.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/MessageDispatcher.kt)**: Refactored `onReceived` to be entirely generic. It no longer contains any `if (key == 0x28)` or model-specific checks. Instead, it delegates to the provided `WatchProtocol` to parse and unwrap data.
- **[WatchDataListener.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/utils/WatchDataListener.kt)**: Now passes the watch's specific protocol from `WatchInfo` to the dispatcher.

### 3. Capability Integration
- **[WatchInfo.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/WatchInfo.kt)**:
    - Added a `protocol` property to `ModelInfo`.
    - Mapped MTG-B1000 and MTG-B3000 to use `AnalogueProtocol`.
    - Standard digital models default to `StandardProtocol`.

### 4. Logic Re-application
- Re-applied feature-based logic for **MTG-B3000** in `TimeIO.kt`, `HomeTimeIO.kt`, `AlarmsIO.kt`, `SettingsIO.kt`, and `TimerIO.kt` that was lost during a previous external sync/revert.
- Ensured thread-safety and proper coroutine handling with `synchronized` blocks and `CompletableDeferred` in all IO handlers.

## Verification Results

### Architecture Confirmation
- The `MessageDispatcher` is now a clean router that works with any implementation of `WatchProtocol`.
- Adding support for a new watch with a completely different command set now only requires a new `WatchProtocol` implementation and a mapping in `WatchInfo.kt`.

### Protocol Accuracy
- `AnalogueProtocol` correctly handles both "bundled" (`ints[1] == 0x01`) and "standard" (`ints[1] == 0x00`) envelopes as observed in the MTG series HCI logs.

> [!TIP]
> This refactoring follows the Open-Closed Principle: the library is now open for extension (new protocols) but closed for modification in the core dispatching loop.
