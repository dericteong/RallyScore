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
- Keep the watch visible in ambient/always-on style use while RallyScore is open.
- Harden connected watch routing so `START ON PHONE`, live scoring, undo, and end behave consistently on real hardware.

Phase 2 exit criteria before Phase 3 becomes the primary focus:

- Connected watch reliably stays on the phone-owned scoreboard during an active phone match.
- `START ON PHONE` from the watch reliably transitions phone, watch, and tablet to the same live match.
- `END` from the watch reliably ends the phone-owned match and returns the watch to the idle/start screen.
- Connected watch does not apply scoring logic locally; rally and undo remain phone-confirmed actions only.
- Connected watch reconnection is stable enough on real hardware for social-play testing.
- Watch Only standalone mode still works after connected-mode hardening.

## Phase 3 - Tablet Standalone Controller

Status: Current phase.

Current priority:

- Harden the existing phone-to-tablet display-client connection before adding new tablet controller features.
- Tablet should reconnect automatically after local-network disconnect/reconnect, app relaunch, or tablet reboot.
- Phone-to-tablet display sync should work over external Wi-Fi or the phone's hotspot with no Internet and no manual IP address entry.
- Phone should accept reconnecting tablets and send the latest match state without manual action.
- Tablet should expose clear Searching for phone, Reconnecting, and Connected states.
- Connected tablet should send rally, Undo, and End commands to the phone and wait for confirmed phone-owned state.
- Watch to phone scoring must remain unchanged.

Goals:

- Add a first-class Tablet Only controller mode inside the Android app.
- Allow match setup on tablet.
- Allow rally-winner scoring on tablet.
- Allow Undo on tablet.
- Support correction mode on tablet when available in the shared phone/tablet UI.
- Show team scores, serving team, server number, player names, and CALL clearly.
- Make typography readable by all four players.
- Keep tablet screen awake.
- Use the shared scoring engine; do not duplicate scoring rules in tablet UI.
- Keep exactly one active source of truth per match.
- Keep phone and watch flows unchanged.
- Avoid cloud, accounts, and complex phone-tablet sync in this phase.

Existing passive tablet display sync may remain as a prototype/fallback, but it is no longer the main Phase 3 direction.

## Phase 4 - Portable Monitor Support

Status: Not started.

Goals:

- Support common USB-C monitor workflows.
- Verify landscape layout on portable monitor resolutions.
- Keep the phone awake while driving the display.
- Avoid accidental touch/control surfaces on the display.

## Phase 5 - Phone + Tablet Synced Controller

Status: Not started.

Goals:

- Build on the new explicit phone-tablet pairing/discovery foundation.
- Share one canonical match state between phone and tablet.
- Evolve current tablet-to-phone command sync into full synced controller mode.
- Allow either phone or tablet to send rally, undo, and correction intent with conflict handling.
- Ensure score updates from either device update all devices.
- Add conflict handling when phone and tablet send commands at nearly the same time.
- Preserve Watch + Phone flow and allow Watch + Phone + Tablet synced mode.
- Harden reconnect behavior across Wi-Fi/hotspot environments.

## Priority Order

1. Phone remains stable source of truth.
2. Watch controls phone score.
3. Phone-only experience is excellent.
4. Tablet-only controller experience is excellent.
5. Phone voice announcements.
6. Large display mode optimized for mirroring.
7. Portable monitor support.
8. Phone-tablet synced mode.
9. Bluetooth speaker support.
10. Tournament features.

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
