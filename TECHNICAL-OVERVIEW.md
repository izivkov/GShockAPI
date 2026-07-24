# GShockAPI Technical Overview

## Introduction
The **GShockAPI** is a specialized Android library designed to facilitate Bluetooth Low Energy (BLE) communication with Casio G-Shock watches. It abstracts the complex Casio-specific protocol into a clean, modern Kotlin API, allowing developers to interact with various watch features such as time synchronization, alarms, reminders, and settings.

---

## Architecture Overview

The project is structured into two main Gradle modules:
- **`:api`**: The core library containing the BLE logic and Casio protocol implementation.
- **`:app`**: A sample application demonstrating how to use the library.

### High-Level Layers
1.  **Public API Layer (`GShockAPI`)**: Provides high-level Kotlin `suspend` functions for app developers.
2.  **IO Layer (`org.avmedia.gshockapi.io`)**: Handles encoding and decoding of specific data types (Alarms, Time, Settings) into raw byte arrays.
3.  **Protocol Layer (`org.avmedia.gshockapi.casio`)**: Defines constants and dispatches messages according to the Casio protocol.
4.  **BLE Management Layer (`org.avmedia.gshockapi.ble`)**: Manages device scanning, connection lifecycle, and low-level GATT operations using the **NordicSemi BLE library**.
5.  **Event System (`ProgressEvents`)**: A custom reactive event bus for broadcasting library states (e.g., connection status, data availability).

---

## Core Components

### 1. `GShockAPI`
The main entry point for developers. It implements `IGShockAPI` and provides functions like:
- `waitForConnection()`: Coroutine-based wait for device connection.
- `setTime()`: Synchronizes watch time with the phone.
- `getAlarms()` / `setAlarms()`: Manages the 5 on-watch alarms.
- `getSettings()` / `setSettings()`: Controls watch behavior (date format, button tones, etc.).

### 2. `Connection` & `IGShockManager`
Handles the underlying BLE connection.
- Uses `BleManager` from Nordic Semiconductor for robust BLE operations.
- **Dynamic Characteristic Discovery**: Instead of hardcoding handles, it discovers all characteristics and subscribes to notifications on all that support it, ensuring compatibility across different watch models (e.g., GW-B5600 vs. DW-H5600).

### 3. `IO` Classes (e.g., `TimeIO`, `AlarmsIO`)
These classes follow a "Command" pattern. Each class is responsible for:
- Requesting data (`request()`).
- Encoding data for writing to the watch (`set()`).
- Parsing raw hex responses into high-level Kotlin objects (e.g., `Alarm`).

### 4. `ProgressEvents`
A central event hub using `MutableSharedFlow`. It allows multiple parts of the application to observe:
- `ConnectionSetupComplete`: When the watch is ready for commands.
- `Disconnect`: When the connection is lost.
- `ButtonPressedInfoReceived`: Identifies which button initiated the connection (e.g., for "Action Button" triggers).

---

## Communication Protocol

### BLE Services & Characteristics
The library primarily uses the Casio Watch Features Service:
- **Service UUID**: `26eb000d-b012-49a8-b1f8-394fb2032b0f`
- **Characteristics**:
    - `0x2c`: Read request (GET).
    - `0x2d`: Write/Notify (SET/Response).
    - `0x30`: Notifications (App notifications).
    - `0x2e`/`0x2f`: Configuration request/data (SP modes).

### Command Structure
Commands are sent as byte arrays. The `IOFunctional` utility handles hex-to-byte conversion. Commands typically start with a feature code (e.g., `0x15` for Alarms) followed by parameters.

---

## Technology Stack

| Technology | Purpose |
| :--- | :--- |
| **Kotlin** | Primary language, utilizing Coroutines for async IO. |
| **NordicSemi BLE** | Reliable BLE GATT management. |
| **Timber** | Structured logging. |
| **Gson** | JSON serialization for message dispatching. |
| **Dokka** | Documentation generation. |
| **JitPack** | Distribution and dependency management. |

---

## Project Structure
```text
api/src/main/java/org/avmedia/gshockapi/
├── ble/           # BLE Connection and Manager logic
├── casio/         # Protocol constants and message dispatcher
├── io/            # Data-specific command handlers (Time, Alarms, etc.)
├── utils/         # Helper classes
├── GShockAPI.kt   # Main public API
└── ProgressEvent.kt # Event system
```

---

## Summary
The GShockAPI project stands out for its clean separation of concerns, robust BLE handling via Nordic's library, and a reactive event system that simplifies asynchronous Bluetooth communication for Android developers. Its generic approach to characteristic handling makes it adaptable to a wide range of Casio Bluetooth-enabled watches.
