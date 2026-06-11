# Portable Courtside Pickleball Scoreboard

Single-phone Android scoreboard for live pickleball games, optimized for courtside readability and fast rally entry.

## Current Product

The MVP is an Android app built with Kotlin, Jetpack Compose, and MVVM. It supports:

- Landscape-first phone scoreboard UI.
- Player/team name setup before a game.
- Standard doubles side-out scoring rules.
- Game start at `0 - 0 - 2`.
- Rally entry by tapping the score for the team that won the rally.
- Automatic serving team, server number, and side-out transitions.
- Unlimited undo within the current match.
- End game confirmation that returns to setup.
- Spoken score calls using Android Text-to-Speech.
- Offline operation.
- Keep-screen-awake behavior.
- Wear OS companion app prototype using the shared scoring engine.

Timed play is the current default assumption, so scores continue beyond 11. Formal match completion can be added later as a configurable mode.

## Project Structure

- `app/` - Phone Android app, Compose UI, ViewModel, TTS, launcher activity.
- `wear/` - Standalone Wear OS app prototype.
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
