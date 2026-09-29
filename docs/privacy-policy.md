# Coaches Assistant — Privacy Policy

_Effective: [DATE]_

Coaches Assistant is an app for sports coaches to manage team rosters, track games, and generate training drills. This policy explains what information the app handles and where it goes.

## Summary

- Your rosters, games, and notes stay on your device.
- The app has no accounts, no advertising, no analytics, and no crash reporting.
- The only information sent over the internet is when you generate training drills: the sport and age group you pick go to the AI provider you chose, using your own API key.

## Information stored on your device

The app stores what you enter, only on your device:

- **Rosters:** player names, and optionally jersey numbers and positions.
- **Games:** scores, substitutions, fouls, playing time, attendance, MVP votes, and coaching notes.
- **Settings:** your chosen sport, display preferences, and AI provider settings. API keys you enter are encrypted on the device.

This information is not sent to us or to anyone else. We do not operate a server and cannot see your data.

## Training drills and AI providers

When you tap **Generate AI Drills**, the app sends a request to the AI provider you selected in Settings (Google Gemini, Anthropic Claude, OpenAI ChatGPT, or another OpenAI-compatible service you configure). The request contains:

- the **sport** and the **age group** you selected, in a short instruction asking for drills; and
- **your API key** for that provider, which it needs to accept the request.

No player names, game data, notes, or other information from the app is included. The provider's own privacy policy governs how it handles these requests. If you configure a server on your local network (for example, one you run yourself), the request goes only to that server.

## Backups and exports

- **Android backup:** if you have Android's backup turned on, your rosters and games may be included in your device's backup (for example, to your Google account) and restored to a new device, like other apps' data. API keys and settings are not included.
- **Files you create:** backups, roster CSVs, game CSVs, and notes you export are saved where you choose. What happens to those files afterwards is up to you.

## Permissions

- **Internet:** to reach the AI provider when you generate drills.
- **Local network access (Android 17 and later):** only requested if you set up an AI server on your local network, so the app can reach it.
- **Notifications (Android 13 and later):** only requested if you turn on the game clock notification, to show the running clock.
- **Do Not Disturb access:** only requested if you use Game Day mode, so the app can silence notifications during a game. The app turns on its own "Game day" mode and never reads your notifications.

## Children

The app is intended for coaches. Coaches may enter the names of players, who may be children; that information stays on the coach's device as described above and is never transmitted by the app.

## Deleting your data

You can delete players, games, or all data at any time in **Settings → Delete data**. Uninstalling the app removes everything it stored on the device (other than files you exported or backups you made).

## Changes

If this policy changes, the new version will be posted at this address with a new effective date.

## Contact

Questions about this policy: [CONTACT EMAIL]
