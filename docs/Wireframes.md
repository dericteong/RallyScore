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
- Shows Voice Announcements. Target MVP choices are Off, Phone, Watch, Tablet, Watch > Phone, Watch > Tablet, and Phone > Tablet.

## Phone Score Screen

The phone score screen is the Phone Only scorer, connected-mode scoring hub display, and mirrored shared display surface. Direct phone scoring remains available even when no watch is connected.

Landscape layout:

```text
---------------------------------------------------------------
| WATCH CONNECTED                                             |
---------------------------------------------------------------
| [blue]  dots | TEAM A P1 & P2             | score [white]  |
| [green] dots | TEAM B P1 & P2             | score [white]  |
---------------------------------------------------------------
| CALL serving - receiving - server | UNDO | END              |
---------------------------------------------------------------
```

Interaction:

- Team A row has a blue background; Team B row has a green background.
  Text and serving dots are white.
- Tap Team A score to record Team A won rally.
- Tap Team B score to record Team B won rally.
- One serving dot means Server 1.
- Two serving dots means Server 2.
- Only the serving team row shows dots.
- Call bar uses dark-navy background (#111827). CALL label is white (28sp).
  Score call text is 52sp, team-colored.
- Watch connection status shown as a full-width bar at top of screen.
- Undo reverses last rally.
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

- Starts with ME SERVES / OPP SERVES selection.
- Uses shared scoring rules.
- Maintains its own local state.
- Speaks the score after first-server selection, rally input, and undo.
- Gives haptic feedback after rally, undo, and reset actions.
- Keeps the watch screen awake while RallyScore is open.
- Reset returns to serving-team selection.

Connected Watch + Phone behavior:

- Shows standalone status when no phone state is available.
- When the phone is connected but idle, shows explicit `START ON WATCH` and `START ON PHONE` choices. `START ON WATCH` begins a standalone watch-owned match with ME SERVES or OPP SERVES. `START ON PHONE` keeps the watch in a waiting state until a phone-owned match begins.
- In the connected idle state, the watch may also start a fresh phone-owned match directly using the existing phone team names and either `ME SERVES` or `OPP SERVES`, so players can restart from the watch after ending a phone-owned game without touching the phone unless names change.
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
