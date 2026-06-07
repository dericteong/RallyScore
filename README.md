# Courtside Pickleball Scoreboard

Android-first MVP for a portable courtside pickleball scoreboard.

## MVP Scope

- Standard doubles side-out scoring.
- Starts at `0 - 0 - 2`.
- Serving-team-first score call.
- Server 1 / Server 2 handling.
- First-server exception.
- Game to 11, win by 2.
- Rally winner controls.
- Undo.
- Reset confirmation.
- Landscape-first scoreboard UI.
- Keep screen awake.
- Offline operation.

## Architecture

The scoring engine is pure Kotlin and independent from Android UI code:

- `domain/` contains immutable game state and scoring rules.
- `ui/` contains the Compose screen and ViewModel.
- `MainActivity` hosts the app and keeps the display awake.

This keeps scoring testable and reusable for future Wear OS and tablet display phases.

## Build

Open the project in Android Studio and sync Gradle.

The project uses:

- Kotlin
- Jetpack Compose
- Material 3
- MVVM with `StateFlow`
- JUnit tests for scoring logic
