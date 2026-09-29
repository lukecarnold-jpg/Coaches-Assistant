# Coaches Assistant

Coaches Assistant is a coach-focused Android app for managing team rosters, tracking live game events, and exporting post-game data — across 16 team sports: Basketball, Netball, Soccer, AFL, Rugby Union, Rugby League, Futsal, Field Hockey, Ice Hockey, Touch Football, Water Polo, Handball, Lacrosse, Gaelic Football, Flag Football, and Ultimate Frisbee.

The app is built with Kotlin, Jetpack Compose, Room, and an optional AI-powered training assistant (Google Gemini, Anthropic Claude, OpenAI ChatGPT, or any other OpenAI-compatible provider — your choice, for every sport). It is designed for local, in-season use and keeps the workflow lightweight: roster setup, live substitutions, score tracking, attendance, and exportable game records.

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
  - Starting lineup selection from the players who are here (not marked absent), sized to the sport's on-court count; when everyone here plays (a full side with no subs, or fewer), they start ticked
  - Short-handed play: with fewer players here than a full side, the team can still take the field down to the sport's minimum (e.g. 7 for soccer under IFAB Law 3, 13 for Gaelic football, 5 for basketball), and the app says so when there aren't enough. Each sport's minimum defaults to its rules and can be changed in Settings for competitions that differ
  - Live substitutions and multi-sub workflows, with numbered badges showing which bench player is paired with which outgoing court player
  - Uneven changes: a player can come on without a swap (e.g. arriving late to a short-handed side, up to a full side) or go off without a replacement (e.g. injured), with a note when that leaves the side below its minimum; these show in the event log as "comes on" / "goes off" and can be undone like any substitution
  - Countdown timer with start, stop, reset, and manual adjustment; it stops itself when the period runs out, and game time is never counted past the end of a period
  - Per-player on-court shift timer that starts from 0 the moment a player is subbed on and freezes when they're subbed off, tracked against the game clock rather than wall-clock time
  - Game stats cards lead with an ON COURT/BENCH status badge and a live time (current shift length while on court, elapsed bench time since their last sub-off while benched)
  - Equal-share playing-time targets: each available (non-absent) player's share of the game's on-court time, shown on player cards and game stats with how far they are behind or ahead of an even share so far; the substitution list puts the bench player furthest behind first
  - Team and opponent score tracking, with the sport's scoring buttons available for both sides, and opponent-score events in the event log labeled with the actual opponent team name (falling back to "Opponent" only when no name was entered)
  - Per-player infraction logging (labeled Foul/Penalty/Card/Free Kick per sport) and MVP vote tracking
  - Per-game attendance management
  - Coaching notes with TXT export
  - Event editing and deletion
  - Undo from the snackbar after a score, team or opponent adjustment, infraction, MVP vote, or substitution (a batch of subs undoes as one); a substitution is only undone if its players are still where it put them
  - End-game workflow
  - Game Day mode (Android 10+): a button on the Game screen silences notifications until that game's End Game (or the game is deleted). It's the app's own "Game day" Do Not Disturb mode, letting through calls from starred contacts, repeat callers, and alarms; the first time, the app explains and opens the system screen to grant Do Not Disturb (Modes) access
- Game history
  - Dedicated History tab listing all finished games, newest first
  - Season summary at the top: win/loss/tie record, points for and against (with per-game averages), and each roster player's games played, total and average minutes, points, infractions, and MVP votes across every finished game for the sport
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
- Training tab (every sport)
  - Age-group drill generation via a user-chosen AI provider: Google Gemini, Anthropic Claude, OpenAI ChatGPT, or a custom OpenAI-compatible endpoint
- Settings tab
  - "Switch sport" returns to the sport landing screen
  - "Minimum players to field a team" for the current sport, from 1 to a full side, defaulting to the sport's rules
  - "Keep screen on while the clock runs" (on by default) stops the phone locking mid-game; the normal screen timeout returns as soon as the clock stops
  - "Show the running clock in notifications" (off by default; asks for the notifications permission on Android 13+): while a clock runs, a silent notification shows the game, period, score, and a live countdown, including on the lock screen, with a Stop clock button. The system counts it down, so it stays right with the app closed, and it disappears when the clock stops or the period ends
  - Choose which AI provider generates drills, and add, update, or remove that provider's API key directly in the app, encrypted at rest on-device with an AES-256-GCM key held in the Android Keystore (`ai/AiSettingsStore.kt`)
  - Each provider requires its own API key; the app does not support signing in with a Gemini, Claude, or ChatGPT account (see "AI provider access" below for why)
  - A Gemini key saved in-app takes priority over any build-time `GEMINI_API_KEY`; Anthropic, OpenAI, and the "Other" provider have no build-time fallback
  - "Other" also requires an endpoint URL and model name, since it can point at any OpenAI-compatible service
  - "Other" endpoints must use `https://`, except servers on the local network (localhost, private IP ranges including Tailscale's `100.64.0.0/10`, and `.local`/`.lan`/single-label hostnames), which may use plain `http://` and can be used without an API key — e.g. `http://192.168.1.20:11434/v1/chat/completions` for Ollama
  - On Android 17+, reaching a server on the local network needs the "local network access" (Nearby devices) permission; the app asks for it when a local endpoint is saved or used, and explains how to turn it on if it was declined
  - Back up all data (every sport's players, games, events, and lineups) to a JSON file, and restore it on this or another device; a restore replaces all current data after a confirmation, and a file that can't be read leaves everything unchanged. API keys aren't included
  - Selectively delete the current sport's players, games, or events (other sports are untouched), or delete everything across every sport

## Tech stack

- Kotlin
- Android Gradle Plugin 9.4.1
- Gradle wrapper 9.8.0
- Java 17
- Jetpack Compose BOM `2026.09.00`
- ViewModel / lifecycle patterns
- Room `2.8.5` + KSP
- OkHttp `5.5.0` (direct REST calls to every AI provider: Gemini, Anthropic, OpenAI, and OpenAI-compatible endpoints)
- AndroidX Security Crypto `1.1.0` — deprecated upstream; used only to move API keys saved by older versions out of `EncryptedSharedPreferences` on first launch, and can be removed once no installs predate that

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
   - In-app: open the Settings tab, pick a provider chip (Google Gemini, Anthropic Claude, OpenAI ChatGPT, or Other), and paste its key into "AI training assistant" (no rebuild required; for Gemini this takes priority over the build-time key), or
   - At build time, in `local.properties` at the project root (Gemini only):

```properties
GEMINI_API_KEY=your_key_here
```

If no key is set for the selected provider, the app still builds, but the Training tab will show an error when generating AI drills.

## Moving from the old app

Before the rename, the app was installed as `com.example.basketballrotation`. Android can't update that install to the new app ID (`com.lukearnold.coachesassistant`), and one app can't read another's data, so the two install side by side. To move your data across:

1. With the device connected, install the transition build over the old app: `./gradlew installDebug -PlegacyAppId` (Windows: `gradlew.bat installDebug -PlegacyAppId`). It's today's code under the old app ID, so it installs as an update: your data is migrated and kept. It shows up as "Old Coaches Assistant".
2. Open it and go to **Settings → Backup → Back up all data**.
3. Open the new Coaches Assistant app, go to **Settings → Restore from backup**, and pick that file.
4. Re-enter your AI API keys in the new app (backups never include them), then uninstall "Old Coaches Assistant".

## AI provider access

The app requires each user's own API key per provider (entered in Settings) rather than a "log in with your Gemini/Claude/ChatGPT account" option. This is deliberate: as of the time this was implemented, Google and Anthropic have both made it a Terms of Service violation for a third-party app to use a consumer subscription's login (Gemini Advanced/Ultra, Claude Pro/Max) instead of billed API access, with real account suspensions enforced — including paying subscribers. OpenAI's "Sign in with ChatGPT" is scoped to its own Codex tooling, not general third-party apps. API keys are the only supported, ToS-compliant path for a third-party app like this one.

The fourth "Other" provider extends this to any additional service by having the user supply their own endpoint URL, model name, and API key, rather than the app adding a named integration for every possible provider. It's scoped specifically to services that speak the OpenAI chat-completions request/response shape (the de facto standard most third-party and self-hosted providers use), not an arbitrary-schema client.

## Running locally

Use the Gradle wrapper from the repo root (on Windows, run `gradlew.bat` instead of `./gradlew`):

```sh
./gradlew testDebugUnitTest   # unit and Compose UI tests (JVM, via Robolectric; no emulator needed)
./gradlew lintDebug
./gradlew assembleDebug
./gradlew installDebug        # install on a connected device or running emulator
```

Release builds have R8 minification and resource shrinking enabled (`./gradlew assembleRelease`). No release signing config is checked in, so the APK is unsigned. To try one on an emulator, sign it with the debug keystore; it then installs over a debug build without losing data:

```sh
./gradlew assembleRelease
BT=$ANDROID_HOME/build-tools/<version>
$BT/zipalign -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk
$BT/apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out release.apk aligned.apk
adb install -r release.apk
```

`versionCode` is the number of commits in the checked-out history (`git rev-list --count HEAD`), so it goes up with every commit on `master`, whose history is linear because PRs are rebase-merged. Build from a full clone: a shallow clone gets a too-low code (with a warning), and a copy with no `.git` falls back to `1`. To set it explicitly, pass `-PversionCode=N`, e.g. `./gradlew assembleRelease -PversionCode=150`. `versionName` comes from the latest `vX.Y.Z` git tag (see Publishing → Releasing).

## Publishing

### Releasing

`versionName` comes from git tags: tag the commit you release as `vX.Y.Z` and builds of it are named `X.Y.Z`; builds of later commits are named e.g. `X.Y.Z-dev.3` (three commits past the release), so they can't be mistaken for it. With no tag yet it's `1.0`; `-PversionName=...` overrides it.

```sh
git tag v1.0.0 && git push origin v1.0.0
./gradlew bundleRelease   # from the tagged commit
```

### Signing

Google Play needs release builds signed with your **upload key**. Create one once and keep it, and its passwords, somewhere safe and out of this repo (with Play App Signing, Google holds the actual app-signing key and a lost upload key can be reset):

```sh
keytool -genkeypair -keystore ~/keys/coaches-assistant-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Then give the build four values, from any one of: `~/.gradle/gradle.properties` (outside the repo), environment variables, or the gitignored `local.properties`:

```properties
RELEASE_STORE_FILE=/Users/you/keys/coaches-assistant-upload.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
```

`./gradlew bundleRelease` then produces a signed `app/build/outputs/bundle/release/app-release.aab` to upload to Play. With none of the values set, release builds stay unsigned; with only some set, the build stops and says which are missing.

To have CI build the signed bundle on every push to `master`, add these repository secrets (Settings → Secrets and variables → Actions): `RELEASE_KEYSTORE_BASE64` (the output of `base64 -i ~/keys/coaches-assistant-upload.jks`), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. The bundle is attached to the workflow run as the `release-bundle` artifact. Without the secrets, that step is skipped.

### Privacy policy and Play listing

Play requires a privacy policy URL. A draft is in [`docs/privacy-policy.md`](docs/privacy-policy.md): fill in the contact email and effective date, then host it at a public URL. This repo is private, so it can't be linked from here; any public page works (a website, Google Sites, a public GitHub Gist, or GitHub Pages from a public repo).

For Play's **Data safety** form, the relevant facts (review them against Google's current definitions before submitting):

- Everything the coach enters (rosters, games, notes) stays on the device; the app has no server, accounts, ads, analytics, or crash reporting.
- The only network traffic is generating drills: the selected sport and age group, plus the user's own API key, go to the AI provider the user chose. No player or game data is sent.
- Data is encrypted in transit (all built-in providers use HTTPS; plain HTTP is only allowed to a server on the user's local network).
- Users can delete their data in the app (Settings → Delete data) or by uninstalling.

## Testing

CI (`.github/workflows/android-ci.yml`) runs on every push and PR to `master`: the unit and UI tests, lint, and a minified release build.

The automated tests cover:

- Game logic: the player time/points/bench calculator (including legacy events past the period end), playing-time targets, season totals, clock rules (auto-stop, never moving before recorded events), and confirmation messages
- Data: `GameRepository`'s transactions and undo against an in-memory Room database, every Room migration against the exported schemas, backup/restore round trips, and CSV import/export
- AI: request/response parsing for each provider, local-network endpoint rules, and settings storage
- Screens (Compose UI tests on Robolectric): confirmation dialogs, toggling rows by their label, and the clock editor

Last checked by hand on the Pixel 10 Pro emulator (API 37) in September 2026:

- A live game: clock auto-stop and keep-screen-on, playing-time labels, scoring and substitution undo, End Game and other confirmations
- Substitutions: joining a short court without a swap, going off without a replacement (below the minimum), and undoing it
- Fielding: the lineup offering only players who are here, pre-ticking them when all play, too few to field a team, and a short-handed lineup after lowering the minimum
- The game clock notification: the permission prompt, the live countdown in the shade, and Stop clock stopping the app's clock
- Game Day mode: granting Modes access, silencing (priority-only Do Not Disturb), staying on after the app is closed, and turning off from its button, End Game, or deleting the game
- Backup and restore, including onto a wiped app; the transition build migrating the old app's data and restoring it into the new app
- The Other provider against a local Ollama server over plain http, including the Android 17 local network permission
- Light and dark mode, and the minified release build (signed with the debug key) through the flows above

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
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/AppViewModel.kt`, with the pure `calculatePlayerGameStats` function in `PlayerGameStats.kt`
  - `app/src/main/java/com/lukearnold/coachesassistant/ui/TrainingViewModel.kt`
- AI provider layer (`app/src/main/java/com/lukearnold/coachesassistant/ai/`)
  - `AiProvider.kt` — the Gemini/Anthropic/OpenAI/Other enum, each provider's preferences key and display strings
  - `CustomEndpointConfig.kt` — user-supplied endpoint URL and model name for the "Other" provider
  - `Drill.kt` — the parsed-drill domain model
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
