# RallyScore Technical Roadmap

This roadmap translates the current senior-architecture review into a staged technical plan for RallyScore.

The goal is to improve production readiness without losing RallyScore's core strengths:
- fast live scoring
- low cognitive load on court
- strong visibility across phone, tablet, and watch
- one authoritative score state per active match

The roadmap is organized into:
- Now
- Next
- Later

Each item includes:
- Problem
- Why it matters
- Recommended direction
- Risk if ignored

## Now

### 1. Harden Local Sync Trust Boundaries

**Problem**

RallyScore currently supports local-network command paths across phone, tablet, and watch fallback modes. Those paths are functionally useful, but they are not yet fully protected against unauthorized control commands from other devices on the same network.

**Why it matters**

Live scoring is the core product promise. If another device can accidentally or intentionally inject commands, the source of truth is no longer trustworthy. This is the most important production hardening area for a multi-device sports app used in uncontrolled real-world network environments.

**Recommended direction**

Protect control commands with lightweight pairing and command authorization while keeping discovery simple. Discovery traffic can remain easy to observe, but scoring, undo, resume, and end commands should only be accepted after explicit pairing.

**Risk if ignored**

Unexpected score changes, broken trust during matches, and higher support burden when multiple nearby devices share the same hotspot or Wi-Fi network.

### 2. Create a Proper Sync and Session State Machine

**Problem**

RallyScore now has several supported device combinations and reconnection paths. Connection behavior is spread across multiple code paths, which increases the chance of inconsistent transitions, UI flicker, stale state, or confusing recovery behavior.

**Why it matters**

Multi-device products become fragile when connection and session lifecycle are implicit. A clear state model reduces bugs and makes the user experience more predictable, especially when devices disconnect, reconnect, or switch roles mid-session.

**Recommended direction**

Define explicit lifecycle states for pairing, connection, active session, stale state, reconnection, and match end. Make sync code and UI derive behavior from those states instead of from scattered flags.

**Risk if ignored**

More reconnect bugs, inconsistent device behavior, harder debugging, and a growing chance that fixes in one mode break another mode.

### 3. Build a Contract-Tested Wire Protocol Layer

**Problem**

RallyScore already has multiple message and payload formats across Wear Data Layer, tablet sync, and watch-tablet fallback. Those protocols are functional, but the structure is not yet strongly contract-tested as a stable protocol surface.

**Why it matters**

As the app evolves, protocol drift becomes one of the easiest ways to break cross-device behavior. Strong contract coverage gives confidence that a change to one side of the system still matches the other side.

**Recommended direction**

Define versioned protocol expectations and add focused tests for serialized commands, score-state payloads, malformed payload handling, and backward-compatible parsing.

**Risk if ignored**

Silent regressions, fragile reconnect behavior, and hard-to-diagnose bugs where devices appear connected but stop understanding each other correctly.

### 4. Strengthen Automated Integration Testing

**Problem**

The shared scoring engine has valuable coverage, but multi-device workflows are now a large part of the product. Those workflows are more complex than pure scoring rules and need broader test support.

**Why it matters**

Most production issues in RallyScore are now likely to happen at the interaction boundary between transport, session state, and UI synchronization rather than inside the scoring engine alone.

**Recommended direction**

Expand test coverage around end-to-end sync scenarios such as watch-to-phone scoring, tablet-to-phone control, reconnect recovery, stale-session rejection, undo propagation, and end-match propagation.

**Risk if ignored**

Higher regression risk as new device behaviors are added, with manual testing becoming too expensive and too easy to miss edge cases.

## Next

### 5. Separate Transport Code from Match Orchestration

**Problem**

The shared scoring engine is cleanly separated, but sync orchestration logic is growing inside phone and device-specific sync classes. Over time, transport concerns and match coordination risk becoming too intertwined.

**Why it matters**

When transport handling and business coordination mix together, future changes become harder to reason about. This increases the cost of every bug fix and makes production hardening slower.

**Recommended direction**

Establish clearer boundaries between:
- scoring domain logic
- sync transport adapters
- session coordination
- UI-facing state projection

This should be done incrementally, preserving current architecture and behavior.

**Risk if ignored**

Rising complexity, slower iteration, and a codebase that becomes harder to safely extend as RallyScore adds more polished sync behavior.

### 6. Unify Persistence and Restore Semantics

**Problem**

RallyScore persists some match and identity state, but the long-term rules for what should restore across phone, tablet, and watch are not yet fully formalized as a product-wide contract.

**Why it matters**

Users care deeply about whether an active match, player names, selected court, connection target, and undo history survive app restarts and network interruptions. Inconsistent restore behavior creates confusion and undermines trust.

**Recommended direction**

Define and document a clear persistence model covering:
- active match state
- player names
- selected court or host
- paired device identity
- undo availability
- session reset behavior

Then align implementation across device roles.

**Risk if ignored**

Unexpected restore outcomes, inconsistent reconnect behavior, and more edge-case bugs after app relaunch or device recovery.

### 7. Add Structured Production-Safe Diagnostics

**Problem**

Basic logging exists and is now being hardened, but long-term production support will need better diagnostic structure than raw log strings.

**Why it matters**

A multi-device system benefits from coarse, structured observability even when logs must stay privacy-safe. Developers need enough signal to diagnose field issues without leaking score details, network details, or session internals.

**Recommended direction**

Introduce structured diagnostic categories, redacted identifiers, connection state counters, and optionally a simple developer-facing diagnostics screen for manual support testing.

**Risk if ignored**

Production issues become harder to diagnose, and the team may be forced to choose between over-logging and flying blind.

## Later

### 8. Introduce a Release-Grade Build and Delivery Process

**Problem**

RallyScore already has core release notes and checklists, but the overall delivery process is still early-stage compared with what a mature Play-distributed product needs.

**Why it matters**

Reliable releases are not only about code correctness. They depend on repeatable versioning, signing ownership, verification gates, and artifact discipline.

**Recommended direction**

Formalize release expectations for:
- versioning
- signing ownership
- bundle creation
- changelog discipline
- release verification
- pre-upload checks

This should complement the existing release documentation already added to the repo.

**Risk if ignored**

Higher risk of release mistakes, inconsistent store submissions, and operational confusion as distribution scales.

### 9. Improve Accessibility and Readability Systematically

**Problem**

RallyScore already emphasizes large text, high contrast, and glanceability, but a complete accessibility pass has not yet been treated as a dedicated technical milestone.

**Why it matters**

The product is intentionally aimed at fast, real-world use, including older players and outdoor environments. Formal accessibility work strengthens both compliance and practical usability.

**Recommended direction**

Run a focused accessibility and readability program covering:
- color contrast
- screen reader labels
- touch target sizing
- large-text resilience
- haptic consistency
- watch readability and interaction ergonomics

**Risk if ignored**

Reduced usability for real players, harder adoption by mixed-skill groups, and avoidable polish issues that become more expensive later.

### 10. Modernize Platform Plumbing Incrementally

**Problem**

The app still contains some platform-level debt such as deprecated APIs, older splash handling, and future Gradle/AGP upgrade work.

**Why it matters**

These items do not define the product experience today, but they gradually increase maintenance cost and can become release blockers if left too long.

**Recommended direction**

Address platform modernization incrementally over time, including:
- deprecated Android API cleanup
- Android 12+ SplashScreen API adoption
- monochrome adaptive icon support
- Gradle and AGP upgrades
- general manifest and platform hygiene

**Risk if ignored**

Increasing maintenance drag, noisier builds, and higher future migration cost when platform changes eventually become mandatory.

## Summary

Recommended sequencing:

- **Now**
  - Harden local sync trust boundaries
  - Create a proper sync and session state machine
  - Build a contract-tested wire protocol layer
  - Strengthen automated integration testing

- **Next**
  - Separate transport code from match orchestration
  - Unify persistence and restore semantics
  - Add structured production-safe diagnostics

- **Later**
  - Introduce a release-grade build and delivery process
  - Improve accessibility and readability systematically
  - Modernize platform plumbing incrementally

The highest-leverage theme is clear: RallyScore is now a multi-device real-time system, not just a local scoreboard UI. The roadmap should prioritize trust, synchronization reliability, and supportability before broader polish work.
