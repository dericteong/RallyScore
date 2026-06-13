# Testing Checklist

## Automated Tests

Run before shipping scoring or ViewModel changes:

```bash
./gradlew :shared:test :app:test
```

Run before shipping phone app changes:

```bash
./gradlew :app:assembleDebug
```

Run before shipping Wear changes:

```bash
./gradlew :wear:assembleDebug
```

Install only the affected app on real hardware:

```bash
./gradlew :app:installDebug
./gradlew :wear:installDebug
```

Do not use root `./gradlew installDebug` on a phone; it installs both phone and Wear modules and creates multiple launcher icons.

## Scoring Rules

Verify:

- Initial state is `0 - 0 - 2`.
- Serving team scores when it wins a rally.
- Receiving team does not score when it wins a rally.
- First-server exception sides out immediately.
- Server 1 loss changes to Server 2.
- Server 2 loss causes side out.
- Score call always shows serving score first.
- Score can continue above 11 for timed play.
- Undo restores complete prior state.

## Phone Setup Manual Test

Use a real phone when possible.

- App launches in landscape.
- Team A Player 1 and Player 2 fields accept more than two characters.
- Team B Player 1 and Player 2 fields accept more than two characters.
- Delete/backspace works.
- Names are uppercased.
- Team A and Team B setup sections are same visual height.
- Player 1 and Player 2 fields fit side by side in each team section.
- Keyboard does not crop entered text.
- Pressing Enter/Done does not crash or jump focus to another field.
- Keyboard-visible `DONE` hides the keyboard and returns to the full setup layout.
- User can select starting server by tapping the full-width Team A or Team B label band.
- Focusing a player field also selects that team as the starting server.
- Start button enables only after all four player names and starting server are set.
- Voice Announcements setting is visible and usable.

## Phone Score / Display Manual Test

- Scores are large and readable.
- Scoreboard uses high-contrast colors suitable for outdoor play.
- Scores, score call, serving dots, and phone controls are readable for older players.
- Team A row uses blue.
- Team B row uses green.
- Team rows show player names joined with `&`.
- Table dividers are neutral.
- Serving team row shows one or two dots.
- Tap Team A score records Team A rally winner.
- Tap Team B score records Team B rally winner.
- No manual score controls are visible.
- No manual server controls are visible.
- Call bar uses team colors for the relevant numbers.
- TTS announces after rally input.
- Voice Announcements defaults appropriately for Phone Only mode.
- Voice Announcements can be set to Off.
- Phone speaker announces the confirmed score after watch rally input.
- Undo restores last rally.
- Phone speaker announces the restored score after undo when voice is enabled.
- End opens confirmation.
- Confirming End returns to setup.
- Screen stays awake during use.
- Display remains readable when mirrored or shown on the intended phone/tablet/monitor display.
- Direct phone scoring remains usable as first-class Phone Only mode.
- Watch connection status indicator is visible.
- Phone Only mode works as a first-class experience without a watch.

## Watch Only Manual Test

- Wear app launches on emulator or watch.
- Phone connection status indicator is visible.
- User can choose Team A or Team B to serve first.
- Score rows fit on round display.
- A WON and B WON buttons are easy to tap.
- Undo works.
- Undo and Reset buttons are easy to tap.
- Reset returns to serving-team selection.
- Score call updates after rally input.
- TTS announces the initial score after choosing first server.
- TTS announces score changes after rally input.
- TTS announces the restored score after undo.
- Watch Only mode works without a connected phone.

## Watch-Phone Sync Manual Test

Initial implementation exists. Required before the connected Watch + Phone product experience is considered complete.

- Watch can discover or pair with the phone scoring hub.
- Watch shows a connected idle/start-on-phone state when the phone is connected but has not started a match.
- Phone owns authoritative match state.
- Watch rally input updates the phone/shared display.
- Watch undo restores the phone/shared display.
- Watch Undo is disabled when phone undo history is empty.
- Watch does not perform score calculations, server transitions, or side-out logic locally.
- Watch receives updated score state from the phone.
- Watch displays the phone-owned score in connected mode.
- Watch never announces a predicted score.
- In Watch then Phone mode, watch announces the confirmed phone score immediately after receiving state.
- In Watch then Phone mode, phone announces the same confirmed score approximately two seconds later.
- In Phone only mode, phone announces immediately.
- In Watch only mode, only the watch announces after confirmed phone state is received.
- In Off mode, no device announces.
- Watch reconnects and resyncs after temporary disconnect.
- Phone-only scoring remains available when no watch is connected.
- External display shows team scores, serving team, and server number with no live-match controls.
- Debug logs show command send/receive and score-state publish/receive.

## Shared Display Manual Test

- Phone display mirroring is readable in landscape.
- Shared display uses large score numbers and high contrast.
- Shared display shows serving team and server number.
- Tablet or portable monitor does not own independent scoring logic.

## Crash Checks

After reproducing or fixing a crash on a device:

```bash
adb logcat -c
# reproduce
adb logcat -b crash -d
```

Confirm no new `com.courtside.pickleball` fatal exception appears.
