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
- Receives connected tablet commands through `TabletDisplaySync`.
- Applies commands through `ScoreboardStore`.
- Restores the last active phone-owned match state after app process restart.
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
- On tablet-sized Android screens, renders normal setup/scoring when used standalone.
- On tablet-sized Android screens, renders the same score UI as a secondary controller when a connected phone-owned match is active.

## Tablet App Mode

Tablet Only is the revised Phase 3 priority.

Responsibilities:

- Show setup for My Team and Opponent Team.
- Select first server.
- Show large scores, serving team, server number, player names, and CALL.
- Tap-to-score panels: tapping a team's score records a rally win for that team.
- Show UNDO and END buttons inside the call bar in local-controller mode.
- Support correction mode when available.
- Announce confirmed tablet-owned score when voice is enabled.
- Keep screen awake.
- Use the shared scoring engine.

Non-responsibilities:

- No duplicated scoring rules in tablet UI.
- No independent scoring of a phone-owned connected match.
- Connected tablet mode sends commands to the phone and waits for confirmed
  phone-owned state.
- Full peer controller sync remains future work until conflict handling exists.
- Local-controller mode provides full controls and owns match state.
- Local match state always takes priority over remote state in routing.

## Display Surface And Sync Prototype

The shared display surface can still be the normal phone score screen mirrored during live play or a Tablet Only controller screen.

Initial wireless tablet display sync is display-only and local-network only. The phone hosts a lightweight local WebSocket publisher for display-ready score snapshots. Tablet-sized Android screens can connect to the phone over external Wi-Fi or the phone's own hotspot and render the latest confirmed phone-owned snapshot. No Internet connection is required. The tablet can also scan its local subnet for the phone WebSocket so the connection is initiated from the tablet on networks that block inbound phone-to-tablet delivery. Discovery uses live IPv4 network interfaces, remembered endpoints, gateway probing, UDP broadcasts, and the earlier TCP endpoint path as fallback aids. Users should not need to know or enter IP addresses.

Snapshots contain display-ready match state only: team names, scores, serving team, server number, score call, active flag, undo availability, and timestamp. The connected tablet renders confirmed phone-owned snapshots and does not run scoring rules.

The same WebSocket carries tablet-to-phone commands. Tablet commands are:

- `TABLET_ME_WON_RALLY`
- `TABLET_OPP_WON_RALLY`
- `TABLET_UNDO`
- `TABLET_END_MATCH`

Commands are intent only. The phone applies them through `ScoreboardStore`
and the shared scoring engine, then broadcasts the confirmed result back to
tablet clients and Wear OS watches.

The tablet display client tracks explicit connection states: Searching for phone, Reconnecting, and Connected. It remembers the last phone WebSocket endpoint, retries that endpoint after app relaunch, treats incoming score snapshots as heartbeat, detects stale connections with a read timeout, and keeps the last received score visible while reconnecting. The phone accepts reconnecting tablet clients and immediately sends the latest phone-owned snapshot when one is available. This transport should be agnostic to whether the local network is a router-backed Wi-Fi network or the phone hotspot.

This command/state sync path is an early prototype, not the final conflict-handled synced tablet controller transport. Real Wi-Fi networks may block local-device discovery or direct delivery. Hardened pairing and conflict handling remain future work.

Tablet screen routing uses the following priority:

1. If a local match is started, the tablet shows the local controller screen
   with full scoring controls, regardless of any remote state.
2. If no local match is active but a remote display snapshot reports an
   active match, the tablet shows the connected controller screen backed by
   phone-owned state.
3. Otherwise the tablet shows the match setup screen, even if disconnected or
   searching for a phone.

Responsibilities:

- Show Team A score.
- Show Team B score.
- Show serving team.
- Show server number.
- Show player names.
- Remain readable by all four players.
- Keep screen awake.

Connected tablet non-responsibilities:

- No separate tablet scoring logic for phone-owned matches.
- No optimistic score mutation before phone confirmation.
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
- Tablet only.
- Watch then Phone.
- Watch then Tablet.
- Phone then Tablet.

Phone Only mode defaults to Phone only and announces immediately after confirmed phone-owned state changes.

Tablet Only mode defaults to Tablet only and announces immediately after confirmed tablet-owned state changes.

Connected Watch + Phone mode defaults to Watch then Phone:

1. Watch sends ME WON, OPP WON, or Undo to phone.
2. Phone updates score as source of truth.
3. Phone publishes confirmed score state.
4. Watch receives confirmed score state and announces immediately.
5. Phone announces the same confirmed score approximately two seconds later.

Connected Watch + Phone + Tablet mode should default to Watch then Tablet:

1. Watch sends ME WON, OPP WON, or Undo to phone.
2. Phone updates score as source of truth.
3. Phone publishes confirmed score state to watch and tablet.
4. Watch announces the confirmed score immediately.
5. Tablet announces the same confirmed score approximately two seconds later.

Connected tablet voice uses only confirmed phone-owned tablet snapshots. Both
announcements in a two-device voice mode must use the same confirmed score
string.

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

Current phone-owned match score state is persisted lightly so an active match can be restored after phone app relaunch. Undo history is still in memory only.

Future persistence options:

- `SavedStateHandle` for process recreation.
- DataStore for in-progress match restore.
- Room only if match history/statistics become real product scope.
