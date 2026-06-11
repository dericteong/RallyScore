# Roadmap

## Phase 0 - Foundation

Status: Complete enough for MVP iteration.

- Kotlin Android project.
- Compose phone UI.
- Shared pure Kotlin scoring module.
- Unit tests for scoring and ViewModel.
- Landscape phone orientation.
- Keep screen awake.

## Phase 1 - Phone MVP

Status: In progress.

Goals:

- Reliable setup name entry.
- Large table-style scoreboard.
- Rally-winner score input.
- Automatic doubles scoring.
- Unlimited undo in current match.
- End game confirmation.
- TTS score calls.
- Timed-play support by allowing scores beyond 11.
- App icon.

Remaining hardening:

- More real-device setup keyboard testing.
- Visual checks on different phone sizes.
- Confirm TTS behavior on common Android devices.
- Verify the compact setup screen's manual `DONE` keyboard-dismiss affordance on real devices.

## Phase 2 - Wear OS Companion

Status: Prototype exists.

Current:

- Standalone Wear app.
- Shared scoring engine.
- Local undo/reset.

Next:

- Decide whether Wear should be standalone, phone remote, or both.
- Add phone-watch communication using Wear OS Data Layer or MessageClient.
- Define source of truth for synchronized match state.
- Add pairing/reconnect states.

## Phase 3 - External Display / Portable Monitor

Status: Not started.

Options:

- Use standard Android display mirroring over cable.
- Add dedicated external-display presentation mode.
- Add "controller on phone, scoreboard on monitor" split mode.
- Consider larger typography and no-control spectator display.

## Phase 4 - Match Modes

Status: Not started.

Potential modes:

- Timed play, current default.
- Game to 11, win by 2.
- Game to 15 or 21.
- Rally scoring variant if required by a venue.
- Best-of match tracking.

## Phase 5 - Persistence And Polish

Status: Not started.

Ideas:

- Save in-progress match across app restart.
- Match summary screen.
- Team/player presets.
- Voice settings.
- Accessibility improvements.
- Tablet-specific layout.
