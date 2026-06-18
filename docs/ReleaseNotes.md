# Release Notes

## 0.1.0 - MVP Development Build

Status: In development.

### Added

- Phone Android app.
- Shared Kotlin scoring engine.
- Standard doubles side-out scoring.
- Rally-winner score input.
- Setup screen for Team A and Team B names.
- Separate Player 1 and Player 2 setup fields for each team.
- Starting serving team selection.
- Start at `0 - 0 - 2`.
- Unlimited in-memory undo.
- End game confirmation.
- Table-style phone scoreboard.
- Team colors on names, scores, and call bar numbers.
- Ampersand-separated player names on scoreboard team rows.
- Serving dots on current serving team.
- Android Text-to-Speech score call.
- Keep-screen-awake behavior.
- App launcher icon.
- Wear OS standalone prototype.
- Wear serving-team selection before scoring.
- Wear Text-to-Speech score calls.
- Initial Wear OS Data Layer sync using watch commands and phone-owned score snapshots.
- Phone-owned score snapshots include active-match and undo-availability state for connected Wear control.
- Phone and Wear connection status indicators.
- Manual `DONE` keyboard-dismiss control on the compact setup screen.
- MVP voice announcement modes: Off, Phone only, Watch only, and Watch then Phone.
- Short setup player-name defaults for faster development and social-play testing.
- Player-perspective setup and watch labels: My Team, Opponent Team, ME WON, OPP WON, ME SERVES, and OPP SERVES.
- Watch haptic feedback for rally input, undo, confirmed connected-mode score updates, and phone-confirmation problems.
- Passive tablet-sized scoreboard layout for Android tablets.
- Initial local-network phone-to-tablet display snapshot sync using UDP discovery/snapshots plus TCP endpoint fallback.
- Initial passive tablet display client over a phone-hosted local WebSocket.
- Tablet display-client reconnect states: Searching for phone, Reconnecting, and Connected.

### Changed

- Product direction changed to a flexible ecosystem: Watch Only, Phone Only, Tablet Only, Watch + Phone, and future synced Phone + Tablet modes.
- Phone Only and Tablet Only are first-class standalone controller experiences.
- Phone remains the primary hub when a watch is used; future Phone + Tablet sync must preserve one canonical match state.
- Timed play is supported by allowing scores to continue beyond 11.
- Manual score/server controls were replaced by rally-winner input.
- Phone scoreboard contrast, score text, serving dots, and phone controls were enlarged for outdoor readability and older players.
- Spoken score-call formatting moved into the shared domain module so UI code does not duplicate score-call logic.
- Phone ViewModel now delegates to a phone-owned score store that can also receive watch commands.
- Setup field Enter/Done no longer auto-focuses the next field because that caused Compose focus crashes on Samsung devices.
- Connected-mode score announcements use confirmed phone-owned state. Watch then Phone mode announces from the watch first, then repeats from the phone approximately two seconds later.
- Connected Wear now waits for an active phone match before showing remote scoring controls.
- Connected Wear now disables rally and undo controls while waiting for phone confirmation to reduce accidental double taps.
- Phone and Wear apps refresh connection state while open to improve reconnection behavior.
- Wear app now keeps the screen awake while RallyScore is open.
- Phone and Wear score announcements now use media speech audio attributes with explicit full-volume TTS parameters.
- External display support is treated as mirroring the existing phone score screen to a tablet or portable monitor.
- Tablet-sized Android screens now show a display-only live match layout with large scores, player names, serving side, server number, and CALL.
- Tablet CALL display is enlarged into a high-visibility lower band.
- Tablet-sized Android screens now show a passive waiting-for-phone screen instead of setup controls when acting as the display client.
- Tablet display client remembers the last phone endpoint, detects heartbeat/read timeouts, keeps the last score visible while reconnecting, and receives the latest phone-owned snapshot immediately after reconnect.
- Phone app now restores the active phone-owned score state after relaunch so reconnecting tablets can receive the current score again.
- Tablet display-client discovery now uses current IPv4 network interfaces, gateway probing, remembered endpoints, UDP broadcasts, TCP fallback, and WebSocket snapshots so it can work over external Wi-Fi or the phone hotspot without Internet or manual IP entry.
- Tablet scoring controller now records rally wins by tapping score panels
  (no separate ME WON/OPP WON buttons). UNDO and END buttons appear inside
  the call bar in local controller mode.
- Tablet screen routing checks local match state first; a local match
  always takes priority over remote display state.
- Phone score rows now use team-colored backgrounds: blue for Team A,
  green for Team B, with white text and serving dots, matching tablet visual
  language.
- CALL bar background changed to dark navy (#111827, was pure black) on
  both tablet and phone.
- CALL label changed to white (was amber), sized separately from the score
  call numbers (38sp tablet, 28sp phone).
- Call score enlarged: 180sp on tablet, 52sp on phone.
- Tablet server dots are now horizontal (..) white circles left of the
  score, with the score centered via a counterbalance spacer.
- Tablet connection status bar shows "PHONE CONNECTED", "PHONE
  RECONNECTING", or "SEARCHING FOR PHONE".
- Phone scoring screen now shows watch connection status as a full-width
  bar at the top (was a compact rail beside UNDO).
- Phone setup screen now displays watch and tablet connection status side
  by side.
- Score call spoken via TTS uses English words for numbers 21–99 (e.g.
  "twenty one") and digit-by-digit for 100+ (e.g. "1 0 3").
- Tablet remote-display mode is explicitly passive (no controls) and
  distinct from local controller mode (full tap-to-score panels and
  UNDO/END).
- TabletWaitingForPhoneScreen is preserved in the routing.

### Known Gaps

- Wear sync is initial and still needs paired real-device hardening.
- Wear app still contains standalone Watch Only scoring logic when no phone state is available.
- Tablet Only controller mode supports tap-to-score, UNDO, and END in the
  call bar. Correction mode is not yet implemented.
- Phone + Tablet synced controller mode is future work and requires conflict handling.
- Wireless tablet sync is an initial local-network prototype and still needs real-venue hardening. Some Wi-Fi paths can block local WebSocket/discovery delivery between phone and tablet.
- Undo history is not persisted after app restart.
- No in-app TTS voice selector.
- No dedicated Bluetooth speaker mode yet; Android audio routing may handle connected speakers.
- No formal game-complete mode for target-score games yet.

## Release Process Notes

Before a release candidate:

- Run all automated tests.
- Test setup input on at least one Samsung phone.
- Test scoring flow on a real Android phone.
- Test Wear build on emulator.
- Review `docs/TestingChecklist.md`.
