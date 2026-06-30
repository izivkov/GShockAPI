# GShockAPI Release Notes - v1.4.76

## Overview
This update introduces support for new watch models, significant refactoring for dynamic BLE characteristics discovery, and critical bug fixes for Jetpack Compose UI stability.

## 🚀 New Features
- **New Watch Models**: Added comprehensive support for MTG-B1000 and GW-BX5600 watches, including their specific time synchronization protocols.
- **Dynamic Characteristics**: Refactored the initialization sequence to dynamically discover and register supported BLE characteristics from the watch directly via GATT services, eliminating the need for manual, hard-coded characteristic injections.

## 🛠 Architectural Improvements
- **HomeTimeIO Refactoring**: Resolved technical debt within the `HomeTimeIO` and related IO modules by enforcing the Functional/IO design pattern. Standardized method signatures, corrected async handling, and centralized constants.

## 🔧 Reliability & Bug Fixes
- **LazyColumn Crash Fix**: Resolved a critical `java.lang.IndexOutOfBoundsException` occurring during Jetpack Compose `LazyColumn` scroll or update operations by stabilizing the underlying data collection and state management.

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
