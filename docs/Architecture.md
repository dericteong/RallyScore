# Architecture

## Summary

PickleCast is a flexible scoring ecosystem that supports multiple device combinations. Additional devices enhance the experience but are not required. Exactly one active source of truth must exist per match. Phone Only, Tablet Only, and Watch Only may each own standalone match state. When a watch is used in connected mode, the phone remains the primary hub. Phone + Tablet synced modes already share one canonical match state today (see "Display Surface And Phone-Tablet Sync" below) — what remains future work is deterministic conflict handling for near-simultaneous multi-device input, not the sync itself.

Supported modes:

- Mode 0: Watch Only.
- Mode 1: Phone Only.
- Mode 2: Watch + Phone.
- Mode 2a: Watch + Tablet fallback.
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

Phone Only and Tablet Only are first-class experiences. Watch Only may own standalone match state. In Watch + Phone mode, the watch becomes a remote control and the phone owns synchronized match state, scoring decisions, undo history, voice timing, and display output. In Phone + Tablet synced modes, phone and tablet already share one canonical match state; explicit conflict handling for near-simultaneous multi-device input is the remaining gap, not the sync itself. The scoring engine is independent of Android so rules can be reused and unit-tested.

## Phone App

`app/src/main/java/com/courtside/pickleball/MainActivity.kt`

- Adds `FLAG_KEEP_SCREEN_ON`.
- Enables edge-to-edge.
- Hosts `ScoreboardApp`.
- Uses `ScoreboardViewModel`, constructed through a `viewModelFactory` that
  wires `ScoreboardStore`, `RallyScorePhoneHub`, `TabletDisplaySync`,
  `WatchTabletFallbackSync`, and `PlayerRepository` explicitly instead of the
  ViewModel reaching for those singletons itself.

`ScoreboardViewModel`

- Observes the phone `ScoreboardStore`, which owns current `GameState`.
- Exposes `StateFlow<GameState>`.
- Starts matches from setup values.
- Records rally winners.
- Maintains undo history.
- Resets/end match state.
- Shares phone-owned state with watch command handling.
- Takes its dependencies as constructor parameters (see `MainActivity` above)
  rather than defaulting to singletons internally.

`RallyScorePhoneHub`

- Initializes Wear OS Data Layer clients.
- Receives watch commands through `MessageClient`.
- Receives connected tablet commands through `TabletDisplaySync`.
- Applies commands through `ScoreboardStore`.
- Restores the last active phone-owned match state after app process restart.
- Publishes phone-owned score snapshots through `DataClient`.
- Exposes watch connection status for phone UI.

`ScoreboardApp` (`ui/ScoreboardApp.kt`)

The UI layer is split into per-screen files instead of one monolithic file:

- `ui/ScoreboardApp.kt` — root composable only: state hoisting, TTS setup,
  and top-level screen routing (setup vs. scoreboard vs. tablet display vs.
  player management).
- `ui/GameStateExtensions.kt` — shared `GameState`/`VoiceAnnouncementMode`
  extensions used across screens.
- `ui/theme/` — shared colors, dimensions, constants.
- `ui/status/` — shared connection-status badge composables.
- `ui/setup/` — `MatchSetupScreen` (including the player-autocomplete
  dropdown) and `PlayerManagementScreen`.
- `ui/tablet/` — `TabletDisplayScreen` and tablet score/control composables.
- `ui/scoreboard/` — phone `ScoreboardScreen` and `MatchCorrectionDialog`.

Responsibilities (unchanged from before the split):

- Owns transient UI-only setup state.
- Lets the user choose the scoring format on setup.
- Renders setup or scoreboard screen.
- Handles Text-to-Speech score calls from confirmed phone-owned state updates.
- Uses fixed `fontScale = 1f` for scoreboard layout stability.
- Provides direct-touch scoring for Phone Only and connected phone control scenarios.
- Provides direct-touch scoring for Tablet Only on tablet-sized screens.
- Provides the score screen that can be mirrored or shown on a larger Android display.
- On tablet-sized Android screens, renders normal setup/scoring when used standalone.
- On tablet-sized Android screens, renders the same score UI as a secondary controller when a connected phone-owned match is active.

`player/PlayerRepository`

- Local, per-device SharedPreferences-backed player list (id, name,
  createdAt, lastPlayed). Powers the setup-screen autocomplete dropdown and
  the Manage Players screen.
- `TabletDisplaySync` calls `markPlayersPlayed(...)` (debounced) whenever a
  passively-displaying tablet receives fresh state for an active match, so a
  tablet that never itself started a match still learns those players.
  Manual add/rename/delete on one device does not sync to the other.

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

## Display Surface And Phone-Tablet Sync

The shared display surface can still be the normal phone score screen mirrored during live play or a Tablet Only controller screen.

Wireless tablet sync is local-network only (no Internet, no cloud relay) and is bidirectional
command/state sync, not just a passive display feed: the phone hosts a lightweight local
WebSocket server for display-ready score snapshots, and the same connection carries
tablet-to-phone commands (rally, undo, end, corrections — see below). Tablet-sized Android
screens can connect to the phone over external Wi-Fi or the phone's own hotspot and render the
latest confirmed phone-owned snapshot. The tablet can also scan its local subnet for the phone
WebSocket so the connection is initiated from the tablet on networks that block inbound
phone-to-tablet delivery. Discovery uses live IPv4 network interfaces, remembered endpoints,
gateway probing, UDP broadcasts, and the earlier TCP endpoint path as fallback aids. Users should
not need to know or enter IP addresses.

Phone-tablet sync now carries two identities:

- `hostId`: stable per phone installation and used to keep a tablet paired to one court host.
- `sessionId`: rotates for each new phone-owned match and is required on tablet-to-phone commands.

Snapshots contain display-ready match state only: host ID, session ID, team names, court-ordered team names, scores, serving team, server number, serving player name, score call, voice announcement mode, active flag, undo availability, and timestamp. The connected tablet renders confirmed phone-owned snapshots and does not run scoring rules.

The same WebSocket carries tablet-to-phone commands. Tablet commands are:

- `TABLET_ME_WON_RALLY`
- `TABLET_OPP_WON_RALLY`
- `TABLET_UNDO`
- `TABLET_END_MATCH`
- `TABLET_ADJUST_TEAM_A_SCORE_DOWN`
- `TABLET_ADJUST_TEAM_A_SCORE_UP`
- `TABLET_ADJUST_TEAM_B_SCORE_DOWN`
- `TABLET_ADJUST_TEAM_B_SCORE_UP`
- `TABLET_SET_SERVING_TEAM_A`
- `TABLET_SET_SERVING_TEAM_B`
- `TABLET_SET_SERVER_ONE`
- `TABLET_SET_SERVER_TWO`

Commands are intent only. The phone applies them through `ScoreboardStore`
and the shared scoring engine, then broadcasts the confirmed result back to
tablet clients and Wear OS watches.

The phone ignores tablet commands whose session ID does not match the current
phone-owned match. The tablet ignores score snapshots from non-paired host IDs.
This is the first safeguard against multiple courts sharing the same network.

Because the state broadcast is plaintext and readable by anyone on the same
Wi-Fi, session-ID equality alone does not prove a command came from the
paired device. The WebSocket command channel additionally requires a
per-connection HMAC-SHA256 signature (secret minted and handed to the peer
once, directly over that connection, never broadcast — `sync/MessageAuthenticator`),
and both the WebSocket and TCP accept loops apply per-IP rate limiting
(`sync/RateLimiter`) to slow down brute-forcing the court code. See
`docs/TDD.md` ("Command channel hardening") for the full design and a note on
why the TCP snapshot channel needs a much higher rate-limit budget than the
WebSocket channel.

The tablet display client tracks explicit connection states: Searching for phone, Reconnecting, and Connected. It remembers the last phone WebSocket endpoint, retries that endpoint after app relaunch, treats incoming score snapshots as heartbeat, detects stale connections with a read timeout, and keeps the last received score visible while reconnecting. The phone accepts reconnecting tablet clients and immediately sends the latest phone-owned snapshot when one is available. This transport should be agnostic to whether the local network is a router-backed Wi-Fi network or the phone hotspot.

The phone tracks its own mirror of this state (`hostConnectionState`, "is a tablet connected to
me") across *both* delivery paths, not just the WebSocket one: a periodic TCP push
(`publishToTabletTcpEndpoints`) is the fallback used when a tablet hasn't (or can't) establish the
WebSocket route, and a successful push marks the phone Connected the same way an accepted
WebSocket client does, falling back to Reconnecting if delivery stops working with no WebSocket
client either. Without this, a pairing that only ever worked over the TCP fallback left the
phone's own status pill stuck on "Searching" indefinitely even though the tablet was receiving
live updates and correctly showing itself as Connected — the tablet's status only depends on
"did I get a fresh snapshot," regardless of transport, so the two sides could disagree.

When a connected-controller tablet's connection to the phone actually drops (e.g. the phone app
was closed), its rally/undo/end taps disable themselves instead of continuing to look tappable —
they only ever send commands to the phone, so tapping them with no phone reachable was previously
a silent no-op that read as the tablet being "hung." Because "end match" specifically has no
meaningful action once the phone is unreachable, its button falls back to a local-only "End Game
Locally" action (`ScoreboardViewModel.forgetPairedTabletPhone`, confirmed via dialog) that forgets
the paired phone and the stale snapshot on this tablet only — it does not, and cannot, notify the
phone. "SETUP" remains available regardless of connection state as a pure local-navigation escape
hatch, unaffected by any of this.

To reduce cross-court confusion on shared hotspot or Wi-Fi networks, tablet discovery is now explicit instead of silently auto-attaching to the first phone that answers. The phone setup screen exposes a compact court code derived from the stable `hostId`. An unpaired tablet stays on its normal setup screen, shows a list of discovered phones with court codes, and joins only after the user picks the intended court. After the first join, the tablet remembers that host and reconnects to it automatically until the user chooses Change Phone / forget pairing.

This command/state sync path is still an MVP synced-controller transport, not the final conflict-handled phone/tablet peer model. Real Wi-Fi networks may block local-device discovery or direct delivery. Deterministic multi-controller conflict handling remains future work, but explicit tablet court selection is now the first user-visible safeguard for multiple PickleCast courts sharing one network.

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

The phone's UDP broadcast (`TabletDisplaySync.runBroadcaster`) idles to a 5-second cadence
instead of running at 1 Hz forever, whenever there's no active match and no tablet connected or
recently seen — it returns to 1 Hz the moment a match starts or a tablet appears. A freshly opened
tablet still discovers the phone within a few seconds.

Every device runs both a phone-hosting connection state and a tablet-client connection state,
because the same `TabletDisplaySync` singleton backs both roles regardless of which one a given
device actually uses. UI that shows pairing status must read the role-appropriate state (client
state for a device acting as tablet, host state for a device acting as phone) — feeding the wrong
one means the UI can never reflect a real, successfully-established connection.

**Known issue:** because every device broadcasts its own phone-hosting availability regardless of
role, a tablet's "AVAILABLE PHONES" list can show its own court code as a pairable candidate
alongside the real phone it's trying to reach. Not yet fixed — see
`docs/CodeReviewFindings.md`.

## Shared Domain

`shared/src/main/kotlin/com/courtside/pickleball/domain/`

Key objects:

- `Team`: A or B.
- `ServerNumber`: One or Two.
- `ScoringFormat`: Traditional or Rally.
- `GameSettings`: team names, individual player names (`teamAPlayer1`, `teamAPlayer2`, `teamBPlayer1`, `teamBPlayer2`), scoring format, and target/win settings.
- `GameState`: immutable score, serving state, `courtOrderedTeamName(team)` for court-position-ordered names, `servingPlayerName()` for the current serving player.
- `PickleballScoringEngine`: applies rally-winner rules.

The domain model is intentionally small. The app should prefer adding tests to shared domain before extending score logic.

Current scoring-format behavior:

- Traditional is the default and remains unchanged.
- Rally mode is PickleCast's custom social-play variant:
  - every rally awards a point to the rally winner
  - two serves remain active
  - first-server exception still applies
  - server transitions and side outs still follow the Traditional model
  - court-position swapping still follows the current odd/even score model
  - the score call remains a three-number call
  - the engine does not auto-end the game at a target score

## Wear App

`wear/` is a separate Android application with package `com.courtside.pickleball.wear`.

The UI layer is split into per-screen files, the same treatment `ScoreboardApp.kt` got in the
phone app:

- `MainActivity.kt` — launcher activity; requests `FLAG_KEEP_SCREEN_ON` (re-asserted on window
  focus regain, not just resume) and implements `AmbientModeSupport.AmbientCallbackProvider`.
- `WearScoreboardApp.kt` — slim root composable (state hoisting + the connection/mode/voice
  `LaunchedEffect`s) plus the ambient-mode model/view.
- `theme/WearTheme.kt` — color constants.
- `setup/WearSetupScreens.kt` — mode-switch and serve-setup screens.
- `scoreboard/WearScoreboardScreens.kt` — active-match screens and their shared subcomponents.

Current behavior:

- Uses the shared scoring engine.
- Keeps local state in Compose.
- Uses score tiles as the primary `WE WON` and `OPP WON` controls.
- Keeps Undo on the main screen and exposes End as a secondary watch action to protect round-screen readability.
- Speaks score calls locally in standalone mode.
- When the phone has an active match, switches to connected remote-control mode.
- When the phone is connected but idle, the watch may still start a standalone local match.
- Implements Wear OS Ambient Mode: instead of just going black once the system dims the display,
  a low-power, burn-in-safe readout (dim gray text on black) of whichever score is currently
  active replaces the normal interactive buttons while ambient. Depends on the device's own Wear
  OS build actually entering the system ambient state before sleep — on at least one tested OEM
  skin, the manufacturer's own system UI takes over the display during the dozing transition
  instead of handing ambient rendering back to the app, a device-level constraint outside
  PickleCast's control.

Standalone Watch Only behavior:

- Watch may be the source of truth.
- Supports match setup, first-server selection, `WE WON`, `OPP WON`, Undo,
  score display, and voice announcements.
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
- When both phone and tablet are connected to the watch, the watch prefers the
  phone as its single command target.
- When no phone is available but a PickleCast tablet is connected, the watch may
  target the tablet directly and the tablet becomes the source of truth for that
  connected match.
- Direct watch-to-tablet discovery includes the tablet court code.
- The watch remembers the last selected tablet court and reconnects to that same
  court when it is rediscovered.
- If multiple tablets are on the same local network, the watch must let the user
  switch tablet courts explicitly instead of auto-attaching to an arbitrary
  tablet.

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
- Watch then Phone then Tablet.

Phone Only mode defaults to Phone only and announces immediately after confirmed phone-owned state changes.

Tablet Only mode defaults to Tablet only and announces immediately after confirmed tablet-owned state changes.

Connected Watch + Phone mode defaults to Watch then Phone:

1. Watch sends `WE WON`, `OPP WON`, or Undo to phone.
2. Phone updates score as source of truth.
3. Phone publishes confirmed score state.
4. Watch receives confirmed score state and announces immediately.
5. Phone announces the same confirmed score approximately two seconds later.

Connected Watch + Phone + Tablet mode should default to Watch then Tablet:

1. Watch sends `WE WON`, `OPP WON`, or Undo to phone.
2. Phone updates score as source of truth.
3. Phone publishes confirmed score state to watch and tablet.
4. Watch announces the confirmed score immediately.
5. Tablet announces the same confirmed score approximately two seconds later.

All three devices can also be chained explicitly via Watch then Phone then Tablet
(a manual selection only; no auto-selected mode picks it today):

1. Watch sends `WE WON`, `OPP WON`, or Undo to phone.
2. Phone updates score as source of truth.
3. Phone publishes confirmed score state to watch and tablet.
4. Watch announces the confirmed score immediately.
5. Phone announces the same confirmed score approximately two seconds later.
6. Tablet announces the same confirmed score approximately four seconds later
   (double the usual secondary delay), so its repeat lands after the phone's
   rather than overlapping it.

Connected tablet voice uses only confirmed phone-owned tablet snapshots. Both
announcements in a two-device voice mode (and all three in the triple-hop mode)
must use the same confirmed score string.

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

Current phone-owned match score state and player names are persisted in SharedPreferences so an active match can be restored after phone app relaunch. Undo history is still in memory only.

Future persistence options:

- `SavedStateHandle` for process recreation.
- DataStore for in-progress match restore.
- Room only if match history/statistics become real product scope.
