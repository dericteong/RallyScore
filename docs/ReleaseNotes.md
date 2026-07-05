# Release Notes

## 0.1.0 - MVP Development Build

Status: In development.

### Added

- Phone Android app.
- Shared Kotlin scoring engine.
- Standard doubles side-out scoring.
- Setup-time scoring format selector with Traditional default and Rally option.
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
- Wear scoring screens use a black high-contrast theme, side-by-side score panels, a larger color-coded CALL, and round-screen-optimized controls.
- Wear direct tablet-fallback discovery now exposes tablet court codes and
  remembers the last selected tablet court on the watch.
- Initial Wear OS Data Layer sync using watch commands and phone-owned score snapshots.
- Phone-owned score snapshots include active-match and undo-availability state for connected Wear control.
- Phone and Wear connection status indicators.
- Manual `DONE` keyboard-dismiss control on the compact setup screen.
- MVP voice announcement modes: Off, Phone only, Watch only, Tablet only, Watch then Phone, Watch then Tablet, and Phone then Tablet.
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
- Rally mode now supports RallyScore's custom social-play format: every rally
  awards a point while the existing two-server, side-out, and court-position
  model stays intact.
- Watch Only mode now exposes its own local Traditional / Rally selector for
  standalone watch-owned matches.
- Manual score/server controls were replaced by rally-winner input.
- Phone scoreboard contrast, score text, serving dots, and phone controls were enlarged for outdoor readability and older players.
- Spoken score-call formatting moved into the shared domain module so UI code does not duplicate score-call logic.
- Phone ViewModel now delegates to a phone-owned score store that can also receive watch commands.
- Setup field Enter/Done no longer auto-focuses the next field because that caused Compose focus crashes on Samsung devices.
- Connected-mode score announcements use confirmed phone-owned state. Watch then Phone mode announces from the watch first, then repeats from the phone approximately two seconds later.
- Tablet voice announcements are supported in Tablet only, Watch then Tablet, and Phone then Tablet modes.
- Connected tablet voice announcements use confirmed phone-owned snapshots and do not announce predicted scores.
- Connected Wear now waits for an active phone match before showing remote scoring controls.
- Connected Wear now disables rally and undo controls while waiting for phone confirmation to reduce accidental double taps.
- Connected watch start flow now uses a single cycling mode button:
  `TABLET MODE`, `WATCH MODE`, `PHONE MODE`, followed by first-server
  selection and a separate `START` action.
- Direct Watch -> Tablet mode now requires explicit watch-side tablet-court
  selection when multiple tablets are discovered on the same network.
- Single-tablet Watch -> Tablet mode now hides the tablet-court selector and
  auto-favors keeping the `START` button visible.
- Phone and Wear apps refresh connection state while open to improve reconnection behavior.
- Wear app now keeps the screen awake while RallyScore is open.
- Wear app now declares the same RallyScore launcher icon as phone and tablet.
- Wear app now attaches ambient support so the watch app can remain visible more reliably while open.
- Connected watch start/end routing was hardened so `START ON PHONE` and `END` behave consistently against confirmed phone-owned match state.
- Watch score-state refresh now prefers the newest phone snapshot and ignores stale regressions.
- Phone match start/reset publish order was hardened so the watch does not receive a fresh-but-inactive match snapshot during connected start.
- Phone and Wear score announcements now use media speech audio attributes with explicit full-volume TTS parameters.
- External display support is treated as mirroring the existing phone score screen to a tablet or portable monitor.
- Tablet-sized Android screens now show a display-only live match layout with large scores, player names, serving side, server number, and CALL.
- Tablet CALL display is enlarged into a high-visibility lower band.
- Tablet-sized Android screens now fall back to normal setup when disconnected or when no active phone-owned match is available.
- Tablet display client remembers the last phone endpoint, detects heartbeat/read timeouts, keeps the last score visible while reconnecting, and receives the latest phone-owned snapshot immediately after reconnect.
- Phone app now restores the active phone-owned score state after relaunch so reconnecting tablets can receive the current score again.
- Tablet display-client discovery now uses current IPv4 network interfaces, gateway probing, remembered endpoints, UDP broadcasts, TCP fallback, and WebSocket snapshots so it can work over external Wi-Fi or the phone hotspot without Internet or manual IP entry.
- Connected tablet mode now sends rally, Undo, and End commands to the phone over the existing local WebSocket.
- Phone applies connected tablet commands through the phone-owned score store and shared scoring engine, then broadcasts confirmed state back to tablets and Wear OS watches.
- Tablet scoring controller now records rally wins by tapping score panels
  (no separate ME WON/OPP WON buttons). UNDO and END buttons appear inside
  the call bar in local controller mode.
- Tablet standalone controller now includes an `EDIT` action that opens a
  score-adjustment dialog for My Team and Opponent Team. Adjustments route
  through the shared store and remain undoable.
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
- Tablet setup header now shows its local court code beside WATCH and PHONE
  connection-state pills.
- Phone scoring screen now shows watch connection status as a full-width
  bar at the top (was a compact rail beside UNDO).
- Phone setup screen now displays watch and tablet connection status side
  by side.
- Score call spoken via TTS uses English words for numbers 21–99 (e.g.
  "twenty one") and digit-by-digit for 100+ (e.g. "1 0 3").
- Tablet remote-display mode is explicitly passive (no controls) and
  distinct from local controller mode (full tap-to-score panels and
  UNDO/END).
- Connected tablet mode is command-only: it does not predict or independently
  mutate phone-owned score state before confirmation.
- The blocking waiting-for-phone tablet screen was removed from the primary routing.
- GameSettings now stores individual player names (teamAPlayer1, teamAPlayer2, teamBPlayer1, teamBPlayer2).
- GameState supports `servingPlayerName()` using a fixed-position server model (P1/P4 always right-side Server 1, P2/P3 left-side Server 2).
- GameState supports `courtOrderedTeamName()` returning player names in court-position order (swaps on odd scores).
- Phone setup screen is now always landscape (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`) with side-by-side layout.
- Setup screen has app title "RallyScore", swap teams button (⇅), solid team-colored cards, `imePadding()`, and always scrollable form column.
- Setup screen now supports persistent local player management and searchable player selectors on phone and tablet layouts.
- Player selectors show recent players before the full saved-player list, while still accepting new typed names.
- New setup player names are saved automatically on Start/Resume with case-insensitive duplicate prevention.
- Score preview card simplified to "WE SERVE FIRST" / "OPP SERVE FIRST" / "CHOOSE SERVER" with team-colored background, tappable.
- Scoreboard shows dynamic court-ordered team names, serving player underlined, call bar shows serving player (e.g., "P1 SERVES").
- Watch score tiles now read `WE WON` and `OPP WON`.
- "Setup" button on scoreboard returns to setup screen to edit names mid-match.
- "START GAME" and "RESUME GAME" buttons use red-orange (#D84315), no confirmation dialogs.
- TabletDisplayState includes `servingPlayerName`, `teamACourtOrderedName`, and `teamBCourtOrderedName`; wire protocol bumped to 16 fields.
- Player names and server indices persisted in SharedPreferences for match restore.
- Phone call score text enlarged to 56sp.
- Player-name autocomplete dropdown on Set Up Game: recent-players section (amber dot marker) and all-players section, each row showing a color-coded initials chip; a "Use '...'" option to confirm a brand-new typed name; and a clear (`×`) button once a field has text.
- Set Up Game screen restructured into per-file Compose modules (`ui/setup`, `ui/scoreboard`, `ui/tablet`, `ui/status`, `ui/theme`) instead of one monolithic `ScoreboardApp.kt`, with no behavior change intended.
- `ScoreboardViewModel` now takes its sync/store/player-repository dependencies as explicit constructor parameters (wired once in `MainActivity`) instead of reaching for singletons internally.
- Phone-tablet WebSocket command channel is now authenticated: on connect, the host mints a random per-connection secret and hands it to the peer once directly over that connection (never broadcast); live-match commands (rally winner, undo, score/serve adjust, end match) must carry a valid HMAC-SHA256 signature using that secret.
- Per-IP rate limiting added to the phone-tablet WebSocket and TCP accept loops to slow down connection-flood/brute-force attempts against the pairing court code.
- Wire-format payload validation added: team/player name fields are length-capped, scores are clamped to a sane range, and server number is restricted to 1 or 2, so a malformed or oversized peer packet can't corrupt local state.
- Release builds now run with R8/`isMinifyEnabled = true` (previously disabled).
- A tablet passively displaying a phone-hosted match now learns the four player names from the existing state broadcast (debounced, not on every tick) and adds them to its own local player list automatically, without needing a new sync channel.
- Tablet Set Up Game screen: team cards and player-name fields are noticeably larger and the whole layout centers vertically in the available height, instead of the phone layout stretched wider with excess bottom whitespace.
- Tablet Set Up Game header: the RallyScore icon/title now gets its own full-width row at a much larger size, with the COURT / WATCH / TABLET status badges moved to their own row below it; "MANAGE PLAYERS" is a larger button on tablet.
- "Enter players by court position" helper text is now spaced between the "SET UP GAME" title and the "MANAGE PLAYERS" button instead of sitting immediately next to the button.
- "AVAILABLE PHONES" collapses to a single lightweight status badge when there is exactly one already-selected candidate, instead of always showing the full dark card.
- Fixed: the UNDO button on both phone and tablet scoreboards was invisible (not just dimmed) when disabled, because no explicit disabled content color was set against the dark call-bar background.
- Fixed: on phone, the tablet-connection status badge rendered at the top of the right-hand controls column instead of alongside the COURT/WATCH badges; all three now sit together in one row.
- Fixed: compact (phone) status badges now use shortened labels ("35DD", "WATCH", "TABLET") and `TextOverflow.Ellipsis`, since three full-length badges ("COURT 35DD", "WATCH CONNECTED", "TABLET CONNECTED") don't fit on a phone-width row even at the smallest supported font size.

### Known Gaps

- Wear sync is initial and still needs paired real-device hardening.
- Wear app still contains standalone Watch Only scoring logic when no phone state is available.
- Tablet Only controller mode supports tap-to-score, EDIT, UNDO, and END in the
  call bar. Correction mode now supports score edits plus serving-side and
  server-number fixes, and those changes remain undoable.
- Phone scoreboard now includes the same `EDIT` capability, using the shared
  match-correction dialog for score, serving-side, and server-number edits.
- Connected tablet mode now also exposes `EDIT`; correction actions are sent
  to the phone as explicit tablet commands, applied by the phone as source of
  truth, and then broadcast back to tablet and watch as confirmed state.
- Phone + Tablet synced controller mode is future work and requires conflict handling.
- Wireless tablet sync is an initial local-network prototype and still needs real-venue hardening. Some Wi-Fi paths can block local WebSocket/discovery delivery between phone and tablet.
- The periodic UDP state broadcast (scores, team/player names) is still sent in plaintext; only the WebSocket command channel is HMAC-authenticated. A passive listener on the same Wi-Fi can still read match data, just not inject commands.
- Manual player add/rename/delete via "Manage Players" on one device still does not propagate to the other device; only players seen through an active match broadcast are auto-learned.
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
