# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Prime Directive

RallyScore is a flexible Android/Wear OS pickleball scoreboard ecosystem. It must work correctly across every supported device combination — Watch Only, Phone Only, Tablet Only, Watch + Phone, Phone + Tablet synced, and Watch + Phone + Tablet synced. Changes must prioritize scoring correctness, low cognitive load, glanceability, tap reliability during play, outdoor readability, and reliable synchronized state whenever multiple devices are present.

**Read `AGENTS.md` before making product or architecture changes** — it is the working contract for agents in this repo (device-mode rules, source-of-truth doc list, verification expectations, git/workspace safety). This file summarizes the parts most relevant to day-to-day coding; `AGENTS.md` and `docs/Architecture.md` are the canonical references and should be consulted directly for anything not covered here. If implementation and docs disagree, inspect the current code, fix the docs or code intentionally, and call out the mismatch.

Source-of-truth docs (check before product/architecture changes): `docs/PRD.md`, `docs/TDD.md`, `docs/ScoringRules.md`, `docs/Wireframes.md`, `docs/Roadmap.md`, `docs/UserStories.md`, `docs/Architecture.md`, `docs/TestingChecklist.md`, `docs/ReleaseNotes.md`.

## Build, Test, Run

```bash
./gradlew :shared:test :app:test          # scoring engine + phone ViewModel unit tests — run for any scoring change
./gradlew :app:assembleDebug               # phone UI changes
./gradlew :wear:assembleDebug               # Wear UI changes
./gradlew :app:installDebug                # install phone app only on a connected device
```

Avoid running root `./gradlew installDebug` against a phone — it installs every installable module, including the standalone Wear prototype, creating an extra launcher icon.

On this Mac, use Android Studio's bundled JBR if `./gradlew` can't find a JDK:

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
ANDROID_HOME=/Users/dericteong/Library/Android/sdk \
./gradlew :shared:test :app:test :app:assembleDebug
```

Run a single test class/method (standard Gradle JUnit filter):

```bash
./gradlew :shared:test --tests "com.courtside.pickleball.domain.PickleballScoringEngineTest"
./gradlew :app:test --tests "com.courtside.pickleball.sync.ScoreboardStoreTest.someTestMethod"
```

When a real device is connected, install and test the affected app on-device — confirm no crash logs after risky input or focus changes. There is an `android-debug-bridge-mcp` MCP server configured (`.mcp.json`) for driving a connected device (tap, screenshot, UI dump, launch apps).

## Module Architecture

Three Gradle modules with a strict one-way dependency:

```
app  -> shared
wear -> shared
shared -> no Android dependency
```

Never introduce a dependency from `shared` back into `app` or `wear`.

- **`shared/`** — pure Kotlin, no Android dependency (`shared/src/main/kotlin/com/courtside/pickleball/domain/`). Contains the scoring engine and is the module to extend/test first for any scoring-rule change:
  - `PickleballGame.kt` — `Team`, `ServerNumber`, `ScoringFormat`, `GameSettings`, `GameState` (immutable; `courtOrderedTeamName(team)`, `servingPlayerName()`).
  - `PickleballScoringEngine.kt` — applies rally-winner rules (side-out logic, server transitions).
  - `WearSyncContract.kt` — shared command/state contract types used by both `app` and `wear` sync layers.
  - Two scoring formats: **Traditional** (default, unchanged) and **Rally** (RallyScore's social-play variant — every rally scores, two serves stay active, first-server exception still applies, no auto-end at target score).

- **`app/`** — phone Android app, the source of truth whenever a phone is present, and the phone/tablet-as-controller hub.
  - `MainActivity.kt` — keeps screen on, edge-to-edge, wires `ScoreboardStore` + `RallyScorePhoneHub` + `TabletDisplaySync` + `WatchTabletFallbackSync` + `PlayerRepository` explicitly into `ScoreboardViewModel` via a `viewModelFactory` (ViewModel takes dependencies as constructor params, not singletons).
  - `ui/ScoreboardViewModel.kt` — observes the phone `ScoreboardStore` (owns `GameState`), exposes `StateFlow<GameState>`, handles start/rally/undo/reset, shares phone-owned state with watch command handling.
  - `sync/RallyScorePhoneHub.kt` — Wear Data Layer `MessageClient`/`DataClient` glue: receives watch + tablet commands, applies them through `ScoreboardStore`, restores last active match after process restart, publishes score snapshots, exposes watch connection status.
  - `sync/TabletDisplaySync.kt` — the local WebSocket display/command channel to tablets (see Sync Protocol below); largest file in the repo, read before touching tablet sync.
  - `sync/WatchTabletFallbackSync.kt` — direct watch-to-tablet path when no phone is present.
  - `sync/MessageAuthenticator.kt` / `sync/RateLimiter.kt` — per-connection HMAC-SHA256 command signing and per-IP rate limiting for the sync channels (see "Command channel hardening" in `docs/TDD.md`).
  - `player/PlayerRepository.kt` — per-device SharedPreferences player list backing setup-screen autocomplete and Manage Players; does **not** sync across devices (a tablet only learns players via `TabletDisplaySync` debounced `markPlayersPlayed(...)` calls when it passively displays an active match).
  - `ui/` is split per-screen, not one monolithic file: `ScoreboardApp.kt` (root routing + TTS setup only), `GameStateExtensions.kt`, `theme/`, `status/`, `setup/` (`MatchSetupScreen`, `PlayerManagementScreen`), `tablet/` (`TabletDisplayScreen`), `scoreboard/` (`ScoreboardScreen`, `MatchCorrectionDialog`).

- **`wear/`** — separate Android app (`com.courtside.pickleball.wear` package, but same `applicationId` as phone — see `wear/build.gradle.kts`). Standalone Watch Only mode keeps local Compose state and may itself be the source of truth; connected mode is command-only (sends rally-winner/undo to phone, never runs scoring rules, never announces a predicted score). Must not share ViewModels with `app`.

## Sync Protocol (phone ⇄ tablet ⇄ watch)

- Exactly one active source of truth per match, always. Local match state on tablet always takes priority over remote state in tablet screen routing.
- Two identities travel with every sync message: `hostId` (stable per phone install, used for tablet pairing) and `sessionId` (rotates per new phone-owned match, required on tablet→phone commands). The phone ignores tablet commands with a stale/mismatched `sessionId`; the tablet ignores snapshots from non-paired `hostId`s.
- Tablet commands are intent-only strings (`TABLET_ME_WON_RALLY`, `TABLET_OPP_WON_RALLY`, `TABLET_UNDO`, `TABLET_END_MATCH`, `TABLET_ADJUST_TEAM_{A,B}_SCORE_{UP,DOWN}`, `TABLET_SET_SERVING_TEAM_{A,B}`, `TABLET_SET_SERVER_{ONE,TWO}`) — the phone applies them through `ScoreboardStore`/the shared engine and broadcasts confirmed state back. Never duplicate scoring logic on the tablet/watch side of a connected match.
- The WebSocket channel is plaintext on local Wi-Fi, so command messages require the per-connection HMAC signature from `MessageAuthenticator`; both the WebSocket and TCP accept loops rate-limit per IP via `RateLimiter`. Tablet discovery is explicit (court code derived from `hostId`) — no silent auto-attach to the first phone found, to avoid cross-court confusion on shared networks.
- Voice announcement ordering matters and is mode-dependent (e.g. Watch+Phone defaults to "Watch then Phone" with phone repeating ~2s later using the *same* confirmed score string). Don't reorder without checking `docs/Architecture.md` "Voice Announcements".

## Non-negotiable product rules (from `AGENTS.md`)

- Do not modify source code when the user asks for documentation-only changes.
- Keep scoring logic in `shared/`; keep Android UI logic out of the pure Kotlin scoring engine.
- Prefer explicit rally-winner input over manual score/server controls.
- Treat Phone Only and Tablet Only as first-class experiences, not degraded fallbacks.
- Phone is the primary hub whenever a watch is used; watch in-match controls stay minimal (Team A won, Team B won, Undo) with no scoring rules, server transitions, or authoritative state on the watch.
- Do not reintroduce automatic focus jumps between setup text fields — this previously caused a Compose focus crash on a real Samsung phone; test any focus-handling change on a real device.
- Team A is blue, Team B is green; table dividers stay neutral (not team-colored). Keep score text and tap targets large; preserve landscape readability on phone.
- Do not implement ad hoc watch-phone/tablet sync — extend the existing pairing/HMAC/rate-limit design instead.
