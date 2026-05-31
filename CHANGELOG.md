# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-05-31

### Added
- Android SDK (Kotlin) with session tracking, screen tracking, and event tracking
- React Native SDK (TypeScript) compatible with RN 0.70+ and Expo SDK 49+
- Auto screen tracking via `ActivityLifecycleCallbacks` (Android) and React Navigation (RN)
- Offline event queue with Room database (Android) and AsyncStorage (RN)
- Automatic flush when connectivity is restored
- Remote on/off control via `/sdk/status` endpoint polling
- Heartbeat ping every 5 minutes to confirm active sessions
- Batch event upload to minimize network requests
- Device info collection (no PII — locale/timezone only, no GPS)
- Day counter for tracking active days within a 14-day testing period
- Deep link support for `launchedFromTestLab` flag
- ProGuard/R8 rules for Android release builds
- OpenAPI 3.1 spec for the SDK API
- MIT license
