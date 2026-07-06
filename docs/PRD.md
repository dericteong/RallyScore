# Product Requirements Document

## Product Name

RallyScore

Tagline: Wear. Play. Score.

## Problem

Pickleball games often need a clear, portable scoreboard that players can update without leaving the court. Existing manual scoreboards require physical handling, and generic scoring apps often require the operator to understand side outs, server number changes, and score-call order.

RallyScore is a flexible pickleball scoring ecosystem. It should work with different combinations of devices, and additional devices should enhance the experience rather than being mandatory. There must be only one active source of truth per match. Phone Only, Tablet Only, and Watch Only may each own standalone match state when used alone. When a watch is connected, the phone remains the primary hub. Future Phone + Tablet synced modes must share one canonical match state rather than independently scoring separate copies of the same match.

Canonical connected system flow:

```text
Wear OS watch
    ↓
Android phone scoring hub
    ↔
Android tablet controller/display
    ↓
Large shared scoreboard
```

## Target Users

- Players who want to score from a Wear OS watch while actively playing.
- Players who want a first-class Phone Only scorer and scoreboard.
- Players who want a first-class Tablet Only scorer and large courtside controller.
- Other players on court who need a shared, readable scoreboard.
- Coaches, organizers, or clubs using a phone, tablet, portable monitor, or large screen display.
- A player, volunteer, or referee scoring directly from the phone.

## Supported Modes

### Mode 0 - Watch Only

- Wear OS watch is the source of truth.
- Supports match setup, first server selection, `WE WON`, `OPP WON`, Undo,
  score display, and voice announcements.
- Purpose: casual play, minimal setup, demo mode, and backup mode.

### Mode 1 - Phone Only

- Android phone is the source of truth.
- Supports match setup, team names, first server selection, score display,
  rally-winner input, Undo, and voice announcements.
- This is a first-class experience.

### Mode 2 - Watch + Phone

- Phone is the source of truth.
- Watch acts as a remote control with rally-winner input and Undo.
- Phone owns scoring rules, side outs, server transitions, undo history, voice announcements, and display output.

### Mode 2a - Watch + Tablet Fallback

- Tablet is the source of truth when no phone is available.
- Watch acts as a remote control with rally-winner input and Undo.
- If both phone and tablet are available to the watch, phone remains the preferred target.

### Mode 3 - Tablet Only

- Android tablet is the source of truth.
- Supports match setup, team names, first server selection, score display,
  rally-winner input, Undo, correction mode when available, and voice
  announcements.
- Uses the same shared scoring engine as the phone.
- Purpose: social play where a tablet is placed courtside and any player can tap scoring controls.

### Mode 4 - Phone + Android Tablet Synced

- Phone and tablet share one canonical match state.
- Connected tablet can send rally, Undo, and End commands to the phone.
- Phone remains source of truth and applies commands through the shared scoring engine.
- Tablet updates only after receiving confirmed phone-owned state.
- Full conflict-handled peer controller sync is future work.

### Mode 5 - Watch + Phone + Android Tablet Synced

- Phone remains the source of truth.
- Watch acts as remote control through the phone.
- Tablet acts as a secondary controller/display by sending commands to the phone.
- Score updates from watch, phone, or tablet resolve through the phone-owned match state.
- Full conflict-handled phone/tablet peer sync is future work.

### Mode 6 - Watch + Phone + Portable Monitor

- Phone remains the source of truth.
- Portable monitor acts as a shared display.
- For MVP, simple display mirroring is acceptable.

## Product Priorities

1. Phone remains stable source of truth.
2. Watch controls phone score.
3. Phone-only experience is excellent.
4. Tablet-only controller experience is excellent.
5. Phone voice announcements.
6. Large display mode optimized for mirroring.
7. Portable monitor support.
8. Phone-tablet synced mode.
9. Bluetooth speaker support.
10. Tournament features.

## MVP Goals

- Wear OS watch as a first-class rally-input controller in connected mode.
- Phone Android app as the scoring hub, rules expert, and source of truth in Phone Only and Watch + Phone modes.
- Watch Only scoring as a valid standalone mode.
- Phone Only scoring as a first-class mode.
- Tablet Only scoring as a first-class standalone controller mode.
- Large-screen, portable-monitor, or Android-tablet scoreboard display for all players.
- Phone-to-tablet display sync works without Internet or venue Wi-Fi when the tablet joins the phone's hotspot.
- Setup-time scoring format selection: Traditional or Rally.
- Traditional scoring remains unchanged and is the default.
- Rally scoring uses RallyScore's social-play variant: every rally awards a point, while two serves, side-out flow, and court-position swapping remain the same as Traditional scoring.
- Setup screen for My Team and Opponent Team player names, with two searchable player selectors per team.
- Persistent local player management with add, edit, delete, search, alphabetical browsing, and recent-player quick selection.
- Setup player fields start blank, while saved players and recent players keep repeat-game setup fast.
- Select which team serves first before starting.
- Start score call is `0 - 0 - 2`.
- User records rally winner only.
- App automatically updates points, serving team, server number, and side outs.
- The app does not auto-end a game at 11, 15, or 21; players decide when to stop the match.
- Undo reverses the full previous rally.
- End game returns to setup after confirmation.
- Score calls are displayed and spoken after rally input.
- MVP voice modes: Off, Phone only, Watch only, Tablet only, Watch then Phone, Watch then Tablet, and Phone then Tablet.
- Offline operation.
- Keep the display source awake during use.

## Watch Responsibilities

Connected mode:

- Provide only extremely simple in-match controls:
  - My Team won rally.
  - Opponent won rally.
  - Undo.
- Send user intent to the phone.
- Avoid local scoring rules, server transitions, side-out handling, and match-state ownership.
- Receive confirmed phone-owned score state.
- Announce only confirmed phone-owned score state when the selected voice mode requires watch speech.

Standalone mode:

- May remain source of truth.
- Supports local Traditional or Rally scoring selection for Watch Only mode.
- Supports setup, scoring, undo, score display, and voice announcements.

Screen behavior (both modes):

- Keeps the display awake and fully interactive during active use.
- When the system dims the display for power saving, shows a low-power ambient readout of the
  current score instead of going fully black, on Wear OS hardware that supports it.

## Phone Responsibilities

- Own score calculations.
- Own server transitions and side outs.
- Own match state and undo history.
- Communicate with the watch.
- Communicate with the display device over the current local IP network, including external Wi-Fi or the phone's own hotspot.
- Announce confirmed score updates from the phone speaker when enabled.
- Continue scoring if the watch, tablet, or monitor disconnects.
- Provide direct phone scoring as a first-class Phone Only experience.

## Tablet Responsibilities

Tablet Only mode:

- Own score calculations using the shared scoring engine.
- Own server transitions and side outs.
- Own match setup, match state, undo history, correction mode when available, voice announcements, and display output.
- Provide tap-to-score panels: tapping a team's score records a rally win.
- Show UNDO and END buttons inside the call bar in controller mode.
- Provide large, high-contrast scoring controls and score display.
- Keep the screen awake.
- Local match state takes priority over remote state in routing.

Future synced mode:

- Share one canonical match state with the phone.
- Send tablet scoring intent to the phone instead of scoring a separate copy.
- Receive confirmed score state and update its display.
- Handle conflicts explicitly when phone and tablet act at nearly the same time.

## Display-Only Responsibilities

- Show team scores.
- Show serving team.
- Show server number.
- Avoid controls during an active match.
- Remain readable by all four players.
- Connect automatically when the phone and tablet are on the same local IP network.
- Require no Internet, cloud service, or manual IP address entry.

## Current Behavior

- The phone app uses team-colored score rows: blue background for Team A,
  green background for Team B, with white text and serving dots.
- Setup player fields start blank, with My Team selected to serve first by default.
- Saved players are stored locally on the device with stable IDs, name, created timestamp, and last-played timestamp.
- Setup player fields open searchable selectors that show recent players before the full alphabetical player list, while still allowing new typed names.
- New typed player names are trimmed, saved automatically when a game starts or resumes, and de-duplicated case-insensitively.
- GameSettings stores individual player names (teamAPlayer1, teamAPlayer2, teamBPlayer1, teamBPlayer2).
- The phone setup screen is always landscape with a side-by-side layout: team name cards on the left, voice controls and score preview card on the right.
- A swap teams button (⇅) sits between My Team and Opponent Team cards.
- The score preview card shows "WE SERVE FIRST" / "OPP SERVE FIRST" / "CHOOSE SERVER" with team-colored background, tappable to toggle starting team.
- Player names on the scoreboard are court-ordered (swap on odd scores to reflect switched court positions).
- The serving player name is underlined on the scoreboard.
- The call bar shows the serving player name below the score call (e.g., "P1 SERVES").
- A "Setup" button on the scoreboard returns to the setup screen to edit names without ending the match.
- Buttons are "START GAME" and "RESUME GAME" in red-orange (#D84315), no confirmation dialogs.
- TabletDisplayState includes servingPlayerName, teamACourtOrderedName, and teamBCourtOrderedName (wire protocol bumped to 16 fields).
- Player names and server indices are persisted in SharedPreferences for match restore after app relaunch.
- Server rotation uses a simplified fixed-position model: P1/P4 are always right-side starters (Server 1), P2/P3 are left-side (Server 2). First-server exception makes Server 2 act as Server 1 at game start.
- The watch start screen now uses a single mode selector that cycles between
  `TABLET MODE`, `WATCH MODE`, and `PHONE MODE`, followed by first-server
  selection and a separate `START` action.
- In direct Watch -> Tablet mode, the watch remembers the last selected tablet
  court and reconnects to that same court when rediscovered.
- If multiple tablets are on the same network, the watch exposes a tablet court
  selector so the player can choose the intended tablet explicitly.
- If only one tablet is discovered, the watch hides the court selector to
  preserve space and favors showing the `START` button fully.
- Watch-to-phone synchronization is implemented in initial form and continues to be hardened.
- My Team / Team A is blue; Opponent Team / Team B is green.
- Each team row displays both player names joined with `&`.
- Serving dots show current server count on the serving team row:
  - One dot means Server 1.
  - Two dots means Server 2.
- Tapping Team A score records Team A as rally winner.
- Tapping Team B score records Team B as rally winner.
- The call bar displays serving score first, receiving score second, server number third.
- Android Text-to-Speech can announce the confirmed phone-owned score call after rally input or undo.
- Connected Watch + Phone voice direction: watch announces the confirmed phone state immediately, and phone repeats the same confirmed score approximately two seconds later when Watch then Phone mode is selected.
- Connected Watch + Phone + Tablet voice direction: watch announces the confirmed phone state immediately, and tablet repeats the same confirmed score approximately two seconds later when Watch then Tablet mode is selected.
- Connected Phone + Tablet voice direction: phone announces the confirmed phone state immediately, and tablet repeats the same confirmed score approximately two seconds later when Phone then Tablet mode is selected.
- Phone Only mode announces immediately with no delay when Phone only mode is selected.
- Tablet Only mode announces immediately with no delay when Tablet only mode is selected.
- Bluetooth speaker routing can rely on Android audio routing for now.
- Scores continue beyond 11 for timed games.
- Tablet-sized screens are always usable. When disconnected or no active phone
  match is available, the tablet shows setup and can own a standalone match.
  In connected mode, tapping team score panels sends commands to the phone and
  the tablet redraws only after confirmed phone state returns. In local
  controller mode, tapping team score panels records rally wins through the
  shared scoring engine, and UNDO/END buttons appear in the call bar.
- The CALL bar on both tablet and phone uses a dark-navy background (#111827).
- The call score is enlarged: 180sp on tablet, 52sp on phone.
- Server dots on tablet are horizontal white circles (..) left of the score,
  with a counterbalance spacer centering the score number.
- On tablet surfaces, the top status row now shows the local court code plus
  compact WATCH and PHONE connection-state pills.
- On phone surfaces, the top connection pills show WATCH and TABLET state from
  the phone perspective.
- Score call spoken via TTS uses English words for numbers 21–99 (e.g.
  "twenty one") and digit-by-digit for 100+ (e.g. "1 0 3").
- Tablet screen routing checks local match state first; a local match always
  takes priority over remote display state. Disconnected or inactive remote
  state falls through to setup.
- Phone-to-tablet display sync is local-network based and should work over external Wi-Fi or a phone hotspot as long as both devices are on the same IP network.
- Player-name fields show a searchable dropdown with a "RECENT PLAYERS" section (marked with a small dot) and an "ALL PLAYERS" section, each row showing a color-coded initials chip; typing a name that doesn't match anyone offers a "Use '...'" option to confirm it as new. A clear (`×`) control appears once a field has text.
- A tablet passively displaying a phone-hosted match automatically adds the four players it observes to its own local player list (debounced so it only records once per match, not on every score update), so its own player suggestions stay useful even if the tablet never itself started a match. Manual edits via "Manage Players" still do not sync between devices.
- The phone-tablet WebSocket command channel authenticates live-match commands (rally winner, undo, score/serve adjustment, end match) with a per-connection HMAC signature in addition to the session-ID check, and both the WebSocket and TCP snapshot channels apply per-IP rate limiting. The periodic state broadcast itself remains unencrypted.
- Tablet Set Up Game screen uses larger team cards and player-name fields than the phone layout, vertically centers the whole screen in the available height, shows the RallyScore icon/title on its own larger top row with the connection-status badges on a second row, and uses a bigger "MANAGE PLAYERS" button.

## Non-Goals For Current MVP

- Cloud sync.
- Accounts.
- Tournament bracket management.
- Cloud-based remote control.
- Advanced external-display optimization beyond Android's normal screen mirroring.
- Cloud-based phone-to-tablet synchronization.
- Multi-controller phone-tablet synchronization before Tablet Only mode is stable.
- Custom voice packs.
- Dedicated Bluetooth speaker controls.
- Full match history persistence after app restart.

## Success Criteria

- A first-time user can understand scoring input within 30 seconds.
- A player can record a rally winner from the watch with minimal attention away from play.
- A tablet-only user can set up and score a match on a courtside tablet without a phone or watch.
- The shared scoreboard is readable from normal player distance on a phone-connected display or tablet.
- The operator does not manually manage side outs or server number.
- Display-only surfaces require no user interaction during a match.
- Controller surfaces expose only clear scoring and correction controls.
- Undo reliably restores the previous full rally state.
- The app can run offline and keep the display awake.
