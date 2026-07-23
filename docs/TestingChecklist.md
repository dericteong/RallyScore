# Testing Checklist

## Automated Tests

Run before shipping scoring or ViewModel changes:

```bash
./gradlew :shared:test :app:test
```

Run before shipping phone app changes:

```bash
./gradlew :app:assembleDebug
```

Run before shipping Wear changes:

```bash
./gradlew :wear:assembleDebug
```

Install only the affected app on real hardware:

```bash
./gradlew :app:installDebug
./gradlew :wear:installDebug
```

Do not use root `./gradlew installDebug` on a phone; it installs both phone and Wear modules and creates multiple launcher icons.

Since release builds run with R8 (`isMinifyEnabled = true`), also run a release build before shipping to catch obfuscation/shrinking issues that debug builds won't surface:

```bash
./gradlew :app:assembleRelease
```

## Scoring Rules

Verify:

- Traditional mode starts at `0 - 0 - 2`.
- Traditional mode: serving team scores when it wins a rally.
- Traditional mode: receiving team does not score when it wins a rally.
- Traditional mode: first-server exception sides out immediately.
- Traditional mode: Server 1 loss changes to Server 2.
- Traditional mode: Server 2 loss causes side out.
- Rally mode starts at `0 - 0 - 2`.
- Rally mode: serving team scores when it wins a rally and keeps the same serve flow.
- Rally mode: receiving team scores when it wins a rally and the existing two-server transition logic still applies.
- Rally mode: Server 1 loss changes to Server 2.
- Rally mode: Server 2 loss causes side out.
- Score call always shows serving score first.
- Rally mode still uses the three-number score call.
- Score can continue above 11 for timed play.
- Score caps at 99 and does not roll over; this applies to rally-winner scoring and to manual
  +/- corrections from both the phone's correction dialog and the tablet's remote adjust commands.
- Tapping a rally winner for a team already at 99 shows an on-screen message about the cap and
  starting a new game, on both phone and tablet.
- Undo restores complete prior state.
- `servingPlayerName()` returns P1 at game start (first-server exception, Team A).
- `servingPlayerName()` returns P4 when Team B serves with Server One and no exception.
- `servingPlayerName()` returns P2 when Team A serves with Server Two and no exception.
- `servingPlayerName()` returns P3 when Team B serves with Server Two and no exception.
- `courtOrderedTeamName(Team.A)` swaps to P2 & P1 when Team A score is odd.
- `courtOrderedTeamName(Team.B)` swaps to P4 & P3 when Team B score is odd.
- Side out to Team B from Team A Server Two gives P4 as Server 1.
- Side out to Team A from Team B Server Two gives P1 as Server 1.

## Phone Setup Manual Test

Use a real phone when possible.

- App launches into landscape setup when no match is active.
- Setup screen shows side-by-side layout: team cards on left, score preview on right.
- App title "RallyScore" with blue square badge at top left.
- Swap teams button (⇅) appears between My Team and Opponent Team cards.
- Team cards use solid blue (My Team) and solid green (Opponent Team) backgrounds.
- Score preview card shows "CHOOSE SERVER" when no team selected, "WE SERVE FIRST" or "OPP SERVE FIRST" when selected, with team-colored background.
- Setup screen exposes Traditional and Rally scoring options.
- Traditional is selected by default.
- Tapping score preview card toggles starting team.
- Player fields are select-only: tapping a field opens its dropdown directly, with no on-screen keyboard ever appearing (there is no free-text entry on this screen).
- Player fields start blank and show Player 1 / Player 2 hints.
- Manage Players opens from setup and can add, edit, delete, search, browse, and bulk-delete saved players.
- "Import From Screenshot" opens the system photo picker (multiple images allowed), runs on-device OCR, and shows a checkable review dialog of newly recognized names before adding them; names already saved are skipped automatically without appearing in the review list.
- Screenshot import is tested against real attendee-list screenshots exported from the **OpenSports** app — the primary real-world source this feature targets. Verify with an actual OpenSports export that player names are recognized from the attendee list, and that surrounding UI text (screen headers, buttons, attendee counts, dates/times) is not imported as a player name.
- "Delete All Players" is disabled when the player list is empty, and prompts for confirmation (stating how many players will be removed) before clearing the list.
- The "IMPORT FROM SCREENSHOT" and "DELETE ALL PLAYERS" buttons are visually balanced (equal width), not one dominating the row.
- Saved players appear alphabetically in All Players.
- Selecting a player on Set Up Game keeps that player's original stored casing (no forced uppercase); Manage Players' own add/search/edit fields still uppercase as typed.
- Duplicate saved players are prevented by case-insensitive name comparison.
- A player already selected in one of the four Set Up Game fields no longer appears in the other three fields' dropdowns.
- If two fields somehow end up with the same name anyway, the offending field shows a red border and Start stays disabled until it's fixed.
- Recently used players appear before All Players in setup player selectors after a game starts.
- Saved player data remains available after app restart.
- Team A is selected by default, and Start is enabled as soon as a starting server is chosen — player names are optional.
- Starting with some or all player fields blank works: each blank field falls back to its court-position placeholder (P1/P2 for My Team, P3/P4 for Opponent), and the scoreboard, voice calls, and any connected tablet/watch all show those placeholders.
- Placeholder names are NOT added to the saved player list — only names actually typed are persisted.
- Start still stays disabled while two entered names duplicate each other (blank fields never count as duplicates).
- Watch and tablet connection pills fit side by side below the `SET UP GAME` title.
- Voice Announcements appears in the right column with dropdown.
- Player 1 and Player 2 fields fit side by side in each team card.
- Team form column is always vertically scrollable.
- User can select starting server by tapping the team label band or tapping a player field.
- Start button ("START GAME") is red-orange (#D84315), enabled once a starting server is set and no two entered names duplicate each other.
- When editing from match, "RESUME GAME" button appears instead (also red-orange).
- No confirmation dialog on Start or Resume.
- Voice Announcements setting is visible and usable.
- Voice Announcements includes Off, Phone, Watch, Tablet, Watch > Phone, Watch > Tablet, Phone > Tablet, and Watch > Phone > Tablet.
- Tapping a player field shows a dropdown with RECENT PLAYERS and ALL PLAYERS sections, each row with a color-coded initials chip.
- If no players are saved yet, the dropdown shows a disabled hint to add players via Manage Players instead.
- Once a field has a selection, tapping the `×` control clears it.
- Selecting a dropdown row fills the field; no keyboard ever appears to dismiss.
- On a phone-width screen, COURT/WATCH/TABLET status badges all fit on one row with no visual clipping (shortened labels: "35DD"/"WATCH"/"TABLET").
- A small "© 2026 Deric Teong" line is visible pinned to the bottom-right corner of the screen, on both phone and tablet layouts.

## Tablet Setup Manual Test

- Team cards and player-name fields are visibly larger than the phone layout.
- The whole setup screen (team cards + right-side controls) is vertically centered in the available height, not pinned to the top with empty space below.
- RallyScore icon/title render on their own row near the top, clearly larger than on phone.
- COURT/WATCH/TABLET status badges render on their own row below the RallyScore row, each showing its full label with no truncation.
- "MANAGE PLAYERS" button is visibly larger than on phone.
- "Enter players by court position" sits between the "SET UP GAME" title and "MANAGE PLAYERS" button, not glued next to the button.
- With exactly one discovered phone already selected, "AVAILABLE PHONES" shows as a single status-badge line, not the full card.
- With more than one discovered phone, the full "AVAILABLE PHONES" card with selectable rows still appears.
- The court-candidate label (e.g. "COURT 1234 · Deric T. & Steve W. vs ..." ) shows abbreviated player names, not full names.

## Phone Score / Display Manual Test

- Phone uses landscape for score display.
- Scores are large and readable.
- Scoreboard uses high-contrast colors suitable for outdoor play.
- Scores, score call, serving dots, and phone controls are readable for older players.
- Team A row uses blue.
- Team B row uses green.
- Team rows show player names in court-ordered format (swaps on odd scores).
- Team rows show abbreviated names ("First L.", e.g. "Deric T.") rather than full names; single-word names (no last name on file) show unabbreviated.
- If an abbreviated name still doesn't fit its column (long first name, serving-highlight's larger
  font, etc.), it shrinks in steps to fit on one line instead of truncating with "…".
- Serving player name is underlined in the team name display.
- Score call bar hyphens ("0 - 0 - 2") render clearly smaller than the digits, vertically centered
  (not sitting low near the baseline), with a small legible gap on both sides.
- Table dividers are neutral.
- Serving team row shows one or two dots.
- Tap Team A score records Team A rally winner.
- Tap Team B score records Team B rally winner.
- No manual score controls are visible.
- No manual server controls are visible.
- Call bar uses team colors for the relevant numbers.
- Call bar shows serving player below score call (e.g., "P1 SERVES").
- "Setup" button in top bar returns to setup screen for editing names mid-match.
- Phone score screen shows an `EDIT` action.
- Phone `EDIT` opens a match-adjustment dialog for Team A score, Team B score, serving side, and server number.
- Correction dialog team labels read "My Team (Blue)" / "Opponent (Green)" or "Opponent Team (Green)", matching the setup screen's color-suffix convention, on both phone and tablet layouts.
- Phone correction updates the visible call row and remains undoable.
- TTS announces after rally input.
- Voice Announcements defaults appropriately for Phone Only mode.
- Voice Announcements can be set to Off.
- Phone speaker announces the confirmed score after watch rally input.
- Undo restores last rally.
- Phone speaker announces the restored score after undo when voice is enabled.
- End opens confirmation.
- Confirming End returns to setup.
- Screen stays awake during use.
- Display remains readable when mirrored or shown on the intended phone/tablet/monitor display.
- Direct phone scoring remains usable as first-class Phone Only mode.
- Watch connection status indicator is visible.
- Tablet connection status indicator is visible.
- Phone Only mode works as a first-class experience without a watch.

## Watch Only Manual Test

- Wear app launches on emulator or watch.
- Phone connection status indicator is visible.
- Watch Only setup exposes `CLASSIC` and `RALLY`.
- Traditional is the default watch-only scoring selection.
- User can choose ME or OPP to serve first.
- Score rows fit on round display.
- Watch uses a black background without a white launch flash.
- ME and OPP score tiles are large, side by side, and remain readable with double-digit scores.
- Tapping the ME score tile records My Team won rally.
- Tapping the OPP score tile records Opponent won rally.
- The center score call has no `CALL` label and its score/server colors match the represented teams.
- The top connection pill sits compactly near the top edge without crowding the score area.
- Connected is amber; disconnected/standalone is red.
- Undo uses an icon-style control with a large enough touch target and accessible Undo label.
- Undo and the score tiles do not overlap on a round watch display.
- Score tiles remain fully inside the round safe area without clipping.
- End remains available through the secondary watch action path without crowding the main row.
- Watch gives haptic feedback after rally input.
- Rally input uses a single short vibration; Undo uses a double vibration.
- Undo works.
- Undo and Reset buttons are easy to tap.
- Watch gives haptic feedback after undo and reset.
- Reset returns to serving-team selection.
- Watch screen stays awake while RallyScore is open.
- Watch screen stays awake through at least 60+ seconds of no input while a match is active (not
  just the first few seconds).
- If the display dims for power saving, it shows a low-power ambient score readout instead of
  going fully black (device/OEM-dependent; some Wear skins hand ambient rendering to their own
  system UI instead of the app — note whichever behavior is observed).
- Score call updates after rally input.
- TTS announces the initial score after choosing first server.
- TTS announces score changes after rally input.
- TTS announces the restored score after undo.
- Watch Only mode works without a connected phone.

## Watch-Phone Sync Manual Test

Initial implementation exists. Required before the connected Watch + Phone product experience is considered complete.

- Watch can discover or pair with the phone scoring hub.
- Watch shows connected phone-idle choices when the phone is connected but has not started a match.
- From that connected idle state, the watch can still start a standalone local
  match with `WE SERVE FIRST` or `OPP SERVE FIRST`.
- In connected idle state, `WATCH MODE` exposes `CLASSIC` and `RALLY` for
  standalone watch-owned matches only.
- Phone owns authoritative match state.
- Watch rally input updates the phone/shared display.
- Watch undo restores the phone/shared display.
- Watch Undo is disabled when phone undo history is empty.
- Connected-mode rally and undo buttons are disabled while waiting for phone confirmation.
- Connected-mode watch gives confirmation haptics after the phone-owned score update arrives.
- Connected-mode watch gives problem haptics if phone confirmation is not received.
- Phone and watch refresh connection state while open after reconnecting.
- Watch does not perform score calculations, server transitions, or side-out logic locally.
- Watch receives updated score state from the phone.
- Watch displays the phone-owned score in connected mode.
- Watch never announces a predicted score.
- In Watch then Phone mode, watch announces the confirmed phone score immediately after receiving state.
- In Watch then Phone mode, phone announces the same confirmed score approximately two seconds later.
- In Phone only mode, phone announces immediately.
- In Watch only mode, only the watch announces after confirmed phone state is received.
- In Tablet only mode on a tablet-owned match, tablet announces immediately after confirmed local tablet state changes.
- In Watch then Tablet mode, watch announces the confirmed phone score immediately and tablet announces the same score approximately two seconds later.
- In Phone then Tablet mode, phone announces the confirmed phone score immediately and tablet announces the same score approximately two seconds later.
- In Watch then Phone then Tablet mode (manual selection, all three devices connected), watch announces immediately, phone announces the same score approximately two seconds later, and tablet announces approximately four seconds later — check the tablet's repeat lands after the phone's rather than overlapping it.
- In Off mode, no device announces.
- Watch reconnects and resyncs after temporary disconnect.
- Phone-only scoring remains available when no watch is connected.
- External display shows team scores, serving team, and server number with no live-match controls.
- Debug logs show command send/receive and score-state publish/receive.

### Connected Watch + Phone Regression Checks

These originally gated the Phase 2 → Phase 3 transition; Phase 3 (Tablet Only) is now complete
too (see `docs/Roadmap.md`), but these remain the core regression checklist for connected
Watch + Phone behavior — confirm all of the following still pass on real hardware after any sync
or wear-module change:

- Connected watch remains on the phone-owned scoreboard during an idle active match and does not flicker back to start.
- From connected idle state, selecting `PHONE MODE`, choosing a serving side,
  and tapping `START` on the watch moves the watch to the connected scoreboard
  without bouncing back to start.
- From connected scoreboard state, watch rally input updates the phone and remains on the connected scoreboard.
- From connected scoreboard state, watch `UNDO` updates the phone and remains on the connected scoreboard.
- From connected scoreboard state, watch `END` with confirmation ends the phone-owned match and returns the watch to the idle/start screen.
- Short connection hiccups do not force the watch out of the connected scoreboard while the phone-owned match is still active.
- Watch Only standalone start, scoring, undo, and end still work after connected-mode hardening.

## Shared Display Manual Test

- Phone display mirroring is readable in landscape.
- Shared display shows court-ordered team names (swapped on odd scores).
- Shared display shows serving team and server number.
- Shared display shows serving player name underlined.
- Mirrored normal score screen shows Team A score, Team B score, serving team, server number, player names, CALL, Undo, End, and "Setup" button.
- Mirrored normal score screen does not show large Team A Won or Team B Won buttons.
- Tablet or portable monitor does not own independent scoring logic.

## Tablet Only Manual Test

- On a tablet-sized Android device or emulator, app launches in landscape.
- Tablet supports My Team and Opponent Team setup.
- Tablet player fields start blank and show Player 1 / Player 2 hints.
- Tablet allows first-server selection.
- Tablet standalone mode shows an `EDIT` action.
- Tablet `EDIT` opens a score-adjustment dialog for My Team and Opponent Team.
- Tablet score correction updates the visible score and remains undoable.
- Tablet can start a match at `0 - 0 - 2`.
- Tablet setup exposes Traditional and Rally scoring options with Traditional as the default.
- Tablet score screen shows player names in court-ordered format.
- Tablet score screen shows serving player name underlined.
- Tablet score screen shows very large Team A and Team B scores.
- Tablet score screen shows serving side and server number with horizontal dots.
- Tablet score screen shows CALL score with serving player below (e.g., "P1 SERVES").
- Tablet scoring controls are large, readable, and easy to tap.
- Tapping team score panel records the rally winner through the shared scoring engine.
- Undo restores the previous rally.
- Before any rally is scored (`UNDO` disabled), the UNDO button is legible as dimmed text, not invisible/blank, against the dark call-bar background.
- SETUP/EDIT/UNDO/END buttons appear grouped inside a subtle panel on the call bar, not floating loosely on the black background.
- With both scores in double digits (e.g. 99 - 99 - 2), the call bar switches to a smaller "wide"
  font size and the full three-part call remains fully visible - it must not clip/lose the
  server-number segment the way a single fixed large font size would at that width.
- Tablet call-bar hyphens are legibly sized relative to the (very large) digits, with visible
  spacing on both sides, not crowded flush against the numbers.
- Correction mode is available if already implemented for the shared phone/tablet UI.
- In tablet standalone mode, `EDIT` can adjust Team A score, Team B score, serving side, and server number.
- After a correction change, `UNDO` restores the full prior state, including serving side and server number.
- Tablet voice announcement setting is visible and usable.
- Tablet announces the confirmed score when Tablet only mode is enabled in standalone tablet mode.
- Connected tablet announces only after receiving confirmed phone-owned state.
- Tablet screen stays awake during use.
- Tablet Only mode works without a phone or watch.
- Existing phone-sized score screen remains unchanged on phones.
- Tablet UI must not duplicate scoring rules outside the shared engine.

## Tablet Display Client / Phone-Tablet Sync Manual Test

- Display-client mode remains passive if explicitly used.
- Before receiving an active phone match, tablet shows the normal setup screen.
- Disconnected tablet can set up, score, undo, and end a standalone match.
- Starting a match on the phone opens the connected tablet controller layout (tap-to-score, EDIT/UNDO/END) after sync connects, not a read-only display.
- Tablet display shows player names, scores, serving side, server number, and CALL.
- When phone and tablet are on the same local network or the tablet is connected to the phone hotspot, tablet discovery/WebSocket logs show one or more phone display endpoints.
- With no external Wi-Fi available, turn on the phone hotspot, join the tablet to that hotspot, and verify the tablet finds the phone without manual IP entry.
- With Internet disabled or unavailable, verify phone-to-tablet display sync still works over the phone hotspot.
- With both devices on normal external Wi-Fi, verify the same discovery/reconnect behavior still works.
- If phone-to-tablet delivery is blocked, tablet-side same-subnet discovery attempts to find the phone WebSocket by initiating the connection from the tablet.
- On an unpaired tablet, verify the normal setup screen remains usable while nearby phones are discovered.
- Verify the phone setup screen shows a local court code badge.
- Verify the tablet setup screen lists discovered phones using court codes and readable labels.
- Verify the tablet does not auto-join a discovered phone until the user explicitly selects that court.
- After selecting a discovered phone, verify the tablet connects to that same phone and remembers the pairing across app relaunch.
- Starting a phone match publishes phone-owned tablet display snapshots over the local WebSocket.
- If the network allows local-device delivery and the phone has an active match, the tablet switches from setup to connected tablet controller display.
- If the tablet remains on setup while the phone has an active match, collect logs for blocked local WebSocket/discovery delivery and verify whether the network has client isolation or inbound-device blocking.
- Watch commands update the phone and then publish updated tablet display snapshots.
- Tapping the blue/My Team score panel on a connected tablet sends a tablet command to the phone.
- Tapping the green/Opponent score panel on a connected tablet sends a tablet command to the phone.
- The tablet does not change the connected score until the phone broadcasts confirmed state.
- Phone applies connected tablet rally commands through the shared scoring engine.
- Phone broadcasts connected tablet score changes back to the tablet and to the watch if connected.
- Connected tablet UNDO sends a command to the phone and all connected displays update from confirmed phone state.
- Connected tablet END sends a command to the phone and all connected displays leave the active match after confirmed phone state.
- Connected tablet shows an `EDIT` action.
- Connected tablet `EDIT` opens the same match-adjustment dialog.
- Score, serving side, and server-number corrections from connected tablet are sent to the phone and only appear after confirmed phone state is broadcast back.
- Tablet remembers the phone host identity after first successful connected sync.
- Tablet can intentionally forget the remembered phone host and re-enter discovery without clearing app data.
- After forgetting the phone host, verify the tablet returns to setup discovery state and requires a fresh explicit court selection.
- With two tablets on the same local network, verify the watch shows a tablet
  court selector and can switch between discovered tablet courts before
  starting a direct watch-to-tablet match.
- With only one tablet on the same local network, verify the watch hides the
  tablet court selector and still allows direct tablet start cleanly.
- Verify direct watch-to-tablet pairing remembers the last selected tablet court
  across watch app relaunch and reconnects to that same court when available.
- In single-tablet `TABLET MODE`, verify the watch start screen favors showing
  the `START` button fully without requiring manual scroll.
- Switch the watch away from `TABLET MODE` to `WATCH MODE`/`PHONE MODE` while no tablet is
  connected, then close and reopen the tablet app (or otherwise make a tablet available again).
  Verify `TABLET MODE` reappears in the mode switcher on its own within a few seconds, without
  requiring the watch app itself to be relaunched.
- When the phone starts a new phone-owned match, the connected tablet receives the new session identity before sending later commands.
- Tablet-to-phone commands include the current phone-owned match session identity.
- Phone ignores connected tablet commands whose session identity does not match the current phone-owned match.
- Tablet ignores phone-owned score snapshots from a different phone host once it has paired to one host.
- After the tablet connects, a normal single-pairing session (one phone, one tablet, scoring continuously for several minutes) is never rate-limited: scores and player-name changes keep syncing past the first ~10-15 seconds, not just briefly at connection time.
- A tablet command sent without a valid per-connection HMAC (or with a stale/mismatched session ID) is rejected by the phone and logged as ignored, not silently applied.
- While a tablet is passively displaying an active phone-hosted match, opening "Manage Players" on the tablet shows the four players from that match, without any manual entry on the tablet.
- With no match active and no tablet connected, the phone's broadcast cadence drops to roughly
  once every 5 seconds instead of once per second (check via logcat timestamps on
  `TabletDisplaySync`'s "Published tablet score snapshot" line); it returns to about once per
  second immediately once a match starts or a tablet connects.
- A tablet showing "COURT xxxx · JOINING · TAP TO RETRY" actually reconnects when tapped, reaching
  "CONNECTED" within about a second, even if the automatic reconnect loop was already mid-retry
  when tapped (not a silent no-op).
- Stopping phone broadcasts clears stale tablet display state after a short timeout.
- Tablet shows Searching for phone before discovering a phone endpoint.
- Tablet shows Connected after receiving confirmed phone-owned score snapshots.
- Tablet shows Reconnecting after heartbeat/read timeout while preserving the last displayed score.
- Tablet remembers the last phone address and retries it after relaunch.
- Phone accepts tablet reconnects without manual action.
- Reconnected tablet receives the full current score state immediately.
- Turning tablet Wi-Fi off and on reconnects automatically.
- Moving the tablet from external Wi-Fi to the phone hotspot reconnects automatically after both devices share the hotspot network.
- Closing and reopening the tablet app reconnects automatically.
- Closing and reopening the phone app restores the active phone-owned score and keeps accepting tablet reconnects.
- Rebooting the tablet reconnects automatically after RallyScore is opened.
- When a tablet is connected only via the TCP-push fallback (not the WebSocket route), the phone's
  own status pill still reaches "TABLET CONNECTED" (not stuck on "FINDING TABLET") once the tablet
  is actually receiving live snapshots.
- Force-close the phone app while a tablet is connected-controller for its match. Once the tablet's
  status pill flips to Reconnecting, verify its rally/UNDO/END taps go visibly disabled (dimmed)
  instead of staying tappable with no effect.
- With the phone still unreachable, tap the tablet's END button (now labeled "END (LOCAL)"),
  confirm the dialog, and verify the tablet forgets the pairing and returns to its own fresh setup
  screen without needing the phone.
- Confirm "SETUP" on a stuck/disconnected connected-controller tablet always works to leave the
  screen, regardless of connection state.

## Persistence Manual Test

- Start a match on the phone, change some scores, then kill the app.
- Reopen the app: the match should resume with the same scores, serving team, server number.
- Player names (teamAPlayer1, teamAPlayer2, teamBPlayer1, teamBPlayer2) should be restored.
- Court-ordered names should reflect current score parity.
- Serving player name should reflect current server state.
- Undo history is NOT persisted (only current match state restores).

## Phone + Tablet Peer Conflict Manual Test

Synced tablet controller mode (command/state sync) is implemented — see "Tablet Display Client /
Phone-Tablet Sync Manual Test" above for its regression checklist. The one item below remains
genuinely future work, not yet implemented or tested:

- Send phone and tablet (or watch and tablet) scoring input at nearly the same instant and confirm
  the outcome is a deliberately-designed, deterministic resolution — not just "whichever command
  the phone's main thread happened to process first," which is today's actual (race-safe but
  undesigned) behavior.

## Crash Checks

After reproducing or fixing a crash on a device:

```bash
adb logcat -c
# reproduce
adb logcat -b crash -d
```

Confirm no new `com.courtside.pickleball` fatal exception appears.
