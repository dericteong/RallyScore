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

Status: In progress.

Goals:

- Keep phone as source of truth.
- Treat Phone Only as a first-class product mode.
- Keep pickleball scoring rules in the shared engine, executed by the phone app.
- Reliable setup name entry.
- Large table-style scoreboard.
- Rally-winner score input.
- Automatic doubles scoring.
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

Status: Standalone Watch Only exists; initial command-only phone sync exists and needs hardening.

Current:

- Standalone Watch Only app.
- Shared scoring engine.
- Local undo/reset.
- Wear score calls.
- Connected watch can send rally and undo commands to phone in initial implementation.

Next:

- Preserve Watch Only as a valid casual/demo/backup mode.
- Remove watch-owned scoring authority from connected Watch + Phone flow.
- Harden phone-watch communication using Wear OS Data Layer or MessageClient.
- Keep phone as the source of truth for synchronized match state.
- Send only simple in-match watch commands:
  - Team A won rally.
  - Team B won rally.
  - Undo.
- Reflect phone-owned state back to the watch.
- Add pairing/reconnect states.
- Implement connected voice timing: confirmed watch announcement immediately, phone repeat approximately two seconds later.

## Phase 3 - Large Display Mode

Status: Partially available through phone display/mirroring; dedicated passive display mode not started.

Goals:

- Add a passive scoreboard display with no controls during live play.
- Show only team scores, serving team, and server number.
- Make typography readable by all four players.
- Keep phone scoring controls separate from passive display mode.
- Optimize for Android display mirroring first.

## Phase 4 - Portable Monitor Support

Status: Not started.

Goals:

- Support common USB-C monitor workflows.
- Verify landscape layout on portable monitor resolutions.
- Keep the phone awake while driving the display.
- Avoid accidental touch/control surfaces on the display.

## Phase 5 - Android Tablet Display

Status: Not started.

Goals:

- Support Android tablet as a shared scoreboard display.
- Provide tablet-specific passive layout if phone mirroring is not sufficient.
- Keep tablet display synchronized with phone match state.

## Priority Order

1. Phone remains stable source of truth.
2. Watch controls phone score.
3. Phone-only experience is excellent.
4. Large display mode optimized for mirroring.
5. Phone voice announcements.
6. Tablet support.
7. Portable monitor support.
8. Wireless tablet mode.
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
