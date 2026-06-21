# Scoring Rules

## Scope

The app implements standard doubles pickleball side-out scoring through rally-winner input.

## Starting State

Every game starts at:

```text
0 - 0 - 2
```

Meaning:

- Serving team score: 0
- Receiving team score: 0
- Server number: 2

The first serving team uses the first-server exception, so the first lost rally by the starting serving team creates an immediate side out.

## Rally Input

The user records only which team won the rally.

- If the serving team wins the rally, that team scores one point.
- If the receiving team wins the rally, no point is scored.
- If Server 1 loses a rally, serve moves to Server 2 on the same team.
- If Server 2 loses a rally, side out occurs and the opponent becomes serving team as Server 1.
- During the first-server exception, the starting serving team begins as Server 2, so losing the first rally causes side out.

## Score Call

The score call is always:

```text
serving team score - receiving team score - server number
```

Example:

```text
8 - 6 - 2
```

If Team A has 8, Team B has 6, Team A is serving, and the current server is Server 2, the call is `8 - 6 - 2`.

If side out changes serving to Team B with the same raw scores, the call becomes:

```text
6 - 8 - 1
```

## Current Timed-Play Rule

Current product behavior allows scores to continue beyond 11 because many social and club games are played by timer.

The domain model has a `targetScore` and `winBy` setting plus `GameStatus.Complete`, but the current engine does not automatically end the game when a score reaches 11.

## Serving Player

`GameState.servingPlayerName()` returns the name of the player currently serving based on a simplified fixed-position model:

- **Team A (right side):** P1 is the right-side player (Server 1), P2 is the left-side player (Server 2).
- **Team B (left side):** P3 is the left-side player (Server 2), P4 is the right-side player (Server 1).

Server 1 is always the right-side player. Server 2 is always the left-side player. The first-server exception makes Server 2 act as Server 1 at game start, so the effective server is Server 1 when `isFirstServerException` is true.

## Court-Ordered Team Names

`GameState.courtOrderedTeamName(team)` returns player names in order matching current court positions. On even scores the names are shown in default order (P1 & P2 for Team A, P3 & P4 for Team B). On odd scores the names are swapped (P2 & P1, P4 & P3) to reflect the switched court positions after a point.

## Undo

Undo reverses the complete previous rally state, including:

- Team A score
- Team B score
- Serving team
- Server number
- First-server exception flag
- Side-out changes

Undo is unlimited within the current in-memory match.
