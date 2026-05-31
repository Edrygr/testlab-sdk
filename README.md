# TestLab SDK

An identity and engagement tracking layer for [TestLab](https://testlab.app)-verified testers. It sits on top of your existing analytics stack and connects tester engagement metrics — sessions, screens visited, events — to a verified tester identity. It does **not** replace Firebase or Play Console.

> **Optional.** If you don't integrate the SDK, TestLab still works. The SDK adds verified-identity data to your tester engagement dashboard.

---

## What we collect

| Field | Why |
|---|---|
| Session start / end timestamps | Measure engagement duration |
| Session duration (seconds) | Active time per session |
| Day number (1–14) | Which day of the testing period |
| Screens visited + time per screen | Feature coverage |
| Custom events + properties | Developer-defined actions |
| Device manufacturer & model | Device diversity reporting |
| Android version & API level | Compatibility tracking |
| Screen resolution & density | UI rendering context |
| Locale (e.g. `es_MX`) | Regional distribution |
| Timezone | Session time context |
| Network type (WiFi / 4G / 5G) | Network condition reporting |
| App version & build number | Which build is being tested |
| Tester ID (anonymous hash) | Link sessions to a verified tester |

## What we do NOT collect

- Name, email, phone number, or any personal identifier
- GPS or precise location
- Contacts, photos, files, or any device content
- Clipboard contents
- Any data from other apps

All tester IDs are opaque hashes — they are never an email address or real name.

---

## Installation

### Android

**settings.gradle**
```gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

**build.gradle (app)**
```gradle
dependencies {
    implementation 'com.github.testlab-app:testlab-sdk-android:1.0.0'
}
```

### React Native

```bash
npm install testlab-sdk-rn
# or
yarn add testlab-sdk-rn
```

**Peer dependencies** (install if not already present):
```bash
npm install @react-native-async-storage/async-storage \
            @react-native-community/netinfo \
            @react-navigation/native
```

---

## Quickstart — 5 minutes

### Android

```kotlin
// ExampleApplication.kt
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TestLabSDK.init(
            context = this,
            config = TestLabConfig(
                apiKey = "tl_live_xxxxxxxxxxxx",
                appId = "com.myapp"
            )
        )
    }
}
```

Handle the deep link from the TestLab app:
```kotlin
// MainActivity.kt — onCreate
val uri = intent?.data
if (uri?.getQueryParameter("from") == "testlab") {
    TestLabSDK.launchedFromTestLab()
}
val testerId = uri?.getQueryParameter("tester")
if (testerId != null) TestLabSDK.identify(testerId)
```

### React Native

```typescript
// App.tsx
import { TestLabSDK, useScreenTracker } from 'testlab-sdk-rn'

await TestLabSDK.init({
  apiKey: 'tl_live_xxxxxxxxxxxx',
  appId: 'com.myapp',
})

// Auto-track screens with React Navigation
const navigationRef = useNavigationContainerRef()
useScreenTracker(navigationRef)
```

If launched from the TestLab app (via deep link params):
```typescript
if (route.params?.from === 'testlab') {
  TestLabSDK.launchedFromTestLab()
}
if (route.params?.tester) {
  await TestLabSDK.identify(route.params.tester)
}
```

---

## API Reference

### Android (`TestLabSDK` object)

| Method | Description |
|---|---|
| `init(context, config)` | Initialize the SDK. Must be called in `Application.onCreate()`. Silent if the API key is inactive. |
| `identify(testerId)` | Link this session to a verified TestLab tester ID. |
| `trackScreen(screenName)` | Manually record a screen view. Auto-tracking via `ActivityLifecycleCallbacks` is enabled by default. |
| `trackEvent(eventName, properties?)` | Record a custom event with optional properties map. |
| `launchedFromTestLab()` | Mark that this session was launched via the TestLab app. Call after receiving the deep link. |
| `registerTotalScreenCount(count)` | Declare total screen count for coverage percentage calculation. |
| `getScreenCoverage()` | Returns coverage percentage (`Float?`). Null if total not registered. |
| `shutdown()` | Shut down the SDK (called automatically by remote control). |

### TestLabConfig (Android)

```kotlin
TestLabConfig(
    apiKey: String,              // Required — from TestLab dashboard
    appId: String,               // Required — your package name
    syncInterval: Long = 60_000, // Milliseconds between background syncs
    debug: Boolean = false,      // Enable console logs
    baseUrl: String = "https://api.testlab.app"
)
```

### React Native (`TestLabSDK` instance)

| Method | Returns | Description |
|---|---|---|
| `init(config)` | `Promise<void>` | Initialize. Call once before anything else. |
| `identify(testerId)` | `Promise<void>` | Link tester identity. |
| `trackScreen(name)` | `void` | Record a screen view. |
| `trackEvent(name, props?)` | `void` | Record a custom event. |
| `launchedFromTestLab()` | `void` | Mark launch source as TestLab. |
| `shutdown()` | `void` | Stop all SDK activity. |
| `isActive` | `boolean` | Whether the SDK is currently active. |
| `testerId` | `string \| null` | Current linked tester ID. |

### Hooks (React Native)

#### `useTestLab()`

```typescript
const { isActive, testerId, trackScreen, trackEvent } = useTestLab()
```

#### `useScreenTracker(navigationRef)`

Auto-tracks every screen change from React Navigation:

```typescript
const navigationRef = useNavigationContainerRef()
useScreenTracker(navigationRef)
// <NavigationContainer ref={navigationRef}>
```

### Predefined events

These are tracked automatically — you don't need to call them manually:

| Event | Triggered when |
|---|---|
| `session_start` | App comes to foreground |
| `session_end` | App goes to background |
| `screen_view` | Screen changes (auto or manual) |
| `launched_from_testlab` | `launchedFromTestLab()` is called |
| `sdk_activated` | SDK activates after status check |
| `sdk_deactivated` | `shutdown()` is called |

---

## Remote control

The developer can deactivate the SDK from the TestLab dashboard at any time. The SDK polls `/sdk/status` on every `syncInterval` (default: 60 seconds). Possible states:

| State | Meaning |
|---|---|
| `active` | SDK running normally |
| `inactive` | Disabled by the developer from the dashboard |
| `suspended` | Disabled by TestLab (app promoted to production) |
| `expired` | 14-day testing period ended |

When the state transitions to anything other than `active`, the SDK calls `shutdown()` silently — no logs, no requests, no battery usage.

---

## Offline support

If the device has no connectivity:
- Events are queued in memory (max 500 events)
- Session data is persisted locally (Room DB on Android, AsyncStorage on RN)
- When connectivity is restored, all pending data is flushed automatically

---

## Privacy

- All network requests use HTTPS (TLS 1.2+)
- The SDK is completely silent when inactive — no logs, no requests, no battery drain
- API keys should be kept in `local.properties` or environment variables, not committed to version control
- Android: ProGuard/R8 rules are included in the library to obfuscate SDK internals

---

## License

MIT — see [LICENSE](./LICENSE).
