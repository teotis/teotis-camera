# Zoom Cockpit V2 Productization — Orchestration Index

## Goal

Turn the existing `ApplyZoomRatio`, `FocalLengthSliderView`, zoom capability, and render-model foundation into a product-level Zoom Cockpit V2: preset dots plus continuous focal slider, one-decimal zoom/focal label, explicit supported/degraded/unsupported capability boundaries, and recording-state restrictions that do not create a second hidden camera runtime owner in UI.

## Execution Mode Recommendation

- Recommended mode: `AGENT_VIEW`
- Why: The work splits into four bounded packages across product contract, widget math, session/recording policy, and cockpit wiring. They are partly sequential but file ownership can stay clean, and manual Agent View dispatch is safer than auto-launch for a product design package that may need human taste checks.
- Alternatives rejected:
  - `SINGLE_AGENT` — viable, but lower auditability and more likely to blur product contract, widget behavior, and session policy.
  - `BACKGROUND_AGENT_SCRIPT` — provided as an optional launcher, but not the default because the user asked for research/design rather than automatic background dispatch.
  - `BATCH` — not a mechanical repo-wide transform.
  - `AGENT_TEAM` — unnecessary for direct implementation and higher token cost.
- Max parallel agents: 2
- Codex-retained work: final integration audit, product taste judgment, real-device smoke, and any decision about whether recording should allow continuous zoom on a specific device.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:

- Read the plan, index, and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches when available as incremental, non-destructive operations.
- Write to ONLY your assigned `status/<package-id>.md` file. Never edit this `INDEX.md` or another package's status file.

## Stop Gates — Must Ask

STOP and ask the user before:

- Crossing project stage boundaries or moving zoom runtime ownership out of Session Kernel / Device Adapter.
- Claiming true optical focal length, lens equivalence, 100x behavior, or vendor/private zoom capability without device evidence.
- Product-level decisions where requirements are genuinely ambiguous, especially active-recording zoom behavior on real hardware.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees, or deleting user changes.
- Network access, external API calls, or adding secrets/credentials.
- Overwriting unrelated dirty changes outside your assigned Allowed Paths.
- Fixing verification failures when the fix expands scope beyond your package.

## Completion Policy

After completing your assigned package:

- Write your evidence pack to `status/<package-id>.md`. Do NOT edit `INDEX.md`.
- Merge, push, or create a PR as the final step if your environment supports it and no stop gate applies.
- Report: what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
| --- | --- | --- | --- | --- |
| G1 | 01-product-contract-capability-boundary, 02-slider-widget-productization | yes | none | low-medium; 01 owns render contract, 02 owns widget/math. |
| G2 | 03-session-recording-zoom-policy | no | 01 complete | medium; session behavior must consume the product/capability policy. |
| G3 | 04-cockpit-wiring-and-ux-integration | no | 01, 02, 03 complete | high; integrates shared cockpit renderer/layout/callbacks. |
| G4 | 99-integration-audit | no | all implementation packages complete | Codex retained final audit. |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` zoom capability fields/helpers only | 01-product-contract-capability-boundary | 02, 04; 03 may read and extend only if 01 leaves an explicit hook. |
| `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` zoom/focal render models only | 01-product-contract-capability-boundary | 02, 03; 04 may consume fields but should not redefine model semantics. |
| `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` zoom/focal sections only | 01-product-contract-capability-boundary | 02/04 may add narrowly scoped tests after 01 lands. |
| `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt` | 02-slider-widget-productization | 01, 03, 04 must not edit. |
| `app/src/main/java/com/opencamera/app/FocalLengthSliderMath.kt` if introduced | 02-slider-widget-productization | 01, 03, 04 must not edit. |
| `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt` and `FocalLengthSliderMathTest.kt` if introduced | 02-slider-widget-productization | 01, 03, 04 must not edit. |
| `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` zoom handlers only | 03-session-recording-zoom-policy | 01, 02, 04 must not edit. |
| `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` zoom/recording tests only | 03-session-recording-zoom-policy | 01, 02, 04 must not edit. |
| `app/src/main/java/com/opencamera/app/gesture/GesturePolicy.kt` pinch zoom branch only | 03-session-recording-zoom-policy | 01, 02, 04 must not edit without coordination. |
| `app/src/test/java/com/opencamera/app/gesture/GesturePolicyTest.kt` zoom sections only | 03-session-recording-zoom-policy | 01, 02, 04 must not edit. |
| `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` focal/zoom render methods only | 04-cockpit-wiring-and-ux-integration | 01, 02, 03 must not edit. |
| `app/src/main/java/com/opencamera/app/MainActivity.kt` zoom callback wiring only | 04-cockpit-wiring-and-ux-integration | 01, 02, 03 must not edit. |
| `app/src/main/res/layout/activity_main.xml` focal slider and zoom cockpit region only | 04-cockpit-wiring-and-ux-integration | 01, 02, 03 must not edit. |
| `docs/plans/zoom-cockpit-v2-productization-orchestration/status/<package-id>.md` | assigned package | Other packages must not edit. |
| `docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md` | Codex/integration auditor only | Package agents must not edit. |

## Agent Budget

- Recommended Claude Code agents: 4 implementation agents plus Codex final audit.
- Max parallel agents: 2.
- Codex usage: final audit, real-device smoke, product wording/taste, and conflict resolution if package evidence disagrees.
- When to pause: any package edits outside allowed paths, any package claims optical/focal-length hardware facts without capability evidence, or focused zoom tests fail after a package claims completion.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File |
| --- | --- | --- | --- | --- |
| 01-product-contract-capability-boundary | agent-view | zoom-v2-01-product-contract | `launchers/agent-view-prompts.md#package-01-product-contract-capability-boundary` | `status/01-product-contract-capability-boundary.md` |
| 02-slider-widget-productization | agent-view | zoom-v2-02-slider-widget | `launchers/agent-view-prompts.md#package-02-slider-widget-productization` | `status/02-slider-widget-productization.md` |
| 03-session-recording-zoom-policy | agent-view | zoom-v2-03-session-policy | `launchers/agent-view-prompts.md#package-03-session-recording-zoom-policy` | `status/03-session-recording-zoom-policy.md` |
| 04-cockpit-wiring-and-ux-integration | agent-view | zoom-v2-04-cockpit-wiring | `launchers/agent-view-prompts.md#package-04-cockpit-wiring-and-ux-integration` | `status/04-cockpit-wiring-and-ux-integration.md` |
| 99-integration-audit | codex | — | `validation/final-audit-prompt.md` | `status/99-integration-audit.md` |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| 01-product-contract-capability-boundary | — | pending | — | — | — | `status/01-product-contract-capability-boundary.md` |
| 02-slider-widget-productization | — | pending | — | — | — | `status/02-slider-widget-productization.md` |
| 03-session-recording-zoom-policy | — | pending | — | — | — | `status/03-session-recording-zoom-policy.md` |
| 04-cockpit-wiring-and-ux-integration | — | pending | — | — | — | `status/04-cockpit-wiring-and-ux-integration.md` |
| 99-integration-audit | Codex | pending | — | — | — | `status/99-integration-audit.md` |

## Merge Strategy

- Merge order: 01 and 02 first; then 03; then 04; final audit last.
- Rebase policy: each package should rebase or merge latest main/package base before final verification.
- Conflict owner: 04 resolves integration conflicts in cockpit renderer/layout/callback wiring while preserving 01/02/03 acceptance criteria.
- Final integration agent: Codex.
- Do not delete worktrees until final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion. Do NOT edit `INDEX.md` directly.

Evidence pack must include:

- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files, full list
- [ ] commands run, verification commands plus output summary
- [ ] test result summary, pass/fail counts
- [ ] commit hash / PR link
- [ ] unresolved risks, if any
- [ ] whether it touched only allowed paths, self-certify

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
| --- | --- | --- | --- | --- |
| [01-product-contract-capability-boundary.md](./packages/01-product-contract-capability-boundary.md) | implementation agent | none | parallel with 02 | Define product/capability/render semantics for dots, slider, label, unsupported/degraded states, and recording availability. |
| [02-slider-widget-productization.md](./packages/02-slider-widget-productization.md) | implementation agent | none | parallel with 01 | Productize `FocalLengthSliderView` math, continuous release, snap threshold, preset dot taps, and one-decimal label. |
| [03-session-recording-zoom-policy.md](./packages/03-session-recording-zoom-policy.md) | implementation agent | 01 | after 01 | Enforce zoom clamp/snap and recording policy in Session Kernel while keeping UI as dispatcher. |
| [04-cockpit-wiring-and-ux-integration.md](./packages/04-cockpit-wiring-and-ux-integration.md) | implementation agent | 01, 02, 03 | final implementation package | Wire render model and widget callbacks into the cockpit without using `ZoomRatioToggled` for exact controls. |
| [99-integration-audit.md](./packages/99-integration-audit.md) | Codex retained | all packages complete | — | Audit all evidence, run integration gates, and perform product/real-device acceptance guidance. |

## Recommended Execution Order

1. Launch `01-product-contract-capability-boundary` and `02-slider-widget-productization` in parallel.
2. Launch `03-session-recording-zoom-policy` after package 01 completes.
3. Launch `04-cockpit-wiring-and-ux-integration` after packages 01, 02, and 03 complete.
4. Run Codex final integration audit.

## Launch Options

- Option A: Agent View manual dispatch — copy prompts from `launchers/agent-view-prompts.md`.
- Option B: Background agent script — run `bash launchers/dispatch-claude-agents.sh g1`, then `g2`, then `g3`. The script creates `claude --bg --name` sessions and prints the `claude agents` command. It does not open Agents View by default; set `CLAUDE_OPEN_AGENT_VIEW=1` to open it after dispatch. Permission mode is inherited from Claude Code settings unless `CLAUDE_PERMISSION_MODE` is set.
- Option C: `/batch` — not recommended; this is not a mechanical migration.
- Option D: Final integration audit — give `validation/final-audit-prompt.md` to Codex after all status files are complete.

## Local CLI Check

- Local `claude --version`: `2.1.142 (Claude Code)`.
- Local `claude agents --help` confirms the `agents` command and options for `--cwd`, `--model`, `--effort`, `--permission-mode`, and `--setting-sources`.
- Official CLI reference checked: https://docs.anthropic.com/en/docs/claude-code/cli-usage. Local help may differ from official/background-agent behavior, so use Agent View as the default and the generated background script as an optional launcher.
