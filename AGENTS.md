# Agent Instructions

This file is the working contract for AI/code agents contributing to RallyScore.

## Prime Directive

Protect the live player-controlled scorekeeping experience across RallyScore's supported device combinations. RallyScore must work correctly as Watch Only, Phone Only, Watch + Phone, and Watch + Phone + shared display. Additional devices enhance the experience but are not mandatory. Changes must prioritize scoring correctness, low cognitive load, glanceability, tap reliability during play, outdoor readability, and reliable synchronized state whenever multiple devices are present.

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
- Pattern: MVVM on phone, standalone state on Wear for Watch Only mode, Wear Data Layer sync for connected Watch + Phone mode
- Tests: JUnit unit tests for shared scoring and phone ViewModel

## Development Rules

- Do not modify source code when the user asks for documentation only.
- Keep scoring logic in `shared/` unless there is a strong reason otherwise.
- Keep Android UI logic out of the pure Kotlin scoring engine.
- Prefer explicit rally-winner input over manual score/server controls.
- Treat Phone Only as a first-class experience.
- Treat Watch Only as a valid casual/demo/backup mode where the watch may be source of truth.
- Treat Watch + Phone as the canonical connected architecture: watch remote control, phone source of truth.
- Treat the phone as the source of truth whenever a phone is present.
- Keep watch in-match controls extremely simple: Team A won rally, Team B won rally, Undo.
- Do not put scoring rules, server transitions, side-out logic, or authoritative match state in connected watch mode.
- Preserve existing standalone watch scoring unless intentionally changing Watch Only mode.
- Keep external/shared display mode passive during live play; do not show controls there.
- Do not reintroduce automatic focus jumps between setup text fields without testing on a real Samsung phone; this previously caused a Compose focus crash.
- Preserve landscape readability on phone.
- Keep score text and tap targets large.
- Keep team colors consistent: Team A blue, Team B green.
- Keep table dividers neutral, not team-colored.
- Do not implement ad hoc watch-phone sync; use an explicit pairing/synchronization design when adding remote control.
- For connected voice announcements, both watch and phone must announce only confirmed phone-owned state. The watch may announce immediately after confirmed state sync; the phone may repeat the same score shortly after.

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
