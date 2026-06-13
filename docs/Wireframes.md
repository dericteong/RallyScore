# Wireframes

These are text wireframes for the current design direction. They are not pixel-perfect specs.

## Supported Screen Modes

- Watch Only: Wear OS setup and scoring screen.
- Phone Only: phone setup and scoring screen.
- Watch + Phone: phone setup/source of truth, watch remote control, phone display.
- Watch + Phone + Tablet or Portable Monitor: phone source of truth, shared display through mirroring for MVP.

## Phone Setup Screen

The phone owns setup, scoring authority, and match state whenever it is present. Phone Only is a first-class mode. In Watch + Phone mode, the phone also communicates with the watch controller and display device.

Landscape layout:

```text
---------------------------------------------------------------
| SET UP GAME                         | FIRST SCORE CALL       |
| Enter names, then tap who serves... |                         |
|                                     |      0 - 0 - 2          |
| [Team A players]                    |                         |
| [ player 1 input ][ player 2 input] | TEAM A/B SERVES FIRST  |
|                                     |                         |
| [Team B players]                    | [ START 0 - 0 - 2 ]     |
| [ player 1 input ][ player 2 input] |                         |
---------------------------------------------------------------
```

Current setup behavior:

- Team player names are entered manually.
- Each team has separate Player 1 and Player 2 fields.
- Player fields sit side by side within each team section.
- Names are normalized to uppercase.
- Tapping the full-width team label band or focusing a team field selects that team as the first server.
- Start button is enabled only when all four player names are non-empty and a starting team is selected.
- Keyboard-visible mode compacts the layout.
- Keyboard-visible mode shows a `DONE` control to hide the keyboard and reveal the Start button.
- Pressing Enter/Done does not auto-focus the next field.
- Shows watch connection status.
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
| [ A WON ] [ B WON ]|
|      [ UNDO ]      |
---------------------
```

Standalone Watch Only behavior:

- Starts with A SERVES / B SERVES selection.
- Uses shared scoring rules.
- Maintains its own local state.
- Speaks the score after first-server selection, rally input, and undo.
- Reset returns to serving-team selection.

Connected Watch + Phone behavior:

- Shows standalone status when no phone state is available.
- Shows connected phone score state when phone sync is available.
- Watch sends Team A won rally, Team B won rally, and Undo commands to the phone during live play.
- Phone remains the source of truth.
- Watch mirrors the phone-owned score state.
- Watch does not contain scoring logic.
- Watch never announces a predicted score.
- In Watch then Phone mode, watch announces the confirmed phone score immediately after receiving phone state.

## Shared Display Screen

Passive display layout for portable monitor, large screen, Android tablet, or phone display:

```text
---------------------------------------------------------------
|                         PICKLEBALL                          |
|                                                             |
|          TEAM A                         TEAM B              |
|           04                             02                 |
|                                                             |
|                    SERVING: TEAM A                          |
|                       SERVER: 2                             |
---------------------------------------------------------------
```

Display behavior:

- Shows team scores.
- Shows serving team.
- Shows server number.
- Does not show rally controls.
- Does not show undo, reset, setup, or end controls during live play.
- Prioritizes readability for all four players.
- MVP shared display may use Android display mirroring.
