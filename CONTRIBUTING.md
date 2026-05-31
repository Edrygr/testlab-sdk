# Contributing to TestLab SDK

## Development setup

### Android

```bash
cd android
./gradlew assembleDebug
```

Run unit tests:
```bash
./gradlew :testlab-sdk:test
```

### React Native

```bash
cd react-native
npm install
npm run build
```

Run example app:
```bash
cd react-native/example
npm install
npx react-native run-android
```

## Pull Request guidelines

- One feature or fix per PR
- Add tests for new behaviour
- Update `CHANGELOG.md` under an `[Unreleased]` heading
- Follow existing code style (Kotlin: official style guide; TypeScript: strict mode, no `any`)

## Reporting issues

Open an issue on GitHub with:
1. SDK version and platform (Android / React Native)
2. Minimal reproducible example
3. Expected vs actual behaviour

## Privacy policy for contributions

Do not add any collection of PII (names, emails, phone numbers, GPS coordinates).
See [README — What we collect](#what-we-collect) for the full data inventory.
