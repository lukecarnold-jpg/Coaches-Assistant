# Security Policy

## Supported Versions

Only the latest release is supported with security fixes. Fixes land on `master` and ship in the next `vX.Y.Z` release. Builds from untagged commits (versions like `4.2.0-dev.3`) are development builds and aren't supported.

| Version | Supported          |
| ------- | ------------------ |
| 4.2.x   | :white_check_mark: |
| 4.1.x   | :x:                |
| 4.0.x   | :x:                |
| < 4.0   | :x:                |

If you're on an older version, update to the latest release before reporting. The problem may already be fixed.

[GitHub Releases](https://github.com/lukecarnold-jpg/Coaches-Assistant/releases) publish source code and release notes only, with no prebuilt APKs. If you find a Coaches Assistant APK on another site, it wasn't published through this repository.

## Reporting a Vulnerability

This repository is public. Please don't report a security problem in a public issue, pull request, or discussion, since anyone can read them. If you have a fix, send it with your report rather than opening a pull request, so it can ship before the details are public.

Report privately in one of these ways:

- **Preferred:** [report a vulnerability on GitHub](https://github.com/lukecarnold-jpg/Coaches-Assistant/security/advisories/new). Only you and the maintainer can see the report. A fix can be developed in a private fork, and an advisory is published when it ships.
- **Email:** send it to **lukec.arnold@gmail.com** with a subject starting with `[Security]`.

Either way, please include:

- a description of the problem and its impact,
- the app version (shown in Android's app info for Coaches Assistant) or commit, and the Android version and device you tested on,
- steps to reproduce, and a proof of concept if you have one.

Please don't include real API keys or real player information in a report. Revoke any key you used for testing, and use made-up names in rosters.

This is a small, volunteer-maintained project, so responses are best-effort. I'll acknowledge a report as soon as I can and keep you updated while I work on a fix. If you'd like, I'll credit you in the release notes. If a report is declined, I'll explain why.

### Disclosure

Please give me a reasonable amount of time to release a fix before you disclose the problem publicly. I'll aim to agree on a date with you. Once a fix ships, a [GitHub security advisory](https://github.com/lukecarnold-jpg/Coaches-Assistant/security/advisories) and the [release notes](https://github.com/lukecarnold-jpg/Coaches-Assistant/releases) will describe the problem and which versions were affected.

### Good-faith research

Test against your own installation and your own data. Don't access other people's devices or data, and don't attack the AI providers' services. Research done this way, and reported as described above, is welcome.

## Scope

In scope: the Android app in this repository, including:

- how it stores API keys and local data,
- its network requests to AI providers,
- backup, restore, and CSV import and export,
- its exported components and permissions,
- its build, signing, and CI configuration.

Out of scope:

- The AI providers' own services (Google Gemini, Anthropic, OpenAI, or any other endpoint you configure). Report those to the provider.
- A server you run yourself on your local network (for example, Ollama) and its configuration.
- Problems that need a rooted device, an unlocked device in an attacker's hands, or a malicious app already installed on the device.
- Debug builds. They aren't distributed and may contain a developer's API key (see below).
- Vulnerabilities in third-party libraries that have no effect on this app. Please report those upstream.

## Security Design

These are the properties the app is built to keep, so a report that breaks one of them is especially useful.

### Data and privacy

- **No backend.** Rosters, games, and notes stay on the device. The app has no accounts, advertising, analytics, or crash reporting. See the [privacy policy](docs/privacy-policy.md).
- **Minimal network traffic.** The only network traffic is drill generation. It sends the selected sport, age group, and app language, plus your API key, to the provider you chose. No player names, game data, or notes are sent.

### API keys

- **Encrypted on the device.** Keys are encrypted with AES-256-GCM using a key held in the Android Keystore that can't be exported.
- **Never backed up.** Keys are excluded from Android cloud backups, device transfers, and the app's own backup files, so a key never leaves the device where it was entered.
- **Sent only in request headers.** Keys go in request headers, never in URLs, so they don't end up in server or proxy logs as part of a URL.
- **No keys in release builds.** The optional `GEMINI_API_KEY` from `local.properties` is only compiled into debug builds. Release APKs and bundles don't contain a provider key.

### Network

- **Plain HTTP only on the local network.** The app allows cleartext at the platform level only so the "Other" provider can reach a server on the local network, such as Ollama on a laptop. The shared HTTP client refuses any plain `http://` request, including a redirect, to a host that isn't on the local network.
- **HTTPS for the built-in providers.** Every built-in provider uses HTTPS.
- **Local network permission on Android 17+.** The app asks for local network access only when the configured endpoint is on the local network.

### Platform

- **One exported component.** The launcher activity is the only exported component. The notification receivers and the substitution reminder service aren't exported.
- **Permissions only when needed.** Notifications, local network access, and Do Not Disturb access are requested only when you turn on the feature that needs them.
- **Exported CSVs are safe to open in a spreadsheet.** Values that start with `=`, `+`, `-`, or `@` are written with a leading `'`, so a spreadsheet shows them as text instead of running them as formulas. Re-importing a roster removes the `'` again.

### Build and release

- **Signing keys stay out of the repository.** Release builds are signed with an upload key kept outside the repository. On Google Play, Play App Signing holds the app-signing key.
- **Signing secrets aren't exposed to pull requests.** CI uses the signing secrets only on pushes to `master`, never on pull request builds. Pull requests from forks run CI with no access to repository secrets.
- **Read-only CI token.** The CI workflow sets its GitHub token to read-only itself, rather than relying on the repository's default.
- **Pinned CI actions.** Every GitHub Action is pinned to a full commit SHA, so a moved or compromised tag can't change the code that runs next to the signing keystore.
- **No secrets in the history.** When the repository was made public (October 2026), its full history, including every branch, was checked for API keys, keystores, and `local.properties`, and none were found. `local.properties` is git-ignored.
- **Secret scanning.** GitHub secret scanning and push protection are on, so a push that contains a recognized credential is blocked.
- **Minified release builds.** Release builds are shrunk and obfuscated with R8.
- **Dependency updates.** Dependabot opens weekly update PRs for Gradle dependencies and GitHub Actions (including the pinned SHAs), and Dependabot security updates are on. CI tests each PR.
- **Verified Gradle download.** The Gradle wrapper checks the SHA-256 checksum of the Gradle distribution before using it.

## Known Limitations

These are deliberate trade-offs, not vulnerabilities. Reports that show a practical way to exploit one are still welcome.

- **Local data isn't encrypted by the app.** The database and settings rely on Android's app sandbox and the device's file-based encryption. A locked, encrypted phone protects them, while a rooted or unlocked one doesn't.
- **Android backups include rosters and games.** If Android backup is turned on, the database is included in the device's backup, protected however Android protects that backup. API keys are not included.
- **Exported files are plain text.** Backup files, CSVs, and notes you export aren't encrypted. They contain player names and game details, so store and share them with care.
- **The local network check is name-based.** It looks at the host name without resolving it, so a name on a private domain, or a bare single-label name, could still point at a public address. It guards against mistakes rather than guaranteeing that plain HTTP stays on the local network.
