# GShockAPI Release Notes - v1.6.0

## Overview
This major release introduces a new **Protocol-Based Architecture**, enabling seamless support for varied watch generations (Standard, Analogue/MTG, and MIP) while significantly improving thread safety and reliability across the entire IO layer.

## 🏗 Major Architectural Shift: Protocol-Delegated Logic
- **WatchProtocol Abstraction**: The library has been refactored to use a decoupled, table-driven protocol system. Core logic for time synchronization, timers, alarms, and settings is now delegated to specific protocol implementations:
    - `StandardProtocol`: Optimized for digital modules (B5600, etc.).
    - `AnalogueProtocol`: Specialized for premium analogue modules (MTG series).
    - `MipProtocol`: Designed for Memory-in-Pixel display watches.
- **Dynamic Routing**: `MessageDispatcher` now performs intelligent packet unwrapping and routing based on the active watch generation, eliminating hardcoded model checks in the IO layer.
- **Hardware Abstraction**: Implementation details like register IDs (e.g., 0x1F vs 0x24 for Home Time) and payload sizes (7-byte vs 15-byte timers) are now fully encapsulated within their respective protocols.

## 🚀 Enhanced Watch Support
- **Full MTG-B3000 & MTG-B3100 Integration**: 
    - Support for the specialized Home Time register (0x24).
    - Automated synchronization for watches with a "Second Dial" (sub-dial).
    - Support for extended status requests (`280000`) and 15-byte timer payloads.
    - Corrected battery level scaling for modules that report direct percentage values.
- **MIP Module Support**: Explicit protocol support for the latest generation of MIP-based modules.

## 🔒 Concurrency & Reliability Improvements
- **Thread-Safe IO Layer**: A system-wide refactoring of all IO handlers now utilizes synchronized state management and localized `CompletableDeferred` instances. This eliminates race conditions during high-frequency BLE notifications.
- **Robust Status Retrieval**: `WatchConditionIO` now includes intelligent filtering to ignore invalid `0/0` status responses that can occur during hardware initialization.
- **Register Routing Fixes**: Resolved a critical hang in `getHomeTime()` on standard watches by ensuring register `0x1F` and `0x24` responses are routed to their designated listeners.

## 🔧 Bug Fixes
- **Timer Accuracy**: Fixed a logic error in `TimerIO` where seconds were incorrectly calculated.
- **Battery Calibration**: Corrected battery limits for the MTG series to ensure accurate 0-100% reporting.
- **API Cleanup**: Removed redundant public methods and unused imports from the main `GShockAPI` facade.

---

# GShockAPI Release Notes - v1.5.0

## Overview
This major update expands watch model support, introduces a new notification API, and modernizes the build infrastructure to the latest Android and Kotlin standards.

## 🚀 New Features
- **App Notifications**: Added support for sending notifications directly to the watch display. This includes support for Calendar, Email, and SMS notifications with secure XOR encryption.
- **MTG-B1000 Support**: Full support for the MTG-B1000 series, including Second Dial configuration.
- **GW-BX5600 Support**: Added support for the GW-BX5600 model, including its new time format and multiple font support.
- **Enhanced Timekeeping**: Implementation of the new time format used by the latest Casio modules.
- **Step Counter Support**: Added step counter functionality for ABL-100 series watches.

## 🛠 Architectural & Build Improvements
- **JDK 21 Migration**: The library and app now target JDK 21, leveraging modern JVM features and better compatibility with the latest Android tools.
- **Gradle 9 & AGP 9**: Updated the project to use Gradle 9.4.1 and Android Gradle Plugin 9.2.1.
- **JitPack Build Fix**: Resolved toolchain compatibility issues on JitPack by streamlining JDK resolution and providing a dedicated `jitpack.yml` configuration.
- **BLE Manager Overhaul**: `IGShockManager` has been refactored to use a more robust "subscribe-all" approach for notifications, improving compatibility with various watch models without requiring per-model whitelists.
- **IO Layer Refinement**: Continued migration of the IO layer to functional programming principles, enhancing reliability and reducing boilerplate.

## 🔧 Reliability & Bug Fixes
- **Connection Stability**: Refined the BLE connection process to handle service discovery and characteristic mapping more reliably.
- **Android 16 Compatibility**: Preliminary support and testing for Android 16 (Baklava).
- **Service Discovery**: Improved detection of optional characteristics for varied watch models (SP_REQUEST, SP_DATA, NOTIFICATIONS).

---

# GShockAPI Release Notes - v1.4.74

## Overview
This update introduces significant functional programming improvements to the event handling and IO layers, along with enhanced watch model support and refined BLE connectivity. We continue to improve reliability across Android versions.

## ✨ Functional Programming Refactoring
- **Event System Redesign**: Refactored `ProgressEvent` and `MessageDispatcher` for cleaner, more composable event handling patterns using Kotlin coroutines and flows.
- **IO Layer Optimization**: Modernized IO classes (`EventsIO`, `SettingsIO`, `TimeIO`, `TimerIO`, etc.) with improved functional composition and reduced code complexity while maintaining backward compatibility.
- **Streamlined API**: Simplified event subscription API for more intuitive application integration, reducing boilerplate in host applications.
- **Code Quality**: Significant reduction in code duplication and improved maintainability across the IO module (2500+ lines refactored for better clarity and functional patterns).

## 🚀 New Features
- **DW-B5600 Support**: Added support for setting and retrieving reminders for the DW-B5600 watch model, and corrected its configuration (e.g., removing unsupported autolight).
- **Alarm Names Support**: Added the ability to set and retrieve custom names for watch alarms.
- **MIP Watch Enhancements**: Introduced support for classic fonts on MIP (Memory In Pixel) display watches.
- **Watch Scratchpad**: Implemented a new "Scratchpad" memory feature, allowing apps to store persistent state directly on the watch.
- **Companion Device Pairing**: Full integration with Android's Companion Device Manager for more reliable background connectivity and simplified pairing workflows.
- **Step Counter Support**: Added step counter functionality for ABL-100 watch models, enabling apps to read daily step counts via the life-log activity record characteristic.

## 🛠 Architectural Improvements
- **BLE Layer Refactoring**: Decoupled BLE scanning logic from the application layer, encapsulating it within the new `GShockScanner` class for better library modularity.
- **Optimized Fallback Scanning**: Improved the background scanning mechanism to use dynamic filters, reducing scan restarts and preserving battery life.
- **Code Quality**: Performed a comprehensive linting pass to resolve `MissingPermission`, `NewApi`, and `SwitchIntDef` warnings across the API.
- **Library Cleanup**: Removed redundant Nordic scanner dependencies to reduce library size and complexity.

## 🔧 Reliability & Bug Fixes
- **Legacy Android Support**: Resolved connection instability and duplicate discovery events on older Android versions (API 26-30).
- **Android 16 Compatibility**: Addressed callback and connectivity issues specifically for Android 16 (BAKLAVA).
- **Association Sync**: Fixed logic errors in watch association synchronization to ensure reliable presence observation cleanup when devices are removed.
- **Initial Connection**: Resolved a race condition that could prevent successful pairing during the very first connection attempt.

## 📚 Documentation
- **API Documentation**: Completed a full documentation pass for `IGShockAPI` and `GShockAPI` using KDoc.
- **Dokka Integration**: Refined the Dokka configuration to produce cleaner, more accurate technical documentation for developers.

---
*For more details on integrating these changes, please refer to the updated [README.md](file:///home/izivkov/projects/GShockAPI/README.md) and the generated Dokka documentation.*
