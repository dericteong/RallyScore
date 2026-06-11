# Wireframes

These are text wireframes for the current design direction. They are not pixel-perfect specs.

## Phone Setup Screen

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

## Phone Score Screen

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

## Wear Score Screen

Round/small-screen optimized layout:

```text
---------------------
| dots | A | score   |
| dots | B | score   |
|   serving call     |
| [ A WON ] [ B WON ]|
| [UNDO]   [RESET]   |
---------------------
```

Current Wear behavior:

- Starts with A SERVES / B SERVES selection.
- Uses shared scoring rules.
- Maintains its own local state.
- Reset returns to serving-team selection.
- Does not sync with phone.
