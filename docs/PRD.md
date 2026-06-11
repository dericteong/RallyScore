# Product Requirements Document

## Product Name

Portable Courtside Pickleball Scoreboard

## Problem

Pickleball games often need a clear, portable scoreboard that can be operated by a player, volunteer, referee, or organizer while play is happening. Existing manual scoreboards require physical handling, and generic scoring apps often require the user to understand side outs, server number changes, and score-call order.

The operator should only need to record who won the rally. The app should handle pickleball scoring mechanics.

## Target Users

- Pickleball players running social or club games.
- Referees and volunteers scoring from courtside.
- Coaches or event organizers using a phone connected to a portable display.
- Future: watch users who want quick score control from their wrist.

## MVP Goals

- Single-phone Android app.
- Landscape-first, courtside-readable scoreboard.
- Standard doubles pickleball scoring.
- Setup screen for Team A and Team B player names, with two player fields per team.
- Select which team serves first before starting.
- Start score call is `0 - 0 - 2`.
- User records rally winner only.
- App automatically updates points, serving team, server number, and side outs.
- Undo reverses the full previous rally.
- End game returns to setup after confirmation.
- Score calls are displayed and spoken after rally input.
- Offline operation.
- Keep the phone screen awake during use.

## Current Behavior

- The phone app uses a table-style score screen.
- Team A is blue; Team B is green.
- Each team row displays both player names joined with `&`.
- Serving dots show current server count on the serving team row:
  - One dot means Server 1.
  - Two dots means Server 2.
- Tapping Team A score records Team A as rally winner.
- Tapping Team B score records Team B as rally winner.
- The call bar displays serving score first, receiving score second, server number third.
- Android Text-to-Speech announces the call after rally input.
- Scores continue beyond 11 for timed games.

## Non-Goals For Current MVP

- Cloud sync.
- Accounts.
- Tournament bracket management.
- Remote Wear-to-phone control.
- External display optimization beyond Android's normal screen mirroring.
- Custom voice packs.
- Full match history persistence after app restart.

## Success Criteria

- A first-time user can understand scoring input within 30 seconds.
- The scoreboard is readable from courtside distance.
- The operator does not manually manage side outs or server number.
- Undo reliably restores the previous full rally state.
- The app can run offline and keep the display awake.
