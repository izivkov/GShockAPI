# Implementation Plan: Protocol-Based Refactoring

Refactor the library to support multiple watch protocols (Standard vs. Analogue/MTG) using a decoupled, table-driven approach. This removes complex branching logic from `MessageDispatcher` and makes the code more maintainable.

## User Review Required

> [!IMPORTANT]
> This change introduces a `WatchProtocol` abstraction. All incoming data handling and key extraction logic will move from the central `MessageDispatcher` into specific protocol implementations.

## Proposed Changes

### Protocol Abstraction
#### [NEW] [WatchProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/WatchProtocol.kt)
- Define `interface WatchProtocol`.
- Properties:
    - `dataReceivedHandlers: Map<Int, (String) -> Unit>`
- Methods:
    - `extractKey(data: String): Int?`
    - `unwrapPayload(data: String, key: Int): String`

#### [NEW] [StandardProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/StandardProtocol.kt)
- Implements `WatchProtocol` for digital models (B5600, etc.).
- Simple first-byte key extraction.
- No unwrapping logic.

#### [NEW] [AnalogueProtocol.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/AnalogueProtocol.kt)
- Implements `WatchProtocol` for MTG-B1000/B3000/B3100.
- Implements `0x28` envelope detection in `extractKey`.
- Implements payload unwrapping (skipping 3 or 4 byte headers) in `unwrapPayload`.

### Integration
#### [MODIFY] [WatchInfo.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/WatchInfo.kt)
- Add `val protocol: WatchProtocol` to `ModelInfo`.
- Assign `AnalogueProtocol` to MTG models and `StandardProtocol` to others.

#### [MODIFY] [MessageDispatcher.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/casio/MessageDispatcher.kt)
- Remove the hardcoded `dataReceivedHandlers` table.
- Update `onReceived(data: String)` to take an optional `WatchProtocol` parameter.
- Use the protocol's `extractKey`, `unwrapPayload`, and `dataReceivedHandlers` to route the message.

#### [MODIFY] [WatchDataListener.kt](file:///home/izivkov/projects/GShockAPI/api/src/main/java/org/avmedia/gshockapi/utils/WatchDataListener.kt)
- Pass `WatchInfo.protocol` to `MessageDispatcher.onReceived(it, protocol)`.

## Verification Plan

### Automated Tests
- Create unit tests for both `StandardProtocol` and `AnalogueProtocol` to verify:
    - Correct key extraction for standard and wrapped packets.
    - Correct payload unwrapping for `0x28` envelopes.
- Verify that the dispatcher correctly routes to the protocol's specific handlers.

### Manual Verification
- Review the refactored code to ensure no model-specific `if` statements remain in the core dispatch loop.
