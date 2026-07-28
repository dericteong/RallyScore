# Release Notes

## 1.1.0

Build: `versionName` 1.1.0, phone `versionCode` 4, Wear `versionCode` 5. Version codes
are pooled across the shared `applicationId`, so the two modules bump as a pair to keep
Wear above the phone (see `docs/RELEASE_CHECKLIST.md` "Versioning").

### Added

- First-run scoring tutorial on phone and tablet. The first time a live scoreboard appears,
  a one-time dialog explains that a rally is recorded by tapping the winning team's score,
  with miniature blue/green rows highlighting the number itself. Only the score is a tap
  target, which isn't obvious from the scoreboard, so the coach mark points at the number
  rather than the row. Dismissing it records a flag in its own `rallyscore_onboarding`
  preferences file, so it never reappears - and clearing a match or deleting all players
  does not re-trigger it. It is shown on the first *scoreboard*, not at app launch, since
  there is nothing to try the gesture on until a match is running.

### Changed

- Both the phone/tablet and Wear modules now target Android 16 (API level 36), with
  `compileSdk` 36 to match. This keeps RallyScore within Google Play's requirement that the
  target API level stay within one year of the latest Android release — API 35 became
  non-compliant for app updates from 2026-08-31. No dependency upgrades were required (AGP
  9.2.1 / Gradle 9.4.1 already support 36); the app was already edge-to-edge, which API 36
  now enforces.
- The phone/tablet install identity (`hostId`, and the derived court code) is now persisted
  synchronously with `commit()` when first generated, instead of async `apply()`. This closes a
  narrow window where a hard process kill (force-stop or an OS SIGKILL) between generation and the
  flush could mint a new id on the next launch, changing the court code and dropping an existing
  tablet pairing. Only the one-time first-launch write is synchronous; later launches read the
  stored value and never write here.
- Player names on the phone/tablet Set Up Game screen are now optional. Start is enabled
  as soon as a starting server is chosen, and any blank field falls back to its
  court-position placeholder when the match begins: `P1`/`P2` for My Team and `P3`/`P4`
  for Opponent (the same defaults already declared on `GameSettings`). This makes it
  possible to start a casual game without typing anyone in.
- Placeholders are applied before the tablet's `StartMatch` payload is built, so a
  tablet-started match reaches the phone hub with the same labels as a locally started
  one. Only names the user actually typed are saved to the local player list —
  placeholders are never persisted.
- Duplicate-name protection is unchanged: Start still stays disabled while two *entered*
  names match. Blank fields never count as duplicates.

## 1.0.0 - Initial Play Release

Status: Released to Google Play internal testing on 2026-07-22.

Build: `versionName` 1.0.0, phone `versionCode` 1, Wear `versionCode` 3 (the Wear
module shares the phone `applicationId`, so its `versionCode` stays above the
phone's on every bump). Both bundles are signed with the same upload key.

Wear `versionCode` 2 was consumed by a discarded first upload and is permanently
unusable: Play retires a `versionCode` the moment a bundle carrying it is
uploaded, regardless of whether that release is ever rolled out. Gaps in the
sequence are expected; only the increase matters.

Since August 2023 Play requires the Wear OS bundle to ship in its own **dedicated
Wear OS track** (Release → Setup → Advanced settings → Form factors → Wear OS),
not alongside the handheld bundle in one release. The phone/tablet bundle and the
Wear bundle are therefore uploaded as two separate releases with separate tester
lists, review passes, and store-listing assets.

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
- MVP voice announcement modes: Off, Phone only, Watch only, Tablet only, Watch then Phone, Watch then Tablet, Phone then Tablet, and Watch then Phone then Tablet.
- Short setup player-name defaults for faster development and social-play testing.
- Player-perspective setup and watch labels: My Team, Opponent Team, ME WON, OPP WON, ME SERVES, and OPP SERVES.
- Watch haptic feedback for rally input, undo, confirmed connected-mode score updates, and phone-confirmation problems.
- Passive tablet-sized scoreboard layout for Android tablets.
- Initial local-network phone-to-tablet display snapshot sync using UDP discovery/snapshots plus TCP endpoint fallback.
- Initial passive tablet display client over a phone-hosted local WebSocket.
- Tablet display-client reconnect states: Searching for phone, Reconnecting, and Connected.
- Manage Players "Import From Screenshot" button: picks one or more attendee-list screenshots
  (e.g. exported from the OpenSports app) via the system photo picker, runs on-device ML Kit text
  recognition, and shows a checkable review dialog of newly recognized names before adding them.
  Names already in the player list are skipped automatically.
- Manage Players "Delete All Players" button, gated behind a confirmation dialog that states how
  many players will be removed.
- Set Up Game duplicate-name protection: a player already selected in one of the four fields no
  longer appears in the other three fields' dropdowns, and Start stays disabled (with the
  offending field outlined in red) if two fields ever end up with the same name.
- Scoreboard and tablet "AVAILABLE PHONES" court-candidate label both abbreviate player names to
  "First L." (e.g. "Deric T.") instead of showing the full name.
- Voice announcement mode "Watch then Phone then Tablet", chaining all three devices: watch
  announces immediately, phone repeats after the usual ~2s delay, and tablet repeats after ~4s so
  its repeat doesn't overlap the phone's.
- Small "© 2026 Deric Teong" copyright line pinned to the bottom-right corner of the Set Up Game
  screen on both phone and tablet.

### Changed

- Set Up Game player fields are now select-only: tapping a field opens a dropdown of saved
  players directly, with no free-text entry and no on-screen keyboard on this screen. New player
  names are added via Manage Players instead of inline during setup.
- Selecting a player on Set Up Game preserves that player's original stored name casing instead of
  forcing it to uppercase.
- Manage Players "Import From Screenshot" and "Delete All Players" buttons are equal width
  (previously Import stretched to dominate the row).

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
- Tablet voice announcements are supported in Tablet only, Watch then Tablet, Phone then Tablet, and Watch then Phone then Tablet modes.
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
- Team labels on Set Up Game now include color: "MY TEAM (BLUE)" / "OPPONENT (GREEN)". The serve-first button reads "WE SERVE FIRST"/"OPP SERVE FIRST" on phone (its own blue/green background conveys the color) and adds "(BLUE)"/"(GREEN)" on tablet where there's room.
- Security fix: `StartMatch`/`ResumeMatch` tablet commands now require a valid per-connection HMAC (they remain exempt from the session-ID check since a fresh tablet has no session). Previously they were exempt from both checks, so any unauthenticated device on the same Wi-Fi could reset a live match by sending a `StartMatch`. This is a lockstep protocol change — upgrade phone and tablet together.
- Concurrency fix: watch commands received over the Wear Data Layer (and the watch-tablet TCP fallback) are now marshalled onto the phone hub's main-immediate scope before touching `ScoreboardStore`, instead of mutating undo history and score state directly from a binder/network thread while phone/tablet commands ran on the main thread.
- Reliability fix: the watch-tablet and tablet-watch fallback sync paths now check `PrintWriter.checkError()` after each write. `PrintWriter` never throws on write failure — it only sets an internal flag — so dead connections were previously never pruned and a watch command over a half-dead socket vanished silently with the connection still shown as green.
- See `docs/CodeReviewFindings.md` for the full 2026-07-05 codebase review, including still-open items (always-on 1 Hz UDP broadcast, sync-singleton lifecycle teardown, wire-format versioning fragility).
- Security fix: the watch-tablet fallback channel (direct watch → tablet, no phone) now uses the same per-connection HMAC + rate-limiting pattern as the phone-tablet channel. Previously any TCP client on the same Wi-Fi could send `END_MATCH`/`START_MATCH` to a tablet-hosted match with no authentication at all. This is a lockstep protocol change for the `wear` module — upgrade the watch and tablet builds together. Verified: both modules compile, unit tests pass, both release builds succeed with R8, both APKs installed and launched cleanly on real hardware. The live handshake was not exercised end-to-end (the test watch went to sleep and didn't respond to remote wake) — please manually verify direct watch-to-tablet commands still work after upgrading.
- Concurrency fix: `PlayerRepository`'s mutating methods are now `@Synchronized`, since the tablet's player auto-learn path calls them from a network thread while Manage Players can be edited from the main thread at the same time.
- Wear release builds now also run with R8 (`isMinifyEnabled = true`), matching the phone app.
- Regression fix: after the security hardening above shipped, the tablet appeared to hang (no
  response tapping END or adjusting scores). Root cause was the phone-tablet WebSocket rate
  limiter (`MAX_CONNECTIONS_PER_MINUTE = 10`, added for the pairing-brute-force fix) being too
  strict for legitimate reconnect traffic: a disconnected tablet retries through several
  concurrent loops (hello broadcaster, subnet scanner, gateway probe, the reconnect loop's own
  retries), which can exceed 10 attempts/minute during real connection trouble and cause a
  self-inflicted lockout. Renamed to `MAX_WEBSOCKET_CONNECTIONS_PER_MINUTE` and raised to 30.
- Fix: the tablet's "AVAILABLE PHONES" pairing badge could get stuck showing "JOINING" forever
  with a "TAP TO RETRY" affordance that did nothing when tapped. Three compounding bugs, found via
  live on-device diagnosis: (1) the retry tap called into `pairToDiscoveredPhone`, which didn't
  cancel an already-active (but failing) reconnect job, so the dedup guard in
  `connectToPhoneWebSocket` silently absorbed the manual retry most of the time; (2) `SyncLog`
  debug logging was silently dead in every build variant, including debug builds, because neither
  `app` nor `wear` had the `buildConfig` build feature enabled, so the reflection-based
  `BuildConfig.DEBUG` check always fell through to `false`; (3) the actual root cause -
  `MatchSetupScreen` was always fed `tabletHostConnectionState` (this device's state as a host
  being joined by other displays) instead of the tablet's own client-role connection state, so the
  UI could never show "CONNECTED" for a tablet pairing to a phone no matter how many times the
  underlying connection actually succeeded. All three fixed; verified live that a tablet now
  reaches "CONNECTED" within about a second and the state survives an app relaunch.

- Refactor: `wear/WearScoreboardApp.kt` split from a 1,653-line monolith into a slim ~900-line
  root (state hoisting + `LaunchedEffect`s) plus `theme/WearTheme.kt`, `setup/WearSetupScreens.kt`,
  and `scoreboard/WearScoreboardScreens.kt` - the same per-screen split the phone app already got.
  Mechanical move only, no behavior change; verified via clean compiles (including R8 release) and
  a live device/emulator smoke test of the connected scoreboard screen.
- Fix: `MatchCorrectionDialog.kt`'s team labels now include the color suffix used everywhere
  else ("My Team (Blue)"/"Opponent (Green)"), matching the setup screen's convention.
- Fix: `RateLimiter`'s per-IP tracking map now self-evicts stale entries instead of growing
  forever as new source IPs are seen.
- Fix: the phone's UDP score/discovery broadcast no longer runs at 1 Hz forever regardless of
  whether anyone's around to see it. It now drops to a 5s idle cadence whenever there's no active
  match and no tablet connected or recently seen, and returns to 1 Hz the moment a match starts or
  a tablet shows up. Also fixed a regression this surfaced: the tablet's WebSocket read timeout
  (4s) was tuned for the old 1 Hz cadence, so a healthy idle connection started looking dead and
  reconnecting every cycle once broadcasts could be 5s apart - raised to 8s to fix.
- Added: the watch now implements Wear OS Ambient Mode (`AmbientModeSupport`) instead of just
  going black once the system decides to dim/sleep the display despite `FLAG_KEEP_SCREEN_ON`.
  While ambient, it shows a low-power, burn-in-safe readout (dim gray text on black, no filled
  color blocks) of whichever score is currently active - standalone Watch Only, or the
  phone/tablet-connected match - instead of the normal interactive buttons. No new permissions
  needed; `androidx.wear:wear:1.3.0` (already a dependency) provides the API.
- Fix: the watch screen could still fall asleep mid-use despite `FLAG_KEEP_SCREEN_ON` already
  being requested in `onResume`. `MainActivity` (wear) now also re-asserts it in
  `onWindowFocusChanged` whenever the window regains focus, closing a gap where transient
  system-level focus loss (without a full onPause/onResume cycle) could let the display's own
  inactivity timeout sneak in. Verified live on real watch hardware: the screen stayed on and
  `dumpsys power` reported `mWakefulness=Awake` after 25+ seconds of no input on the setup screen
  (previously the system default ~15s timeout would have put it to sleep).
- Fix: the watch's "TABLET MODE" option could disappear from the mode switcher permanently (only
  recoverable by relaunching the watch app) after switching away from it while no tablet was
  connected. Root cause: the watch's direct tablet-discovery UDP listener only ran while Tablet
  mode was already selected, but Tablet mode could only be selected once a tablet was already
  discovered - a deadlock. `shouldRunTabletFallbackDiscovery` in `WearScoreboardApp.kt` no longer
  depends on the current mode; it now only checks that no match is active, since the discovery
  socket is passive listen-only and cheap to leave running. Verified live on real watch hardware.

- Fix: a connected-controller tablet's rally, undo, and end-match taps looked interactive even
  after the phone became unreachable (e.g. its app was closed) - they just silently did nothing,
  since the taps send commands to the phone rather than scoring locally. Root cause was an
  inverted enable check for the score taps plus no connectivity gating at all on the undo/end
  buttons. All three now visibly disable (dimmed) once the connection drops. Since sending an
  "end match" command to an unreachable phone is itself a no-op, the END button falls back to a
  local-only "END (LOCAL)" action (behind a confirmation dialog) that forgets the paired phone and
  its stale match snapshot on this tablet only, so the tablet is never stuck with no way out.
- Fix: the phone's own "tablet connected" status pill could stay stuck on "Searching" /
  "FINDING TABLET" even while a tablet was actively receiving live score updates over the TCP-push
  fallback transport, because only the WebSocket accept path updated the phone's
  `hostConnectionState`; the tablet's own status (any fresh snapshot, any transport) didn't have
  this asymmetry. `publishToTabletTcpEndpoints` now also marks the phone's status Connected on a
  successful push, and falls back to Reconnecting if delivery stops working with no WebSocket
  client either.
- Added: a hard cap of 99 on either team's score (`MAX_MATCH_SCORE` in the shared domain module),
  applied everywhere a score can change - rally-winner scoring, and both phone-side and
  tablet-remote manual score corrections. Tapping a rally winner for a team already at 99 shows an
  on-screen message explaining the cap and prompting a new game, instead of silently no-opping.
- Added: player names on the scoreboard (phone and tablet) that don't fit their column even after
  the "First L." abbreviation now shrink in small steps until they fit on one line, instead of
  truncating with "…".
- Added: a tablet-specific "wide" call-bar font size that kicks in once either score reaches double
  digits, matching a pattern the phone already had. Previously the tablet call bar used one fixed
  (very large) size regardless of digit count, which at double-digit-both-sides scores overflowed
  badly enough to clip the third score-call segment (server number) off-screen entirely.
- Changed: the score call bar's hyphens (phone and tablet) render smaller and vertically centered
  relative to the digits, with a bit of surrounding space, instead of a full-digit-height dash
  sitting flush against the numbers.

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
