# Contributing to OBDvis

Thanks for taking an interest in OBDvis. This project aims to stay small,
correct, and practical: one Android app module, Jetpack Compose UI, Kotlin
coroutines/StateFlow, and no third-party architecture framework.

## Ways to Contribute

- Report reproducible bugs, especially around ELM327 adapters, PID parsing,
  Android Auto behavior, permissions, and diagnostics.
- Improve documentation for setup, supported adapters, known vehicle behavior,
  or safe use.
- Add a real-world vehicle, Android device, and adapter result to
  `TESTED_CONFIGURATIONS.md`.
- Add focused tests for parsing, polling, sample storage, diagnostics, and
  post-drive aggregation.
- Propose new diagnostics rules when the thresholds and evidence can be stated
  clearly.

## Before Opening an Issue

- Search existing issues first.
- Include the Android version, phone model, adapter model, vehicle year/make/model
  if relevant, and whether the issue reproduces in demo mode.
- Do not include VINs, license plates, precise locations, or other personal data.
- For diagnostic behavior, include the relevant sensor values or exported CSV if
  you are comfortable sharing them.

## Development Setup

Requirements:

- Android Studio Ladybug or newer, or an Android SDK usable from Gradle.
- JDK 17.
- Android device or emulator with API 23+.
- An ELM327-compatible Bluetooth OBD-II adapter for real vehicle testing.

Build from the repository root:

```powershell
cd android
.\gradlew.bat assembleDebug
```

On macOS/Linux:

```sh
cd android
./gradlew assembleDebug
```

Run tests:

```powershell
cd android
.\gradlew.bat test
```

## Project Conventions

- Keep changes small and well-contained.
- Prefer existing patterns over new abstractions.
- Preserve public APIs unless there is a clear reason to change them.
- Add or update tests when changing parsing, polling, diagnostics, storage, or
  user-visible behavior.
- Keep diagnostics deterministic and explainable. Thresholds should be explicit;
  avoid opaque heuristics.
- The app is privacy-conscious by design. Do not add network uploads or telemetry
  without an explicit design discussion.
- Bump `versionCode` and `versionName` in `android/app/build.gradle.kts` for
  meaningful app behavior changes.

## Pull Requests

Before opening a pull request:

- Run `.\gradlew.bat test` from `android/` when possible.
- Describe the user-visible change and any edge cases considered.
- Mention any tests that were not run and why.
- Keep unrelated refactors out of feature or bug-fix PRs.

## Releases

GitHub releases are created by `.github/workflows/release.yml` when a `v*` tag
is pushed. Before tagging, update `versionCode` and `versionName` in
`android/app/build.gradle.kts`; the tag must equal `v` followed by `versionName`
(for example, `v0.1.7`).

Create a `github-release` GitHub Actions environment with a required reviewer.
Configure these environment secrets before publishing a release:

- `ANDROID_APP_SIGNING_KEYSTORE_BASE64` — the permanent app-signing keystore
  encoded as base64.
- `ANDROID_APP_SIGNING_KEY_ALIAS` — the app-signing key alias.
- `ANDROID_APP_SIGNING_STORE_PASSWORD` — the app-signing keystore password.
- `ANDROID_APP_SIGNING_KEY_PASSWORD` — the app-signing key password.

Also configure `ANDROID_APP_SIGNING_CERT_SHA256` as a repository variable. Set
it to the app-signing certificate's SHA-256 fingerprint from `keytool -list -v`.
This prevents publishing an APK signed by an unexpected key. Keep any future
Play Store upload key separate from this GitHub APK release workflow.

The workflow runs unit tests and release lint, builds a signed APK, and attaches
the APK, its SHA-256 checksum, and the packaged third-party notices to a GitHub
release with generated notes. Existing releases and their assets are never
overwritten.

## AI Agent Notes

`AGENTS.md` is the source of truth for AI coding agent instructions in this
repository. `CLAUDE.md` points compatible tools to that file. These files are
kept public intentionally so automated contributions follow the same architecture
and safety expectations as human contributions.
