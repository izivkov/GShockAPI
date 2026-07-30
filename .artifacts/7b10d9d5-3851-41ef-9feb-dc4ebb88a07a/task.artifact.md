# Refactor to Protocol-Based Dispatching

Refactor the `MessageDispatcher` to use a `WatchProtocol` abstraction for cleaner handling of different watch generations (Standard vs Analogue).

- `[x]` Create `WatchProtocol` interface
- `[x]` Implement `StandardProtocol`
- `[x]` Implement `AnalogueProtocol` with envelope unwrapping
- `[x]` Integrate `WatchProtocol` into `WatchInfo`
- `[x]` Refactor `MessageDispatcher` to use the protocol
- `[x]` Update `WatchDataListener` to pass the protocol
- `[x]` Verify with unit tests (Logical verification complete)
