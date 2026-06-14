# Architecture

## Summary

RallyScore is a flexible scoring ecosystem that supports multiple device combinations. Additional devices enhance the experience but are not required. Exactly one active source of truth must exist per match. Phone Only, Tablet Only, and Watch Only may each own standalone match state. When a watch is used in connected mode, the phone remains the primary hub. Future Phone + Tablet synced modes must share one canonical match state.

Supported modes:

- Mode 0: Watch Only.
- Mode 1: Phone Only.
- Mode 2: Watch + Phone.
- Mode 3: Tablet Only.
- Mode 4: Phone + Tablet synced.
- Mode 5: Watch + Phone + Android Tablet synced.
- Mode 6: Watch + Phone + Portable Monitor.

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
            v / ^
    +-------+--------+
    | Android tablet |
    | controller or  |
    | display        |
    +----------------+
```

Phone Only and Tablet Only are first-class experiences. Watch Only may own standalone match state. In Watch + Phone mode, the watch becomes a remote control and the phone owns synchronized match state, scoring decisions, undo history, voice timing, and display output. In future Phone + Tablet synced modes, phone and tablet must share one canonical match state and explicit conflict handling. The scoring engine is independent of Android so rules can be reused and unit-tested.

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
- Provides direct-touch scoring for Tablet Only on tablet-sized screens.
- Provides the score screen that can be mirrored or shown on a larger Android display.
- On tablet-sized Android screens, should render Tablet Only setup/scoring when used standalone.
- May render a passive display-only match screen from a remote phone-owned tablet snapshot only when explicitly in display-client mode.

## Tablet App Mode

Tablet Only is the revised Phase 3 priority.

Responsibilities:

- Show setup for My Team and Opponent Team.
- Select first server.
- Show large scores, serving team, server number, player names, and CALL.
- Record ME WON and OPP WON.
- Undo previous rally.
- Support correction mode when available.
- Announce confirmed tablet-owned score when voice is enabled.
- Keep screen awake.
- Use the shared scoring engine.

Non-responsibilities:

- No duplicated scoring rules in tablet UI.
- No independent scoring of a phone-owned synced match.
- No phone-tablet bidirectional sync until conflict handling exists.

## Display Surface And Sync Prototype

The shared display surface can still be the normal phone score screen mirrored during live play, a Tablet Only controller screen, or an explicit passive display-client mode.

Initial wireless tablet display sync is display-only and local-network only. The phone hosts a lightweight local WebSocket publisher for display-ready score snapshots. Tablet-sized Android screens can connect to the phone over the same Wi-Fi or phone hotspot path and render the latest confirmed phone-owned snapshot. The tablet can also scan its local subnet for the phone WebSocket so the connection is initiated from the tablet on networks that block inbound phone-to-tablet delivery. UDP discovery and the earlier TCP endpoint path may remain as fallback aids.

Snapshots contain display-ready match state only: team names, scores, serving team, server number, score call, active flag, and timestamp. The tablet renders the snapshot passively and does not run scoring rules.

This display sync path is an early prototype, not the final synced tablet controller transport. Real Wi-Fi networks may block local-device discovery or direct delivery. Hardened pairing, hotspot behavior, bidirectional commands, and conflict handling remain future work.

Responsibilities:

- Show Team A score.
- Show Team B score.
- Show serving team.
- Show server number.
- Show player names.
- Remain readable by all four players.
- Keep screen awake.

Display-only non-responsibilities:

- No separate tablet scoring logic.
- No tablet scoring controls.
- No tablet setup flow during display-client mode.
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
