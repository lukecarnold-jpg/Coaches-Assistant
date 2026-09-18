# Basketball Rotation

Basketball Rotation is a coach-focused Android app for managing team rosters, tracking live game events, and exporting post-game data.

The app is built with Kotlin, Jetpack Compose, Room, and an optional Gemini-powered training assistant. It is designed for local, in-season use and keeps the workflow lightweight: roster setup, live substitutions, score tracking, attendance, and exportable game records.

## Project status

- Single-module Android app (`:app`)
- UI: Jetpack Compose + Material 3
- Persistence: Room database (schema version 9)
- AI drills: Google Generative AI (`gemini-3.8-flash`), using a key entered on the Settings tab or, if none is saved, `GEMINI_API_KEY` from `local.properties`
- Build status: validated with the existing Gradle test task (`BUILD SUCCESSFUL`); a GitHub Actions workflow runs unit tests and lint on every push/PR to `master`

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
  - Per-player on-court shift timer that starts from 0 the moment a player is subbed on and freezes when they're subbed off, tracked against the game clock rather than wall-clock time
  - Game stats cards lead with an ON COURT/BENCH status badge and a live time (current shift length while on court, elapsed bench time since their last sub-off while benched)
  - Team and opponent score tracking, with opponent-score events in the event log labeled with the actual opponent team name (falling back to "Opponent" only when no name was entered)
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
  - Age-group drill generation via a user-chosen AI provider: Google Gemini, Anthropic Claude, or OpenAI ChatGPT
  - Coming-soon placeholders for future training modules
- Settings tab
  - Choose which AI provider generates drills, and add, update, or remove that provider's API key directly in the app, encrypted at rest on-device (`EncryptedSharedPreferences`)
  - Each provider requires its own API key; the app does not support signing in with a Gemini, Claude, or ChatGPT account (see "AI provider access" below for why)
  - A Gemini key saved in-app takes priority over any build-time `GEMINI_API_KEY`; Anthropic and OpenAI have no build-time fallback
  - Clear all local data (players, games, events)

## Tech stack

- Kotlin
- Android Gradle Plugin 9.3.1
- Gradle wrapper 9.7.1
- Java 17
- Jetpack Compose BOM `2026.08.00`
- ViewModel / lifecycle patterns
- Room `2.8.4` + KSP
- Google Generative AI client `0.9.0` (Gemini)
- OkHttp `4.12.0` (direct REST calls to Anthropic and OpenAI, which have no official Android SDK)
- AndroidX Security Crypto `1.1.0-alpha06` (`EncryptedSharedPreferences`)

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
3. Add an optional API key for whichever AI provider you want the Training tab to use, either:
   - In-app: open the Settings tab, pick a provider chip (Google Gemini, Anthropic Claude, or OpenAI ChatGPT), and paste its key into "AI training assistant" (no rebuild required; for Gemini this takes priority over the build-time key), or
   - At build time, in `local.properties` at the project root (Gemini only):

```properties
GEMINI_API_KEY=your_key_here
```

If no key is set for the selected provider, the app still builds, but the Training tab will show an error when generating AI drills.

## AI provider access

The app requires each user's own API key per provider (entered in Settings) rather than a "log in with your Gemini/Claude/ChatGPT account" option. This is deliberate: as of the time this was implemented, Google and Anthropic have both made it a Terms of Service violation for a third-party app to use a consumer subscription's login (Gemini Advanced/Ultra, Claude Pro/Max) instead of billed API access, with real account suspensions enforced — including paying subscribers. OpenAI's "Sign in with ChatGPT" is scoped to its own Codex tooling, not general third-party apps. API keys are the only supported, ToS-compliant path for a third-party app like this one.

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

Release builds have R8 minification and resource shrinking enabled (`./gradlew.bat assembleRelease`). No release signing config is checked in, so the output APK is unsigned; sign it with your own keystore (or a debug keystore, for local testing) before installing it on a device.

## Verified status

The current project state was validated in this workspace with:

- Clean Kotlin compilation of the main, unit test, and instrumented test source sets, with zero compiler warnings
- `./gradlew.bat lintDebug` -> `BUILD SUCCESSFUL`, with a single informational finding (a newer Android Gradle Plugin patch/minor version is available)
- `./gradlew.bat testDebugUnitTest` -> `BUILD SUCCESSFUL`, all unit tests passing, including coverage for the player shift/bench/points calculator and MVP-vote serialization
- `./gradlew.bat assembleRelease` -> `BUILD SUCCESSFUL` with R8 minification and resource shrinking enabled
- A signed release build was installed and driven end-to-end on an emulator (roster setup, game creation, starting lineup, live substitution, and API key save/persist-through-restart/remove), with no crashes or logged exceptions

The existing test coverage includes the core game-stat calculation logic, CSV/event parsing, and MVP-vote serialization, but repository-level Room integration tests (exercising `BasketballRepository`'s transactions against a real database) are still a gap compared with a full production QA suite.

## High-level project structure

- App entry
  - `app/src/main/java/com/example/basketballrotation/MainActivity.kt`
- UI screens
  - `app/src/main/java/com/example/basketballrotation/ui/RosterScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/GameScreen.kt` — top-level Game tab layout; delegates to:
    - `GameControlPanel.kt` (clock/score controls, lineup, substitution)
    - `GameStatsPanel.kt` (game creation/list, stats, event log)
    - `PlayerActionCard.kt` (per-player on-court action card)
    - `GameDialogs.kt` (attendance and event-edit dialogs)
    - `GameNotesPanel.kt` (coaching notes panel and dialog)
    - `GameFormatting.kt` (event description and time formatting helpers)
  - `app/src/main/java/com/example/basketballrotation/ui/GameHistoryScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/TrainingScreen.kt`
  - `app/src/main/java/com/example/basketballrotation/ui/SettingsScreen.kt`
  - Shared components: `app/src/main/java/com/example/basketballrotation/ui/Components.kt` (includes `GameListCard`, used by both the Game and Game History screens)
  - Shared file-export launcher: `app/src/main/java/com/example/basketballrotation/ui/FileExport.kt`
- State and logic
  - `app/src/main/java/com/example/basketballrotation/ui/AppViewModel.kt` (also hosts the pure `calculatePlayerGameStats` function)
  - `app/src/main/java/com/example/basketballrotation/ui/TrainingViewModel.kt`
- AI provider layer (`app/src/main/java/com/example/basketballrotation/ai/`)
  - `AiProvider.kt` — the Gemini/Anthropic/OpenAI enum, each provider's preferences key and display strings
  - `BasketballDrill.kt` — the parsed-drill domain model
  - `DrillParsing.kt` — pure prompt-building and response-parsing functions (unit tested)
  - `DrillTextGenerators.kt` — one `DrillTextGenerator` implementation per provider (Gemini via its SDK, Anthropic/OpenAI via direct REST calls)
- Data layer
  - Entities: `app/src/main/java/com/example/basketballrotation/data/Entities.kt`
  - DAOs: `app/src/main/java/com/example/basketballrotation/data/Dao.kt`
  - Repository: `app/src/main/java/com/example/basketballrotation/data/BasketballRepository.kt`
  - Database: `app/src/main/java/com/example/basketballrotation/data/AppDatabase.kt`
  - MVP-vote serialization: `app/src/main/java/com/example/basketballrotation/data/MvpVotes.kt`
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
- Repository-level Room integration tests (exercising `BasketballRepository`'s transactions against a real database) are not yet in place; game-flow logic itself is covered by unit tests against the pure `calculatePlayerGameStats` function.
- The project is local-first and does not currently include a backend, cross-device sync, or account-based auth system.

## Roadmap and history

See `DEVELOPMENT_HISTORY.md` for the version-by-version background leading up to the current project state.

## License

There is currently no license file in this repository. If you plan to distribute or publish the app, add a `LICENSE` file and update this section appropriately.
