# Technical Design Document

## Overview

The project is a Kotlin multi-module Gradle Android project.

- `app`: phone Android application, scoring hub, rules owner, first-class Phone Only scorer, and display source.
- `wear`: Wear OS application, Watch Only scorer, and connected command controller.
- `shared`: pure Kotlin scoring domain shared by phone and Wear.

RallyScore supports multiple product modes: Watch Only, Phone Only, Watch + Phone, Watch + Phone + Android Tablet, and Watch + Phone + Portable Monitor. Additional devices enhance the experience but are not required. Whenever a phone is present, the phone owns synchronized match state and all scoring decisions. Watch Only mode may keep standalone watch-owned state for casual play, demo mode, and backup mode. The phone app uses Jetpack Compose and a ViewModel with `StateFlow`. The shared module owns scoring rules so they can be tested without Android.

## Technology Stack

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Lifecycle ViewModel and Compose lifecycle collection
- Android Text-to-Speech
- JUnit 4
- Gradle Kotlin DSL
- Java 17 toolchain

## Module Responsibilities

### `shared`

Contains:

- `Team`
- `ServerNumber`
- `GameStatus`
- `GameSettings`
- `GameState`
- `PickleballScoringEngine`

Responsibilities:

- Apply rally-winner scoring rules.
- Preserve score-call order as serving score, receiving score, server number.
- Keep Android dependencies out of scoring logic.
- Remain the only place for pickleball scoring rules.

### `app`

Contains:

- `MainActivity`
- `ScoreboardViewModel`
- `ScoreboardStore`
- `RallyScorePhoneHub`
- `PhoneWearListenerService`
- `ScoreboardApp` and phone Compose UI

Responsibilities:

- Keep screen awake.
- Preserve landscape orientation for phone display readability.
- Act as scoring hub, rules executor, and display source.
- Manage match setup state.
- Expose game state to Compose.
- Maintain rally history for undo.
- Receive watch commands through Wear OS `MessageClient`.
- Publish score state to the watch through Wear OS `DataClient`.
- Provide Text-to-Speech score calls from the phone after authoritative state changes.
- Support target MVP voice modes: Off, Phone only, Watch only, and Watch then Phone.
- Render large table-style scoreboard and controls.
- Preserve direct phone scoring as a first-class Phone Only mode.
- Own communication with the watch and external display surfaces when those layers are implemented.

### `wear`

Contains:

- Wear launcher activity.
- Wear Compose UI.
- `WearPhoneSync`
- `WearDataLayerListenerService`
- Text-to-Speech score calls for standalone Watch Only mode.

Current Wear behavior supports connected remote mode when phone score state is available and standalone Watch Only mode when phone state is unavailable. Connected Wear behavior is command-only: Team A won rally, Team B won rally, and Undo. The watch should not contain pickleball scoring logic, server-transition logic, side-out logic, or authoritative match state in connected mode.

## Supported Product Modes

- Mode 0 - Watch Only: watch source of truth.
- Mode 1 - Phone Only: phone source of truth.
- Mode 2 - Watch + Phone: phone source of truth, watch remote control.
- Mode 3 - Watch + Phone + Android Tablet: phone source of truth, tablet display.
- Mode 4 - Watch + Phone + Portable Monitor: phone source of truth, mirrored monitor display.

## Data Flow

### Phone Present Data Flow

1. User sets Team A and Team B names on the phone.
2. User selects starting serving team during setup.
3. Phone ViewModel creates the authoritative `GameState`.
4. Primary flow: active player taps A WON or B WON on the watch.
5. Watch sends a `MessageClient` command event to the phone.
6. Fallback flow: user taps Team A or Team B score directly on the phone.
7. Phone ViewModel stores the previous state in history.
8. Phone ViewModel asks `PickleballScoringEngine.recordRallyWinner`.
9. Phone updates authoritative match state.
10. Phone publishes updated score state through `DataClient`.
11. Shared display redraws team scores, serving team, and server number.
12. Voice announcements use only confirmed phone-owned state.
13. Watch receives the phone-owned score state and updates its connected display.

The phone-to-watch score snapshot includes whether a phone-owned match is active and whether Undo is available. Connected watch controls are enabled only when the phone has an active match.

Connected Watch + Phone voice flow when Watch then Phone mode is selected:

1. Watch receives confirmed phone score state.
2. Watch announces the confirmed score immediately.
3. Phone announces the same confirmed score approximately two seconds later.

Phone Only voice flow when Phone only mode is selected:

1. Phone state updates after rally input or undo.
2. Phone announces the confirmed score immediately.

### Watch Only Data Flow

1. User chooses first server on the watch.
2. User taps A WON or B WON on the watch.
3. Watch-local state stores previous state in history.
4. Watch asks `PickleballScoringEngine.recordRallyWinner`.
5. Watch Compose redraws and announces the score locally.

The standalone Wear flow is valid for Watch Only mode. Connected Wear flow must keep the phone as source of truth even when rally input originates on the watch.

Connected Wear mode may speak only after confirmed phone state is received. It must never speak a predicted score.

## Watch Command Contract

The target watch-to-phone command surface should stay intentionally small:

- `TeamAWonRally`
- `TeamBWonRally`
- `Undo`

Commands represent user intent. The phone converts commands into scoring changes.

## Wear OS Data Layer

Current implementation uses:

- `MessageClient` for immediate watch-to-phone commands.
- `DataClient` for latest phone-to-watch score state.
- Wear listener services on both modules for background delivery.
- Basic Android logs tagged around command send/receive and score-state publish/receive.
- `match_active` and `can_undo` fields in phone score snapshots to protect phone setup state and disable invalid Undo actions.

The shared module owns only command/path/key constants and scoring domain types. Android Data Layer code remains in `app` and `wear`.

## Display Mode

Display mode is passive during a match. For MVP, simple display mirroring is acceptable. It should show:

- Team scores.
- Serving team.
- Server number.

No scoring, undo, setup, reset, or end controls should appear on the external display surface during live play.

## State Management

`GameState` is immutable. Each rally creates a new state.

Undo is implemented by keeping prior `GameState` values in a list for the current match. Undo is unlimited within the in-memory match session.

## Text Input Notes

Setup name input normalizes names to uppercase and replaces line breaks with spaces.

Do not reintroduce automatic focus jumps from Team A to Team B without device testing. Samsung keyboard plus Compose focus previously crashed with:

```text
IllegalArgumentException: ActiveParent with no focused child
```

Current behavior: pressing Enter/Done does not auto-focus the next field.

## Known Technical Gaps

- `GameStatus.Complete` exists, but current timed-play behavior does not automatically complete games.
- Rally history is not persisted across process death.
- TTS voice selection uses best available English voice from Android; no in-app voice picker exists.
- Voice settings implementation may lag the target modes; target MVP modes are Off, Phone only, Watch only, and Watch then Phone.
- Wear app has an initial phone sync path, but real-device pairing/reconnect behavior still needs hardening.
- Wear app currently contains prototype scoring logic; target watch control must move scoring authority back to the phone.
- External display has no dedicated presentation mode.
