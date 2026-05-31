# App Language Switch Exposure - Orchestration Index

## Goal

Expose the existing app-language capability to users as a small, verifiable settings flow: persist the selected language, render a Common settings language control, apply the locale immediately, and leave real-device visual confirmation as an explicit external gate.

This is a narrow follow-up to [`../i18n-multi-language-system/INDEX.md`](../i18n-multi-language-system/INDEX.md). The broader i18n plan is still useful context, but current code inspection shows the user-facing language switch has not landed.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/app-language-switch-exposure/integration`
- Functional package branches: `agent/app-language-switch-exposure/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash <plan-root>/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes or intermediate artifacts only under their assigned scratch path from `bash <plan-root>/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-language-persistence-contract | none | status | completed | 1 |
| 02-settings-language-entry | none | status | completed | 1 |
| 03-language-switch-verification | 01-language-persistence-contract, 02-settings-language-entry | status+code | both upstream packages completed | 2 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01-language-persistence-contract, 02-settings-language-entry, 03-language-switch-verification
- Code dependency policy: status dependency for Wave 1; 03 verifies the integrated behavior after 01 and 02 complete.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Landing Strategy

- Primary landing path: language selection appears in Common settings, cycles between Chinese and English, persists through `PersistedSettingsSerializer`, and focused tests plus `:app:assembleDebug` pass.
- Preapproved fallback paths, in order:
  - `fallback-label-only`: if immediate locale re-render cannot be proven locally, land the persisted setting and visible control only when the UI truthfully says restart/reopen may be needed. Verification must still pass. This may merge only with a documented limitation.
  - `fallback-persistence-only`: if layout conflicts make UI unsafe, land only serializer/display-name tests as an independent candidate and leave UI exposure blocked. This may not be called feature success.
- Unacceptable degradation: hidden debug-only toggles, resetting language to Chinese on restart, storing language outside persisted settings, claiming real-device pass without device evidence, or adding a second settings/session owner.
- Abort conditions: AppCompat locale APIs are unavailable in the current dependency set, settings-panel layout cannot accept another Common control without unacceptable clipping, repeated identical verification failure occurs three times, or the implementation requires a broader settings redesign.
- Independent merge candidates if main plan fails:
  - `01-language-persistence-contract`: independent because it only repairs serialization/display-name contract and does not require UI layout changes. Standalone acceptance is serializer round-trip coverage and existing settings tests passing.

Allowed task-level outcomes:
- `landed`: primary path landed and verification passed.
- `landed-with-approved-fallback`: a preapproved fallback landed and verification passed.
- `ready-for-external-gate`: autonomous work is complete but declared real-device release confidence is still pending.
- `failed-no-merge`: the main plan failed, no fallback is approved, and nothing may be merged.
- `failed-with-candidate-independent-fixes`: the main plan failed, but predeclared independent fixes are available for separate review.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- Abort condition in Landing Strategy is met.
- A package identifies the main plan as non-landable and no preapproved fallback applies.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-language-persistence-contract | autonomous | Claude Code | n/a | core settings tests | none | normal graph |
| 02-settings-language-entry | autonomous | Claude Code | n/a | render-model/layout wiring tests + app unit tests | none | normal graph |
| 03-language-switch-verification | agent-verifiable substitute | Claude Code | cannot prove physical-device visual refresh | focused tests, resource key checks, assembleDebug, APK path/install command | none for merge | normal graph |
| real-device-language-switch-qa | external-assist | user/Codex/device owner | requires physical device and visual judgment across panels | APK path, install command, panel checklist | screenshot/video/log showing Chinese and English settings panels after toggling | release confidence only |
