# Agent Instructions

This file is the working contract for AI/code agents contributing to Portable Courtside Pickleball Scoreboard.

## Prime Directive

Protect the live scorekeeping experience. This app is used while people are watching a rally, so changes must prioritize readability, tap reliability, scoring correctness, and low cognitive load.

## Source Of Truth

Use these documents before making product or architecture changes:

- `docs/PRD.md` - product goals and MVP scope.
- `docs/TDD.md` - technical design.
- `docs/ScoringRules.md` - scoring behavior.
- `docs/Wireframes.md` - screen structure.
- `docs/Roadmap.md` - phase plan.
- `docs/UserStories.md` - user needs.
- `docs/Architecture.md` - module and data-flow decisions.
- `docs/TestingChecklist.md` - manual and automated verification.
- `docs/ReleaseNotes.md` - shipped changes and known gaps.

If implementation and docs disagree, inspect the current code, fix the docs or code intentionally, and call out the mismatch.

## Current Architecture

- Phone app: `app/`
- Wear OS app: `wear/`
- Shared scoring engine: `shared/`
- Language: Kotlin
- UI: Jetpack Compose
- Pattern: MVVM on phone, standalone state on Wear prototype
- Tests: JUnit unit tests for shared scoring and phone ViewModel

## Development Rules

- Do not modify source code when the user asks for documentation only.
- Keep scoring logic in `shared/` unless there is a strong reason otherwise.
- Keep Android UI logic out of the pure Kotlin scoring engine.
- Prefer explicit rally-winner input over manual score/server controls.
- Do not reintroduce automatic focus jumps between setup text fields without testing on a real Samsung phone; this previously caused a Compose focus crash.
- Preserve landscape readability on phone.
- Keep score text and tap targets large.
- Keep team colors consistent: Team A blue, Team B green.
- Keep table dividers neutral, not team-colored.
- Treat Wear OS remote control as future work unless explicitly implementing pairing/sync.

## Verification Expectations

For scoring changes, run:

```bash
./gradlew :shared:test :app:test
```

For phone UI changes, also run:

```bash
./gradlew :app:assembleDebug
```

For Wear UI changes, also run:

```bash
./gradlew :wear:assembleDebug
```

When a real device is connected, install and test the affected app on device. Confirm no crash logs after risky input or focus changes.

## Git And Workspace Safety

- The repo may have existing uncommitted work. Do not revert user changes unless explicitly requested.
- Keep changes scoped to the user request.
- Avoid unrelated refactors.
- Do not delete screenshots or generated files unless the user asks.
