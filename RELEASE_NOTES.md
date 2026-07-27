# GShockAPI Release Notes - v1.5.1

## Overview
This update adds support for the Casio MTG-B3000 series and introduces a more robust, feature-based architectural approach for handling watch capabilities.

## 🚀 New Features
- **MTG-B3000 Support**: Full support for the MTG-B3000 series, including specialized city synchronization, single alarm restriction, and model-specific setting payloads.
- **HomeTime Register Support**: Implementation of the dedicated Home Time register (`0x24`) used by premium models for city data, complementing the standard World Cities (`0x1F`) register.
- **Fine Watch Condition**: Added support for high-precision battery and temperature reporting via the new `hasFineWatchCondition` capability flag, used by premium hardware modules.

## 🛠 Architectural Refactoring
- **Feature-Based Logic**: Refactored the entire library to drive protocol logic using generic feature flags (capabilities) instead of explicit watch model checks. This improves maintainability and makes supporting new models zero-config for the IO logic.
- **New Capability Flags**: Introduced flags for `hasHomeTime`, `hasTimeFormat`, `hasFineWatchCondition`, `settingsSize`, and `timerSize` in `WatchInfo.kt`.
- **Dynamic Payload Sizing**: IO handlers now automatically adjust byte array lengths for settings and timers based on the connected watch's capabilities.
- **Clean Code Pass**: Removed redundant package qualifiers and standardized imports across all IO classes for cleaner, more idiomatic Kotlin.

## 🔧 Reliability & Bug Fixes
- **Battery & Temperature Reporting**: Fixed an issue where the MTG-B3000 would report 0% battery and 0°C. The library now supports direct percentage reporting and handles the extended 3-byte status request format.
- **Watch Condition Robustness**: Implemented filtering to ignore "null" status responses from hardware modules during initialization.
- **City Sync Logic**: Improved the time-setting sequence to correctly choose between World City and Home Time registers depending on the hardware feature set.
- **Single Alarm Handling**: Standardized the alarm IO layer to respect watches with restricted alarm counts (e.g., MTG-B3000).

## ✅ Verification
- **Unit Testing**: Expanded the test suite to include MTG-B3000 specific logic, verifying correct binary encoding for timers, settings, and watch condition status.

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
