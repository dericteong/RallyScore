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
- Player fields default to P1, P2, P3, and P4.
- Team A is selected by default so Start is available immediately.
- Defaults can be edited normally.
- Delete/backspace works.
- Names are uppercased.
- My Team and Opponent Team setup sections are same visual height.
- Watch connection status appears beside the `SET UP GAME` title and does not consume a separate row.
- Player 1 and Player 2 fields fit side by side in each team section.
- Keyboard does not crop entered text.
- Pressing Enter/Done does not crash or jump focus to another field.
- Keyboard-visible `DONE` hides the keyboard and returns to the full setup layout.
- User can select starting server by tapping the full-width My Team or Opponent Team label band.
- Focusing a player field also selects that team as the starting server.
- Start button enables only after all four player names and starting server are set.
- Voice Announcements setting is visible and usable.
- Voice Announcements includes Off, Phone, Watch, Tablet, Watch > Phone, Watch > Tablet, and Phone > Tablet.

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
- User can choose ME or OPP to serve first.
- Score rows fit on round display.
- Watch uses a black background without a white launch flash.
- ME and OPP score tiles are large, side by side, and remain readable with double-digit scores.
- Tapping the ME score tile records My Team won rally.
- Tapping the OPP score tile records Opponent won rally.
- The center score call has no `CALL` label and its score/server colors match the represented teams.
- The top connection pill sits compactly near the top edge without crowding the score area.
- Connected is amber; disconnected/standalone is red.
- Undo uses an icon-style control with a large enough touch target and accessible Undo label.
- Undo and the score tiles do not overlap on a round watch display.
- Score tiles remain fully inside the round safe area without clipping.
- End remains available through the secondary watch action path without crowding the main row.
- Watch gives haptic feedback after rally input.
- Rally input uses a single short vibration; Undo uses a double vibration.
- Undo works.
- Undo and Reset buttons are easy to tap.
- Watch gives haptic feedback after undo and reset.
- Reset returns to serving-team selection.
- Watch screen stays awake while RallyScore is open.
- Score call updates after rally input.
- TTS announces the initial score after choosing first server.
- TTS announces score changes after rally input.
- TTS announces the restored score after undo.
- Watch Only mode works without a connected phone.

## Watch-Phone Sync Manual Test

Initial implementation exists. Required before the connected Watch + Phone product experience is considered complete.

- Watch can discover or pair with the phone scoring hub.
- Watch shows connected phone-idle choices when the phone is connected but has not started a match.
- From that connected idle state, the watch can still start a standalone local match with ME SERVES or OPP SERVES.
- Phone owns authoritative match state.
- Watch rally input updates the phone/shared display.
- Watch undo restores the phone/shared display.
- Watch Undo is disabled when phone undo history is empty.
- Connected-mode rally and undo buttons are disabled while waiting for phone confirmation.
- Connected-mode watch gives confirmation haptics after the phone-owned score update arrives.
- Connected-mode watch gives problem haptics if phone confirmation is not received.
- Phone and watch refresh connection state while open after reconnecting.
- Watch does not perform score calculations, server transitions, or side-out logic locally.
- Watch receives updated score state from the phone.
- Watch displays the phone-owned score in connected mode.
- Watch never announces a predicted score.
- In Watch then Phone mode, watch announces the confirmed phone score immediately after receiving state.
- In Watch then Phone mode, phone announces the same confirmed score approximately two seconds later.
- In Phone only mode, phone announces immediately.
- In Watch only mode, only the watch announces after confirmed phone state is received.
- In Tablet only mode on a tablet-owned match, tablet announces immediately after confirmed local tablet state changes.
- In Watch then Tablet mode, watch announces the confirmed phone score immediately and tablet announces the same score approximately two seconds later.
- In Phone then Tablet mode, phone announces the confirmed phone score immediately and tablet announces the same score approximately two seconds later.
- In Off mode, no device announces.
- Watch reconnects and resyncs after temporary disconnect.
- Phone-only scoring remains available when no watch is connected.
- External display shows team scores, serving team, and server number with no live-match controls.
- Debug logs show command send/receive and score-state publish/receive.

## Shared Display Manual Test

- Phone display mirroring is readable in landscape.
- Shared display uses large score numbers and high contrast.
- Shared display shows serving team and server number.
- Mirrored normal score screen shows Team A score, Team B score, serving team, server number, player names, CALL, Undo, End, and the small watch connection rail.
- Mirrored normal score screen does not show large Team A Won or Team B Won buttons.
- Tablet or portable monitor does not own independent scoring logic.

## Tablet Only Manual Test

- On a tablet-sized Android device or emulator, app launches in landscape.
- Tablet supports My Team and Opponent Team setup.
- Tablet player fields default to P1, P2, P3, and P4.
- Tablet allows first-server selection.
- Tablet can start a match at `0 - 0 - 2`.
- Tablet score screen shows both player-name groups.
- Tablet score screen shows very large Team A and Team B scores.
- Tablet score screen shows serving side and server number.
- Tablet score screen shows CALL score.
- Tablet scoring controls are large, readable, and easy to tap.
- Tapping ME WON or OPP WON records the rally winner through the shared scoring engine.
- Undo restores the previous rally.
- Correction mode is available if already implemented for the shared phone/tablet UI.
- Tablet voice announcement setting is visible and usable.
- Tablet announces the confirmed score when Tablet only mode is enabled in standalone tablet mode.
- Connected tablet announces only after receiving confirmed phone-owned state.
- Tablet screen stays awake during use.
- Tablet Only mode works without a phone or watch.
- Existing phone-sized score screen remains unchanged on phones.
- Tablet UI must not duplicate scoring rules outside the shared engine.

## Tablet Display Client / Sync Prototype Manual Test

- Display-client mode remains passive if explicitly used.
- Before receiving an active phone match, tablet shows the normal setup screen.
- Disconnected tablet can set up, score, undo, and end a standalone match.
- Starting a match on the phone opens the passive tablet display layout after sync connects.
- Tablet display shows player names, scores, serving side, server number, and CALL.
- When phone and tablet are on the same local network or the tablet is connected to the phone hotspot, tablet discovery/WebSocket logs show a phone display endpoint.
- With no external Wi-Fi available, turn on the phone hotspot, join the tablet to that hotspot, and verify the tablet finds the phone without manual IP entry.
- With Internet disabled or unavailable, verify phone-to-tablet display sync still works over the phone hotspot.
- With both devices on normal external Wi-Fi, verify the same discovery/reconnect behavior still works.
- If phone-to-tablet delivery is blocked, tablet-side same-subnet discovery attempts to find the phone WebSocket by initiating the connection from the tablet.
- Starting a phone match publishes phone-owned tablet display snapshots over the local WebSocket.
- If the network allows local-device delivery and the phone has an active match, the tablet switches from setup to connected tablet controller display.
- If the tablet remains on setup while the phone has an active match, collect logs for blocked local WebSocket/discovery delivery and verify whether the network has client isolation or inbound-device blocking.
- Watch commands update the phone and then publish updated tablet display snapshots.
- Tapping the blue/My Team score panel on a connected tablet sends a tablet command to the phone.
- Tapping the green/Opponent score panel on a connected tablet sends a tablet command to the phone.
- The tablet does not change the connected score until the phone broadcasts confirmed state.
- Phone applies connected tablet rally commands through the shared scoring engine.
- Phone broadcasts connected tablet score changes back to the tablet and to the watch if connected.
- Connected tablet UNDO sends a command to the phone and all connected displays update from confirmed phone state.
- Connected tablet END sends a command to the phone and all connected displays leave the active match after confirmed phone state.
- Stopping phone broadcasts clears stale tablet display state after a short timeout.
- Tablet shows Searching for phone before discovering a phone endpoint.
- Tablet shows Connected after receiving confirmed phone-owned score snapshots.
- Tablet shows Reconnecting after heartbeat/read timeout while preserving the last displayed score.
- Tablet remembers the last phone address and retries it after relaunch.
- Phone accepts tablet reconnects without manual action.
- Reconnected tablet receives the full current score state immediately.
- Turning tablet Wi-Fi off and on reconnects automatically.
- Moving the tablet from external Wi-Fi to the phone hotspot reconnects automatically after both devices share the hotspot network.
- Closing and reopening the tablet app reconnects automatically.
- Closing and reopening the phone app restores the active phone-owned score and keeps accepting tablet reconnects.
- Rebooting the tablet reconnects automatically after RallyScore is opened.

## Future Phone + Tablet Sync Manual Test

Not required until synced tablet controller mode is implemented.

- Phone and tablet join the same match intentionally.
- Only one canonical match state exists.
- Rally input from phone updates tablet.
- Rally input from tablet updates phone.
- Watch input updates phone and tablet.
- Undo from phone or tablet restores the same canonical previous state.
- Simultaneous phone/tablet commands are resolved deterministically.
- Devices resync after temporary disconnect.

## Crash Checks

After reproducing or fixing a crash on a device:

```bash
adb logcat -c
# reproduce
adb logcat -b crash -d
```

Confirm no new `com.courtside.pickleball` fatal exception appears.
