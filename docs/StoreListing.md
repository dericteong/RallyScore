# Google Play Store Listing — PickleCast

Draft copy for the Play Console listing. Character limits noted per field.

---

## App title (max 30 chars)

```
PickleCast: Pickleball Score
```
*(28 chars. Keeps the brand first, adds the "pickleball" keyword for search. The
phone launcher label stays "PickleCast" via `app_name` — this title is Play-only.)*

Alternatives:
- `PickleCast — Pickleball` (23)
- `PickleCast Pickleball Scorer` (28)

---

## Short description (max 80 chars)

```
Pickleball scoreboard for watch, phone & tablet. Score every rally, hands-free.
```
*(79 chars.)*

Alternatives:
- `Pickleball scoreboard & score caller for Wear OS, phone and tablet.` (66)
- `Score pickleball from your watch. Phone & tablet scoreboard with voice calls.` (76)

---

## Full description (max 4000 chars)

```
PickleCast is a flexible pickleball scoreboard that works the way you play —
from a Wear OS watch, an Android phone, a courtside tablet, or all of them
together. Add devices to enhance the experience; none of them are mandatory.

Wear. Play. Score.

SCORE FROM YOUR WRIST
Tap "ME WON" or "OPP WON" straight from your Wear OS watch while you play — no
need to walk to a scoreboard. Undo a mistap instantly, feel haptic confirmation,
and hear the score called out loud.

A SCOREBOARD FOR EVERY SETUP
• Watch Only — casual, minimal-setup scoring right on your wrist.
• Phone Only — a full-featured, first-class scorer and scoreboard.
• Tablet Only — drop a tablet courtside and let anyone tap the score.
• Watch + Phone — the phone runs the rules while the watch is your remote.
• Phone + Tablet — a big, glanceable shared scoreboard synced to your phone.

KNOWS THE RULES SO YOU DON'T HAVE TO
PickleCast handles doubles side-outs, server number changes, and the correct
score-call order for you. Choose your format at setup:
• Traditional — standard side-out scoring (only the serving team scores).
• Rally — a fast, social variant where every rally awards a point, while two
  serves and side-out flow stay familiar.

HANDS-FREE VOICE CALLS
Text-to-speech announces the score after every rally, with multiple
announcement modes so the right device speaks at the right time across your
watch, phone, and tablet.

BUILT FOR THE COURT
• Big, high-contrast scores that stay readable in bright outdoor sun.
• Large, forgiving tap targets for scoring mid-rally.
• Team A blue, Team B green — always clear who's who.
• Screen stays awake during a match.
• Unlimited undo and quick score correction.

PLAYER ROSTER MADE EASY
Save your regular players for fast setup, with searchable name selectors for
each team. Importing a group? Snap or select a screenshot of an attendee list
and PickleCast reads the names on-device and lets you review before adding them.

WORKS WITHOUT THE INTERNET
Phone-to-tablet scoreboard sync runs over your local Wi-Fi — or the phone's own
hotspot — so it keeps working on courts with no signal. On-device text
recognition means your player names never leave your device.

PRIVACY-FIRST
No account required. No ads. PickleCast doesn't sell your data — scoring and
player names stay on your own devices.

Whether you're playing a quick social game, running a club session, or setting
up a shared courtside display, PickleCast keeps scoring simple, glanceable, and
correct.

Wear. Play. Score.
```

---

## Notes for the listing metadata

- **Category:** Sports (alternative: Health & Fitness).
- **Tags/keywords to weave in naturally (Play has no keyword field — these live in
  the descriptions):** pickleball, scoreboard, scorekeeper, score counter, Wear OS,
  smartwatch, pickleball scorer, doubles, side-out, court, tablet scoreboard.
- **Contact email:** dericteong@gmail.com (or a dedicated support address).
- **Privacy policy:** REQUIRED before publishing. Must be a public URL. Given the
  app is offline/on-device (camera+ML Kit OCR local, local-network sync, no
  accounts), the policy should state that no personal data is collected or
  transmitted to servers.
- **Data Safety form:** likely "No data collected / No data shared." Review the
  camera + photo-picker usage (screenshot import) and local-network permissions
  when filling it out.
- **Screenshots needed (per Play requirements):** phone (2–8), and — if you list
  the Wear app — Wear OS screenshots. A 1024×500 feature graphic is required.

## Copyright / attribution
© 2026 Deric Teong (matches the in-app Set Up Game footer).

---

# Wear OS listing (same app, Wear form factor)

The Wear app ships in the **same** Play listing (shared `applicationId`). Play
lets the Wear OS form factor carry its own short/full description; this copy is
watch-focused to complement the phone listing above.

## Wear title (max 30 chars)

```
PickleCast: Pickleball Watch
```
*(28 chars.)*

## Wear short description (max 80 chars)

```
Score pickleball from your wrist — tap, undo, and hear the call, hands-free.
```
*(76 chars.)*

## Wear full description (max 4000 chars)

```
Keep scoring without leaving the court. PickleCast on Wear OS turns your watch
into a pickleball scorer you control from your wrist.

SCORE FROM YOUR WRIST
Big, color-coded "ME WON" and "OPP WON" buttons make scoring a glance-and-tap —
no reaching for a phone or scoreboard between rallies. Undo a mistap instantly,
and feel a haptic buzz confirming every point.

HEAR THE SCORE
Your watch calls the score out loud after each rally, so everyone on court knows
where things stand — hands-free.

TWO WAYS TO PLAY
• Watch Only — a complete standalone scorer. Set up, pick who serves first,
  choose Traditional or Rally scoring, and play. No phone required.
• Watch + Phone — use the watch as a fast remote while your Android phone runs
  the rules and drives a big courtside scoreboard. Tap on the wrist; the phone
  keeps the official score and can announce it too.

BUILT FOR THE COURT
• Round-screen-optimized layout with a large, color-coded score call.
• High-contrast black theme that stays readable in bright sun.
• Blue vs green team colors, side-out and serve tracking handled for you.

PRIVACY-FIRST
No account. No ads. Scoring stays on your devices.

PickleCast keeps scoring simple, glanceable, and correct — right from your wrist.

Wear. Play. Score.
```

Wear screenshots: `play-assets/watch-screenshots/` (Watch Only setup, Phone Mode
setup, connected scoreboard).

---

# Publishing configuration

- **Package name (`applicationId`):** `com.courtside.pickleball` — shared by the
  phone and Wear artifacts, which is what puts them in ONE Play listing.
  Permanent once published; cannot be changed.
- **One app, not two:** create a single Play Console app. Both bundles keep the
  same `applicationId` and live under one listing — only the tracks are separate.
- **Wear ships in its own dedicated Wear OS track.** Play has required this since
  August 2023 and rejects a release that mixes the Wear bundle in with the
  handheld one. Enable it via Release → Setup → Advanced settings → Form factors
  → Add form factor → Wear OS. The phone/tablet bundle and the Wear bundle are
  uploaded as two separate releases, each with its own tester list, review pass,
  and store-listing assets.
- **Version codes:** phone `1`, Wear `3`. Wear must stay distinct from AND higher
  than the phone on every future bump (a watch matches both artifacts and gets
  the highest applicable version code). Wear `2` was consumed by a discarded
  first upload — a `versionCode` is retired at **upload**, not at rollout, so
  gaps in the sequence are normal and codes can never be reused.
- **Free or paid: choose FREE.** No upfront download price. A Free app fully
  supports ads and in-app purchases.
  - Planned later: a **"Remove ads" in-app purchase** (a one-time managed IAP on
    top of the Free app) — this does NOT require the app to be "Paid."
  - Direction rule: Free → Paid is NOT allowed; Paid → Free is. Free is both the
    correct and the flexible choice.
  - When ads/IAP are added later: check "Contains ads", set up a Payments/merchant
    profile + the `remove_ads` product, and REVISE the Data Safety form and the
    privacy policy (both currently state no ads / no data collected).
