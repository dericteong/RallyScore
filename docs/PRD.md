# Product Requirements Document

## Product Name

RallyScore

Tagline: Wear. Play. Score.

## Problem

Pickleball games often need a clear, portable scoreboard that players can update without leaving the court. Existing manual scoreboards require physical handling, and generic scoring apps often require the operator to understand side outs, server number changes, and score-call order.

RallyScore is a flexible pickleball scoring ecosystem. It should work with different combinations of devices, and additional devices should enhance the experience rather than being mandatory. The phone is the source of truth whenever a phone is present. Watch Only remains a valid casual, demo, and backup mode.

Canonical connected system flow:

```text
Wear OS watch
    ↓
Android phone scoring hub
    ↓
Portable monitor, Android tablet, or phone display
    ↓
Large shared scoreboard
```

## Target Users

- Players who want to score from a Wear OS watch while actively playing.
- Players who want a first-class Phone Only scorer and scoreboard.
- Other players on court who need a shared, readable scoreboard.
- Coaches, organizers, or clubs using a phone connected to a portable monitor, large screen, or Android tablet display.
- A player, volunteer, or referee scoring directly from the phone.

## Supported Modes

### Mode 0 - Watch Only

- Wear OS watch is the source of truth.
- Supports match setup, first server selection, A WON, B WON, Undo, score display, and voice announcements.
- Purpose: casual play, minimal setup, demo mode, and backup mode.

### Mode 1 - Phone Only

- Android phone is the source of truth.
- Supports match setup, team names, first server selection, score display, A WON, B WON, Undo, and voice announcements.
- This is a first-class experience.

### Mode 2 - Watch + Phone

- Phone is the source of truth.
- Watch acts as a remote control with A WON, B WON, and Undo.
- Phone owns scoring rules, side outs, server transitions, undo history, voice announcements, and display output.

### Mode 3 - Watch + Phone + Android Tablet

- Phone remains the source of truth.
- Tablet acts as a shared display.
- For MVP, simple mirrored display is acceptable. Future wireless tablet client support may be added.

### Mode 4 - Watch + Phone + Portable Monitor

- Phone remains the source of truth.
- Portable monitor acts as a shared display.
- For MVP, simple display mirroring is acceptable.

## Product Priorities

1. Phone remains stable source of truth.
2. Watch controls phone score.
3. Phone-only experience is excellent.
4. Large display mode optimized for mirroring.
5. Phone voice announcements.
6. Tablet support.
7. Portable monitor support.
8. Wireless tablet mode.
9. Bluetooth speaker support.
10. Tournament features.

## MVP Goals

- Wear OS watch as a first-class rally-input controller in connected mode.
- Phone Android app as the scoring hub, rules expert, and source of truth whenever present.
- Watch Only scoring as a valid standalone mode.
- Phone Only scoring as a first-class mode.
- Large-screen, portable-monitor, or Android-tablet scoreboard display for all players.
- Standard doubles pickleball scoring.
- Setup screen for Team A and Team B player names, with two player fields per team.
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
  - Team A won rally.
  - Team B won rally.
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
- Communicate with the display device.
- Announce confirmed score updates from the phone speaker when enabled.
- Continue scoring if the watch, tablet, or monitor disconnects.
- Provide direct phone scoring as a first-class Phone Only experience.

## Display Responsibilities

- Show team scores.
- Show serving team.
- Show server number.
- Avoid controls during an active match.
- Remain readable by all four players.

## Current Behavior

- The phone app currently uses a table-style score screen and can be used directly as a first-class Phone Only scoring mode.
- The Wear app currently supports standalone prototype scoring with watch-side score calls. This is a temporary implementation mismatch with the target architecture; future watch work should evolve it into a command-only controller.
- Watch-to-phone synchronization is not implemented yet.
- Team A is blue; Team B is green.
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

## Non-Goals For Current MVP

- Cloud sync.
- Accounts.
- Tournament bracket management.
- Cloud-based remote control.
- Advanced external-display optimization beyond Android's normal screen mirroring.
- Custom voice packs.
- Dedicated Bluetooth speaker controls.
- Full match history persistence after app restart.

## Success Criteria

- A first-time user can understand scoring input within 30 seconds.
- A player can record a rally winner from the watch with minimal attention away from play.
- The shared scoreboard is readable from normal player distance on a phone-connected display or tablet.
- The operator does not manually manage side outs or server number.
- The display device requires no user interaction during a match.
- Undo reliably restores the previous full rally state.
- The app can run offline and keep the display awake.
