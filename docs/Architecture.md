# Architecture

## Summary

The app uses a shared-domain architecture:

```text
                 +----------------+
                 | shared module  |
                 | scoring engine |
                 +-------+--------+
                         |
          +--------------+--------------+
          |                             |
   +------+-------+              +------+------+
   | phone app    |              | wear app    |
   | Compose/MVVM |              | Compose     |
   +--------------+              +-------------+
```

The scoring engine is independent of Android so rules can be reused and unit-tested.

## Phone App

`app/src/main/java/com/courtside/pickleball/MainActivity.kt`

- Adds `FLAG_KEEP_SCREEN_ON`.
- Enables edge-to-edge.
- Hosts `ScoreboardApp`.
- Uses `ScoreboardViewModel`.

`ScoreboardViewModel`

- Owns current `GameState`.
- Exposes `StateFlow<GameState>`.
- Starts matches from setup values.
- Records rally winners.
- Maintains undo history.
- Resets/end match state.

`ScoreboardApp`

- Owns transient UI-only setup state.
- Renders setup or scoreboard screen.
- Handles Text-to-Speech score calls.
- Uses fixed `fontScale = 1f` for scoreboard layout stability.

## Shared Domain

`shared/src/main/kotlin/com/courtside/pickleball/domain/`

Key objects:

- `Team`: A or B.
- `ServerNumber`: One or Two.
- `GameSettings`: names and target/win settings.
- `GameState`: immutable score and serving state.
- `PickleballScoringEngine`: applies rally-winner rules.

The domain model is intentionally small. The app should prefer adding tests to shared domain before extending score logic.

## Wear App

`wear/` is a separate Android application with package `com.courtside.pickleball.wear`.

Current behavior:

- Uses the shared scoring engine.
- Keeps local state in Compose.
- Provides A WON, B WON, UNDO, and RESET controls.

Future remote-control behavior should add an explicit synchronization layer rather than sharing ViewModels between phone and Wear.

## Dependencies

Current dependency direction:

```text
app  -> shared
wear -> shared
shared -> no Android dependency
```

Do not introduce dependencies from `shared` back to Android modules.

## Persistence

Current match state is in memory only.

Future persistence options:

- `SavedStateHandle` for process recreation.
- DataStore for in-progress match restore.
- Room only if match history/statistics become real product scope.
