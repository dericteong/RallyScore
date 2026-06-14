# Wireframes

These are text wireframes for the current design direction. They are not pixel-perfect specs.

## Supported Screen Modes

- Watch Only: Wear OS setup and scoring screen.
- Phone Only: phone setup and scoring screen.
- Watch + Phone: phone setup/source of truth, watch remote control, phone display.
- Watch + Phone + Tablet or Portable Monitor: phone source of truth, shared display through mirroring or passive tablet layout for MVP.

## Phone Setup Screen

The phone owns setup, scoring authority, and match state whenever it is present. Phone Only is a first-class mode. In Watch + Phone mode, the phone also communicates with the watch controller and display device.

Landscape layout:

```text
---------------------------------------------------------------
| SET UP GAME                    [⌚]  | FIRST SCORE CALL       |
| Enter names, then tap who serves... |                         |
|                                     |      0 - 0 - 2          |
| [My Team]                           |                         |
| [ player 1 input ][ player 2 input] | ME/OPP SERVES FIRST    |
|                                     |                         |
| [Opponent Team]                     | [ START 0 - 0 - 2 ]     |
| [ player 1 input ][ player 2 input] |                         |
---------------------------------------------------------------
```

Current setup behavior:

- Team player names are entered manually.
- Each team has separate Player 1 and Player 2 fields.
- Player fields sit side by side within each team section.
- Player fields are pre-populated with neutral development/social-play defaults: P1, P2, P3, and P4.
- Names are normalized to uppercase.
- Tapping the full-width team label band or focusing a team field selects that team as the first server.
- Team A is selected as the default first server, so the user may start immediately or edit values first.
- Start button is enabled when all four player names are non-empty and a starting team is selected.
- Keyboard-visible mode compacts the layout.
- Keyboard-visible mode shows a `DONE` control to hide the keyboard and reveal the Start button.
- Pressing Enter/Done does not auto-focus the next field.
- Shows watch connection status as a compact amber/red watch marker beside the `SET UP GAME` title.
- Shows Voice Announcements. Target MVP choices are Off, Phone only, Watch only, and Watch then Phone.

## Phone Score Screen

The phone score screen is the Phone Only scorer, connected-mode scoring hub display, and mirrored shared display surface. Direct phone scoring remains available even when no watch is connected.

Landscape layout:

```text
---------------------------------------------------------------
| dots | TEAM A PLAYER 1 & PLAYER 2             | Team A score |
|------+----------------------------------------+--------------|
| dots | TEAM B PLAYER 1 & PLAYER 2             | Team B score |
---------------------------------------------------------------
| CALL serving - receiving - server | UNDO | END              |
---------------------------------------------------------------
```

Interaction:

- Tap Team A score to record Team A won rally.
- Tap Team B score to record Team B won rally.
- One serving dot means Server 1.
- Two serving dots means Server 2.
- Only the serving team row shows dots.
- Call bar numbers are colored by team.
- Undo reverses last rally.
- End opens a confirmation dialog.
- Shows watch connection status.
- Phone speaker announces the confirmed score after rally input or undo when enabled.
- In Watch then Phone mode, the phone repeats the same confirmed score approximately two seconds after the watch announces it.

## Wear Controller Screen

Round/small-screen optimized primary controller layout:

```text
---------------------
|       SCORE        |
|      0 - 0 - 2     |
|                    |
| [ME WON] [OPP WON] |
|      [ UNDO ]      |
---------------------
```

Standalone Watch Only behavior:

- Starts with ME SERVES / OPP SERVES selection.
- Uses shared scoring rules.
- Maintains its own local state.
- Speaks the score after first-server selection, rally input, and undo.
- Gives haptic feedback after rally, undo, and reset actions.
- Keeps the watch screen awake while RallyScore is open.
- Reset returns to serving-team selection.

Connected Watch + Phone behavior:

- Shows standalone status when no phone state is available.
- Shows connected phone score state when phone sync is available.
- Watch sends My Team won rally, Opponent won rally, and Undo commands to the phone during live play.
- Phone remains the source of truth.
- Watch mirrors the phone-owned score state.
- Watch does not contain scoring logic.
- Watch never announces a predicted score.
- Connected-mode rally and undo controls are disabled while the watch waits for phone confirmation, reducing accidental double taps.
- Watch gives haptic feedback for sent commands, confirmed score updates, and phone-confirmation problems.
- Watch periodically refreshes phone connection and latest score state while open.
- In Watch then Phone mode, watch announces the confirmed phone score immediately after receiving phone state.

## Shared Display / Mirroring

The normal phone score screen is mirrored to a portable monitor, large screen, or Android tablet. No separate Display screen is required at this stage.

```text
---------------------------------------------------------------
| dots | TEAM A PLAYER 1 & PLAYER 2             | Team A score |
|------+----------------------------------------+--------------|
| dots | TEAM B PLAYER 1 & PLAYER 2             | Team B score |
---------------------------------------------------------------
| CALL serving - receiving - server | rail | UNDO | END       |
---------------------------------------------------------------
```

Display behavior:

- Shows team scores.
- Shows serving team.
- Shows server number.
- Shows player names.
- Keeps the normal phone CALL / UNDO / END row and small watch connection rail.
- Does not show large Team A Won or Team B Won buttons.
- Prioritizes readability for all four players.
- MVP shared display may use Android display mirroring.

## Tablet Display Mode

Tablet-sized Android screens use a passive display-only live match layout. The tablet display reads the current app-owned match state and does not show scoring controls.

```text
---------------------------------------------------------------
| P1 & P2                         | P3 & P4                    |
|                                 |                            |
|      08                         |      06                    |
|                                 |                            |
| SERVING - SERVER 2              | RECEIVING                  |
---------------------------------------------------------------
|                                                             |
|                         CALL                                |
|                       8 - 6 - 2                             |
|                  P1 & P2 SERVES - SERVER 2                  |
|                                                             |
---------------------------------------------------------------
```

Tablet display behavior:

- Shows team scores with very large numbers.
- Shows serving side and server number.
- Shows player names when space allows.
- Shows CALL in an oversized lower band, roughly half the tablet display area.
- Uses Team A blue and Team B green.
- Uses high contrast and landscape-first spacing.
- Keeps the screen awake through the existing app activity behavior.
- Does not show Team A Won, Team B Won, Undo, End, Reset, setup, or other live-match controls.
- Does not contain scoring logic or independent match authority.
- Initial wireless sync uses local-network display snapshots from the phone source of truth.
- If wireless snapshots stop, the tablet drops stale remote display state after a short timeout.
