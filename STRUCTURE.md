# Project Structure

Coaches Assistant is a single-module Android application built with Kotlin,
Jetpack Compose, Room, and optional AI-powered basketball training tools. The
application supports multiple sports while sharing the same roster, game,
event, export, and persistence infrastructure.

## Top-level layout

```text
.
├── .github/workflows/
│   └── android-ci.yml                 # Unit tests and lint on master pushes/PRs
├── app/
│   ├── schemas/                       # Exported Room database schemas
│   ├── proguard-rules.pro             # Release R8/ProGuard rules
│   ├── build.gradle.kts               # Android module configuration and dependencies
│   └── src/
│       ├── main/                      # Production Android application
│       ├── test/                      # JVM unit tests
│       └── androidTest/               # Instrumented Android tests
├── gradle/
│   ├── libs.versions.toml             # Central dependency and plugin versions
│   └── wrapper/                       # Gradle wrapper configuration
├── build.gradle.kts                   # Root plugin declarations
├── settings.gradle.kts                # Project and repository configuration
├── gradle.properties                  # Gradle build properties
├── gradlew / gradlew.bat              # Gradle wrapper entry points
├── README.md                          # Product overview, setup, and feature documentation
├── DEVELOPMENT_HISTORY.md             # Version and implementation history
└── STRUCTURE.md                       # This guide
```

Generated build output is written to `build/` and `app/build/`. Local
configuration such as `local.properties` is not committed.

## Application source

Production code lives under:

```text
app/src/main/java/com/lukearnold/coachesassistant/
```

### Application entry point

- `MainActivity.kt` initializes the Compose content and routes between the
  sport-selection screen and the sport-specific application shell.
- `SportApp` owns the top-level tab navigation, responsive navigation rail or
  bottom navigation, shared `AppViewModel`, and screen composition.

### `ui/` - Compose screens and presentation logic

The UI package contains the screens, reusable components, view models, and
presentation helpers:

- `SportSelectionScreen.kt` - landing screen for choosing Basketball, Netball,
  Soccer, AFL, Rugby Union, or Rugby League.
- `RosterScreen.kt` - roster management, CSV import, and CSV export.
- `GameScreen.kt` - top-level live-game screen.
- `GameControlPanel.kt` - timer, scoring, lineup, and substitution controls.
- `GameStatsPanel.kt` - game selection, player statistics, and event display.
- `PlayerActionCard.kt` - per-player live game actions.
- `GameDialogs.kt` - attendance and event editing dialogs.
- `GameNotesPanel.kt` - coaching notes and TXT export.
- `GameFormatting.kt` - event descriptions, period labels, and time formatting.
- `LiveGameClock.kt` - the Game screen's single live clock ticker, shared by the game clock, player cards, and stats.
- `KeepScreenOn.kt` - keeps the screen awake while a game clock runs, and the setting that controls it.
- `PlayingTime.kt` - equal-share playing-time targets, pace labels, and the bench substitution order.
- `GameHistoryScreen.kt` - finished-game history, viewing, exporting, and deletion, with the season summary on top.
- `SeasonStats.kt` - season totals (team record and per-player totals) across a sport's finished games.
- `TrainingScreen.kt` - basketball-only AI training assistant UI.
- `SettingsScreen.kt` - sport switching, AI provider settings, and data clearing.
- `Components.kt` - shared Compose components, including game list cards.
- `ResponsiveLayout.kt` - compact and expanded layout decisions.
- `Theme.kt` - the app's Material 3 theme (dark mode and Android 12+ dynamic color).
- `FileExport.kt` - shared file-export launcher.
- `SportConfig.kt` - sport-specific rules, scoring, periods, positions, and labels.
- `SportPreferences.kt` - remembers the last selected sport.
- `AppViewModel.kt` - screen state, user actions, repository coordination, and
  player game-stat calculations.
- `TrainingViewModel.kt` - AI provider selection, endpoint configuration, and
  API key state.

### `data/` - domain and persistence

The data package defines the multi-sport domain model and local Room database:

- `Sport.kt` - supported sport enum and sport metadata.
- `Entities.kt` - Room entities for players, games, and game events.
- `Dao.kt` - Room data access objects.
- `AppDatabase.kt` - Room database, version 10, and migrations.
- `GameRepository.kt` - repository operations used by the view models.
- `MvpVotes.kt` - serialization and parsing for MVP vote data.

The database is local-first and is stored in a Room database named
`basketball_rotation.db`. Room schema snapshots are kept in
`app/schemas/com.lukearnold.coachesassistant.data.AppDatabase/`.

### `ai/` - training assistant integrations

The AI package is used by the basketball Training tab:

- `AiProvider.kt` - supported providers and provider metadata.
- `CustomEndpointConfig.kt` - configuration for an OpenAI-compatible endpoint.
- `BasketballDrill.kt` - parsed drill model.
- `DrillParsing.kt` - prompt construction and response parsing.
- `DrillTextGenerators.kt` - provider-specific text generation implementations
  for Gemini, Anthropic, OpenAI, and custom OpenAI-compatible services.

API keys are supplied by the user in Settings and stored using encrypted
preferences. A Gemini build-time fallback may be supplied through
`GEMINI_API_KEY` in `local.properties`.

## Android resources

Resources live under `app/src/main/res/`:

```text
res/
├── drawable/
│   ├── ic_launcher_background.xml
│   └── ic_launcher_foreground.xml
├── mipmap-anydpi/
│   ├── ic_launcher.xml
│   └── ic_launcher_round.xml
├── mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
│   └── launcher bitmap assets
├── values/
│   ├── colors.xml
│   ├── strings.xml
│   ├── style.xml
│   └── themes.xml
├── values-night/
│   └── themes.xml
└── xml/
    ├── backup_rules.xml
    └── data_extraction_rules.xml
```

`AndroidManifest.xml` declares the launcher activity, application label, icon,
backup behavior, and required network permission.

## Tests

JVM unit tests are under `app/src/test/` and cover:

- AI drill parsing
- event types and descriptions
- MVP vote serialization
- player game statistics
- sport-specific configuration

Instrumented Android tests are under `app/src/androidTest/`. Test and lint
commands are also run by `.github/workflows/android-ci.yml`.

## Build and dependency configuration

- `settings.gradle.kts` configures plugin/dependency repositories and includes
  the `:app` module.
- Root `build.gradle.kts` declares shared Android, Kotlin Compose, and KSP
  plugins.
- `app/build.gradle.kts` configures SDK levels, application ID, build types,
  Compose, Room/KSP, and runtime dependencies.
- `gradle/libs.versions.toml` centralizes dependency versions and aliases.
- Release builds use R8 minification and resource shrinking.

## Main runtime flow

```text
MainActivity
  -> sport selection or SportApp
  -> AppViewModel / TrainingViewModel
  -> GameRepository
  -> Room DAOs
  -> AppDatabase
```

The selected sport is persisted independently from roster and game data.
`AppViewModel` instances are keyed by sport so each sport's state remains
isolated while using the same application screens and repository patterns.
