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
- Wear Text-to-Speech score calls.
- Initial Wear OS Data Layer sync using watch commands and phone-owned score snapshots.
- Phone-owned score snapshots include active-match and undo-availability state for connected Wear control.
- Phone and Wear connection status indicators.
- Manual `DONE` keyboard-dismiss control on the compact setup screen.
- Initial MVP voice announcement setting.

### Changed

- Product direction changed to a flexible ecosystem: Watch Only, Phone Only, Watch + Phone, and Watch + Phone + shared display.
- Phone Only is now a first-class experience. The phone remains source of truth whenever present.
- Timed play is supported by allowing scores to continue beyond 11.
- Manual score/server controls were replaced by rally-winner input.
- Phone scoreboard contrast, score text, serving dots, and phone controls were enlarged for outdoor readability and older players.
- Spoken score-call formatting moved into the shared domain module so UI code does not duplicate score-call logic.
- Phone ViewModel now delegates to a phone-owned score store that can also receive watch commands.
- Setup field Enter/Done no longer auto-focuses the next field because that caused Compose focus crashes on Samsung devices.
- Connected-mode score announcements must use confirmed phone-owned state. Target voice behavior is watch first, then phone repeat when Watch then Phone mode is selected.
- Connected Wear now waits for an active phone match before showing remote scoring controls.

### Known Gaps

- Wear sync is initial and still needs paired real-device hardening.
- Wear app still contains standalone Watch Only scoring logic when no phone state is available.
- No external-display-specific mode yet.
- No persisted match state after app restart.
- No in-app TTS voice selector.
- Target voice modes are not fully implemented yet: Off, Phone only, Watch only, and Watch then Phone.
- No dedicated Bluetooth speaker mode yet; Android audio routing may handle connected speakers.
- No formal game-complete mode for target-score games yet.

## Release Process Notes

Before a release candidate:

- Run all automated tests.
- Test setup input on at least one Samsung phone.
- Test scoring flow on a real Android phone.
- Test Wear build on emulator.
- Review `docs/TestingChecklist.md`.
