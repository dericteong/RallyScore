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
- Supports match setup, first server selection, ME WON, OPP WON, Undo, score display, and voice announcements.
- Purpose: casual play, minimal setup, demo mode, and backup mode.

### Mode 1 - Phone Only

- Android phone is the source of truth.
- Supports match setup, team names, first server selection, score display, ME WON, OPP WON, Undo, and voice announcements.
- This is a first-class experience.

### Mode 2 - Watch + Phone

- Phone is the source of truth.
- Watch acts as a remote control with ME WON, OPP WON, and Undo.
- Phone owns scoring rules, side outs, server transitions, undo history, voice announcements, and display output.

### Mode 3 - Tablet Only

- Android tablet is the source of truth.
- Supports match setup, team names, first server selection, score display, ME WON, OPP WON, Undo, correction mode when available, and voice announcements.
- Uses the same shared scoring engine as the phone.
- Purpose: social play where a tablet is placed courtside and any player can tap scoring controls.

### Mode 4 - Phone + Android Tablet Synced

- Phone and tablet share one canonical match state.
- Either phone or tablet may update the score after synchronization is established.
- Score updates from either device must update the other device.
- Conflict handling is required when both devices send changes at nearly the same time.
- This is future work after Tablet Only is stable.

### Mode 5 - Watch + Phone + Android Tablet Synced

- Phone remains the source of truth.
- Watch acts as remote control through the phone.
- Tablet may act as a synced controller/display once phone-tablet sync exists.
- Score updates from watch, phone, or tablet must resolve through one canonical match state.
- This is future work after Tablet Only and Phone + Tablet sync are stable.

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
- Standard doubles pickleball scoring.
- Setup screen for My Team and Opponent Team player names, with two player fields per team.
- Short development/social-play defaults pre-populate setup fields so a user can start immediately.
- Select which team serves first before starting.
- Start score call is `0 - 0 - 2`.
- User records rally winner only.
- App automatically updates points, serving team, server number, and side outs.
- Undo reverses the full previous rally.
- End game returns to setup after confirmation.
- Score calls are displayed and spoken after rally input.
- MVP voice modes: Off, Phone only, Watch only, and Watch then Phone.
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
- Supports setup, scoring, undo, score display, and voice announcements.

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
- Send tablet scoring intent to the sync owner instead of scoring a separate copy.
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
- Setup defaults are P1, P2, P3, and P4, with My Team selected to serve first by default.
- The Wear app currently supports standalone prototype scoring with watch-side score calls. This is a temporary implementation mismatch with the target architecture; future watch work should evolve it into a command-only controller.
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
- Phone Only mode announces immediately with no delay when Phone only mode is selected.
- Bluetooth speaker routing can rely on Android audio routing for now.
- Scores continue beyond 11 for timed games.
- Tablet-sized screens support both a passive remote-display mode and a local
  controller mode. In controller mode, tapping team score panels records rally
  wins, and UNDO/END buttons appear in the call bar. In remote-display mode,
  no controls are shown.
- The CALL bar on both tablet and phone uses a dark-navy background (#111827).
- The CALL label is white (38sp tablet, 28sp phone) rendered as a separate
  Text composable from the score call numbers.
- The call score is enlarged: 180sp on tablet, 52sp on phone.
- Server dots on tablet are horizontal white circles (..) left of the score,
  with a counterbalance spacer centering the score number.
- Phone and tablet show connection status bars at the top of the score screen
  (WATCH CONNECTED/OFFLINE and TABLET CONNECTED/SEARCHING).
- Score call spoken via TTS uses English words for numbers 21–99 (e.g.
  "twenty one") and digit-by-digit for 100+ (e.g. "1 0 3").
- Tablet screen routing checks local match state first; a local match always
  takes priority over remote display state. `TabletWaitingForPhoneScreen` is
  preserved.
- Phone-to-tablet display sync is local-network based and should work over external Wi-Fi or a phone hotspot as long as both devices are on the same IP network.

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
