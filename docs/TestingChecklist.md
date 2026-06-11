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

## Phone Score Manual Test

- Scores are large and readable.
- Team A row uses blue.
- Team B row uses green.
- Team rows show player names joined with `&`.
- Table dividers are neutral.
- Serving team row shows one or two dots.
- Tap Team A score records Team A rally winner.
- Tap Team B score records Team B rally winner.
- Call bar uses team colors for the relevant numbers.
- TTS announces after rally input.
- Undo restores last rally.
- End opens confirmation.
- Confirming End returns to setup.
- Screen stays awake during use.

## Wear Manual Test

- Wear app launches on emulator or watch.
- User can choose Team A or Team B to serve first.
- Score rows fit on round display.
- A WON and B WON buttons are easy to tap.
- Undo works.
- Undo and Reset buttons are easy to tap.
- Reset returns to serving-team selection.
- Score call updates after rally input.

## Crash Checks

After reproducing or fixing a crash on a device:

```bash
adb logcat -c
# reproduce
adb logcat -b crash -d
```

Confirm no new `com.courtside.pickleball` fatal exception appears.
