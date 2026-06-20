# Basketball Rotation

Basketball Rotation is an Android app for coaches to manage roster rotation, live game events, and post-game exports.

It is built with Kotlin, Jetpack Compose, Room, and an optional Gemini-powered training assistant.

## Project Status

- Single-module Android app (`:app`)
- UI: Jetpack Compose + Material 3
- Persistence: Room database (schema version 8)
- AI drills: Google Generative AI (`gemini-2.5-flash`) using `GEMINI_API_KEY` from `local.properties`

## Core Features

- Team roster management
  - Add, edit, remove players
  - Optional jersey numbers
  - CSV export and CSV import
- Game management
  - Create/select/delete games
  - Flexible periods (quarters or halves) with configurable duration
  - Starting lineup selection (5 players)
  - Substitutions (multi-sub support)
  - Live countdown clock with start/stop/reset and manual time set
  - Team and opponent score adjustments
  - Foul logging and MVP vote tracking
  - Attendance management (mark players absent per game)
  - Coaching notes per game with TXT export
  - Edit/delete recorded events
  - End game workflow
- Exports
  - Roster CSV
  - Game events CSV
  - Coaching notes TXT
- Adaptive layout
  - Bottom navigation for compact widths
  - Navigation rail and split panels for larger screens
- Training tab
  - Age-group drill generation via Gemini
  - “Coming Soon” placeholders for future training modules

## Tech Stack

- Kotlin
- Android Gradle Plugin 9.2.1
- Gradle 9.4.1 wrapper
- Java 17 target
- Jetpack Compose BOM `2026.06.00`
- Lifecycle ViewModel
- Room `2.8.4` + KSP
- Google Generative AI client `0.9.0`

## Requirements

From the current Gradle config:

- `compileSdk = 37`
- `targetSdk = 37`
- `minSdk = 26`
- JDK 17
- Android Studio with SDK 37 installed

## Setup

1. Clone/open the project in Android Studio.
2. Ensure JDK 17 is configured for Gradle.
3. Add your optional Gemini key to `local.properties` (project root):

```properties
GEMINI_API_KEY=your_key_here
```

If you do not set `GEMINI_API_KEY`, the app still builds, but AI drill generation in the Training tab will show an error message.

## Run Locally

Use the Gradle wrapper from the project root:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

Install/debug via Android Studio, or:

```powershell
.\gradlew.bat installDebug
```

## Verified Check

The local unit test task has been executed successfully in this workspace:

- `./gradlew.bat --no-daemon test` -> **BUILD SUCCESSFUL**

Current automated tests now include focused unit checks for event parsing and event-description formatting, but overall behavioral coverage is still limited.

## High-Level Architecture

- Entry and navigation
  - `app/src/main/java/com/example/basketballrotation/MainActivity.kt`
- UI screens
  - `app/src/main/java/com/example/basketballrotation/ui/RosterScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/GameScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/TrainingScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/SettingsScreen.kt`
  - Shared UI components: `app/src/main/java/com/example/basketballrotation/ui/Components.kt`
- State / view models
  - `app/src/main/java/com/example/basketballrotation/ui/AppViewModel.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/TrainingViewModel.kt`
- Data layer
  - Entities: `app/src/main/java/com/example/basketballrotation/data/Entities.kt`
  - DAOs: `app/src/main/java/com/example/basketballrotation/data/Dao.kt`
  - Repository: `app/src/main/java/com/example/basketballrotation/data/BasketballRepository.kt`
  - Room DB: `app/src/main/java/com/example/basketballrotation/data/AppDatabase.kt`
  - Exported schemas: `app/schemas/com.example.basketballrotation.data.AppDatabase/`

## Data Model Summary

- `players`: roster entries (`name`, optional `jerseyNumber`)
- `games`: game metadata + state (score, timer, period config, attendance CSV, notes)
- `game_events`: timeline entries (lineup, subs, team score, opponent score, fouls, MVP votes, period change, game end)

## CSV/TXT Notes

- Roster CSV columns:
  - `id,name,jerseyNumber`
- Game CSV columns include game metadata and event detail:
  - `gameId,gameTitle,opponent,createdAt,teamScoreAtExport,gameNotes,...`
- Notes export is plain text and includes game header + notes body.

## Known Risks / Review Notes

- Destructive migration is still enabled for downgrades in `app/src/main/java/com/example/basketballrotation/data/AppDatabase.kt`; this is intentional safety behavior but will clear local data if the app is downgraded.
- Test coverage remains partial; critical game-flow logic (timer behavior, substitution rules, scoring edits across full flows, import parser edge cases) needs deeper unit/instrumentation coverage.

## Roadmap (from project history)

See `DEVELOPMENT_HISTORY.txt` for version-by-version details through `v2.1`, including adaptive layouts, attendance support, roster import, and AI training enhancements.

## License

No license file is currently present in this repository.
If you plan to distribute this project, add a `LICENSE` file and update this section.

