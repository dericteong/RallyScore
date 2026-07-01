# Wireframes

These are text wireframes for the current design direction. They are not pixel-perfect specs.

## Supported Screen Modes

- Watch Only: Wear OS setup and scoring screen.
- Phone Only: phone setup and scoring screen.
- Tablet Only: tablet setup and large scoring-controller screen.
- Watch + Phone: phone setup/source of truth, watch remote control, phone display.
- Phone + Tablet synced: future shared canonical match state across phone and tablet.
- Watch + Phone + Tablet synced: future watch remote control with phone/tablet sharing one canonical match state.
- Watch + Phone + Portable Monitor: phone source of truth, shared display through mirroring.

## Phone Setup Screen

The phone owns setup, scoring authority, and match state whenever it is present. Phone Only is a first-class mode. In Watch + Phone mode, the phone also communicates with the watch controller and display device.

Landscape side-by-side layout:

```text
------------------------------------------------------------------
| [blue square] RallyScore         WATCH CONNECTED | TABLET S... |
| SET UP GAME                                                    |
| Enter players by court position.                               |
| -------------------------------------------------------------- |
| [blue card] My Team        | [dark gray circle ⇅] | SCORING    |
| [P1 input ][P2 input ]     |                       | [CLASSIC]  |
|                             |                       | [RALLY]    |
| [green card] Opponent Team |                       |  VOICE     |
| [P3 input ][P4 input ]     |                       |  [mode]    |
|                             |                       |  [WE SERVE |
|                             |                       |   FIRST]   |
| -------------------------------------------------------------- |
|                                            [ START GAME ]      |
------------------------------------------------------------------
```

Current setup behavior:

- The phone setup screen uses landscape orientation (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`).
- Side-by-side layout with team name fields on the left and score preview/voice controls on the right.
- App title "RallyScore" with a solid blue square badge at top left.
- Swap teams button (⇅) between My Team and Opponent Team cards.
- Solid team-colored cards (blue for My Team, green for Opponent Team).
- `imePadding()` applied so keyboard does not crop content.
- The team form column is always vertically scrollable.
- Each team has separate Player 1 and Player 2 fields, side by side within the team card.
- Player fields are pre-populated with defaults: P1, P2, P3, P4.
- Names are normalized to uppercase.
- Tapping the team label band or focusing a team field selects that team as the first server.
- Team A is selected as the default first server.
- Start button is enabled when all four player names are non-empty and a starting team is selected.
- Keyboard-visible mode shows a `DONE` control to hide the keyboard.
- Pressing Enter/Done does not auto-focus the next field.
- Connection status pills appear below the `SET UP GAME` title.
- Scoring selector appears in the right column with Traditional and Rally options.
- Traditional is the default selected option.
- Voice Announcements dropdown appears in the right column.
- Score Preview Card in the right column shows "WE SERVE FIRST" / "OPP SERVE FIRST" / "CHOOSE SERVER" with team-colored background, tappable to toggle starting team.

## Phone Score Screen

The phone score screen is the Phone Only scorer, connected-mode scoring hub display, and mirrored shared display surface. Direct phone scoring remains available even when no watch is connected.

Landscape layout:

```text
---------------------------------------------------------------
| WATCH CONNECTED | TABLET S... |                    [Setup]   |
---------------------------------------------------------------
| [blue]  dots | P1 & P2 (or P2 & P1)       | score [white]  |
|              | P1 underlined if serving    |                |
| [green] dots | P3 & P4 (or P4 & P3)       | score [white]  |
|              | P4 underlined if serving    |                |
---------------------------------------------------------------
| [SETUP] [EDIT]         8 - 6 - 2                    [UNDO] [END] |
---------------------------------------------------------------
```

Interaction:

- Team A row has a blue background; Team B row has a green background.
  Text and serving dots are white.
- Player names are court-ordered: swapped on odd scores (e.g., P2 & P1 on Team A odd score).
- The serving player name is underlined in the team name display.
- Tap Team A score to record Team A won rally.
- Tap Team B score to record Team B won rally.
- One serving dot means Server 1.
- Two serving dots means Server 2.
- Only the serving team row shows dots.
- Call bar uses dark charcoal background (#202124) with all-white score-call text.
  The phone call bar is 174dp high with 120sp score-call text, with a
  152dp/109sp compact-height fallback. Double-digit calls use slightly smaller
  adaptive text so the server number remains visible. Setup/Edit controls remain
  on the left and Undo/End controls remain on the right.
- Watch and tablet connection status bars shown at top of screen, with a "Setup" button to return to the setup screen.
- Edit opens a match-adjustment dialog for score, serving side, and server number.
- Undo reverses last rally or correction.
- End opens a confirmation dialog.
- Phone speaker announces the confirmed score after rally input or undo when enabled.
- In Watch then Phone mode, the phone repeats the same confirmed score approximately two seconds after the watch announces it.
- In Watch then Tablet mode, the tablet repeats the same confirmed score approximately two seconds after the watch announces it.
- In Phone then Tablet mode, the tablet repeats the same confirmed score approximately two seconds after the phone announces it.

## Wear Controller Screen

Round/small-screen optimized primary controller layout:

```text
---------------------
|  • PHONE CONNECTED |
|      8 - 6 - 2     |
|        [ ↶ ]       |
| [ ME  8 ][ OPP  6 ]|
---------------------
```

- Uses a black background with white primary text and light-gray secondary text.
- My Team uses bright blue; Opponent uses bright green.
- Connected status uses amber and disconnected/standalone status uses red.
- The top connection state is a compact rounded pill that keeps status visible without dominating the screen.
- The score tiles are the primary scoring controls. Tapping the blue or green score tile records the rally winner.
- The score call remains above the controls and omits the literal `CALL` label.
- Undo sits alone in a compact utility row beneath the score call.
- End moves off the crowded main row and is triggered from a secondary gesture so score tiles can stay fully readable on round displays.

Standalone Watch Only behavior:

- Supports a local `CLASSIC` / `RALLY` scoring selector before starting the match.
- Starts with `WE SERVE FIRST` / `OPP SERVE FIRST` selection.
- Uses shared scoring rules.
- Maintains its own local state.
- Speaks the score after first-server selection, rally input, and undo.
- Gives haptic feedback after rally, undo, and reset actions.
- Keeps the watch screen awake while RallyScore is open.
- Reset returns to serving-team selection.

Connected Watch + Phone behavior:

- Shows standalone status when no phone state is available.
- When a remote target is available but idle, the watch shows a single mode
  button that cycles through `TABLET MODE`, `WATCH MODE`, and `PHONE MODE`.
- After selecting the mode, the user chooses `WE SERVE FIRST` or
  `OPP SERVE FIRST`, then taps a separate `START` button.
- When `WATCH MODE` is selected, the watch also shows a local `CLASSIC` /
  `RALLY` scoring selector for standalone watch-owned matches only.
- In the connected idle state, `PHONE MODE` can start a fresh phone-owned match
  directly from the watch using the existing phone team names unless the names
  need to change.
- When `TABLET MODE` is selected and more than one tablet court is discovered,
  the watch shows the selected tablet court code and lets the user tap to
  switch among discovered tablet courts on the same network.
- When only one tablet is discovered, the watch hides the tablet court selector
  to preserve space and keep `START` visible.
- Shows connected phone score state when phone sync is available and the phone has an active match.
- Watch sends My Team won rally, Opponent won rally, and Undo commands to the phone during live play.
- Phone remains the source of truth.
- Watch mirrors the phone-owned score state.
- Watch does not contain scoring logic.
- Watch never announces a predicted score.
- Connected-mode rally and undo controls are disabled while the watch waits for phone confirmation, reducing accidental double taps.
- Watch gives haptic feedback for sent commands, confirmed score updates, and phone-confirmation problems.
- Watch periodically refreshes phone connection and latest score state while open.
- In Watch then Phone mode, watch announces the confirmed phone score immediately after receiving phone state.
- In Watch then Tablet mode, watch announces the confirmed phone score immediately after receiving phone state.

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

## Tablet Controller Mode

Tablet-sized Android screens should support standalone setup and scoring. Tablet Only owns its own match state and uses the shared scoring engine, matching the Phone Only rules and behavior.

Tablet setup:

```text
---------------------------------------------------------------
| SET UP GAME                                                  |
|                                                             |
| My Team                     Opponent Team                    |
| [ P1 ][ P2 ]                [ P3 ][ P4 ]                     |
|                                                             |
| Who serves first?       [ ME ] [ OPP ]                       |
|                                                             |
|                     [ START 0 - 0 - 2 ]                      |
---------------------------------------------------------------
```

Tablet score controller:

```text
---------------------------------------------------------------
| PHONE CONNECTED                                              |
---------------------------------------------------------------
| [blue] P1 & P2                  | [green] P3 & P4            |
|                                 |                            |
|  ..    08                       |        06                  |
|                                 |                            |
---------------------------------------------------------------
| [dark navy]                                                   |
| CALL             8 - 6 - 2      [white, oversized]           |
| P1 & P2 SERVES  •  SERVER 2               [UNDO]  [END]      |
---------------------------------------------------------------
```

Tablet controller behavior:

- Shows a connection status bar at the top: "PHONE CONNECTED",
  "PHONE RECONNECTING", or "SEARCHING FOR PHONE".
- Each team panel uses its team color as background (blue/green)
  with white text and serving dots.
- Tapping a team's score panel records a rally win for that team
  (no separate ME WON/OPP WON buttons).
- Server dots appear as white circles in a horizontal row (..)
  left of the score, and serving/server status now lives in the call bar.
- Call bar uses dark-navy background (#111827). CALL label sits left of the
  score call, which is oversized for distance readability.
- UNDO and END buttons appear inside the call bar in local
  controller mode. In remote-display mode, no controls are shown.
- Provides correction mode when available.
- Provides voice announcements when enabled.
- Uses Team A blue and Team B green.
- Uses high contrast and landscape-first spacing.
- Keeps the screen awake through the existing app activity behavior.
- Uses the shared scoring engine; UI must not duplicate scoring rules.
- Owns match state only in Tablet Only mode.
- Must not independently score a phone-owned synced match.

## Tablet Display Client Prototype

When the tablet is disconnected, searching, or has no active phone-owned match,
it uses the normal setup screen and can start a standalone tablet-owned match.
There is no blocking waiting-for-phone screen in the primary flow.

```text
---------------------------------------------------------------
| SEARCHING FOR PHONE / RECONNECTING / PHONE CONNECTED        |
---------------------------------------------------------------
| [blue] P1 & P2                  | [green] P3 & P4            |
|                                 |                            |
|  ..    08                       |        06                  |
|                                 |                            |
| SERVING - SERVER 2              | RECEIVING                  |
---------------------------------------------------------------
| [dark navy]                                                   |
|                         CALL                                  |
|                       8 - 6 - 2      [white, 180sp]          |
|                  P1 & P2 SERVES - SERVER 2                    |
---------------------------------------------------------------
```

Connected tablet behavior:

- Shows team scores on team-colored backgrounds (blue/green) with white text.
- Shows serving side and server number with horizontal server dots.
- Shows CALL in an oversized lower band with dark-navy background
  (#111827) and white label/score.
- Connection status bar at top shows current phone connection state.
- Uses Team A blue and Team B green. Uses high contrast.
- Keeps the screen awake.
- Tapping team score panels sends rally-winner commands to the phone.
- UNDO and END send commands to the phone.
- Does not contain scoring logic or independent match authority while
  connected to a phone-owned match.
- Does not update optimistically; the screen updates only after confirmed
  phone-owned snapshots return.
- Initial wireless sync uses local-network WebSocket snapshots and
  tablet-to-phone commands.
- If wireless snapshots stop, the tablet drops stale remote display
  state after a short timeout.

Routing note: The tablet checks local match state first. If a local
match is active, the full controller screen (with tap-to-score and
UNDO/END) is shown regardless of remote state. Connected phone-owned
screens only render when no local match is active and the phone snapshot
reports an active match. Otherwise the tablet shows setup.
