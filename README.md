# Basketball Rotation

Basketball Rotation is a coach-focused Android app for managing team rosters, tracking live game events, and exporting post-game data.

The app is built with Kotlin, Jetpack Compose, Room, and an optional Gemini-powered training assistant. It is designed for local, in-season use and keeps the workflow lightweight: roster setup, live substitutions, score tracking, attendance, and exportable game records.

## Project status

- Single-module Android app (`:app`)
- UI: Jetpack Compose + Material 3
- Persistence: Room database (schema version 9)
- AI drills: Google Generative AI (`gemini-2.5-flash`) using `GEMINI_API_KEY` from `local.properties`
- Build status: validated with the existing Gradle test task (`BUILD SUCCESSFUL`)

## Core features

- Roster management
  - Add, edit, and remove players
  - Optional jersey numbers
  - CSV import and CSV export
- Game management
  - Create, select, and delete games
  - Flexible periods (quarters or halves) with configurable durations
  - Starting lineup selection
  - Live substitutions and multi-sub workflows, with numbered badges showing which bench player is paired with which outgoing court player
  - Countdown timer with start, stop, reset, and manual adjustment
  - Team and opponent score tracking
  - Foul logging and MVP vote tracking
  - Per-game attendance management
  - Coaching notes with TXT export
  - Event editing and deletion
  - End-game workflow
- Game history
  - Dedicated History tab listing all finished games, newest first
  - Win/loss/tie badge, final score, and end date/time per game
  - View (jumps to the read-only game on the Game tab), export, and delete actions
  - Finished games are removed from the active "Games" list on the Game tab once ended
- Exports
  - Roster CSV
  - Game event CSV
  - Coaching notes TXT
- Adaptive layouts
  - Compact layout with bottom navigation
  - Expanded screens with navigation rail and split panels
- Training tab
  - Age-group drill generation via Gemini
  - Coming-soon placeholders for future training modules

## Tech stack

- Kotlin
- Android Gradle Plugin 9.3.1
- Gradle wrapper 9.4.1
- Java 17
- Jetpack Compose BOM `2026.06.01`
- ViewModel / lifecycle patterns
- Room `2.8.4` + KSP
- Google Generative AI client `0.9.0`

## Requirements

From the current Gradle configuration:

- `compileSdk = 37`
- `targetSdk = 37`
- `minSdk = 26`
- JDK 17
- Android Studio with Android SDK 37 installed

## Setup

1. Clone or open the project in Android Studio.
2. Ensure JDK 17 is configured for Gradle.
3. Add your optional Gemini key to `local.properties` at the project root:

```properties
GEMINI_API_KEY=your_key_here
```

If `GEMINI_API_KEY` is not set, the app still builds, but the Training tab will show an error when generating AI drills.

## Running locally

Use the Gradle wrapper from the repo root:

```powershell
./gradlew.bat test
./gradlew.bat assembleDebug
```

To deploy directly to a connected device/emulator:

```powershell
./gradlew.bat installDebug
```

## Verified status

The current project state was validated in this workspace with:

- `./gradlew.bat --no-daemon test` -> `BUILD SUCCESSFUL`

The existing test coverage includes focused unit checks for parsing and event formatting, but the app still has limited end-to-end behavioral coverage compared with a full production QA suite.

## High-level project structure

- App entry
  - `app/src/main/java/com/example/basketballrotation/MainActivity.kt`
- UI screens
  - `app/src/main/java/com/example/basketballrotation/ui/RosterScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/GameScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/GameHistoryScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/TrainingScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/SettingsScreen.kt`
  - Shared components: `app/src/main/java/com/example/basketballrotation/ui/Components.kt` (includes `GameListCard`, used by both the Game and Game History screens)
  - Shared file-export launcher: `app/src/main/java/com/example/basketballrotation/ui/FileExport.kt`
- State and logic
  - `app/src/main/java/com/example/basketballrotation/ui/AppViewModel.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/TrainingViewModel.kt`
- Data layer
  - Entities: `app/src/main/java/com/example/basketballrotation/data/Entities.kt`
  - DAOs: `app/src/main/java/com/example/basketballrotation/data/Dao.kt`
  - Repository: `app/src/main/java/com/example/basketballrotation/data/BasketballRepository.kt`
  - Database: `app/src/main/java/com/example/basketballrotation/data/AppDatabase.kt`
  - Room schema exports: `app/schemas/com.example.basketballrotation.data.AppDatabase/`

## Data model summary

- `players`: roster entries (`id`, `name`, optional `jerseyNumber`)
- `games`: game metadata and state (score, timer, period configuration, absence list, notes, `isFinished` flag and `finishedAt` timestamp)
- `game_events`: event timeline entries (lineup, substitutions, team score, opponent score, fouls, MVP votes, period changes, game end)

## CSV and TXT notes

- Roster CSV columns include:
  - `id,name,jerseyNumber`
- Game export data includes metadata and event detail such as:
  - `gameId,gameTitle,opponent,createdAt,teamScoreAtExport,gameNotes,...`
- Coaching notes export is plain text and includes a header plus the game notes body.

## Known notes and risks

- Destructive migration is intentionally enabled for downgrades in `AppDatabase.kt`; this protects the normal upgrade workflow but will clear local data if the app is downgraded.
- Automated coverage is still limited for broader game-flow logic, timer edge cases, and parser validation.
- The project is local-first and does not currently include a backend, cross-device sync, or account-based auth system.

## Roadmap and history

See `DEVELOPMENT_HISTORY.txt` for the version-by-version background leading up to the current project state.

## License

There is currently no license file in this repository. If you plan to distribute or publish the app, add a `LICENSE` file and update this section appropriately.
