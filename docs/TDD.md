# Technical Design Document

## Overview

The project is a Kotlin multi-module Gradle Android project.

- `app`: Android phone/tablet application, scoring hub, rules owner, first-class Phone Only scorer, first-class Tablet Only scorer, and display source.
- `wear`: Wear OS application, Watch Only scorer, and connected command controller.
- `shared`: pure Kotlin scoring domain shared by phone and Wear.

RallyScore supports multiple product modes: Watch Only, Phone Only, Tablet Only, Watch + Phone, Phone + Tablet synced, Watch + Phone + Android Tablet synced, and Watch + Phone + Portable Monitor. Additional devices enhance the experience but are not required. Exactly one active source of truth must exist per match. Phone Only, Tablet Only, and Watch Only may each own standalone state when used alone. When a watch is connected, the phone remains the primary hub. Future phone-tablet synced modes must share one canonical match state. The Android app uses Jetpack Compose and a ViewModel with `StateFlow`. The shared module owns scoring rules so they can be tested without Android.

## Technology Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Lifecycle ViewModel and Compose lifecycle collection
- Android Text-to-Speech
- JUnit 4
- Gradle Kotlin DSL
- Java 17 toolchain

## Module Responsibilities

### `shared`

Contains:

- `Team`
- `ServerNumber`
- `GameSettings` (team names, individual player names `teamAPlayer1`/`teamAPlayer2`/`teamBPlayer1`/`teamBPlayer2`, scoring format, target, winBy)
- `GameState` (score, serving, `courtOrderedTeamName()`, `servingPlayerName()`)
- `PickleballScoringEngine`

Responsibilities:

- Apply rally-winner scoring rules.
- Support both Traditional and Rally formats through the same pure scoring engine.
- Preserve score-call order as serving score, receiving score, server number.
- Keep Android dependencies out of scoring logic.
- Remain the only place for pickleball scoring rules.

### `app`

Contains:

- `MainActivity`
- `ScoreboardViewModel`
- `ScoreboardStore`
- `RallyScorePhoneHub`
- `PhoneWearListenerService`
- `ScoreboardApp` and phone Compose UI

Responsibilities:

- Keep screen awake.
- Use landscape orientation for phone setup.
- Preserve landscape orientation for phone score/display screens and tablet score/display readability.
- Act as scoring hub, rules executor, and display source.
- Manage match setup state.
- Expose game state to Compose.
- Maintain rally history for undo.
- Receive watch commands through Wear OS `MessageClient`.
- Publish score state to the watch through Wear OS `DataClient`.
- Provide Text-to-Speech score calls from the phone after authoritative state changes.
- Support target MVP voice modes: Off, Phone only, Watch only, Tablet only, Watch then Phone, Watch then Tablet, and Phone then Tablet.
- Render large table-style scoreboard and controls.
- Keep the normal score screen readable enough for mirroring to a tablet or portable monitor.
- Preserve direct phone scoring as a first-class Phone Only mode.
- Preserve direct tablet scoring as a first-class Tablet Only mode.
- Own communication with the watch and external display surfaces.

### `wear`

Contains:

- Wear launcher activity.
- Wear Compose UI.
- `WearPhoneSync`
- `WearDataLayerListenerService`
- Text-to-Speech score calls for standalone Watch Only mode.

Current Wear behavior supports connected remote mode when the phone or a
paired tablet has an active match and standalone Watch Only mode when no
remote match is active. The connected start screen uses a single mode button
that cycles between `TABLET MODE`, `WATCH MODE`, and `PHONE MODE`, followed by
first-server selection and a separate `START` button. Connected Wear behavior
is command-only: My Team won rally, Opponent won rally, and Undo. Connected-mode
controls are temporarily disabled while awaiting remote confirmation to reduce
accidental double taps during play. The watch should not contain pickleball
scoring logic, server-transition logic, side-out logic, or authoritative match
state in connected mode. If both phone and tablet are present, the watch
prefers the phone as its single target. If no phone is available, the watch may
target a RallyScore tablet directly using the same command/state-sync pattern.
When multiple tablets are discovered on one network, the watch requires an
explicit tablet court selection and remembers the last selected tablet court for
reconnect.

## Supported Product Modes

- Mode 0 - Watch Only: watch source of truth.
- Mode 1 - Phone Only: phone source of truth.
- Mode 2 - Watch + Phone: phone source of truth, watch remote control.
- Mode 2a - Watch + Tablet fallback: tablet source of truth, watch remote control when no phone is available.
- Mode 3 - Tablet Only: tablet source of truth.
- Mode 4 - Phone + Tablet synced: one shared canonical source of truth.
- Mode 5 - Watch + Phone + Android Tablet synced: phone primary hub, one shared canonical source of truth.
- Mode 6 - Watch + Phone + Portable Monitor: phone source of truth, mirrored monitor display.

## Data Flow

### Phone Present Data Flow

1. User reviews or edits My Team and Opponent Team names on the phone.
2. User selects starting serving team during setup.
3. User selects Traditional or Rally scoring during setup.
4. Phone ViewModel creates the authoritative `GameState`.
5. Primary flow: active player taps `WE WON` or `OPP WON` on the watch.
6. Watch sends a `MessageClient` command event to the phone.
7. Fallback flow: user taps Team A or Team B score directly on the phone.
8. Phone ViewModel stores the previous state in history.
9. Phone ViewModel asks `PickleballScoringEngine.recordRallyWinner`.
10. Phone updates authoritative match state.
11. Phone publishes updated score state through `DataClient`.
12. Shared display redraws team scores, serving team, and server number.
13. Voice announcements use only confirmed phone-owned state.
14. Watch receives the phone-owned score state and updates its connected display.

The phone-to-watch score snapshot includes whether a phone-owned match is active and whether Undo is available. Connected watch controls are enabled only when the phone has an active match and no prior command is awaiting phone confirmation. Phone and watch clients periodically refresh peer connection state while open to recover from stale connection indicators.

Connected Watch + Phone voice flow when Watch then Phone mode is selected:

1. Watch receives confirmed phone score state.
2. Watch announces the confirmed score immediately.
3. Phone announces the same confirmed score approximately two seconds later.

Phone Only voice flow when Phone only mode is selected:

1. Phone state updates after rally input or undo.
2. Phone announces the confirmed score immediately.

Tablet Only voice flow when Tablet only mode is selected:

1. Tablet state updates after rally input or undo.
2. Tablet announces the confirmed score immediately.

Connected Watch + Phone + Tablet voice flow when Watch then Tablet mode is selected:

1. Watch receives confirmed phone score state.
2. Watch announces the confirmed score immediately.
3. Tablet receives the same confirmed phone-owned tablet snapshot.
4. Tablet announces the same score approximately two seconds later.

Connected Phone + Tablet voice flow when Phone then Tablet mode is selected:

1. Phone updates confirmed phone-owned state.
2. Phone announces the confirmed score immediately.
3. Tablet receives the same confirmed phone-owned tablet snapshot.
4. Tablet announces the same score approximately two seconds later.

### Tablet Only Data Flow

1. User reviews or edits My Team and Opponent Team names on the tablet.
2. User selects starting serving team during setup.
3. User selects Traditional or Rally scoring during setup.
4. Tablet ViewModel creates the authoritative `GameState`.
5. User taps a team score panel on the tablet to record a rally win.
6. Tablet state stores the previous state in history.
7. Tablet asks `PickleballScoringEngine.recordRallyWinner`.
8. Tablet updates authoritative match state.
9. Tablet redraws the large scoreboard and controls.
10. Tablet announces the confirmed score when enabled.
11. Undo restores the previous tablet-owned state.

Tablet Only mode should reuse the same shared scoring engine and core Android scoring state patterns as Phone Only mode. UI code must not duplicate scoring rules.
Standalone tablet correction mode adjusts scores through the shared
`ScoreboardStore` rather than mutating Compose state directly, so corrections
remain undoable.

### Future Phone + Tablet Synced Data Flow

Phone + Tablet synced mode starts as command/state sync with the phone as
the sole source of truth. Full peer-controller sync with conflict handling is
future work.

Required principles:

- Phone and tablet share one canonical match state.
- Tablet may send rally, undo, or end intent once synchronized.
- Phone applies intent through the shared scoring engine.
- All devices render the confirmed resulting state.
- Tablet must not predict or locally mutate the connected phone-owned score.
- Conflict handling is required before allowing truly simultaneous peer controller ownership.
- The app must not allow phone and tablet to independently score separate copies of the same live match without clear standalone-mode separation.

### Watch Only Data Flow

1. User selects Traditional or Rally scoring on the watch.
2. User chooses first server on the watch.
3. User taps `WE WON` or `OPP WON` on the watch.
4. Watch-local state stores previous state in history.
5. Watch asks `PickleballScoringEngine.recordRallyWinner`.
6. Watch Compose redraws and announces the score locally.

The standalone Wear flow is valid for Watch Only mode. Connected Wear flow must keep the phone as source of truth even when rally input originates on the watch.

Connected Wear mode may speak only after confirmed phone state is received. It must never speak a predicted score.

### Watch + Tablet Fallback Data Flow

1. Tablet advertises its watch-fallback endpoint together with its court code.
2. Watch discovers one or more tablet courts on the local network.
3. If more than one tablet court is visible, the watch user explicitly selects
   the intended court from the watch start flow.
4. Watch remembers the selected tablet court for later reconnects.
5. Watch sends start, rally, undo, and end intent only to the selected tablet.
6. Tablet applies scoring through the shared engine and returns confirmed state
   to the watch.

## Watch Command Contract

The target watch-to-phone command surface should stay intentionally small:

- `TeamAWonRally`
- `TeamBWonRally`
- `Undo`

Commands represent user intent. The phone converts commands into scoring changes.

## Wear OS Data Layer

Current implementation uses:

- `MessageClient` for immediate watch-to-phone commands.
- `DataClient` for latest phone-to-watch score state.
- Wear listener services on both modules for background delivery.
- Basic Android logs tagged around command send/receive and score-state publish/receive.
- `match_active` and `can_undo` fields in phone score snapshots to protect phone setup state and disable invalid Undo actions.

The shared module owns only command/path/key constants and scoring domain types. Android Data Layer code remains in `app` and `wear`.

## Mirrored Display

External display support can still use the normal phone score screen mirrored to an Android tablet or external portable monitor. The mirrored score screen should show:

- Team scores.
- Serving team.
- Server number.
- Player names.
- CALL, Undo, End, and the small watch connection rail from the normal phone score screen.

The phone remains the only source of truth and scoring hub.

## Tablet Standalone Controller

Revised Phase 3 starts with Tablet Only mode. Tablet-sized screens support
setup, score display, tap-to-score panels for rally-winner input, undo and
end controls inside the call bar, correction mode when available, and voice
announcements while keeping the screen awake and using large high-contrast
layout.

Tablet Only owns its match state and uses the shared scoring engine exactly
like Phone Only mode. This is simpler and immediately useful before
multi-device sync exists.

In screen routing, a local match always takes priority over remote display
state. The tablet shows its full controller UI whenever a local match is
active, regardless of any remote snapshot. When no local match is active,
it renders a connected phone-owned controller only when an active phone match
snapshot exists; otherwise it renders the normal setup screen so the tablet is
always usable as a standalone controller.

## Tablet Display Client And Future Sync

The existing passive tablet display client can remain as a prototype or fallback, but it is no longer the main Phase 3 direction.

The tablet distinguishes two modes:

- **Local controller mode:** when a match is started locally, the tablet
  shows tap-to-score panels with full UNDO/END controls and owns match
  state through the shared scoring engine.
- **Connected phone controller mode:** when no local match is active but a
  phone-owned active snapshot is available, the tablet renders the same large
  scoreboard controls. Tapping score panels, UNDO, or END sends a command to
  the phone. The tablet redraws only after receiving confirmed phone-owned
  state.
- **Passive remote display mode:** may remain as an optional future display-only
  surface, but the preferred connected tablet behavior is secondary controller.

Connected tablet mode renders phone-owned display snapshots:

- Team scores.
- Serving side.
- Server number.
- Player names (including court-ordered names and serving player name).
- CALL score.
- Match-active timestamp.

The phone hosts a lightweight local WebSocket publisher for tablet display snapshots. Tablet clients connect over the current local IP network and render the latest confirmed phone-owned score state. The network can be venue/home Wi-Fi or the phone's own hotspot; Internet access is not required. The tablet also performs conservative same-subnet discovery so it can initiate the connection when phone-to-tablet inbound delivery is blocked. Discovery uses the remembered phone endpoint, the current network gateway when available, UDP broadcast announcements, TCP fallback endpoints, WebSocket availability broadcasts, and IPv4 interface-based subnet probing. No user-facing IP address entry is required.

The same WebSocket also carries tablet-to-phone commands:

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

Commands represent intent only. The tablet does not run connected scoring
logic or update its connected score optimistically.

Connected phone-tablet command/state sync now carries two lightweight
identities:

- `hostId`: persisted per phone installation so one tablet can stay attached
  to one court host on a shared network.
- `sessionId`: rotated for each phone-owned match start/reset/end boundary so
  stale commands from an earlier match cannot mutate the current match.

The phone accepts connected tablet commands only when the command session ID
matches the current phone-owned match session. The tablet ignores snapshots
from non-paired host IDs and updates its remembered session ID when the paired
phone rotates to a new match.

This is now exposed as an explicit MVP pairing flow for multi-court use on one
hotspot or shared Wi-Fi:

- The phone shows a compact local court code derived from its persisted host ID.
- An unpaired tablet stays usable in normal setup mode while it discovers nearby
  phones.
- The tablet lists discovered courts and joins only after the user selects the
  intended phone.
- After a successful join, the tablet remembers that host and reconnects to it
  automatically until the pairing is intentionally cleared.

Connection robustness requirements for the display client:

- Tablet remembers the last successful phone WebSocket endpoint.
- Tablet retries the remembered endpoint on launch and reconnect.
- Tablet can discover the phone over external Wi-Fi or the phone hotspot as long as both devices share a local IP network.
- Tablet shows Searching for phone, Reconnecting, and Connected states.
- Phone accepts reconnecting tablet clients without manual action.
- Phone sends the latest display snapshot immediately after a WebSocket reconnect when available.
- Score snapshots act as heartbeat.
- Tablet detects heartbeat/read timeout and enters Reconnecting.
- Tablet keeps the last received score visible while reconnecting.
- After Wi-Fi returns, tablet reconnects automatically and restores the current phone-owned score.
- Tablet should not auto-attach to an arbitrary newly discovered phone when no
  explicit court selection has been made yet.

Future synced tablet controller mode should evolve beyond command/state sync
into conflict-handled bidirectional peer control. Until that exists, Tablet
Only and phone-owned connected tablet mode must remain clearly separate so
there is never more than one active source of truth for a match.

The routing check always inspects local match state first. The `ScoreboardApp`
composable evaluates `matchStarted` before any remote-state branch, ensuring
a local match is never hidden by a remote snapshot. If no local match is active
and no active phone-owned snapshot exists, the tablet falls through to setup
instead of showing a blocking waiting screen.

## State Management

`GameState` is immutable. Each rally creates a new state.

`GameState` includes `courtOrderedTeamName(team)` which returns player-name pairs in current court position order (swaps on odd scores), and `servingPlayerName()` which returns the name of the current serving player under a fixed-position model (P1/P4 always right-side Server 1, P2/P3 always left-side Server 2).

Tablet standalone correction mode may adjust score, serving side, and server number, but those edits still go through `ScoreboardStore` so the UI does not implement scoring rules and every correction remains undoable.

Undo is implemented by keeping prior `GameState` values in a list for the current match. Undo is unlimited within the in-memory match session.

## Text Input Notes

Setup name input normalizes names to uppercase and replaces line breaks with spaces.

Do not reintroduce automatic focus jumps from Team A to Team B without device testing. Samsung keyboard plus Compose focus previously crashed with:

```text
IllegalArgumentException: ActiveParent with no focused child
```

Current behavior: pressing Enter/Done does not auto-focus the next field.

## Known Technical Gaps

- Rally history is not persisted across process death.
- TTS voice selection uses best available English voice from Android; no in-app voice picker exists.
- Voice settings support Off, Phone only, Watch only, Tablet only, Watch then Phone, Watch then Tablet, and Phone then Tablet.
- Wear app has an initial phone sync path, but real-device pairing/reconnect behavior still needs hardening.
- Wear app currently contains prototype scoring logic; target watch control must move scoring authority back to the phone.
- Tablet Only mode is not fully implemented yet.
- Phone + Tablet synced scoring is future work and needs conflict handling.
- Tablet display WebSocket sync is an initial prototype and still needs venue/hotspot hardening if retained.
- Phone app restores active match score state and player names after app relaunch, but undo history is not persisted yet.
