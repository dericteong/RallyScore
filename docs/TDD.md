# Technical Design Document

## Overview

The project is a Kotlin multi-module Gradle Android project.

- `app`: phone Android application.
- `wear`: Wear OS application prototype.
- `shared`: pure Kotlin scoring domain shared by phone and Wear.

The phone app uses Jetpack Compose and a ViewModel with `StateFlow`. The shared module owns scoring rules so they can be tested without Android.

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

### `app`

Contains:

- `MainActivity`
- `ScoreboardViewModel`
- `ScoreboardApp` and phone Compose UI

Responsibilities:

- Keep screen awake.
- Force landscape orientation through manifest.
- Manage match setup state.
- Expose game state to Compose.
- Maintain rally history for undo.
- Provide Text-to-Speech score calls.
- Render large table-style scoreboard and controls.

### `wear`

Contains:

- Wear launcher activity.
- Standalone Wear Compose UI.
- Local state and undo history.
- Shared scoring engine usage.

Current Wear behavior is standalone. It does not control or sync with the phone app yet.

## Data Flow

1. User sets Team A and Team B names.
2. User selects starting serving team.
3. Phone ViewModel creates a `GameState`.
4. User taps Team A or Team B score.
5. ViewModel stores the previous state in history.
6. ViewModel asks `PickleballScoringEngine.recordRallyWinner`.
7. Compose redraws from `StateFlow`.
8. Phone app announces `servingScore receivingScore serverNumber` using TTS.

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
- Wear app does not yet sync with phone.
- External display has no dedicated presentation mode.
