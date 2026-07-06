# Future Ideas

These ideas are not MVP commitments. Move items to `Roadmap.md` only after they become planned work.

## Voice

- In-app TTS voice picker.
- Slower/faster voice speed control.
- Repeat current score button.
- Optional announcements for side out or server change.
- Bluetooth-specific voice mode.
- ~~Tablet voice announcements.~~ Shipped — `VoiceAnnouncementMode.TabletOnly` and the
  Watch-then-Tablet/Phone-then-Tablet combined modes are implemented (see `docs/PRD.md`
  "Voice Announcements" and `docs/Architecture.md`).

## Watch Controller Polish

- ~~Haptic feedback variants after watch rally entry.~~ Shipped — the watch already uses four
  distinct vibration patterns for rally input, undo, confirmed remote state, and connection
  problems (`vibrateSingleTap`/`vibrateDoubleTap`/`vibrateWatchConfirmed`/`vibrateWatchProblem`
  in `wear/.../WearScoreboardApp.kt`). A remaining idea in this space: user-customizable/selectable
  haptic styles, which is not implemented.
- Optional watch complication or tile for current score.
- Repeat current score from watch.
- Left-handed watch layout option.

## External Display

- Dedicated external scoreboard display mode.
- Spectator-only view with no controls.
- HDMI/USB-C monitor layout testing.
- Alternate tablet-first display layout.

## Game And Match Modes

- Timed game countdown.
- Game to 11/15/21, win by 2.
- Best-of-three match tracking.
- Team switch sides prompt.
- Manual correction mode protected behind confirmation.

## Data

- Save player/team presets.
- Save recent matches.
- Export match summary.
- Basic stats such as rally count and side outs.

## Accessibility

- Larger setup input mode.
- High contrast theme.
- Color-blind friendly palette.
- TalkBack descriptions for score cells and controls.
