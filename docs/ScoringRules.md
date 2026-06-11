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

## Undo

Undo reverses the complete previous rally state, including:

- Team A score
- Team B score
- Serving team
- Server number
- First-server exception flag
- Side-out changes

Undo is unlimited within the current in-memory match.
