# RallyScore Connectivity Research

Originally a point-in-time research snapshot; reviewed and corrected 2026-07-06 against the
current codebase. `docs/Architecture.md` and `docs/TDD.md` are the continuously-maintained source
of truth for connectivity design going forward — treat this doc as useful background/history, not
the primary reference.

## 1. Wear OS Data Layer Sync

The watch-to-phone synchronization uses the **Android Wear OS Data Layer API** (`com.google.android.gms:play-services-wearable:19.0.0`) with two distinct communication channels.

### Channel A: Watch-to-Phone Commands (MessageClient)

The watch sends lightweight, fire-and-forget messages to the phone using `MessageClient.sendMessage()`. Three command paths are defined in the shared contract (`WearSyncContract`):

- `/rallyscore/command/a_won_rally`
- `/rallyscore/command/b_won_rally`
- `/rallyscore/command/undo`

These represent user intent only — no scoring logic lives on the watch in connected mode. The phone's `PhoneWearListenerService` (a `WearableListenerService`) receives messages in `onMessageReceived()` and delegates to `RallyScorePhoneHub.handleWatchCommand()`.

### Channel B: Phone-to-Watch State Snapshots (DataClient)

The phone publishes full score state snapshots to the Data Layer at path `/rallyscore/score_state` using `DataClient.putDataItem()` with `setUrgent()`.

The snapshot includes: team A score, team B score, serving team, server number, team names, score call text, spoken score call text, timestamp, match active flag, undo availability flag, and voice announcement mode.

The watch's `WearDataLayerListenerService` (a `WearableListenerService`) receives data changes in `onDataChanged()` and delegates to `WearPhoneSync.handleDataEvents()`. The watch also actively queries the latest data state via `DataClient.dataItems` on startup and reconnection.

### Node Discovery (NodeClient)

Both phone and watch use `Wearable.getNodeClient().connectedNodes` to discover connected peer nodes. Connection state is exposed as `StateFlow<Boolean>`: `RallyScorePhoneHub.watchConnected` on the phone and `WearPhoneSync.phoneConnected` on the watch.

### API Usage Summary

| Client API | Direction | Purpose |
|---|---|---|
| `MessageClient` | Watch → Phone | Rally winner / undo commands |
| `DataClient` | Phone → Watch | Full score state snapshots |
| `NodeClient` | Bidirectional | Peer discovery, connection tracking |
| `WearableListenerService` | Both sides | Background message/data/peer event delivery |

The pattern is a **command/state sync** model: the watch is an input-only remote control; the phone owns all state, applies scoring rules, and publishes authoritative state back.

---

## 2. Phone as Hub Architecture

The phone is the **single source of truth** whenever present and connected to a watch.

### Central State Ownership

`RallyScorePhoneHub` (`app/src/main/java/com/courtside/pickleball/sync/RallyScorePhoneHub.kt`) is a Kotlin `object` singleton central coordinator. It owns the `ScoreboardStore` instance which holds:

- `GameState` (immutable score/serving state, exposed as `StateFlow<GameState>`)
- Match active flag (exposed as `StateFlow<Boolean>`)
- Undo history (in-memory list of prior `GameState` values)

All scoring logic flows through `PickleballScoringEngine` in the `shared/` module.

### Sync State Management

- Watch command → `RallyScorePhoneHub.handleWatchCommand()` → `store.recordRallyWinner()` or `store.undo()` → shared scoring engine
- After any state change → `publishScoreState()` sends updated `GameState` to Wear Data Layer
- State changes collected via coroutine `Flow` automatically trigger: persistence to `SharedPreferences`, Data Layer publishing, and Tablet display broadcasting

### Persistence

- Phone match state (scores, serving team, server number, team names, match active flag) is persisted to `SharedPreferences` (`rallyscore_phone_match`) on every state change
- On relaunch, `restorePersistedMatch()` recovers the active match state
- **Undo history is not persisted** (known gap, in-memory only)

### Initialization Flow

1. `PhoneWearListenerService.onCreate()` calls `RallyScorePhoneHub.initialize()`
2. `MainActivity.onCreate()` also calls `initialize()` (idempotent via `initialized` flag)
3. Initialization: restores persisted match, refreshes connected Wear nodes, starts `TabletDisplaySync` broadcaster/listener, publishes initial score state

---

## 3. Bluetooth / Network Connectivity

### Bluetooth

There is **no Bluetooth-specific connectivity code**. The only Bluetooth references are in docs (`FutureIdeas.md`, `Roadmap.md`) listing "Bluetooth-specific voice mode" and "Bluetooth speaker support" as future ideas. Current audio routing relies on Android's native Bluetooth audio support.

### Wi-Fi / Local Network (Phone-to-Tablet)

Extensive local-network code in `TabletDisplaySync.kt`:

| Port | Protocol | Direction | Purpose |
|---|---|---|---|
| 45454 | UDP broadcast | Phone → Tablet | Display snapshot broadcast + tablet hello discovery |
| 45455 | TCP | Phone → Tablet | Fallback display snapshot delivery |
| 45456 | TCP (WebSocket) | Phone ← Tablet | Persistent tablet display client connection |

**Details:**
- **UDP Broadcast Discovery**: Phone broadcasts score snapshots to `255.255.255.255` and interface-specific broadcast addresses on port `45454`
- **TCP Fallback**: Phone publishes to known tablet TCP endpoints on port `45455`
- **WebSocket Server**: Phone hosts a lightweight WebSocket server on port `45456` implementing RFC 6455 handshake (no external library)
- **Multicast Lock**: Acquires `WifiManager.MulticastLock` for reliable UDP broadcast reception
- **Wi-Fi Permissions**: `INTERNET`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`

### Tablet Discovery Mechanisms

The tablet discovers the phone through multiple fallback strategies (all local-network only):

1. **Remembered endpoint**: Persists last-known phone IP/port in `SharedPreferences` and retries on launch
2. **Gateway probing**: Tries the current Wi-Fi gateway IP as the phone WebSocket endpoint
3. **Subnet scanning**: Scans the entire local IPv4 `/24` subnet for port `45456`
4. **UDP hello broadcasting**: Tablet broadcasts `RALLYSCORE_TABLET_HELLO_V1` for phone discovery

---

## 4. Multi-Device Scenarios

### Mode 0: Watch Only

**Status: Fully implemented.**

The watch owns its own `GameState`, runs its own `PickleballScoringEngine`, maintains undo history in `mutableStateListOf`, and announces scores via TTS. Controls: ME SERVES / OPP SERVES, ME WON, OPP WON, UNDO, RESET. Connection label shows "STANDALONE."

### Mode 1: Phone Only

**Status: First-class experience.**

Phone app is fully functional without any watch connection. `ScoreboardViewModel` uses `RallyScorePhoneHub.store` directly. Direct tap scoring, Undo, End Game all work. Voice mode defaults to `PhoneOnly` when `watchConnected == false`.

### Mode 2: Watch + Phone

**Status: Initial implementation (needs hardening).**

- Watch detects phone connection via `WearPhoneSync.phoneConnected` and switches to connected mode
- Connected mode: watch shows `PhoneScoreState` from DataClient snapshots; controls are ME WON, OPP WON, UNDO (sent as MessageClient commands)
- Controls disabled while awaiting phone confirmation (700ms debounce, 2.2s confirmation timeout)
- Watch shows "START ON PHONE" idle screen when phone connected but no match active
- Confirmation feedback: Sent (amber), Confirmed (green + double-haptic), Problem (red + long-haptic)
- Voice defaults to `WatchThenPhone`

### Mode 3: Tablet Only

**Status: Complete — first-class mode (Phase 3 done).**

Tablet-sized screens (≥600dp) render `MatchSetupScreen` for standalone tablet scoring using the same `ScoreboardStore` and `PickleballScoringEngine` as the phone. Tablet controller mode (tap-to-score, EDIT/UNDO/END) is fully implemented, not mid-transition — the tablet is either a standalone controller (owns its own match) or a connected controller sending intent to the phone, never a purely passive display.

### Mode 4: Phone + Tablet Synced

**Status: Command/state sync implemented; conflict handling not yet implemented.**

The tablet sends rally/Undo/End/correction commands to the phone over an HMAC-authenticated, rate-limited WebSocket channel with explicit court-code pairing (not passive display-only, as this doc originally said). The phone applies every command through the shared scoring engine and broadcasts confirmed state back to all connected displays. What's still missing is deterministic conflict handling for near-simultaneous phone/tablet/watch input — today's behavior is race-safe (main-thread serialized, no state corruption) but not a designed conflict-resolution policy. See `docs/Roadmap.md` Phase 5 and `docs/CodeReviewFindings.md`.

### Mode 5: Watch + Phone + Tablet

**Status: Works today.**

Exercised across all three device combinations during Phase 3 hardening. Phone is the primary hub; watch sends command-only input; tablet is either standalone or a connected controller — never two simultaneous sources of truth. The same conflict-handling gap noted in Mode 4 applies here too.

### Mode 6: Watch + Phone + Portable Monitor

**Status: Not yet implemented (Phase 4).**

Docs suggest simple screen mirroring via USB-C/HDMI for MVP.

### Mode Detection Logic (ScoreboardApp.kt)

```
if (useTabletDisplayLayout && activeRemoteTabletState != null) → TabletDisplayScreen (passive client)
else if (useTabletDisplayLayout && !matchStarted) → TabletWaitingForPhoneScreen
else if (matchStarted) → ScoreboardScreen (phone controller) or TabletDisplayScreen (tablet controller)
else → MatchSetupScreen
```

---

## 5. Sync Direction and Conflict Resolution

### Sync Direction

| Path | Direction | Content |
|---|---|---|
| Watch → Phone | Message (`MessageClient`) | User intent commands only |
| Phone → Watch | Data (`DataClient`) | Full authoritative score state |
| Phone → Tablet | UDP/TCP/WebSocket | Display snapshots |
| Tablet → Phone | UDP | Hello discovery |
| Tablet → Phone | WebSocket (HMAC-signed) | Rally/Undo/End/correction commands (not discovery-only) |
| Watch ↔ Tablet | TCP (HMAC-signed), UDP hello | Direct fallback command/state sync when no phone is present |

### State Ownership

| Mode | Source of Truth |
|---|---|
| Watch Only | Watch (local `GameState` + `PickleballScoringEngine`) |
| Phone Only | Phone (`ScoreboardStore` → `ScoreboardViewModel`) |
| Watch + Phone | Phone (`RallyScorePhoneHub.store`) |
| Tablet Only | Tablet (`ScoreboardStore` via same code as phone) |
| Phone + Tablet connected | Phone (tablet sends intent-only commands, never mutates its own score optimistically) |
| Watch + Phone + Tablet | Phone (single hub; watch and tablet are both command-only in this mode) |

### Conflict Resolution

In Watch + Phone mode, there is a single command source (the watch) and a single state owner (the phone), so conflicts cannot occur.

The confirmation flow provides basic reliability:
- Watch sends command, sets `awaitingPhoneConfirmation = true`, disables further input
- Watch stores `baselinePhoneUpdateAt` timestamp
- When a new phone score snapshot arrives with `updatedAt > baseline`, command is confirmed and input re-enabled
- If 2.2 seconds pass without confirmation, watch shows "PHONE?" error feedback

In Phone + Tablet (and Watch + Phone + Tablet) connected mode, the phone is still the single state
owner even though there can now be multiple command *sources* (watch and tablet at once). Every
command is applied on the phone's main thread in the order it's received, which is race-safe (no
corrupted state) but not a designed conflict-resolution policy — if two devices send input at
nearly the same instant, whichever the phone processes first simply wins and the second applies on
top of the new state. Deterministic, tested conflict handling for that near-simultaneous case is
still a real gap (see `docs/Roadmap.md` Phase 5).

---

## 6. Connectivity Gaps and Error Handling

### Watch-Phone Robustness

- **Detection**: Both sides refresh connected nodes every 5 seconds
- **Connection UI**: Phone shows "WATCH CONNECTED" (amber) or "WATCH OFFLINE" (red); watch shows "PHONE CONNECTED" (amber) or "STANDALONE" (gray)
- **Command reliability**: Fire-and-forget via `MessageClient`; confirmation relies on timestamp comparison against incoming data updates rather than explicit acknowledgment
- **Recovery**: Watch re-queries score state on every peer connection event and performs periodic full refresh every 5 seconds
- **Persistence**: Phone persists match state to survive process death (undo history lost)

### Tablet-Phone Robustness

- **Connection states**: `Searching` (gray), `Reconnecting` (red, keeps last score visible), `Connected` (amber) — the UI now correctly reflects these; a previously-shipped bug fed the setup screen the wrong (host-role instead of client-role) connection state, so a tablet's pairing badge could never show `Connected` no matter how many times the underlying connection actually succeeded. Fixed.
- **Staleness**: If no snapshot in 5 seconds → `Reconnecting`; tablet endpoint entries removed after 30 seconds
- **Reconnect**: Tablet remembers last endpoint, retries WebSocket up to 5 times with 2s intervals, subnet scanning every 15 seconds while disconnected. A manual "tap to retry" badge action now reliably forces a fresh attempt (a previous version silently no-opped if an automatic retry was already mid-backoff).
- **Broadcast cadence**: Phone broadcasts at 1 Hz while a match is active or a tablet is connected/recently seen; idles to a 5-second cadence otherwise, instead of running at 1 Hz forever.
- **Security**: WebSocket commands require a per-connection HMAC-SHA256 signature plus rate limiting on the accept loop; payload fields are length-capped/clamped.
- **Known issue**: Some Wi-Fi paths block local WebSocket/discovery delivery (client isolation, firewall rules). Separately, a tablet's own phone-hosting broadcast can appear in its own "AVAILABLE PHONES" discovery list (every device advertises regardless of role) — not yet fixed.

### Watch Debounce and Feedback

| Parameter | Value |
|---|---|
| Minimum tap gap | 700ms |
| Confirmation timeout | 2,200ms |
| Feedback: Sent | Visual + single vibration |
| Feedback: Confirmed | Visual + double vibration |
| Feedback: Problem | Visual + single long vibration |

### Known Gaps

1. **Watch-Phone sync has had substantial real-device hardening** (main-thread command serialization, `PrintWriter` write-failure detection) but no dedicated automated test coverage for the sync paths themselves exists yet.
2. **Tablet display sync is local-network only** — some Wi-Fi networks block delivery; this remains an inherent constraint of local-network-only sync, not a bug.
3. **No Bluetooth-specific connectivity** — only Android audio routing for speakers.
4. **Phone-Tablet sync is bidirectional command/state sync today** (rally, undo, end, corrections both ways, HMAC-authenticated), not passive display-only as this doc originally said — but it's not yet a fully conflict-handled peer-controller model (see "Conflict Resolution" above).
5. **Undo history not persisted** — in-memory only, lost on restart.
6. **Watch-Phone discovery has no capability-based pairing** — `NodeClient.connectedNodes` has no explicit pairing handshake (this is still accurate for the Wear Data Layer path specifically). The phone-tablet and watch-tablet-fallback paths, by contrast, now do have explicit pairing: a court code, HMAC-signed commands, and per-IP rate limiting.
7. **Watch confirmation is timestamp-based** — no explicit acknowledgment from transport layer.
8. **Sync-layer singletons have no lifecycle teardown** — `RallyScorePhoneHub`/`TabletDisplaySync`/`WatchTabletFallbackSync` live for the process lifetime with no formal shutdown; deliberately left alone during this pass to avoid destabilizing hardening that just landed (see `docs/CodeReviewFindings.md` finding #9).
9. **A tablet can see its own court code as a pairable candidate** in its own discovery list, since every device advertises phone-hosting availability regardless of role — not yet fixed.

---

## 7. Key Files

### Connectivity Implementation

| File | Path | Description |
|---|---|---|
| `WearSyncContract.kt` | `shared/src/main/kotlin/com/courtside/pickleball/domain/WearSyncContract.kt` | Command paths, DataMap keys, wire values shared between phone and wear apps |
| `RallyScorePhoneHub.kt` | `app/src/main/java/com/courtside/pickleball/sync/RallyScorePhoneHub.kt` | Central phone hub: owns store, receives watch commands, publishes state, manages watchConnected |
| `ScoreboardStore.kt` | `app/src/main/java/com/courtside/pickleball/sync/ScoreboardStore.kt` | In-memory state store with undo history, delegates to PickleballScoringEngine |
| `PhoneWearListenerService.kt` | `app/src/main/java/com/courtside/pickleball/sync/PhoneWearListenerService.kt` | Phone-side WearableListenerService receiving watch commands |
| `WearPhoneSync.kt` | `wear/src/main/java/com/courtside/pickleball/wear/WearPhoneSync.kt` | Watch-side sync manager: receives state, sends commands, manages phoneConnected |
| `WearDataLayerListenerService.kt` | `wear/src/main/java/com/courtside/pickleball/wear/WearDataLayerListenerService.kt` | Watch-side WearableListenerService receiving phone state snapshots |
| `TabletDisplaySync.kt` | `app/src/main/java/com/courtside/pickleball/sync/TabletDisplaySync.kt` | Phone-to-tablet display sync: UDP, TCP, WebSocket |
| `WearScoreboardApp.kt` | `wear/src/main/java/com/courtside/pickleball/wear/WearScoreboardApp.kt` | Watch UI: mode routing, command sending, confirmation tracking, haptic feedback |
| `ScoreboardApp.kt` | `app/src/main/java/com/courtside/pickleball/ui/ScoreboardApp.kt` | Phone/Tablet UI mode router |
| `ScoreboardViewModel.kt` | `app/src/main/java/com/courtside/pickleball/ui/ScoreboardViewModel.kt` | ViewModel bridging UI to state, exposing connectivity status |

### Configuration

| File | Path | Description |
|---|---|---|
| `app/AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` | Permissions: INTERNET, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE; registers PhoneWearListenerService |
| `wear/AndroidManifest.xml` | `wear/src/main/AndroidManifest.xml` | Permission: VIBRATE; registers WearDataLayerListenerService |
| `app/build.gradle.kts` | `app/build.gradle.kts` | `play-services-wearable:19.0.0`, shared module |
| `wear/build.gradle.kts` | `wear/build.gradle.kts` | `play-services-wearable:19.0.0`, shared module (same applicationId) |

### Domain (Shared)

| File | Path | Description |
|---|---|---|
| `PickleballScoringEngine.kt` | `shared/src/main/kotlin/com/courtside/pickleball/domain/PickleballScoringEngine.kt` | Pure scoring logic: recordRallyWinner, scorePoint, loseServe, side-out |
| `PickleballGame.kt` | `shared/src/main/kotlin/com/courtside/pickleball/domain/PickleballGame.kt` | Domain model: Team, ServerNumber, GameStatus, GameSettings, GameState |

### Documentation

| File | Description |
|---|---|
| `docs/Architecture.md` | Full architecture: device modes, responsibilities, data flow, voice flow, persistence |
| `docs/TDD.md` | Technical design: module responsibilities, data flows, watch command contract, Wear Data Layer details |
| `docs/PRD.md` | Product requirements: supported modes, priorities, MVP goals |
| `docs/Roadmap.md` | Six-phase plan: Foundation through Club/Tournament features |
| `docs/ReleaseNotes.md` | v0.1.0 release notes: implemented features and known gaps |
| `docs/TestingChecklist.md` | Manual and automated test checklists for all device modes |
