# Coaches Assistant - working agreement

Android app (Kotlin, Jetpack Compose, Room). See README.md for what the app does and STRUCTURE.md for where code lives.

## Keep the four project docs in step with every change

Any change that touches the app, its build, its CI, or its docs must update the affected documents **in the same change, before committing**. Don't leave doc updates for later or for a separate commit.

| File | Update it when a change... |
| --- | --- |
| `README.md` | changes what the app does, how to set it up or run it, how to test or release it, its requirements, its AI provider behaviour, or its known notes and risks |
| `STRUCTURE.md` | adds, moves, renames, or removes a file, package, screen, view model, repository, test, or build module, or changes how the runtime flow connects them |
| `DEVELOPMENT_HISTORY.md` | ships a user-visible feature, fix, or version change. Add an entry under the current or next version heading, written as plain milestones like the existing ones. Skip purely internal refactors unless they change behaviour |
| `SECURITY.md` | changes permissions, data storage or export, backups, network calls, AI API key handling, logging or crash reporting, signing, CI secrets or token scopes, third-party actions or dependencies, or the security contact |

- If a change touches none of these areas, say so in the commit summary rather than editing the docs for the sake of it.
- Keep each doc's existing headings and tone. Make the smallest accurate edit; don't rewrite sections that are still true.
- Don't record in these docs anything the code and git history already state with no added meaning (for example, a line-by-line diff summary).
- Before finishing a change, check each row of the table against the diff and say which docs you updated and which you checked and left alone.

## Project review with Opus

When the user asks for a project review (or a bug and improvement list), follow the `project-review` skill in `.claude/skills/project-review/SKILL.md`. It runs the review as an Agent on **Opus** (`claude-opus-5-5`) and returns a priority list of bugs (P0 to P2) and improvements. The review only reports; don't fix anything without being asked, and note any conflict with a doc rather than editing the doc silently.

## Verifying changes

- Formatting: `./gradlew spotlessCheck` (apply fixes with `./gradlew spotlessApply`).
- Tests: the unit and Robolectric tests run in CI (`.github/workflows/android-ci.yml`). Run them locally before committing when the change touches logic, data, or UI.
- The instrumented smoke test (`./gradlew connectedDebugAndroidTest`) needs an emulator. Say so if you couldn't run it.
- Report test and build results faithfully, including failures.

## Git

- Commit and push only when the user asks.
- Commit messages and PR descriptions end with the attribution lines given in the session's system reminder.
