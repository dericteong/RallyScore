# Roadmap

## Phase 0 - Foundation

Status: Complete enough for MVP iteration.

- Kotlin Android project.
- Compose phone UI.
- Shared pure Kotlin scoring module.
- Unit tests for scoring and ViewModel.
- Landscape phone orientation.
- Keep screen awake.

## Phase 1 - Phone Scoring Engine

Status: Complete enough for Phase 2 iteration.

Goals:

- Keep phone as source of truth.
- Treat Phone Only as a first-class product mode.
- Keep pickleball scoring rules in the shared engine, executed by the phone app.
- Reliable setup name entry.
- Large table-style scoreboard.
- Rally-winner score input.
- Automatic doubles scoring.
- Setup-time scoring format selection with Traditional default and Rally as an alternative.
- Unlimited undo in current match.
- End game confirmation.
- TTS score calls.
- Voice settings foundation.
- Timed-play support by allowing scores beyond 11.
- App icon.

Remaining hardening:

- More real-device setup keyboard testing.
- Visual checks on different phone sizes.
- Confirm TTS behavior on common Android devices.
- Verify the compact setup screen's manual `DONE` keyboard-dismiss affordance on real devices.

## Phase 2 - Wear OS Watch Control

Status: Complete enough for Phase 3 iteration.

Current:

- Standalone Watch Only app.
- Shared scoring engine.
- Local undo/reset.
- Wear score calls.
- Connected watch can send rally and undo commands to phone in initial implementation.

Delivered in this phase:

- Preserve Watch Only as a valid casual/demo/backup mode.
- Remove watch-owned scoring authority from connected Watch + Phone flow.
- Harden phone-watch communication using Wear OS Data Layer or MessageClient.
- Keep phone as the source of truth for synchronized match state.
- Send only simple in-match watch commands:
  - My Team won rally.
  - Opponent won rally.
  - Undo.
- Reflect phone-owned state back to the watch.
- Add pairing/reconnect states.
- Implement connected voice timing: confirmed watch announcement immediately, phone repeat approximately two seconds later.
- Keep the watch visible in ambient/always-on style use while PickleCast is open.
- Harden connected watch routing so `START ON PHONE`, live scoring, undo, and end behave consistently on real hardware.

Phase 2 exit criteria before Phase 3 becomes the primary focus:

- Connected watch reliably stays on the phone-owned scoreboard during an active phone match.
- `START ON PHONE` from the watch reliably transitions phone, watch, and tablet to the same live match.
- `END` from the watch reliably ends the phone-owned match and returns the watch to the idle/start screen.
- Connected watch does not apply scoring logic locally; rally and undo remain phone-confirmed actions only.
- Connected watch reconnection is stable enough on real hardware for social-play testing.
- Watch Only standalone mode still works after connected-mode hardening.

## Phase 3 - Tablet Standalone Controller

Status: Complete enough for Phase 4/5 iteration. Tablet Only is a first-class mode, and the
phone-to-tablet connection has been hardened through live-device debugging (see
`docs/CodeReviewFindings.md` for the specific bugs found and fixed): HMAC-authenticated commands,
per-IP rate limiting on both the WebSocket and TCP channels, correct Searching/Reconnecting/Connected
state reporting (a real bug had this permanently stuck), a working manual retry action, and an
idle-vs-active broadcast cadence that doesn't spam the network.

Delivered:

- Phone-to-tablet display-client connection is hardened: HMAC-signed commands, rate-limited
  accept loops, payload bounds-checking, and a read-timeout tuned to the broadcast cadence.
- Tablet reconnects automatically after local-network disconnect/reconnect, app relaunch, or
  tablet reboot; the phone accepts reconnecting tablets without manual action.
- Phone-to-tablet display sync works over external Wi-Fi or the phone's hotspot with no Internet
  and no manual IP address entry.
- Tablet exposes working Searching for phone, Reconnecting, and Connected states, plus a manual
  "tap to retry" action that reliably forces a fresh connection attempt.
- Connected tablet sends rally, Undo, End, and score/server corrections to the phone and waits
  for confirmed phone-owned state before updating its display.
- First-class Tablet Only controller mode: match setup, rally-winner scoring, Undo, correction
  mode, large readable scoreboard, keeps screen awake, uses the shared scoring engine exclusively.
- Watch-to-phone scoring is unchanged by any of the above.

Remaining known gaps (see `docs/CodeReviewFindings.md` for details):

- A tablet's "AVAILABLE PHONES" list can show its own court code as a pairable candidate, since
  every device broadcasts phone-hosting availability regardless of role — not yet fixed.
- Sync-layer singletons (`RallyScorePhoneHub`, `TabletDisplaySync`, `WatchTabletFallbackSync`)
  still have no lifecycle teardown; deliberately left alone rather than risk destabilizing the
  sync layer this hardening pass just fixed.
- The wire protocol's version detection (nine legal field counts) is fragile; revisit at the next
  protocol-breaking change rather than migrating speculatively.

## Phase 4 - Portable Monitor Support

Status: Not started.

Goals:

- Support common USB-C monitor workflows.
- Verify landscape layout on portable monitor resolutions.
- Keep the phone awake while driving the display.
- Avoid accidental touch/control surfaces on the display.

## Phase 5 - Phone + Tablet Synced Controller

Status: Command/state sync foundation already delivered as part of Phase 3 hardening — this is
further along than "not started." What's already true today (Mode 4 in `docs/PRD.md`): phone and
tablet share one canonical match state, the tablet sends rally/Undo/End/correction intent to the
phone over an authenticated channel, the phone applies every command through the shared scoring
engine, and confirmed state is broadcast back to all connected displays (tablet and watch alike).
Watch + Phone + Tablet synced mode (Mode 5) works today and was exercised extensively across all
three device combinations during Phase 3 hardening.

What's genuinely still missing:

- Deterministic conflict handling when phone and tablet (or watch and tablet) send commands at
  nearly the same instant is not explicitly engineered or tested — today's behavior is "whichever
  command the phone's main thread processes first wins, the next applies on top of the new state,"
  which is race-safe (no corrupted state) but not a designed conflict-resolution policy.
- Reconnect behavior across Wi-Fi/hotspot environments has been hardened substantially (see Phase
  3), but that hardening happened at the connection-transport level, not as bidirectional
  peer-controller conflict handling specifically.

Remaining goals:

- Explicitly design and test conflict handling for near-simultaneous phone/tablet/watch input,
  rather than relying on incidental main-thread serialization.
- Decide whether "either device can be the source of truth mid-match" is actually desired product
  behavior, or whether "phone is always the source of truth, tablet/watch are always controllers"
  (today's model) is the intended permanent design — the current architecture is the latter, and
  changing that would be a deliberate product decision, not just an implementation gap.

## Priority Order

1. Phone remains stable source of truth. — Done.
2. Watch controls phone score. — Done.
3. Phone-only experience is excellent. — Done.
4. Tablet-only controller experience is excellent. — Done (Phase 3).
5. Phone voice announcements. — Done (Off/Phone/Watch/Tablet/Watch→Phone/Watch→Tablet/Phone→Tablet).
6. Large display mode optimized for mirroring. — Done (`docs/Architecture.md` "Mirrored Display").
7. Portable monitor support. — Not started (Phase 4).
8. Phone-tablet synced mode. — Command/state sync foundation done (Phase 5); genuine
   simultaneous-input conflict handling still open.
9. Bluetooth speaker support. — Not started.
10. Tournament features. — Not started (Phase 6).

## Phase 6 - Club And Tournament Features

Status: Not started.

Potential modes and features:

- Timed play, current default.
- Game to 11, win by 2.
- Game to 15 or 21.
- Rally scoring variant if required by a venue.
- Best-of match tracking.
- Save in-progress match across app restart.
- Match summary screen.
- Team/player presets.
- Expanded voice settings.
- Accessibility improvements.
