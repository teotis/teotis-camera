# Latest Real-Device Vivo Feedback - Orchestration Index

## Goal

Turn the 2026-06-02 latest APK real-device findings into a tail-driven multi-agent execution package. The desired outcome is a more camera-like main screen and panel system: bottom controls use the available vertical space, zoom preview is governed by a discrete preview-window contract instead of laggy continuous preview-stream zoom, Dev logs become actionable for timing and device capability diagnosis, Quick/Style/Settings copy is localized and dismissible, blur-four-border watermark quality is restored, and vivo reference screenshots guide concrete UX choices without claiming vendor parity.

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Findings Captured

1. Bottom cockpit layout is too low: thumbnail and shutter sit near the bottom while there is unused space above and below the mode track.
2. Zoom regressed: the preview stream visibly zooms continuously and lags. Desired behavior separates preview window and capture frame. For example, if real lens nodes are `0.7x`, `1.0x`, and `3.0x`, keep preview at `0.7x` for `0.7-1.1`, at `1.0x` for `1.1-3.3`, at `3.0x` for `3.3-5.5`, and at `5.0x` for `5.5-10`; show the changing capture area with the frame overlay instead of forcing preview stream zoom.
3. Dev panel should merge `耗时` into `链路` and keep only the latter.
4. Dev logs are too terse and lack trigger time and location/source information.
5. Quick panel Live button defaults to off.
6. Quick panel watermark option still exposes English.
7. App startup/device probe should log supported camera count, focal/lens ranges, output pixels, degraded/unsupported capability information, and similar facts when machine-quality detection runs.
8. When Quick is open, tapping the preview area outside the panel does not dismiss it.
9. Style panel still has English residue.
10. Settings > Common lacks a language switch.
11. Blur-four-border watermark regressed: the four borders are no longer convincingly blurred content-derived borders.
12. vivo reference screenshots should guide optimization: task-local function panels, clear selected effect cards, preview-visible watermark/frame affordance, restrained controls, and direct dismiss/exit affordances.

## Projection Ownership

- `INDEX.md` owns static human intent, policy, authorization, landing strategy, capability gates, and the intended dependency contract.
- `launchers/package-graph.tsv` owns machine-readable dispatch topology. It must match the dependency intent in this INDEX.
- `status/state.tsv` owns current scheduler state. It must not be edited manually.
- `status/events.jsonl` owns append-only event history, retry accounting, and failure fingerprints.
- `status/<package-id>.md` owns human-readable evidence, risks, verification details, and blocker diagnosis.
- `scratch/` is temporary and non-authoritative; it cannot unlock dependencies or satisfy acceptance criteria by itself.
- If these projections disagree, treat the orchestration as invalid or blocked until repaired; do not infer success from one artifact alone.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/latest-real-device-vivo-feedback/integration`
- Functional package branches: `agent/latest-real-device-vivo-feedback/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/latest-real-device-vivo-feedback/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; inside assigned worktrees, Gradle must use `rtk ./scripts/run_isolated_gradle.sh ...`.
- Current main checkout has unrelated dirty/deleted files. Package agents must use assigned worktrees and must not repair or revert unrelated main-checkout changes.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes or intermediate artifacts only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- resolve unrelated main-checkout dirty state
- claim real-device visual/performance acceptance from unit tests alone

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-cockpit-bottom-layout | none | status | initial ready package | 1 |
| 02-zoom-preview-window-frame-contract | none | status | initial ready package | 1 |
| 03-dev-log-device-probe | none | status | initial ready package | 1 |
| 06-watermark-vivo-reference-polish | none | status | initial ready package | 1 |
| 04-quick-panel-behavior-defaults | 01-cockpit-bottom-layout | status | 01 completed | 2 |
| 05-style-settings-i18n-cleanup | 04-quick-panel-behavior-defaults | status+code | 04 completed; text/render conflicts understood | 3 |
| 07-real-device-acceptance-protocol | 01-cockpit-bottom-layout, 02-zoom-preview-window-frame-contract, 03-dev-log-device-probe, 04-quick-panel-behavior-defaults, 05-style-settings-i18n-cleanup, 06-watermark-vivo-reference-polish | status+code | all implementation packages completed | 4 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-cockpit-bottom-layout -> 02-zoom-preview-window-frame-contract -> 03-dev-log-device-probe -> 06-watermark-vivo-reference-polish -> 04-quick-panel-behavior-defaults -> 05-style-settings-i18n-cleanup -> 07-real-device-acceptance-protocol`
- Code dependency policy: packages 01/02/03/06 are independent first-wave changes. Package 04 follows 01 to reduce panel/layout conflicts. Package 05 follows 04 because both may touch text resources, settings render models, and panel routing.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Landing Strategy

- Primary landing path: all six implementation packages land with focused tests, `:app:assembleDebug`, and relevant Stage 7 verification passing; package 07 produces APK/install/log/screenshot checklist for device-owner confirmation.
- Preapproved fallback paths, in order:
  - `fallback-ui-copy-only`: if zoom or CameraX preview semantics are blocked by hardware/API constraints, still land independent cockpit, Quick/Style/Settings localization, Dev log UI, and watermark-copy fixes when their standalone tests pass. This is `landed-with-approved-fallback`, not full success.
  - `fallback-diagnostics-only`: if visual changes are too risky, land only device probe logging and Dev link-log enrichment. This may merge only as an independent diagnostics improvement with explicit user-facing behavior deferred.
  - `fallback-acceptance-doc-only`: if implementation conflicts become too large, package 07 may still land an updated real-device QA protocol, but this is not product success.
- Unacceptable degradation: driving CameraX directly from UI, creating a hidden session kernel, hard-coding vivo-only camera IDs without `supported/degraded/unsupported`, hiding English text by deleting controls, disabling Live globally just to make the default off, replacing blur border with a fixed pale frame, or claiming real-device pass without device evidence.
- Abort conditions: repeated identical terminal failure fingerprint three times, discovery that current CameraX/Camera2 APIs cannot support the preview-window contract and no fallback is approved, settings/i18n layout cannot expose language without unacceptable clipping, or implementation cost expands into a full UI redesign outside this request.
- Independent merge candidates if main plan fails:
  - `01-cockpit-bottom-layout`: independent UI geometry/layout fix with focused render/layout tests.
  - `03-dev-log-device-probe`: independent observability improvement with log/export tests.
  - `05-style-settings-i18n-cleanup`: independent localization/settings exposure if tests pass.
  - `06-watermark-vivo-reference-polish`: independent saved-rendering/preview-affordance fix if watermark tests pass.

Allowed task-level outcomes:

- `landed`: primary path landed and verification passed.
- `landed-with-approved-fallback`: a preapproved fallback landed and verification passed.
- `ready-for-external-gate`: autonomous work is complete but declared real-device confidence is still pending.
- `failed-no-merge`: the main plan failed, no fallback is approved, and nothing may be merged.
- `failed-with-candidate-independent-fixes`: the main plan failed, but predeclared independent fixes are available for separate review.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-cockpit-bottom-layout | autonomous | Claude Code | n/a | render-model/layout tests and assemble | none | normal graph |
| 02-zoom-preview-window-frame-contract | agent-verifiable substitute | Claude Code | real smoothness/lag requires physical multi-lens device | session/device/app tests, zoom mapping tables, APK | device video crossing zoom ranges | normal graph; release confidence external |
| 03-dev-log-device-probe | autonomous | Claude Code | n/a | log render/export tests and capability probe unit tests | none | normal graph |
| 04-quick-panel-behavior-defaults | agent-verifiable substitute | Claude Code | outside-tap feel needs real screen | route/render tests, focused app tests, APK | screenshot/video if release confidence required | normal graph |
| 05-style-settings-i18n-cleanup | agent-verifiable substitute | Claude Code | complete visual language QA needs device | resource audit, settings/render tests, assemble | screenshots of Chinese/English panels | normal graph |
| 06-watermark-vivo-reference-polish | agent-verifiable substitute | Claude Code | final border aesthetics require visual judgment | pixel tests, preview hint tests, saved-render tests | before/after saved image and preview screenshot | normal graph |
| 07-real-device-acceptance-protocol | agent-verifiable substitute | Claude Code | cannot perform physical-device QA locally | APK path, install commands, checklists, expected logs | none for implementation merge | normal graph |
| real-device-final-qa | external-assist | user/Codex device owner | requires latest APK installed on the phone and human visual judgment | package 07 evidence bundle | screenshots/video/log exports for all listed findings | final product confidence only unless user makes it release-blocking |

## vivo Reference Design Notes

- Image 1 suggests effect panels should be task-local, compact, and selected-state obvious. For this app, that means Style/Watermark/Quick controls should show concise localized labels, selected state, and no orphan English/internal IDs.
- Image 2 suggests watermark/frame selection must preview the chosen template directly in the camera surface; text/card options are secondary to visual confidence.
- The app should not copy vivo branding or proprietary visual design. Use the reference only to guide affordance clarity: selected cards, immediate preview, clear close/dismiss, and minimal mode-specific controls.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- Abort condition in Landing Strategy is met.
- A package identifies the main plan as non-landable and no preapproved fallback applies.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| [01-cockpit-bottom-layout.md](packages/01-cockpit-bottom-layout.md) | Rebalance bottom cockpit vertical layout and shutter/thumbnail placement | cockpit render/layout files |
| [02-zoom-preview-window-frame-contract.md](packages/02-zoom-preview-window-frame-contract.md) | Restore discrete preview-window behavior and frame-only capture-area indication | session/device/zoom/preview files |
| [03-dev-log-device-probe.md](packages/03-dev-log-device-probe.md) | Merge Dev timing into link logs, enrich log time/source, print device capability probes | diagnostics, Dev log, CameraX capability files |
| [04-quick-panel-behavior-defaults.md](packages/04-quick-panel-behavior-defaults.md) | Live default off, outside preview tap dismiss, localized Quick watermark | Quick/panel/settings files |
| [05-style-settings-i18n-cleanup.md](packages/05-style-settings-i18n-cleanup.md) | Remove Style English residue and expose language switch in Common settings | i18n/settings/style render files |
| [06-watermark-vivo-reference-polish.md](packages/06-watermark-vivo-reference-polish.md) | Restore blur-four-border quality and improve preview-visible watermark/frame affordances | watermark renderer/preview files |
| [07-real-device-acceptance-protocol.md](packages/07-real-device-acceptance-protocol.md) | Produce APK/install/log/screenshot checklist for device-owner validation | docs/status evidence only |
| [99-finalize.md](packages/99-finalize.md) | Merge, verify, report, and clean up after success | integration branch and coordinator files |
