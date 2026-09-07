# PickleCast

Wear. Play. Score.

Flexible Android/Wear OS scoreboard ecosystem for live pickleball games. PickleCast works with different combinations of devices: Watch Only, Phone Only, Watch + Phone, and Watch + Phone + shared display. Additional devices enhance the experience but are not required.

## Current Product

The current MVP foundation is built with Kotlin, Jetpack Compose, and MVVM. It supports:

- Landscape-first phone scoreboard UI for Phone Only scoring and display output.
- Wear OS scoring prototype using the shared scoring engine.
- Player/team name setup before a game.
- Standard doubles side-out scoring rules.
- Game start at `0 - 0 - 2`.
- Rally entry by tapping the team that won the rally.
- Automatic serving team, server number, and side-out transitions.
- Unlimited undo within the current match.
- End game confirmation that returns to setup.
- Spoken score calls using Android Text-to-Speech.
- Offline operation.
- Keep-screen-awake behavior.

The connected product direction is watch-controlled and phone-owned: when a phone is present, the phone is the source of truth and the watch acts as a remote control. Phone Only is a first-class experience. Watch Only remains valid for casual play, demo mode, and backup mode.

Supported modes:

- Mode 0: Watch Only, with watch-owned setup, scoring, display, undo, and voice.
- Mode 1: Phone Only, with phone-owned setup, scoring, display, undo, and voice.
- Mode 2: Watch + Phone, with watch commands and phone-owned scoring/state/voice/display.
- Mode 3: Watch + Phone + Android Tablet, with tablet as shared display.
- Mode 4: Watch + Phone + Portable Monitor, with monitor as mirrored shared display.

Timed play is the current default assumption, so scores continue beyond 11. Formal match completion can be added later as a configurable mode.

## Project Structure

- `app/` - Phone Android app, source of truth when present, scoring hub/display source, first-class phone scoring UI, ViewModel, TTS, launcher activity.
- `wear/` - Wear OS app, Watch Only scorer, and connected remote controller.
- `shared/` - Pure Kotlin scoring domain and unit tests.
- `docs/` - Source-of-truth project documentation.

## Build And Test

Open the project in Android Studio and sync Gradle.

Useful commands:

```bash
./gradlew :shared:test :app:test
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
```

Install only the phone app on a connected phone:

```bash
./gradlew :app:installDebug
```

Avoid running root `./gradlew installDebug` against a phone because it installs every installable module, including the standalone Wear prototype, which creates an extra launcher icon.

For local development on this Mac, Android Studio's bundled JBR has been used successfully:

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
ANDROID_HOME=/Users/dericteong/Library/Android/sdk \
./gradlew :shared:test :app:test :app:assembleDebug
```

## Documentation

Start with:

- [docs/PRD.md](docs/PRD.md)
- [docs/Architecture.md](docs/Architecture.md)
- [docs/ScoringRules.md](docs/ScoringRules.md)
- [docs/Roadmap.md](docs/Roadmap.md)
- [AGENTS.md](AGENTS.md)
