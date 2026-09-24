# Coaches Assistant

Coaches Assistant is a coach-focused Android app for managing team rosters, tracking live game events, and exporting post-game data — across 16 team sports: Basketball, Netball, Soccer, AFL, Rugby Union, Rugby League, Futsal, Field Hockey, Ice Hockey, Touch Football, Water Polo, Handball, Lacrosse, Gaelic Football, Flag Football, and Ultimate Frisbee.

The app is built with Kotlin, Jetpack Compose, Room, and an optional AI-powered training assistant (Google Gemini, Anthropic Claude, OpenAI ChatGPT, or any other OpenAI-compatible provider — your choice, Basketball only for now). It is designed for local, in-season use and keeps the workflow lightweight: roster setup, live substitutions, score tracking, attendance, and exportable game records.

## Project status

- Single-module Android app (`:app`)
- Application ID and Kotlin package: `com.lukearnold.coachesassistant`
- Multi-sport: a landing screen lets the user pick from 16 supported team sports (categorized into Court, Field, and Ice & Water sports); each sport gets its own roster and game history, plus sport-correct on-court size, scoring values, and period defaults (see "Sport support" below)
- UI: Jetpack Compose + Material 3
- Persistence: Room database (schema version 13)
- AI drills: user picks a provider on the Settings tab — Google Gemini (`gemini-3.8-flash`), Anthropic Claude (`claude-sonnet-5`), OpenAI ChatGPT (`gpt-5.5`), or "Other" (any OpenAI-compatible endpoint the user configures, e.g. OpenRouter, Groq, Mistral, Azure OpenAI, or a self-hosted Ollama/vLLM server) — and supplies that provider's API key (Gemini also falls back to `GEMINI_API_KEY` from `local.properties` if none is saved in-app)
- Build status: validated with the existing Gradle test task (`BUILD SUCCESSFUL`); a GitHub Actions workflow runs unit tests, lint, and a minified release build on every push/PR to `master`

## Core features

- Sport support
  - Landing screen to pick from 16 team sports across Court, Field, and Ice & Water categories (Rugby prompts a "which code?" dialog for Union vs League)
  - Each sport gets its own roster and game history — players and games are tagged by sport and never mix across sports
  - One shared rotation-tracking engine, configured per sport (`ui/SportConfig.kt`) rather than a separate implementation per sport: on-court size (5 for Basketball up to 18 for AFL), optional squad positions (e.g. Netball's GS/GA/WA/C/WD/GD/GK), default period structure and duration, sport-correct scoring buttons (from a single "Goal" worth 1 point up to Rugby's Try/Conversion/Penalty/Drop Goal), and the label used for the per-player infraction counter (Foul, Penalty, Card, or Free Kick)
  - "Switch sport" in Settings returns to the landing screen without clearing any sport's data; the app remembers the last-picked sport across restarts
  - Position is an optional, informational label on a roster player — the app does not enforce sport-specific positional legality, interchange caps, or replacement rules
- Roster management
  - Add, edit, and remove players
  - Optional jersey number and optional sport-appropriate position
  - CSV import and CSV export (per sport; the CSV format itself is unchanged — name and jersey number only)
- Game management
  - Create, select, and delete games
  - Flexible periods (2 halves, 3 periods, or 4 quarters) with configurable durations, defaulted per sport (e.g. 3 periods for Ice Hockey)
  - Starting lineup selection, sized to the sport's on-court count
  - Live substitutions and multi-sub workflows, with numbered badges showing which bench player is paired with which outgoing court player
  - Countdown timer with start, stop, reset, and manual adjustment; it stops itself when the period runs out, and game time is never counted past the end of a period
  - Per-player on-court shift timer that starts from 0 the moment a player is subbed on and freezes when they're subbed off, tracked against the game clock rather than wall-clock time
  - Game stats cards lead with an ON COURT/BENCH status badge and a live time (current shift length while on court, elapsed bench time since their last sub-off while benched)
  - Team and opponent score tracking, with the sport's scoring buttons available for both sides, and opponent-score events in the event log labeled with the actual opponent team name (falling back to "Opponent" only when no name was entered)
  - Per-player infraction logging (labeled Foul/Penalty/Card/Free Kick per sport) and MVP vote tracking
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
- Training tab (Basketball only — hidden from navigation for the other 15 sports)
  - Age-group drill generation via a user-chosen AI provider: Google Gemini, Anthropic Claude, OpenAI ChatGPT, or a custom OpenAI-compatible endpoint
  - Coming-soon placeholders for future training modules
- Settings tab
  - "Switch sport" returns to the sport landing screen
  - Choose which AI provider generates drills, and add, update, or remove that provider's API key directly in the app, encrypted at rest on-device (`EncryptedSharedPreferences`)
  - Each provider requires its own API key; the app does not support signing in with a Gemini, Claude, or ChatGPT account (see "AI provider access" below for why)
  - A Gemini key saved in-app takes priority over any build-time `GEMINI_API_KEY`; Anthropic, OpenAI, and the "Other" provider have no build-time fallback
  - "Other" also requires an endpoint URL and model name, since it can point at any OpenAI-compatible service
  - Selectively delete the current sport's players, games, or events (other sports are untouched), or delete everything across every sport

## Tech stack

- Kotlin
- Android Gradle Plugin 9.4.1
- Gradle wrapper 9.7.1
- Java 17
- Jetpack Compose BOM `2026.08.00`
- ViewModel / lifecycle patterns
- Room `2.8.4` + KSP
- OkHttp `4.12.0` (direct REST calls to every AI provider: Gemini, Anthropic, OpenAI, and OpenAI-compatible endpoints)
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

The fourth "Other" provider extends this to any additional service by having the user supply their own endpoint URL, model name, and API key, rather than the app adding a named integration for every possible provider. It's scoped specifically to services that speak the OpenAI chat-completions request/response shape (the de facto standard most third-party and self-hosted providers use), not an arbitrary-schema client.

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
- The multi-provider Settings UI was device-tested for all four providers, including a real network round-trip for the "Other" provider against a live OpenAI-compatible endpoint (the request correctly reached the server and the app surfaced the server's real error response for an invalid key)
- Multi-sport support was device-tested on an emulator across all six sports: Basketball and Netball were driven through a full roster (with positions) → game creation → starting lineup → scoring → infraction → substitution cycle; Soccer, AFL, and Rugby Union were spot-checked for correct on-court size, period defaults, and scoring buttons. This testing caught and led to a fix for a real bug where switching sports reused the previous sport's `AppViewModel` instance instead of creating a new one, leaking one sport's roster into another's Roster tab

The unit test suite covers the core game-stat calculation logic, CSV/event parsing, MVP-vote serialization, `GameRepository`'s transactions against an in-memory Room database (via Robolectric), and the Room migration chain against the exported schemas.

## High-level project structure

- App entry
  - `app/src/main/java/com/lukearnold/coachesassistant/MainActivity.kt` — routes between the sport landing screen and the per-sport app shell (`SportApp`), and hosts a sport-keyed `AppViewModel` factory
- Sport layer
  - `app/src/main/java/com/lukearnold/coachesassistant/data/Sport.kt` — the `Sport` enum (domain concept, shared by the DAOs/repository and the UI)
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/SportConfig.kt` — per-sport rotation/scoring configuration (on-court size, squad positions, period defaults, scoring options, infraction label)
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/SportSelectionScreen.kt` — the landing screen, including the Rugby Union/League picker dialog
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/SportPreferences.kt` — remembers the last-picked sport across app restarts
- UI screens
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/RosterScreen.kt`
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/GameScreen.kt` — top-level Game tab layout; delegates to:
    - `GameControlPanel.kt` (clock/score controls, lineup, substitution)
    - `GameStatsPanel.kt` (game creation/list, stats, event log)
    - `PlayerActionCard.kt` (per-player on-court action card)
    - `GameDialogs.kt` (attendance and event-edit dialogs)
    - `GameNotesPanel.kt` (coaching notes panel and dialog)
    - `GameFormatting.kt` (event description, period label, and time formatting helpers)
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/GameHistoryScreen.kt`
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/TrainingScreen.kt`
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/SettingsScreen.kt`
  - Shared components: `app/src/main/java/com/lukearnold/coachesassistant/ui/Components.kt` (includes `GameListCard`, used by both the Game and Game History screens)
  - Theme: `app/src/main/java/com/lukearnold/coachesassistant/ui/Theme.kt`
  - Shared file-export launcher: `app/src/main/java/com/lukearnold/coachesassistant/ui/FileExport.kt`
- State and logic
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/AppViewModel.kt` (also hosts the pure `calculatePlayerGameStats` function)
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/TrainingViewModel.kt`
- AI provider layer (`app/src/main/java/com/lukearnold/coachesassistant/ai/`)
  - `AiProvider.kt` — the Gemini/Anthropic/OpenAI/Other enum, each provider's preferences key and display strings
  - `CustomEndpointConfig.kt` — user-supplied endpoint URL and model name for the "Other" provider
  - `BasketballDrill.kt` — the parsed-drill domain model
  - `DrillParsing.kt` — pure prompt-building and response-parsing functions (unit tested)
  - `DrillTextGenerators.kt` — one `DrillTextGenerator` implementation per provider (Gemini via its SDK; Anthropic, OpenAI, and Other via direct REST calls, with Other and OpenAI sharing an `openAiCompatibleRequest` helper)
- Data layer
  - Entities: `app/src/main/java/com/lukearnold/coachesassistant/data/Entities.kt`
  - DAOs: `app/src/main/java/com/lukearnold/coachesassistant/data/Dao.kt`
  - Repository: `app/src/main/java/com/lukearnold/coachesassistant/data/GameRepository.kt`
  - Database: `app/src/main/java/com/lukearnold/coachesassistant/data/AppDatabase.kt`
  - MVP-vote serialization: `app/src/main/java/com/lukearnold/coachesassistant/data/MvpVotes.kt`
  - Room schema exports: `app/schemas/com.lukearnold.coachesassistant.data.AppDatabase/`

## Data model summary

- `players`: roster entries (`id`, `name`, optional `jerseyNumber`, `sport`, optional `position`)
- `games`: game metadata and state (score and MVP totals derived from events, timer, `periodCount` and period length, notes, `isFinished` flag and `finishedAt` timestamp, `sport`, `onCourtSize`)
- `game_players`: each game's on-court and absent players, one row per player; rows cascade-delete with their game or player
- `game_events`: event timeline entries (lineup, substitutions, team score, opponent score, per-player infractions, MVP votes, period changes, game end) — the `FOUL` event type is shared across sports and relabeled per sport in the UI (Foul/Penalty/Card/Free Kick)

## CSV and TXT notes

- Roster CSV columns include:
  - `id,name,jerseyNumber`
- Game export data includes metadata and event detail such as:
  - `gameId,gameTitle,opponent,createdAt,teamScoreAtExport,gameNotes,...`
- Coaching notes export is plain text and includes a header plus the game notes body.

## Known notes and risks

- Destructive migration is intentionally enabled for downgrades in `AppDatabase.kt`; this protects the normal upgrade workflow but will clear local data if the app is downgraded.
- A game's score and MVP totals are stored on the game row for quick display, but are always recomputed from its events (the source of truth) whenever events change.
- The project is local-first and does not currently include a backend, cross-device sync, or account-based auth system.

## Roadmap and history

See `DEVELOPMENT_HISTORY.md` for the version-by-version background leading up to the current project state.

## License

There is currently no license file in this repository. If you plan to distribute or publish the app, add a `LICENSE` file and update this section appropriately.
