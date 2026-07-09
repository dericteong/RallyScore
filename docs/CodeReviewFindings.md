# Code Review Findings — 2026-07-05

## Watch "TABLET MODE" disappears permanently after first found (2026-07-06) — **FIXED**

Reported as: after the watch found a tablet once (fixed by a watch app relaunch, see below),
switching away from Tablet mode to Phone/Watch mode while the tablet was later unreachable (e.g.
its app got closed) made "TABLET MODE" vanish from the mode switcher for good - only recoverable
by relaunching the watch app again, every time.

Root cause, found by connecting to the watch directly over ADB and confirming (via
`/proc/net/udp` and live logcat) that its UDP discovery socket for the direct watch-tablet
fallback channel was simply not running: `WearScoreboardApp.kt`'s `shouldRunTabletFallbackDiscovery`
required `selectedStartMode == WatchStartMode.Tablet` (or already being tablet-connected) before
it would even start listening. But `availableStartModes()` only offers Tablet as a *selectable*
mode once a tablet has already been discovered. That's a deadlock: discovery needs Tablet mode
selected to run, and Tablet mode can only be selected once discovery has already found something.
`selectedStartMode` defaults to `Tablet` on a cold app launch (explaining why a relaunch always
"fixed" it), but switching away from it while no tablet was connected killed discovery permanently
until the next relaunch.

Fix: `shouldRunTabletFallbackDiscovery` no longer depends on the currently selected/connected
mode at all - it only gates on "no active match," matching the fact that this discovery loop is a
passive listen-only UDP socket (the tablet does the broadcasting), so leaving it running in the
background whenever the watch is idle costs nothing. Verified live on real watch hardware:
discovery now runs continuously (logcat shows `Discovered tablet fallback court EC5C at
192.168.50.237:45462` on a ~1s cadence) regardless of which mode is currently displayed. Also
confirmed via `dumpsys wifi` that the watch (192.168.50.136) and tablet (192.168.50.237) were on
the same Wi-Fi network (`DMZONE_5G`) throughout, ruling out a network-separation explanation.

## "Tap to retry" stuck at JOINING forever (2026-07-06) — **FIXED**

After the version-skew/rate-limit regression below was fixed, the tablet still showed
"COURT 35DD · JOINING · TAP TO RETRY" indefinitely, and tapping the retry badge produced no
visible change. Live on-device diagnosis (ADB taps + fresh logcat + a `uiautomator` dump to
confirm the tap really landed on the clickable region) found three independent, compounding bugs:

1. **The retry tap was a silent no-op most of the time.** `TabletDisplaySync.pairToDiscoveredPhone`
   (what the "TAP TO RETRY" badge calls) forwarded straight into `connectToPhoneWebSocket`, which
   has a dedup guard that skips the connection attempt whenever a `tabletWebSocketClientJob` is
   already active — a legitimate guard against the hello broadcaster/subnet scanner/gateway probe
   racing each other, but it also silently absorbed a deliberate manual retry whenever the
   automatic reconnect loop happened to be mid-backoff (which is most of the time a stuck pairing
   is visible on screen). Fixed: `pairToDiscoveredPhone` now cancels any existing client job and
   resets connection state before reconnecting, so a manual tap always launches a genuinely fresh
   attempt instead of being swallowed.
2. **All `SyncLog`/`WearSyncLog` debug logging was silently dead in every build variant**,
   including debug builds — used to diagnose bug #1 and worth fixing on its own since it cost
   significant time this session. `isDebugBuild` reflects `BuildConfig.DEBUG`, but neither
   `app/build.gradle.kts` nor `wear/build.gradle.kts` had the `buildConfig` build feature enabled,
   so the `BuildConfig` class never existed and the reflection always fell through to `false`.
   Fixed by adding `buildConfig = true` to both modules' `buildFeatures` blocks.
3. **The real root cause: `MatchSetupScreen` was wired to the wrong connection-state flow.**
   `ScoreboardApp.kt` fed it `tabletHostConnectionState` (this device's state as a *host* being
   joined by other displays) unconditionally, even on a device running in tablet/client role. A
   tablet's "AVAILABLE PHONES" pairing UI needs its *client*-role state
   (`tabletConnectionState`/`clientConnectionState`) — the one that actually reflects whether
   pairing to a real phone succeeded. Because of this, the UI could never show "CONNECTED" for a
   tablet no matter how many times the connection actually succeeded underneath. Fixed: the call
   site now picks `tabletConnectionState` when `useTabletDisplayLayout` is true and
   `tabletHostConnectionState` otherwise, matching each device's actual role.

Verified live on real tablet + phone hardware: after all three fixes, tapping retry (or simply
relaunching) reliably reaches "COURT 35DD · CONNECTED" within about a second, confirmed via
logcat showing `Connected to phone tablet WebSocket` and matching per-second score-snapshot
exchange, and confirmed the state survives an app relaunch (persisted pairing, not a fluke).

## Regression found during on-device verification (2026-07-06) — **FIXED**

After deploying fixes #1/#2/#3/#4/#6/#8, the tablet appeared to hang: tapping "END" or trying to
increase scores did nothing, and the tablet showed "PHONE RECONNECTING" indefinitely.

Two compounding causes, found via live logcat on the real devices:

1. **Version skew.** An earlier `adb install` to the phone had silently failed
   ("device not found" — a transient Wi-Fi-adb drop) and was never retried, so the phone was
   running yesterday's build while the tablet had today's. Reinstalled the current APK on the
   phone to resync.
2. **The WebSocket rate limiter (added for #1-style hardening) was too strict for legitimate
   reconnect behavior.** `MAX_CONNECTIONS_PER_MINUTE = 10` assumed "one connection per pairing
   session," but a disconnected tablet retries through several independent, concurrent loops
   (hello broadcaster, subnet scanner, gateway probe, the reconnect loop's own internal
   retries). During real connection trouble this legitimately exceeds 10 attempts/minute, and
   phone logcat showed "Rate limited a WebSocket connection attempt" repeating every ~2 seconds
   — a self-inflicted lockout where the rate limiter prevented recovery from the very
   instability the retries were trying to fix. Renamed the constant to
   `MAX_WEBSOCKET_CONNECTIONS_PER_MINUTE` and raised it to 30 (still turns brute-forcing the
   4-char court code into an hours-long attack, just tolerates normal reconnect churn).

Verified: compiles, unit tests pass, both devices reinstalled and relaunched, fresh logcat shows
no more rate-limit spam. Not independently re-verified that scoring/END fully recovered after
the fix on the live devices (requires the user to re-pair the tablet to the phone after the
reinstalls, then retry scoring) — please confirm on your end.


Full-codebase review (app, shared, wear modules, manifests, build config).
No hardcoded API keys or secrets exist anywhere in the codebase (verified by scan).
Statuses updated as items are fixed; keep this document current when addressing an item.

## High severity

### 1. Unauthenticated StartMatch/ResumeMatch could reset a live match — **FIXED**

`TabletDisplaySync.acceptWebSocketClient` exempted `StartMatch`/`ResumeMatch` from both the
session-ID check and HMAC verification, and `RallyScorePhoneHub.handleTabletCommand` routes
`StartMatch` into `startMatch()`, which zeroes the score. Anyone on the same Wi-Fi who opened a
WebSocket to the phone could wipe a match in progress without knowing any secret.

Fix: Start/Resume now require a valid per-connection HMAC (they remain exempt from the
session-ID check, since a fresh tablet has no session yet). Any legitimate tablet can sign —
it received the welcome secret on connect — while a replaying third device cannot. The pending
setup-command payload is now built (and signed) at flush time rather than enqueue time so it is
always signed with the live connection's secret.

Note: this is a lockstep protocol change — a pre-HMAC tablet build can no longer start a match
on a post-HMAC phone. Phone and tablet run the same APK from this repo, so upgrade both together.

### 2. Watch commands mutated match state from a binder thread — **FIXED**

`PhoneWearListenerService.onMessageReceived` (background binder thread) and
`WatchTabletFallbackSync`'s TCP client threads both invoked
`RallyScorePhoneHub.handleWatchCommand` directly, which mutated `ScoreboardStore`'s plain
`mutableListOf` undo history and did non-atomic StateFlow read-modify-write — concurrently with
tablet commands and phone UI taps on the main thread. A watch tap landing at the same instant as
a phone/tablet tap could corrupt undo history or drop a rally.

Fix: `handleWatchCommand` now posts its body onto the hub's main-immediate scope, matching how
`handleTabletCommand` already worked, so every command source serializes onto the main thread.

### 3. Watch↔tablet fallback TCP server has no authentication or rate limiting — **FIXED**

`WatchTabletFallbackSync.runServer` (port 45462) accepted any TCP client and executed its
commands — including `END_MATCH` and `START_MATCH` — with zero session, HMAC, or rate-limit
checks. Same class of hole as the (already fixed) phone-tablet channel, on the parallel channel
used when the tablet is the hub with a watch attached.

Fix: mirrors the phone-tablet pattern. `WatchTabletFallbackSync` (tablet side) gained a
`RateLimiter` on its TCP accept loop (30 connections/min per IP — this channel is one persistent
connection per watch, not a per-tick reconnect like the phone→tablet TCP push, so it doesn't
need the higher budget that channel required) plus a per-connection secret
(`MessageAuthenticator.newSecret()`) sent once as a `RALLYSCORE_WATCH_TABLET_WELCOME_V1` line
immediately after accept. Every watch command now carries `commandPath|timestamp|mac` and is
rejected unless the HMAC verifies. `WearTabletFallbackSync` (watch side) gained a new
`WearMessageAuthenticator` (sign-only, no `matches`/`newSecret` — the watch never verifies)
and reads the welcome line before sending signed commands, on both the persistent socket and the
one-shot command path used when no persistent connection exists yet.

**Verification status:** both modules compile, all unit tests pass, both `app` and `wear` R8
release builds succeed, and both APKs installed and launched without crashing on the real tablet
and a real Wear OS watch. I could not exercise the live handshake end-to-end — the watch went to
sleep and did not respond to remote wake attempts (power key, tap) over adb, likely needing a
physical gesture. **Please manually verify on-device**: put the tablet in Tablet Only mode with
no phone attached, get the watch into direct watch→tablet mode, and confirm rally/undo/end
commands still reach the tablet (check tablet logcat for "Ignored unauthenticated or malformed
watch-tablet command" — that would indicate a handshake bug slipped through static review).

## Medium severity

### 4. PrintWriter silently swallowed network write failures — **FIXED**

`WatchTabletFallbackSync.publishState`/`sendCommand` paths and
`WearTabletFallbackSync.sendCommand`/`sendOneShotCommand` wrapped sockets in `PrintWriter`,
whose `println` never throws — it sets an internal error flag. The surrounding catch blocks that
were supposed to prune dead clients and trigger reconnect were unreachable for write failures:
a watch tap over a half-dead connection vanished silently with `watchConnected` still green.

Fix: every such write now calls `PrintWriter.checkError()` (which flushes) and treats `true` as
the failure the catch block was waiting for — pruning the client socket on the tablet side and
disconnecting/reporting failure on the watch side.

### 5. HMAC secret is delivered in plaintext over the unencrypted WebSocket — **OPEN (accepted risk)**

The welcome frame carries the per-connection secret in clear text. This blocks forging commands
from the sniffed UDP broadcast alone, but an attacker capturing the TCP stream at connection
time gets the secret. Full mitigation needs a real key exchange; judged disproportionate for a
casual courtside app. Revisit if used on adversarial networks.

### 6. PlayerRepository is not thread-safe — **FIXED**

`PlayerRepository.replacePlayer` did unsynchronized read-modify-write on `_players.value`.
The tablet auto-learn path (`TabletDisplaySync.learnPlayerNamesIfChanged`) calls it from
`Dispatchers.IO` network threads while the user may simultaneously edit players in Manage
Players on the main thread. Concurrent upserts could drop a player.

Fix: `@Synchronized` added to `upsertPlayer`, `markPlayersPlayed`, `renamePlayer`,
`deletePlayer`, and `replacePlayer`. Kotlin monitors are reentrant so the
`markPlayersPlayed` → `upsertPlayer` → `replacePlayer` call chain is safe.

### 7. Phone broadcasts UDP at 1 Hz forever from app launch — **FIXED**

`TabletDisplaySync.runBroadcaster` started in `RallyScorePhoneHub.initialize()` and never idled,
match or no match — and `PhoneWearListenerService.onCreate` also initializes the hub, so a
single watch message spins up the full network stack without the app ever being opened.

Fix: `runBroadcaster`'s loop now computes an `idle` flag each iteration -
`!matchActive && webSocketClients.isEmpty() && recentTabletEndpoints.isEmpty() &&
currentTabletTcpEndpoints().isEmpty()` - and delays `IDLE_BROADCAST_INTERVAL_MS` (5s) instead of
`BROADCAST_INTERVAL_MS` (1s) whenever true. A freshly opened tablet still discovers the phone
within a few seconds; a genuinely idle phone (no match, no tablet connected or recently seen)
drops to a fifth of the network chatter.

This surfaced a real regression during live testing: the tablet's WebSocket read loop had
`WEBSOCKET_READ_TIMEOUT_MS = 4_000`, tuned around the old 1 Hz cadence. Once the phone could go
5 seconds between snapshots, a perfectly healthy idle *connection* looked dead to the tablet,
which tore it down and reconnected every cycle - confirmed live via `TabletDisplaySync`'s
"Tablet host connection state: Connected -> Reconnecting -> Connected" repeating every ~4s.
Fixed by raising the read timeout to 8s, giving comfortable margin over the new idle interval.

Verified live on real phone + tablet hardware: connected cadence stayed a stable ~1.03s with no
reconnect churn, and after ending the match and closing the tablet app, cadence cleanly widened
to ~5.0s between broadcasts and returned to ~1s immediately once a tablet reconnected.

The unpaired tablet's subnet scanner (probes up to 254 hosts every 15s) is a separate, already
fairly well-behaved loop - it bails out per-host once connected and its 15s interval was left
as-is; not part of this fix.

### 8. Wear release build is not minified — **FIXED**

`wear/build.gradle.kts` had `isMinifyEnabled = false` after the app module enabled R8.
Flipped to `true`; `:wear:assembleRelease` succeeds with the existing `proguard-rules.pro`
(only pre-existing deprecation warnings, no missing-class errors).

## Architecture

### 9. Sync-layer singletons have no lifecycle teardown — **OPEN**

`RallyScorePhoneHub.initialize` launches collectors that live for the process lifetime;
`TabletDisplaySync`'s handler/provider lambdas are never nulled on shutdown; nothing resets
between matches beyond session rotation.

### 10. Wire format distinguishes versions by nine legal field counts — **OPEN**

`TabletDisplaySync.toTabletDisplayState` accepts field counts 10/11/13/14/16/18/19/20/24 with
manual offset arithmetic. Every added field compounds the fragility. Migrate to a
self-describing format (JSON or key=value) at the next protocol-breaking change.

### 11. wear/WearScoreboardApp.kt is a 1,653-line monolith — **FIXED**

Applied the same per-screen split the phone app got, mechanical move only (no logic changes):
- `theme/WearTheme.kt` — the 14 color constants.
- `setup/WearSetupScreens.kt` — `WearServeSetupScreen`, `WearConnectedStartChoiceScreen`,
  `WearScoringFormatSelector`/`WearScoringFormatButton`, `WearStartModeButton`, `Int.floorMod`.
- `scoreboard/WearScoreboardScreens.kt` — `WearConnectedScoreboardScreen`, `WearScoreboardScreen`,
  and their shared subcomponents (`ServeChoiceButton`, `PrimaryStartButton`, `ScoreSummary`,
  `ConnectedScoreSummary`, `WearConnectionLabel`, `WearScorePanels`/`WearScorePanel`,
  `WearCallScoreText`, `WearUtilityRow`, `WearServeDots`, `RallyButton`).
- `WearScoreboardApp.kt` stays as the slim root (902 lines, down from 1,728): the state hoisting
  and ~10 interdependent `LaunchedEffect`s that are the actual "disease" the finding named aren't
  something a mechanical file-move can cure - that would require deeper state-management
  restructuring (e.g. extracting a ViewModel-like holder), which is out of scope for a per-screen
  split and wasn't what this finding asked for.

Cross-file references had `private` dropped to plain top-level (matching the phone app's
convention) rather than exported as public API. Incidentally found three pieces of dead code
while moving things around - `String.watchTeamLabel()`, `ConnectedAndroidRole.startLabel()`, and
`RallyButton` are all defined but never called anywhere. Left them in place untouched (this
refactor promises zero behavior change, dead code included) rather than silently deleting them
as a bonus cleanup; worth a follow-up look.

Verified: `:wear:compileDebugKotlin`, `:wear:assembleDebug`, and `:wear:assembleRelease` (R8) all
succeed. Installed the debug build on a live Wear device/emulator and confirmed the connected
scoreboard screen (post-split composables: `WearConnectedScoreboardScreen`, `WearScorePanels`,
`WearConnectionLabel`, `WearCallScoreText`, `WearUtilityRow`, `WearServeDots`) renders pixel-correct
with a real connected match (`PHONE CONNECTED`, live score, WE WON/OPP WON panels).

## Tablet lists its own court code as a pairable phone (2026-07-06) — **OPEN**

Observed live during on-device testing: a tablet's "AVAILABLE PHONES" list sometimes shows its
own court code (e.g. "COURT EC5C") as a selectable candidate, alongside the real phone it's
actually trying to pair to. Root cause: `RallyScorePhoneHub.initialize` calls
`TabletDisplaySync.startBroadcaster(...)` unconditionally regardless of `deviceRole` — every
device, tablet or phone, broadcasts its own score-state and `PHONE_WS_PROTOCOL` availability, so
a tablet-role device's UDP hello gets picked up by other tablets (or itself) exactly like a real
phone host would. Tapping that self-entry would presumably attempt to pair a tablet to itself,
though this wasn't tested since it's an obviously-wrong entry to avoid in the UI (visually
distinguishable by its court code not matching any real phone). Not fixed — flagged during a
live debugging session and the user didn't ask for a fix at the time. Likely fix: gate
`startBroadcaster`'s phone-availability advertisement on `deviceRole == DEVICE_ROLE_PHONE`, or
filter a device's own `hostId` out of its locally-discovered-phones list.

## Tablet connected-controller taps silently no-op once the phone disconnects (2026-07-09) — **FIXED**

Reported as: "the tablet can't end game or can't increase score" after the user closed the phone
app mid-match. The tablet's connection-status pill correctly showed Reconnecting, but its rally,
UNDO, and END taps stayed fully interactive and simply did nothing when tapped — these taps only
ever send commands to the phone (`TabletDisplaySync`/`RallyScorePhoneHub`), they never score
locally, so once the phone was unreachable every tap was a silent, invisible no-op.

Root cause was two-fold in `TabletDisplayScreen.kt`:
1. The score-tap `enabled` expression was `!isConnectedController || isController`, which
   evaluates `true` almost regardless of actual connection state (inverted logic — it should have
   required *both* "this is a controller" and "actually connected").
2. The UNDO and END buttons in `TabletControlBar` had no connectivity gating at all — UNDO only
   checked local undo-availability, and END had no `enabled` parameter whatsoever.

Fix: added an explicit `isRemoteControlled` flag (true only for the phone-owned remote-controller
flow; a local standalone Tablet Only match is unaffected, since its own `tabletConnectionState`
is unrelated to whether its own local match can be scored) and a `remoteCommandsEnabled` value
derived from it, applied to all three controls. Once disabled, score text also dims (35% alpha)
so it reads as inactive rather than just silently unresponsive.

This surfaced a second gap: once END is correctly disabled while disconnected, there was *no way
at all* to end a stuck remote match — "SETUP" only navigates away locally, it doesn't clear the
match, and RESUME just goes back to the same disabled screen. Found and reused an existing but
previously unwired method, `ScoreboardViewModel.forgetPairedTabletPhone()` (wraps
`TabletDisplaySync.forgetPairedPhone()`), which clears the paired-phone identity and cached remote
snapshot locally without trying to notify the (unreachable) phone. Wired it to the END button: while
disconnected, END relabels to "END (LOCAL)" and, behind a confirmation dialog explaining the
phone is unreachable and this is local-only, forgets the pairing and returns the tablet to its own
fresh setup screen. Verified live: force-closing the paired phone app correctly dimmed/disabled the
tablet's controls within a few seconds, and "END (LOCAL)" successfully recovered the tablet to a
usable state without the phone.

## Phone's "tablet connected" status stuck on Searching despite live TCP-fallback delivery (2026-07-09) — **FIXED**

Reported as: tablet showed "PHONE CONNECTED" while the phone simultaneously showed
"FINDING TABLET" for the same live pairing. Root cause: `TabletDisplaySync` has two independent
transports for delivering phone-owned state to a tablet — a WebSocket connection, and a periodic
TCP-push fallback (`publishToTabletTcpEndpoints`, used when the tablet hasn't/can't establish the
WebSocket route). Only the WebSocket accept path (`acceptWebSocketClient`) ever updated the
phone's own `hostConnectionState`. The tablet side has no such asymmetry — `updateRemoteDisplayState`
marks the tablet Connected on receipt of *any* fresh snapshot regardless of which transport
delivered it — so a pairing that happened to work only over the TCP fallback left the phone
permanently reporting "Searching"/"FINDING TABLET" even though delivery was succeeding and the
tablet correctly showed itself Connected.

Fix: `publishToTabletTcpEndpoints` now marks the phone's `hostConnectionState` Connected on a
successful push, and falls back to Reconnecting if delivery stops working and there's no
WebSocket client either (guarded so it never downgrades an actually-healthy WebSocket connection).
Verified live: reinstalling with the fix flipped the phone's status pill from "FINDING TABLET" to
"TABLET CONNECTED" against the same live pairing, with no change on the tablet side needed.

## Tablet call bar could silently clip the score-call's third segment at high scores (2026-07-09) — **FIXED**

Found while live-testing the new 99-point score cap: at a 99-99 score, the tablet's call bar
rendered only "99 - 99" — the third segment (server number) was entirely missing, not truncated
with an ellipsis, just gone. Root cause: unlike the phone (`PhoneCallBarWideTextSize` etc.), the
tablet call bar used one single fixed font size (`TabletCallBarTextSize = 292.sp`) regardless of
digit count, and `Text`'s default overflow (`Clip`, combined with `TextAlign.Center`) silently
drops content from the wider side without any visual indicator when the full string doesn't fit at
that size.

Fix: added a `TabletDisplayState.callHasDoubleDigitScore()` check and a `TabletCallBarWideTextSize`
tier (mirroring the phone's existing pattern), applied whenever either team's score reaches double
digits. Iterated the exact size and the call bar's hyphen spacing twice more based on live
screenshots taken directly from the connected tablet (250sp text / 254sp line height / single-space
hyphen padding was the setting that both fit the worst case (99-99-2) and looked appropriately
large, per direct user feedback comparing screenshots at each step).

## Low severity

- `MatchCorrectionDialog.kt` labeled teams "My Team"/"Opponent Team" without the color suffix
  used on the setup screen — **FIXED**. All four labels (`CorrectionScoreCard`/`CorrectionScoreRow`
  for both compact-phone and regular/tablet dialog layouts, plus the matching "Serving" choice
  options) now read "My Team (Blue)"/"Opponent (Green)" or "Opponent Team (Green)", matching the
  setup screen's "MY TEAM (BLUE)"/"OPPONENT (GREEN)" convention.
- `RateLimiter` per-IP map never evicted old keys; a scanning attacker (or just ordinary Wi-Fi
  DHCP churn over time) grew it unboundedly — **FIXED**. `allow()` now sweeps the whole map at
  most once per `windowMs`, dropping any key whose timestamp list is empty after pruning, so only
  recently-active keys are retained. Added `RateLimiterTest.kt` covering the sliding window,
  per-key independence, and that sweeping many stale keys doesn't disturb an active key's own
  window.
- `gradle.properties` is a stack of AGP-9 compatibility escape hatches scheduled for removal in
  AGP 10 — **OPEN** (budget cleanup before upgrading; not touched, out of scope for this pass).
- `.mcp.json` (local adb MCP connector config, no secrets) is untracked and not gitignored —
  **RESOLVED (stale finding)**. It's already tracked in git (`git ls-files` confirms, committed
  in "Enhanced with Claude Code"); no action needed.
- `shared` module: no findings — pure Kotlin, immutable state, well-tested engine.
