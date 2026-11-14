# Contributing to BitChord

Thank you for contributing to BitChord. Follow these guidelines to ensure an efficient development and review process.

## Proposing Contributions
- Substantial features, refactors, or UI redesigns should normally be discussed before implementation. Open an issue on GitHub to outline your proposal.
- Straightforward bug fixes, documentation improvements, or minor corrections can be submitted directly as a Pull Request.

## Branch and Pull Request Workflow
This repository has two long-lived branches:

- `main` contains the latest release. Do not open a pull request for a change against `main`.
- `next` contains the changes for the subsequent release.

Obey these rules:

1. Make a new branch from `next` for each change.
2. Open one pull request for each change, with `next` as its base branch. Thus one change can be reverted, or sent to BitChord, without the other changes.
3. Merge the pull request with a merge commit. Do not squash many changes into one commit.
4. Get the test build from `next`. The Check workflow builds the arm64 test APK for each pull request, and for each push to `next` and to `main`.
5. For a release, set the version and write `RELEASE_NOTES.md` on `next`. Then merge `next` into `main` with a merge commit. The Release workflow publishes the release only when a push to `main` changes `RELEASE_NOTES.md`.
6. Do not force-push to `main` or to `next`.

When contributing:
- Fork the repository and create a descriptive branch (e.g., `fix/streaming-buffer`, `docs/translation-guide`).
- Keep pull requests focused on a single change or bug fix. Avoid mixing unrelated refactors, cosmetic tweaks, or mass reformatting with functional changes.
- Keep your branch reasonably synchronized with the target base branch.
- Provide a clear PR description explaining what was changed and why.
- Include relevant testing and validation details with your PR.
- Address review feedback promptly and keep discussions focused on technical merits.

## Development Setup
The project requires the following tools:
- **JDK**: Java Development Kit 17 (Eclipse Temurin 17 recommended).
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- **C/C++ NDK & CMake**: CMake 3.22.1+ and Android NDK (for native audio DSP components configured under `app/src/main/cpp`).
- **Listen Together Backend (Optional)**: Go 1.22+ if developing or testing the party server (`backend/`).

## Build and Test Commands
Run Gradle commands from the repository root.

Build:
```bash
./gradlew assembleDevDebug
```

Windows:
```powershell
.\gradlew.bat assembleDevDebug
```

Tests:
```bash
./gradlew testDevDebugUnitTest
```

Windows:
```powershell
.\gradlew.bat testDevDebugUnitTest
```

Run a specific test:
```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.playback.audio.DirectAudioStreamingRegressionTest"
```

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests, must not introduce new failures, and should document known pre-existing failures when applicable.
- Keep diffs focused and minimal. Avoid unnecessary third-party dependencies.

## Audio Changes and Telemetry
For audio changes: application telemetry must not be presented as proof of physical hardware behavior unless that hardware behavior was actually verified.

Distinguish clearly between source metadata, decoder format, internal DSP format, AudioTrack output, AudioFlinger/HAL state, advertised device capability, and external physical endpoint behavior.

## Review Process
All pull requests require review and approval by repository maintainers before merging. Maintainers review changes for correctness, architecture fit, and maintainability.

## Dependencies and Licensing
BitChord is licensed under the **GNU General Public License v3.0 (GPLv3)**.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
