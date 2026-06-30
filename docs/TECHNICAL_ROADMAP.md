# RallyScore Technical Roadmap

This roadmap translates the current RallyScore codebase review into a practical
12-month technical plan.

It focuses on:
- scalability
- maintainability
- performance
- testing
- Google Play readiness
- future premium feature foundations

The goal is to keep RallyScore reliable during live play while preparing it for
broader production use across Watch Only, Phone Only, Tablet Only, Watch +
Phone, Phone + Tablet, and Watch + Phone + Tablet experiences.

## Must Do

### 1. Secure Local Control Commands

**Problem**

RallyScore supports local-network control paths across phone, tablet, and
watch-tablet fallback, but long-term command trust is not yet strong enough for
shared hotspot or Wi-Fi environments.

**Why it matters**

Live scoring trust is the product. If the wrong nearby device can inject score,
undo, resume, or end commands, RallyScore loses credibility immediately.

**Recommended direction**

Add lightweight pairing and session protection for all control commands while
keeping discovery simple and quick for real pickleball use.

**Risk if ignored**

Unexpected score changes, broken trust on shared networks, and higher support
burden in real-world multi-court environments.

### 2. Formalize Sync and Session State Machines

**Problem**

RallyScore now has several supported device combinations and reconnect paths,
but connection lifecycle behavior is still spread across multiple sync classes
and UI decisions.

**Why it matters**

Explicit state models reduce flicker, stale-state bugs, confusing reconnect
behavior, and regression risk as device combinations grow.

**Recommended direction**

Define clear states for discovery, pairing, connected, reconnecting, active
match, stale session, and ended session, then make sync logic and UI derive
from those states.

**Risk if ignored**

Growing reconnect instability, harder debugging, and more cases where one fix
breaks another device path.

### 3. Expand Automated Multi-Device Sync Testing

**Problem**

The shared scoring engine has useful coverage, but RallyScore's main complexity
now sits in cross-device state synchronization rather than pure scoring rules.

**Why it matters**

The highest-risk regressions are now watch-to-phone scoring, tablet-to-phone
control, reconnect behavior, undo propagation, and stale-session handling.

**Recommended direction**

Add focused tests for:
- watch to phone scoring
- tablet to phone scoring
- reconnect and restore
- stale-session rejection
- undo propagation
- end-match propagation
- correction propagation

**Risk if ignored**

Manual testing cost rises, sync regressions become easier to miss, and release
confidence drops as features accumulate.

### 4. Contract-Test Sync Protocol Payloads

**Problem**

RallyScore already uses multiple wire formats across Wear Data Layer, phone to
tablet sync, and watch to tablet fallback, but those contracts are not yet
strongly protected by protocol-level tests.

**Why it matters**

As the product evolves, protocol drift is one of the easiest ways to create
silent multi-device breakage.

**Recommended direction**

Add contract tests for message serialization, parsing, malformed payload
handling, versioning assumptions, and backward-compatible parsing.

**Risk if ignored**

Devices may appear connected while quietly failing to understand one another
after future changes.

### 5. Finish Google Play Production Baseline

**Problem**

Release-readiness documentation now exists, but Play submission discipline,
final manifest review, policy review, asset readiness, and release ownership
still need to be treated as an operational baseline.

**Why it matters**

A working app is not automatically release-ready. Play distribution needs
repeatable release mechanics and clear ownership.

**Recommended direction**

Complete the production baseline for:
- release process
- versioning discipline
- upload-key ownership
- store assets
- privacy/data-safety answers
- final exported-component and manifest review

**Risk if ignored**

Higher chance of release mistakes, submission friction, and operational confusion.

### 6. Harden Wear Reliability and Battery Behavior

**Problem**

The watch is now central to RallyScore's product experience, but it remains the
most constrained device for reconnect behavior, battery life, background sync,
and glanceability under long social-play sessions.

**Why it matters**

If the watch feels flaky, drains too quickly, or reconnects poorly, the core
product promise weakens even if the phone and tablet behave well.

**Recommended direction**

Continue focused hardening around:
- connection stability
- idle-state battery use
- graceful reconnect behavior
- long-match resilience
- real-device Wear testing

**Risk if ignored**

Strong phone/tablet behavior will still be overshadowed by a fragile primary
controller experience.

## Should Do

### 1. Separate Transport Orchestration from UI-Facing Coordination

**Problem**

The shared scoring engine is well isolated, but sync transport, session
coordination, and UI-facing behavior are gradually becoming more intertwined in
the app and wear modules.

**Why it matters**

Cleaner boundaries reduce the cost of future changes and make multi-device bugs
safer to diagnose and fix.

**Recommended direction**

Incrementally separate:
- scoring domain logic
- transport adapters
- session orchestration
- UI state projection

**Risk if ignored**

Maintainability slows down, architectural confidence drops, and future sync
changes become riskier than they need to be.

### 2. Unify Persistence and Restore Semantics

**Problem**

RallyScore already persists some match and identity state, but the long-term
rules for what should restore across phone, tablet, and watch are not yet fully
formalized as a product-wide contract.

**Why it matters**

Users care about whether active matches, player names, paired targets, selected
courts, and undo behavior survive process death and network interruptions.

**Recommended direction**

Define and document consistent restore semantics for:
- active match state
- player names
- scoring format
- paired device identity
- court selection
- undo availability
- session reset behavior

**Risk if ignored**

Restore behavior stays useful but inconsistent, especially after app relaunch
or reconnect scenarios.

### 3. Run a Performance Pass on Phone and Tablet Sync

**Problem**

The local sync layer is functionally useful, but there is still room to reduce
snapshot churn, discovery noise, and idle network activity.

**Why it matters**

Improved efficiency helps battery life, hotspot stability, and long-match
performance, especially on older devices.

**Recommended direction**

Review and trim unnecessary:
- snapshot publishing
- broadcast frequency
- reconnect chatter
- idle-state refresh loops

**Risk if ignored**

The app may remain correct but feel heavier than necessary on real hardware.

### 4. Complete an Accessibility and Readability Pass

**Problem**

RallyScore already favors large scores and high contrast, but accessibility is
not yet treated as a dedicated engineering milestone.

**Why it matters**

The product is intentionally meant for outdoor use, mixed-age groups, and fast
glanceability under movement and lighting changes.

**Recommended direction**

Audit and improve:
- color contrast
- touch target sizes
- screen-reader labels
- large-text resilience
- haptic consistency
- readability on watch, phone, and tablet

**Risk if ignored**

The app may work well for the core developer test devices but still have avoidable
usability issues for real players.

### 5. Add Structured Production-Safe Diagnostics

**Problem**

Logging has been improved, but long-term support will still benefit from more
structured and privacy-safe diagnostics than raw log statements alone.

**Why it matters**

Multi-device sync issues are much easier to support when the app exposes enough
redacted signal to diagnose connection and session problems.

**Recommended direction**

Introduce:
- structured diagnostic categories
- redacted connection identifiers
- basic counters or state summaries
- optionally a lightweight developer diagnostics screen

**Risk if ignored**

Production issues become harder to debug without falling back to noisy or risky
logging.

### 6. Formalize a Manual Device Test Matrix

**Problem**

RallyScore behavior is highly device-specific across Samsung, Huawei, Lenovo,
emulators, and physical Wear OS hardware.

**Why it matters**

A repeatable device matrix lowers regression risk for setup, sync, readability,
reconnect, and voice behavior.

**Recommended direction**

Maintain a stable smoke suite across representative:
- phones
- tablets
- Wear devices
- hotspot and Wi-Fi environments

**Risk if ignored**

The team may keep passing builds that behave differently on real-world hardware.

## Nice to Have

### 1. Add Premium-Ready Team and Player Presets

**Problem**

Repeated setup entry still costs time during social play and testing.

**Why it matters**

Saved presets are a natural convenience upgrade and a strong premium foundation.

**Recommended direction**

Support reusable team/player presets and fast setup templates.

**Risk if ignored**

Setup friction remains acceptable but repetitive, and a valuable premium path is delayed.

### 2. Add Match History and Summaries

**Problem**

Completed matches currently end without forming a longer-term player record.

**Why it matters**

History adds user value and creates a clean bridge toward premium features and
club workflows.

**Recommended direction**

Store completed matches with metadata such as:
- date
- duration
- scoring format
- final score

**Risk if ignored**

RallyScore remains highly functional live, but less sticky after the match ends.

### 3. Expand Voice Announcement Options

**Problem**

The voice system already supports multiple devices and sequencing, but the
available options are still MVP-level.

**Why it matters**

Voice is one of RallyScore's most distinctive real-world usability features.

**Recommended direction**

Add optional enhancements such as:
- more announcement styles
- device-priority preferences
- repeat timing options
- venue-friendly speech tuning

**Risk if ignored**

The current system remains useful, but there is less differentiation and less
room for personalization.

### 4. Mature Synced Peer Controller Mode

**Problem**

Tablet-to-phone control works, but fully mature simultaneous peer-controller
behavior still needs explicit conflict handling and broader protection.

**Why it matters**

True synced controller mode is powerful, but only if it stays deterministic.

**Recommended direction**

Build conflict-aware phone and tablet control after sync hardening is complete.

**Risk if ignored**

The current command model remains viable, but richer shared-control workflows stay limited.

### 5. Polish Portable Monitor Support

**Problem**

Portable monitor use is already part of the product story, but dedicated
resolution/layout polish is still limited.

**Why it matters**

This mode matters for clubs and social settings where a larger shared view helps
all players.

**Recommended direction**

Tune layouts and behavior for common mirrored-display and monitor resolutions.

**Risk if ignored**

Portable monitor support remains workable but less polished than the core phone/tablet paths.

### 6. Add Battery Diagnostics or Low-Power Options

**Problem**

Long matches on Wear and older Android hardware benefit from explicit
low-power-aware behavior.

**Why it matters**

Battery performance is a practical product quality issue, especially for watch-led use.

**Recommended direction**

Consider optional low-power or reduced-refresh behaviors where they do not hurt
scoring trust.

**Risk if ignored**

Battery usage stays acceptable for some users but may still limit longer sessions.

## Future Ideas

### 1. Club and Venue Mode

Create a broader court-management experience with:
- reusable court identities
- saved setup profiles
- club-friendly match launch flows

This is a strong premium direction once the core sync foundation is mature.

### 2. Tournament Mode

Support more formal structured play such as:
- configurable game targets
- official win conditions
- best-of match tracking
- referee-friendly workflows

This should come after the current social-play-first product is fully stable.

### 3. Statistics and Analytics

Add player or venue insights such as:
- match counts
- duration trends
- scoring trends
- court activity summaries

This becomes more valuable after match history exists.

### 4. Cloud Backup and Account Sync

Add optional cloud-backed restore and cross-device continuity only after the
offline-first local sync model is robust and trusted.

This is a later-stage capability, not a current need.

### 5. Additional Spectator or Display Clients

Extend RallyScore beyond the current mirrored/tablet model into broader passive
display surfaces for spectators or venues.

This should follow stronger controller and session hardening.

### 6. Billing and Premium Packaging

Define a sustainable packaging model such as:
- free core live scoring
- paid convenience features
- paid club/admin features

This should be introduced after the core product earns trust through stability.

## Recommended 12-Month Sequence

### Quarter 1

- Secure local control commands
- Add protocol contract tests
- Formalize sync and session states
- Continue Wear reliability and battery hardening

### Quarter 2

- Expand multi-device integration coverage
- Unify persistence and restore behavior
- Finish Google Play production baseline
- Add structured diagnostics improvements

### Quarter 3

- Clean up orchestration and transport boundaries
- Run a performance pass on phone and tablet sync
- Complete accessibility and readability review
- Improve portable monitor polish

### Quarter 4

- Build premium foundations such as presets and match history
- Start early club/venue capabilities
- Decide whether synced peer-controller polish or premium packaging is the next
  higher-priority step
