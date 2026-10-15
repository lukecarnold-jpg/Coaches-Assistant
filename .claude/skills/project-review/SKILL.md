---
name: project-review
description: Run a project-wide review of Coaches Assistant on Opus and return a priority list of bugs and improvements. Use when the user asks for a project review, a bug list, or a prioritised list of improvements.
---

# Project review (Opus)

Run the review as an Agent with `model: "opus"` (Opus 5.5, `claude-opus-5-5`), not the current model. Do not fix anything; this skill only reports.

## Brief for the agent

Give the agent this task:

1. Read `CLAUDE.md`, `README.md`, `STRUCTURE.md`, `DEVELOPMENT_HISTORY.md` and `SECURITY.md`, then read the app source under `app/`, the build files, and `.github/workflows/android-ci.yml`.
2. Look for:
   - Correctness bugs: wrong game-clock, playing-time, scoring or substitution logic; Room migrations and transactions; undo; CSV and backup round trips.
   - Crash and data-loss risks: unhandled nulls or exceptions, failed writes, race conditions between the clock, the notification and the view models.
   - Security and privacy issues, checked against `SECURITY.md`: permissions, API key storage, network calls, logging, exports, CI token scopes and action pinning.
   - Test gaps: behaviour in the code with no test covering it.
   - Maintainability problems that will cause bugs.
   - Places where a doc contradicts the code.
3. For every finding, give `file:line`, what goes wrong and when, the evidence, and a confidence level. Mark anything it could not confirm from the code as unverified.
4. Return two lists, ranked most-severe first:
   - **Bugs:** P0 (data loss, crash, or security), P1 (wrong behaviour), P2 (minor).
   - **Improvements:** ranked by value against effort.

Ask the agent to keep the report under about 1,500 words and to skip style nits that ktlint already enforces.

## Reporting back

Relay the agent's priority lists to the user as they are. Note any finding that conflicts with a project doc, and say which docs you did not check if the agent skipped any.
