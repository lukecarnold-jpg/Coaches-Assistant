# Coaches Assistant & Stats Tracker - Development History

This history captures the key milestones in the app's evolution, from the initial game tracker through the current roster-and-game-management workflow.

## Version 1.0 (Initial Release)
- Basic player roster management (name and optional jersey number)
- Game creation and selection
- Starting lineup selection (5 players)
- Real-time substitution tracking
- Basic scoring (+1, +2, +3) and foul tracking
- Simple game clock (start/stop)
- CSV export for rosters and games
- Persistent storage using Room

## Version 1.1 (Scoring & UI Enhancements)
- Added manual team score adjustments, including corrections
- Added opponent score tracking with manual controls
- Fixed overflow issues for long player names
- Enhanced the game clock with a reset action
- Enabled manual time adjustment to sync with official timers

## Version 1.2 (MVP & Branding)
- Implemented MVP voting
  - MVP vote buttons on active player action cards
  - Vote tallying in game stats
  - Votes persisted and included in CSV exports
- Updated the app launcher icon with a custom orange basketball design
- Incremented the database schema to version 2 for MVP and opponent score data

## Version 1.3 (Validation & Management)
- Jersey number validation and stricter roster input handling
- Added delete-game support with cascading cleanup for related events
- Increased DB schema version to support non-null jersey data
- Fixed warnings and finalized the README documentation

## Version 1.4 (Structure & Corrections)
- Added flexible periods (quarters or halves) with configurable durations
- Replaced the count-up timer with a standard countdown clock
- Allowed starting lineups to be redefined at each period transition
- Added edit/delete actions for recorded game events
- Automatic score recalculation when events are edited or removed
- Added end-game locking and final-state saving
- Increased the schema version for game-status and period tracking

## Version 1.5 (Data Retention & Maintenance)
- Enabled Room schema export for safer migrations
- Added backup rules and extraction rules for persistence across app updates
- Restricted destructive migration to downgrades only
- Completed a project-wide review for warnings and consistency fixes
- Fixed stale edit-form data after player updates

## Version 1.6 (Adaptive Tablet Layout)
- Integrated Material 3 window size detection
- Added a navigation rail for larger devices
- Refined roster layout to support side-by-side editing and lists on expanded screens
- Redesigned the game screen for split-panel layouts on tablets and foldables
- Extracted reusable UI components for better maintainability

## Version 1.7 (Stability & Tablet Fix)
- Migrated the app theme from MaterialComponents to Material3
- Fixed measurement stability in tablet layouts
- Switched to `LocalConfiguration.current.screenWidthDp` for adaptive layout detection
- Standardized edit-form initialization and event handling

## Version 1.8 (AI Training Assistant & Maintenance)
- Added a dedicated training section for development and coaching support
- Integrated Gemini AI drill generation for age-specific basketball activities
- Added age-group dropdown selection from Mini Ball to Under 18
- Moved the API key to `local.properties` + `BuildConfig` for safer project portability
- Added "coming soon" placeholders and modernized dependencies
- Updated the project to target SDK 37 and moved dependency management to a version catalog

## Version 1.9 (Attendance & Game Logic)
- Added automatic on-court cleanup when a game ends
- Implemented per-game attendance tracking for absent players
- Filtered absent players out of live roster or stat views for the selected game
- Increased the database schema to version 6 to support attendance data

## Version 2.0 (Data Management & Import)
- Added roster CSV import support
- Restored safer Room migration behavior and protected data from accidental destructive upgrades
- Built a robust, quote-aware CSV parser

## Version 2.1 (Adaptive Training & Flexibility)
- Redesigned the training screen for larger devices with multi-column layouts
- Switched the AI model to `gemini-2.5-flash`
- Restored optional jersey numbers in the roster workflow
- Added placeholders for upcoming training modules and tracking features
- Completed a project-wide audit and refined internal docs

## Version 2.2 (Substitution Clarity & Game History)
- Added numbered badges to the substitution picker so each bench selection visibly pairs with its matching outgoing court selection (previously the pairing was tracked only by tap order, with no on-screen indication)
- Added a dedicated Game History tab listing all finished games (newest first), each with a win/loss/tie badge, final score, end date, and View/Export/Delete actions
- Finished games now move out of the active "Games" list on the Game tab as soon as "End Game" is used, keeping that list focused on in-progress games
- Added a `finishedAt` timestamp to the `games` table, set when a game ends, and increased the database schema to version 9

## Version 2.3 (Bug Fix & Shared UI Helpers)
- Fixed a regression where the compact/phone layout's "Games" list still showed finished games after ending them, while the tablet layout correctly filtered them out (both now share the same `activeGames` filter)
- Extracted `rememberIdFileExportLauncher` in a new `FileExport.kt` to remove five duplicated CSV/TXT export-launcher blocks spread across GameScreen.kt and GameHistoryScreen.kt
- Extracted a shared `GameListCard` component so the active-games list and Game History screen render game cards the same way instead of maintaining two near-identical layouts
- Verified with a targeted code review of the recent commits; build and lint remain clean with no new warnings

## Version 2.4 (On-Court Shift Timer Fix)
- Fixed the per-player "Shift" timer on the on-court player cards, which previously ticked up from time-since-the-game-clock-was-last-started instead of resetting to 0 on sub-in, so a player subbed on mid-period could show a shift time far larger than their actual time on court
- Added `shiftStartGameTimeMillis` to `PlayerGameStat` so the UI tracks each player's shift against the exact game-clock moment they entered, matching how the main game clock computes its own live time
- Sub-off freezing behavior was already correct and is unchanged; the fix is isolated to the sub-on live-ticking calculation

## Version 2.5 (Opponent Score Event Labeling)
- Fixed the "Recent events" log showing the literal word "Opponent" for opponent-score events instead of the team name entered when the game was created
- The fix required two passes: the first threaded the game's opponent name into the event-description function and two of its three call sites; the second caught a third `GameStatsPanel` call site (the wide/expanded split-panel layout) that still wasn't passing the opponent name through, so it kept showing the fallback label
- The opponent name is resolved from the selected game at render time, so the corrected label applies retroactively to existing games' event history, not just newly recorded events

## Version 2.6 (Bench Status & Time Display)
- The read-only "Game stats" section previously showed an ON COURT badge and shift time only for players currently on court, positioned to the side of the card; bench players had no status indicator at all
- Every player card now leads with a status badge (ON COURT or BENCH) and a matching elapsed time at the top of the card, ahead of the player's name and other stats
- Added bench-time tracking: a `currentBenchTimeMillis` field on `PlayerGameStat`, computed the same way as the existing on-court shift timer but measuring time since a player's last sub-off (or since game start, for a player who hasn't been on court yet)

## Version 2.7 (In-App Gemini API Key)
- Added an "AI training assistant" section to the Settings tab so a Gemini API key can be entered, viewed (with a show/hide toggle), and removed directly in the app, with no rebuild required
- `TrainingViewModel` now persists the key in a dedicated `SharedPreferences` file and prefers it over the build-time `GEMINI_API_KEY` from `local.properties`, which remains as a fallback for developers who prefer to configure it at build time
- Converted `TrainingViewModel` to an `AndroidViewModel` (needed for `SharedPreferences` access) and hoisted a single shared instance in `MainActivity` so the Training and Settings tabs read and write the same key state
- The `GenerativeModel` client is now built fresh per drill-generation request using the currently effective key, instead of being cached for the lifetime of the ViewModel, so a key saved in Settings takes effect immediately

## Version 2.8 (Security, Testability & Maintainability Pass)
- Moved Gemini API key storage from plain `SharedPreferences` to `EncryptedSharedPreferences` (AES-256, backed by the Android Keystore via `MasterKey`), with a one-time silent migration that copies any key from the old plaintext file and wipes it
- Added a shared `MvpVotes` helper (`data/MvpVotes.kt`) to remove duplicated, exception-swallowing MVP-vote JSON parsing that had been copy-pasted separately into `AppViewModel` and `BasketballRepository`; new votes are written as real JSON while the old hand-rolled format is still read for existing saved games
- Extracted the player shift/bench/points calculator out of `AppViewModel`'s `combine {}` block into a standalone, pure `calculatePlayerGameStats()` function and added unit tests covering substitutions, period-end shift closing, bench time, absent-player filtering, and MVP votes — previously the least-tested, highest-risk logic in the app
- Deduplicated the `MainActivity` navigation rail / bottom bar tab definitions (five near-identical blocks each) into a single `navTabSpecs` list both bars loop over
- Split the 1,302-line `GameScreen.kt` into `GameScreen.kt`, `GameControlPanel.kt`, `GameStatsPanel.kt`, `PlayerActionCard.kt`, `GameDialogs.kt`, `GameNotesPanel.kt`, and `GameFormatting.kt`
- Added a GitHub Actions workflow (`.github/workflows/android-ci.yml`) that runs unit tests and lint on every push/PR to `master`
- Enabled R8 minification and resource shrinking for release builds
- Fixed the on-court/bench status badge on player action cards, which derived its state from shift time and timer status instead of the authoritative `onCourt` flag, so a just-added starter showed BENCH until the game clock had been started at least once
- Verified end-to-end on a real emulator: signed and installed the release (minified) build, exercised the roster → game → starting lineup → substitution flow, and confirmed the encrypted API key survives a full process kill and relaunch

## Version 2.9 (Gemini Model Update)
- Switched the Training tab's drill-generation model from `gemini-2.5-flash` to `gemini-3.8-flash`
- `gemini-2.5-flash` (released June 2025) is the oldest model in Google's current Gemini API lineup; Google has signaled its retirement (no earlier than October 16, 2026) and some developers have already reported it returning "model no longer available" errors ahead of that date
- `gemini-3.8-flash` is the current stable flagship Flash-tier model with no shutdown date announced, per Google's official models and deprecations documentation (checked September 2026)
- Deliberately pinned to the specific stable version rather than the `gemini-flash-latest` alias, which Google's own release notes recommend against for production use since it can silently repoint to a newer (and at the time of checking, preview-status) model without a code change

## Version 2.10 (Multi-Provider AI: Gemini, Claude, and ChatGPT)
- Investigated letting users "log into" their Gemini/Claude/ChatGPT account instead of an API key; found that Google and Anthropic have both made it a Terms of Service violation (with real account suspensions, including paying subscribers) to use a consumer subscription's login in a third-party app, and OpenAI's equivalent is scoped to its own Codex tooling only — so this was not built
- Instead extended the existing "bring your own API key" pattern to all three providers: added a provider picker (Google Gemini, Anthropic Claude, OpenAI ChatGPT) to the Settings tab, each with its own encrypted API key storage, save/remove actions, and get-a-key hint text
- Added a new `ai` package: `AiProvider` (the provider enum), `BasketballDrill` (moved out of `TrainingViewModel`), `DrillParsing` (pure, now unit-tested prompt-building and response-parsing functions), and `DrillTextGenerators` (one `DrillTextGenerator` per provider — Gemini via its existing SDK, Anthropic and OpenAI via direct REST calls, since neither publishes an official Android SDK)
- Added OkHttp as a dependency for the Anthropic (`/v1/messages`) and OpenAI (`/v1/chat/completions`) REST calls; both providers' current stable model IDs (`claude-sonnet-5`, `gpt-5.5`) were confirmed via each vendor's documentation before use
- `TrainingViewModel` now stores one key per provider plus the selected provider in the same encrypted preferences file, and `generateDrills()` dispatches to whichever provider is currently selected; error messages (e.g. "Add your OpenAI ChatGPT API key in Settings") name the active provider
- The Gemini-only legacy plaintext-key migration continues to work unchanged, since it maps into the same `AiProvider.GEMINI` storage slot
- Smoke-tested on a device: verified the provider chips (using a `FlowRow` so the third chip wraps instead of getting squeezed into a single row), independent per-provider key save/switch/remove, and provider-aware error messaging on the Training tab

## Version 2.11 ("Other" Provider for Any OpenAI-Compatible Endpoint)
- Added a fourth AI provider option, "Other (OpenAI-compatible)", so users aren't limited to the three named providers — it lets them point the app at any service that speaks the OpenAI chat-completions request/response shape (OpenRouter, Groq, Mistral, Azure OpenAI, or a self-hosted Ollama/vLLM server), rather than the app needing a bespoke integration per provider
- Added `CustomEndpointConfig` (endpoint URL + model name) alongside the existing per-provider API key storage; `AiProvider.OTHER` reuses the same encrypted-preferences pattern as the other three
- `OpenAiTextGenerator` and the new `CustomTextGenerator` now share an `openAiCompatibleRequest` helper, since "Other" is deliberately just a user-configurable version of the same request shape OpenAI's API already uses
- The Settings screen shows two extra fields (endpoint URL, model name) only when "Other" is selected, and `generateDrills()` validates both are set before attempting a request, with a provider-specific error message if not
- **Found and fixed a real bug during device testing**: `SettingsScreen`'s outer `Column` had never had a `verticalScroll` modifier. It happened to fit on screen before, but the two new "Other" fields pushed the Save button, "Data management" (including "Clear all data"), and "Exports" completely off-screen with no way to reach them on a phone-sized viewport. Added `.verticalScroll(rememberScrollState())` to fix it.
- Verified end-to-end on a device: filled in a real third-party endpoint (OpenRouter) with a placeholder key, saved it, confirmed it persisted through a full process kill/relaunch, then tapped "Generate AI Drills" — the request actually reached the live OpenRouter API over the network, which rejected the placeholder key, and the app correctly displayed the server's real error message ("Missing Authentication header"), confirming the whole request/response pipeline works, not just the UI

## Version 3.0 (Multi-Sport Support: Netball, Soccer, AFL, Rugby Union, Rugby League)
- Added a sport landing screen (shown on first launch, or after "Switch sport" in Settings) listing Basketball, Netball, Soccer, AFL, and Rugby; tapping Rugby opens a "which code?" dialog for Union vs. League. The last-picked sport is remembered across app restarts (`SportPreferences`, plain `SharedPreferences`)
- Generalized the previously basketball-only rotation engine to a single shared implementation configured per sport, rather than a separate engine per sport: the pre-existing event log (`GameEventEntity`) already stored arbitrary `points: Int` per event and supported N-for-N substitutions, so most of the work was removing hardcoded basketball assumptions rather than rewriting the engine
  - New `ui/SportConfig.kt`: per-sport on-court size, optional squad positions, default period structure/duration, scoring button set, and the label used for the per-player infraction counter
  - `PlayerEntity` gained `sport` and optional `position` columns; `GameEntity` gained `sport` and `onCourtSize` columns (Room schema version 9 → 10, via `AutoMigration` with column defaults backfilling all existing rows to Basketball — consistent with every prior schema change in this project)
  - Rosters and game history are now genuinely per-sport: `PlayerDao`/`GameDao` gained sport-scoped queries, and `AppViewModel` is now constructed per-sport (via a new `AppViewModelFactory`) so a Netball roster and a Basketball roster never mix
  - `GameControlPanel`, `PlayerActionCard`, `GameStatsPanel`, `GameFormatting`, and `GameDialogs` now read the starting-lineup size, scoring buttons, and infraction label from `SportConfig` instead of basketball-only hardcoded values (`5` players, `+1/+2/+3`, the word "Foul")
  - Roster's add/edit form shows an optional position picker (e.g. Netball's GS/GA/WA/C/WD/GD/GK) when the sport defines squad positions; position is purely an informational label and isn't enforced anywhere in the rotation logic
  - The Training tab (AI drill generation) stays Basketball-only and is hidden from navigation for the other five sports, since AI training content was out of scope for this pass
- **Found and fixed a real bug during emulator testing**: switching sports via Settings didn't actually create a new `AppViewModel` — Compose's `viewModel()` composable caches instances by class name within the Activity's `ViewModelStore`, so without an explicit per-sport `key`, switching to Netball kept returning the already-constructed Basketball-scoped `AppViewModel`, and the Netball Roster tab showed Basketball's players. Fixed by keying the `viewModel()` call on the sport (`key = "AppViewModel-${sport.name}"`)
- Verified on a Pixel 10 emulator: ran the full roster (with positions) → game creation → starting lineup → scoring → infraction → substitution cycle for both Basketball and Netball; spot-checked Soccer, AFL, and Rugby Union for correct on-court size gates, period defaults, and scoring button values (including Rugby's four-button row); confirmed switching back to Basketball preserved its own roster/games untouched after the fix

## Version 3.1 (Coaches Assistant Branding, Documentation & Data Management)
- Renamed the user-facing app label from "Basketball Rotation" to "Coaches Assistant" to reflect the app's multi-sport scope
- Replaced the basketball-specific launcher artwork with a generic coaching/roster icon while retaining adaptive and round launcher variants
- Added `STRUCTURE.md`, documenting the project layout, package responsibilities, Android resources, tests, build configuration, and main runtime flow
- Added `STRUCTURE_DIAGRAM.png` and its design philosophy companion as a visual overview of the entry point, UI, AI, data, Room, resources, tests, and CI layers
- Added selective local-data deletion in Settings: users can independently remove players, games, or events, while retaining a separate "Delete all data" option
- Game deletion continues to remove related game events transactionally; selecting games in the new data-management dialog applies the same cascading cleanup to all games
- Updated the README feature list to describe selective deletion and the full-data deletion option

## Version 3.2 (Expanded Multi-Sport Support: 10 Additional Team Sports)
- Added 10 additional team sports to `Sport` and `SportConfig`: **Futsal**, **Field Hockey**, **Ice Hockey**, **Touch Football**, **Water Polo**, **Handball**, **Lacrosse**, **Gaelic Football**, **Flag Football**, and **Ultimate Frisbee** — bringing the total supported team sports to 16
- Configured each new sport with its standard on-court/on-field size, squad position options, default period structure (halves/quarters) and duration, scoring actions, and infraction terminology (Foul, Penalty, Card, Exclusion)
- Redesigned `SportSelectionScreen.kt` to organize all 16 sports into clean, categorized sections (**Court Sports**, **Field Sports**, **Ice & Water Sports**)
- Expanded unit tests in `SportConfigTest.kt` to cover on-court team sizes, scoring options, and infraction pluralizations for all 16 sports (`26 passed, 0 failed`)
- Updated `README.md` to document the 16 supported team sports and updated layout capabilities

## Since version 3.2 (unreleased)
Not yet tagged as a release (release versions now come from `vX.Y.Z` tags; see README, "Publishing").

**Live game**
- The game clock stops itself at the end of each period, and recorded times are capped at the period length, so an unattended clock can't credit extra playing time; the clock can't be moved earlier than events already recorded
- Per-game period count (2 halves, 3 periods, or 4 quarters), so Ice Hockey plays three periods
- One shared clock ticker drives every on-screen game time
- Equal-share playing-time targets on player cards and stats, with the bench sorted by who's furthest behind
- Substitution suggestions: a banner (and optional notification with a Sub now action) when a player on court is 2 minutes ahead of an even share and one on the bench is 2 minutes behind; keepers (in sports where they play through) and players taken off injured are left out
- Undo from the snackbar for scores, adjustments, infractions, MVP votes, and substitutions
- Confirmations before ending a game, deleting a game, removing a player, changing period, or resetting the clock
- Starting lineups come from the players who are here; a short-handed side can take the field down to each sport's minimum (from its rules, adjustable in Settings), and players can come on without a swap or go off without a replacement
- Game Day mode silences notifications (priority only) until End Game, as the app's own Do Not Disturb mode
- Optional lock-screen game clock notification with a Stop clock action
- Keep the screen on while the clock runs
- The event log shows times as the game clock read them (period and countdown), only offers edit and remove for events that can change on their own, and finished games are read-only for live actions
- Foul counts never go below zero; the team -1 button is a team-level adjustment

**Data**
- On-court and absent players moved into a `game_players` table; foreign keys with cascades on events (Room schema now version 13, with migration tests)
- Whole-app backup and restore to a file; a transition build moves data from the old `com.example.basketballrotation` app
- Season stats (record, points, and each player's games, minutes, points, infractions, and MVP votes) at the top of the History tab
- Roster CSV imports skip players already on the roster and keep positions; duplicate players are caught when added by hand
- Coaching notes survive rotation and are saved when the dialog is closed

**AI drills**
- Offered for every sport; Gemini is called through its REST API; API keys are encrypted with an Android Keystore key
- The Other (OpenAI-compatible) provider can use plain http to a server on the local network, with Android 17's local network permission
- Drills are requested in the app's language
- The build-time `GEMINI_API_KEY` is only used by debug builds, so a key can't ship inside a release APK

**App-wide**
- Application ID and package renamed to `com.lukearnold.coachesassistant`
- All text moved into `strings.xml`, ready for translation, with a per-app language choice once a translation exists
- Accessibility: labelled controls, 48dp touch targets, and layouts that hold up at the largest text size
- Opens on the Game tab when a game is in progress, without flashing empty states while data loads
- Release builds ship with a baseline profile (about 19% faster startup on an emulator), generated by a new `:baselineprofile` module

**Build and publishing**
- Release signing with an upload key kept outside the repo; `versionCode` from the commit count and `versionName` from release tags
- Privacy policy draft and Play listing notes
- CI runs unit tests, lint, and a minified release build, and builds a signed bundle when the signing secrets are set
- Tests grew from the core stat logic to 206: repository tests against an in-memory Room database, migration tests, backup, CSV, and Compose UI tests on Robolectric

## Current repository review
- Two modules: the app (`:app`) and `:baselineprofile`, which generates the baseline profile and holds a startup benchmark.
- `./gradlew testDebugUnitTest` passes 206 tests, all on the JVM (Robolectric where Android is needed; there are no instrumented tests); `./gradlew lintDebug` has no findings; `./gradlew assembleRelease` builds with R8 minification, resource shrinking, and the baseline profile.
- Room schema version 13, with every migration from version 5 tested against the exported schemas.
- 16 sports share one rotation-tracking engine, configured per sport (on-court size, minimum to field a team, positions, periods, scoring, infraction, keeper position).
- The training assistant works for every sport, with the provider and API key chosen in Settings (keys encrypted with an Android Keystore key); in debug builds, Gemini also falls back to `GEMINI_API_KEY` in `local.properties`.
- The app is local-first: no backend, cloud sync, accounts, analytics, or crash reporting. The only network traffic is drill generation.
- Before publishing: create the upload key, fill in and host the privacy policy, and tag the first release (see README, "Publishing").
