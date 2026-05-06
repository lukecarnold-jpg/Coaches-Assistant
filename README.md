# Basketball Rotation & Stats Tracker

A comprehensive Android application designed for team managers and coaches to streamline player rotations and track game-time statistics in real-time.

## Features

### 👥 Roster Management
- **Add/Remove Players:** Easily manage your team list.
- **Required Details:** Enforces a valid Player Name to ensure accurate tracking. Jersey numbers are optional.
- **In-place Editing:** Update player details directly from the roster list.

### 🏀 Game Tracking
- **Live Rotation:** Track exactly who is on the court and how long their current shift has lasted.
- **Game History:** View and manage your list of games, with the ability to **Delete** (🗑️) older or concluded records.
- **Flexible Game Structure:** Support for both **Halves** or **Quarters** with customizable period durations.
- **Advanced Scoring:** 
    - Log 1, 2, or 3-point plays for individual players.
    - Manual score adjustments (including negative corrections) for both your team and the opponent.
- **Foul Tracking:** Keep a running count of fouls per player with quick increment/decrement controls.
- **Game Clock:** Interactive **Countdown** timer with start, stop, and **Reset** (🔄) functionality. Supports manual time entry for precise synchronization.
- **Period Management:** Change the starting lineup at the beginning of each period (Half or Quarter).
- **Post-Game Editing:** Correct mistakes after the buzzer by editing or deleting events (scores, fouls, etc.) directly from the event log.
- **End Game:** Formally conclude games to finalize stats and prevent accidental further edits.

### 🏆 MVP Voting
- **Live Voting:** Cast MVP votes for players during the game directly from their action cards.
- **Tallying:** View the total vote count for each player in the game stats section.

### 🏋️ Training Section (AI-Powered)
- **Age-Appropriate Drills:** Access drills tailored for specific age groups, from Mini Ball (Ages 5-6) to Under 18's.
- **AI Integration:** Uses Google Gemini AI to generate customized basketball drills on-demand.
- **Teaching Points:** Each drill includes a clear description and key teaching points for coaches.

### 📊 Data & Exports
- **Persistence:** All data is saved locally using Room Database. The project is configured with schema tracking and Android Auto Backup to ensure your team roster and game history are preserved across app updates and device transfers.
- **CSV Export:** 
    - Export the full Team Roster.
    - Export detailed Game Logs including every point, foul, substitution, and MVP vote.
    - Files are saved to the device's public Downloads/Documents folder for easy sharing.

### 🎨 Modern UI/UX
- **Adaptive Layout:** Responsive design that auto-adjusts for **Phones** and **Tablets**.
    - **Phones:** Bottom navigation for easy thumb access.
    - **Tablets/Foldables:** Navigation rail and multi-column layouts to leverage larger screen real estate.
- **Material 3 Design:** Clean, modern interface with intuitive navigation.
- **Basketball Theme:** Custom orange-and-black basketball themed app icon and themed UI elements.
- **Real-time Feedback:** Instant UI updates for timer shifts and scoring events.

## Technical Details
- **Architecture:** MVVM (Model-View-ViewModel)
- **UI Framework:** Jetpack Compose
- **Database:** Room (SQLite) with multi-version migration support.
- **Concurrency:** Kotlin Coroutines & Flow for reactive UI updates.

---
*Developed to help coaches focus on the game, not the clipboard.*
