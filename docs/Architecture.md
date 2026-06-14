# Architecture

## Summary

RallyScore is a flexible scoring ecosystem that supports multiple device combinations. Additional devices enhance the experience but are not required. The phone is the source of truth whenever a phone is present. Watch Only mode remains valid for casual play, demo mode, and backup mode.

Supported modes:

- Mode 0: Watch Only.
- Mode 1: Phone Only.
- Mode 2: Watch + Phone.
- Mode 3: Watch + Phone + Android Tablet.
- Mode 4: Watch + Phone + Portable Monitor.

Canonical connected architecture:

```text
    +----------------+
    | Wear OS watch  |
    | command input  |
    +-------+--------+
            |
            v
    +-------+--------+        +----------------+
    | Android phone  |------->| shared module  |
    | scoring hub    |        | scoring engine |
    +-------+--------+        +----------------+
            |
            v
    +-------+--------+
    | passive display|
    | monitor/tablet |
    +----------------+
```

Phone Only is a first-class experience. Watch Only may own standalone match state. In Watch + Phone mode, the watch becomes a remote control and the phone owns synchronized match state, scoring decisions, undo history, voice timing, and display output. The scoring engine is independent of Android so rules can be reused and unit-tested.

## Phone App

`app/src/main/java/com/courtside/pickleball/MainActivity.kt`

- Adds `FLAG_KEEP_SCREEN_ON`.
- Enables edge-to-edge.
- Hosts `ScoreboardApp`.
- Uses `ScoreboardViewModel`.

`ScoreboardViewModel`

- Observes the phone `ScoreboardStore`, which owns current `GameState`.
- Exposes `StateFlow<GameState>`.
- Starts matches from setup values.
- Records rally winners.
- Maintains undo history.
- Resets/end match state.
- Shares phone-owned state with watch command handling.

`RallyScorePhoneHub`

- Initializes Wear OS Data Layer clients.
- Receives watch commands through `MessageClient`.
- Applies commands through `ScoreboardStore`.
- Publishes phone-owned score snapshots through `DataClient`.
- Exposes watch connection status for phone UI.

`ScoreboardApp`

- Owns transient UI-only setup state.
- Renders setup or scoreboard screen.
- Handles Text-to-Speech score calls from confirmed phone-owned state updates.
- Uses fixed `fontScale = 1f` for scoreboard layout stability.
- Provides direct-touch scoring for Phone Only and connected phone control scenarios.
- Provides the score screen that can be mirrored or shown on a larger Android display.
- On tablet-sized Android screens, renders a passive display-only match screen using the same phone/app-owned `GameState`.

## Display Surface

The shared display surface can be the normal phone score screen mirrored during live play, an adaptive passive tablet display layout when the Android app runs on a tablet-sized screen, or the initial local-network tablet sync path.

Initial wireless tablet sync is display-only and local-network only. Tablet-sized Android screens advertise themselves with a UDP display hello. The phone publishes display-ready score snapshots using UDP broadcast/unicast and a TCP fallback to discovered tablet display endpoints. Snapshots contain display-ready match state only: team names, scores, serving team, server number, score call, active flag, and timestamp. The tablet renders the snapshot passively and does not run scoring rules.

This sync path is an early Phase 3 foundation, not the final hardened transport. Real Wi-Fi networks may block phone-to-tablet inbound UDP/TCP even when tablet-to-phone discovery succeeds. Hardened pairing, hotspot behavior, and reconnection strategy remain Phase 3/5 work.

Responsibilities:

- Show Team A score.
- Show Team B score.
- Show serving team.
- Show server number.
- Show player names.
- Remain readable by all four players.
- Keep screen awake.

Non-responsibilities:

- No separate tablet scoring logic.
- No tablet scoring controls.
- No scoring calculations.
- No cloud relay.

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
- Provides ME WON, OPP WON, UNDO, and RESET controls.
- Speaks score calls locally in standalone mode.
- When phone state is available, switches to connected remote-control mode.

Standalone Watch Only behavior:

- Watch may be the source of truth.
- Supports match setup, first server selection, ME WON, OPP WON, Undo, score display, and voice announcements.
- Existing standalone behavior should be preserved unless intentionally changing Watch Only mode.

Connected Watch + Phone behavior:

- Watch sends only rally winner and undo commands to the phone during live play.
- Phone remains the source of truth for synchronized match state.
- Watch renders a compact local view of the phone-owned state.
- Watch does not own scoring logic, server transitions, side outs, match state, or undo history.
- Watch must never announce a predicted score.
- If enabled, watch speech uses only confirmed phone-owned score state.

Remote-control behavior uses Wear OS Data Layer:

- `MessageClient` for watch-to-phone commands:
  - My Team won rally.
  - Opponent won rally.
  - Undo.
- `DataClient` for phone-to-watch score state snapshots.
- Phone score snapshots include active-match and undo-availability flags.

Connected watch mode must not share ViewModels between phone and Wear and must not run watch-owned scoring logic.
Connected watch controls must not become active until the phone has started a match.

## Voice Announcements

MVP voice modes:

- Off.
- Phone only.
- Watch only.
- Watch then Phone.

Phone Only mode defaults to Phone only and announces immediately after confirmed phone-owned state changes.

Connected Watch + Phone mode defaults to Watch then Phone:

1. Watch sends ME WON, OPP WON, or Undo to phone.
2. Phone updates score as source of truth.
3. Phone publishes confirmed score state.
4. Watch receives confirmed score state and announces immediately.
5. Phone announces the same confirmed score approximately two seconds later.

Both announcements must use the same confirmed phone state.

## Dependencies

Current dependency direction:

```text
app  -> shared
wear -> shared
shared -> no Android dependency
```

Do not introduce dependencies from `shared` back to Android modules.

Target dependency direction for connected sync:

```text
app  -> shared
wear -> shared command contract / Android sync API
shared -> no Android dependency
```

Future sync dependencies should live in Android modules or a dedicated Android-facing sync layer, not in `shared`. The Wear app should not depend on shared scoring rules once it becomes a command-only controller.

## Persistence

Current match state is in memory only.

Future persistence options:

- `SavedStateHandle` for process recreation.
- DataStore for in-progress match restore.
- Room only if match history/statistics become real product scope.
