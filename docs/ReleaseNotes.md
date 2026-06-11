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
- Manual `DONE` keyboard-dismiss control on the compact setup screen.

### Changed

- Timed play is supported by allowing scores to continue beyond 11.
- Manual score/server controls were replaced by rally-winner input.
- Setup field Enter/Done no longer auto-focuses the next field because that caused Compose focus crashes on Samsung devices.

### Known Gaps

- Wear app does not remote-control the phone yet.
- No external-display-specific mode yet.
- No persisted match state after app restart.
- No in-app TTS voice selector.
- No formal game-complete mode for target-score games yet.

## Release Process Notes

Before a release candidate:

- Run all automated tests.
- Test setup input on at least one Samsung phone.
- Test scoring flow on a real Android phone.
- Test Wear build on emulator.
- Review `docs/TestingChecklist.md`.
