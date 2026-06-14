# User Stories

## Primary Player/Watch Flow

- As an active player wearing a watch, I want to tap the team that won the rally so I do not need to think about side outs or server number.
- As an active player wearing a watch, I want large, simple watch controls so I can score between rallies without slowing the game.
- As an active player wearing a watch, I want spoken or haptic confirmation so I know the rally input was accepted.
- As an active player wearing a watch, I want undo from my wrist so I can quickly fix a wrong tap.
- As an active player wearing a watch, I want the watch to reconnect and resync if it briefly disconnects from the phone.
- As an active player wearing a watch, I want the watch to send only simple commands so the phone remains the rules expert.
- As an active player wearing a watch, I want the watch to announce only confirmed phone-owned scores when connected.

## Watch Only

- As a casual player, I want to use only my Wear OS watch so I can score a game with minimal setup.
- As a player without my phone nearby, I want the watch to choose first server, record rallies, undo, display score, and speak the score.
- As a demo user, I want Watch Only mode to keep working even when no phone is connected.

## Phone Hub And Setup

- As a player, I want to enter both teams' player names before a game so the scoreboard is easy for players to identify.
- As a player, I want to select which team serves first from the phone during setup.
- As a player, I want the setup screen to stay usable when the keyboard is open so I can enter names on a real phone.
- As a player, I want the phone to remain the match source of truth so the watch and shared display agree.
- As a player, I want the phone to calculate score, server transitions, side outs, and undo history so I never manage those manually.
- As a player, I want Phone Only mode to be excellent even when I do not use a watch.

## Live Scoring

- As a first-time user, I want the current score to be obvious without reading instructions.
- As a player on court, I want the score to be readable from a shared display.
- As a player on court, I want the display to update automatically after the watch user records a rally outcome.
- As a player, I want the score called out after a rally so everyone knows the current call.
- As a player on court, I want the phone speaker to announce confirmed score updates so all players can hear the call.
- As a player on court, I want to turn voice announcements off when they are not needed.
- As a player on court, I want connected Watch + Phone mode to announce first from the watch and then repeat from the phone so the watch user gets immediate feedback and everyone else can hear.
- As a player, I want the scoreboard to show the serving score first so it matches the official score call.
- As a player, I want server number shown clearly so I can verify whether the call is `1` or `2`.
- As a player, I want serving dots on the active serving team so the server is easy to spot.
- As a player, I want the first server exception handled automatically so the game starts correctly at `0 - 0 - 2`.

## Phone Only

- As a player without a connected watch, I want to score directly on the phone as a first-class experience.
- As a volunteer or referee, I want phone controls to remain usable for courtside scoring when needed.

## Tablet Only

- As a player, I want to set up a match directly on a tablet so I can use RallyScore without a phone or watch.
- As a social player, I want a tablet placed courtside so any player can tap the rally winner between points.
- As a player, I want tablet scoring controls to be large and readable outdoors.
- As a player, I want tablet Undo and correction tools so mistakes can be fixed without restarting the match.
- As a player, I want tablet voice announcements so everyone can hear the confirmed score.
- As a player, I want Tablet Only mode to use the same scoring rules as Phone Only mode.

## Corrections

- As a player, I want undo to restore scores, serving team, server number, and side outs together.

## Timed Play

- As a club player, I want scores to continue beyond 11 because some games are run by a timer rather than a target score.

## Game End

- As a player, I want an End control with confirmation so I do not accidentally clear a game.

## Shared Display

- As a player, I want to show the scoreboard on a portable monitor, large screen, or Android tablet so everyone on court can see the score clearly.
- As an organizer, I want the shared display to avoid accidental touch controls when it is only being used as the scoreboard.
- As a player, I want the shared display to show only team scores, serving team, and server number during a match.
- As a player, I want the tablet to follow the phone-owned score automatically so I can keep scoring from the watch without touching the tablet.

## Future Phone + Tablet Sync

- As a player, I want the phone and tablet to share the same match so scoring from either device updates the other.
- As a player wearing a watch, I want watch input to update the phone and tablet without creating conflicting match states.
- As a player, I want the app to prevent two unsynchronized devices from independently scoring the same live match.
