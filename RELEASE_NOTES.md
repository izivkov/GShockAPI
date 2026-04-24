# GShockAPI Release Notes

## Overview
This update focuses on architectural refinement, improved BLE connectivity, and expanded feature support for G-Shock watches. We've significantly improved reliability on both legacy and cutting-edge Android versions.

## 🚀 New Features
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
