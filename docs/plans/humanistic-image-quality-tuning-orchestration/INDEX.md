# Humanistic Image Quality Tuning — Orchestration Index

## Goal

研究并固化 OpenCamera 在 vivo 真机样张对比后暴露出的长期成片调校方向：不承诺追平系统相机的底层 ISP、HDR、多帧和厂商算法能力，而是把"更稳的曝光、更低噪的暗部、更自然的高光压制、更有辨识度但不过度的色彩曲线"转译成 OpenCamera 可实现、可降级、可验证的人文模式 / 滤镜 / Color Lab 风格目标、管线边界和真机验收协议。

## User Entry Points

- **Manual**: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- **Script**: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- **Status**: run `bash launchers/orchestrate.sh status`.
- **Retry**: run `bash launchers/orchestrate.sh retry <package-id>`.
- **Manual advancement fallback**: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/humanistic-image-quality-tuning-orchestration`
- Mainline branch: `main`
- Integration branch: `integration/humanistic-iq-tuning`
- Functional package branches: `agent/humanistic-iq-tuning/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths (own status file).
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
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

### Stop Gates — Must Ask

STOP and ask the user before:
- Editing runtime code, tests, resources, Gradle files, or shared project docs outside your assigned status file.
- Claiming OpenCamera can match vivo system camera, BlueImage/ISP behavior, multi-frame HDR, or vendor tuning.
- Adding network dependencies, SDKs, models, secrets, or external API calls.
- Moving ownership across UI / Session Kernel / Device Adapter / Media Pipeline boundaries.
- Creating a second hidden session kernel or making UI directly drive camera runtime behavior.
- Turning taste judgments into deterministic pass/fail without a user/Codex real-device review owner.
- Crossing Stage boundaries or declaring Stage 7 complete.
- Touching another package's status file or editing `INDEX.md`.
- Running destructive git operations: force-push, hard reset, deleting branches/worktrees.

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-current-iq-gap-audit | none | status | completed | 1 |
| 02-style-target-scorecard | none | status | completed | 1 |
| 03-feasible-rendering-pipeline-design | 01-current-iq-gap-audit, 02-style-target-scorecard | status | completed | 2 |
| 04-real-device-capture-protocol | 03-feasible-rendering-pipeline-design | status | completed | 3 |
| 05-implementation-roadmap | 04-real-device-capture-protocol | status | completed | 4 |
| 99-finalize | 01, 02, 03, 04, 05 | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01, 02, 03, 04, 05
- Code dependency policy: status dependency — all packages are read-only research that write only to their own status files; no code merge conflicts expected.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/01-current-iq-gap-audit.md` | 01-current-iq-gap-audit | 02, 03, 04, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/02-style-target-scorecard.md` | 02-style-target-scorecard | 01, 03, 04, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/03-feasible-rendering-pipeline-design.md` | 03-feasible-rendering-pipeline-design | 01, 02, 04, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/04-real-device-capture-protocol.md` | 04-real-device-capture-protocol | 01, 02, 03, 05, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/05-implementation-roadmap.md` | 05-implementation-roadmap | 01, 02, 03, 04, 99 |
| `docs/plans/humanistic-image-quality-tuning-orchestration/status/99-finalize.md` | 99-finalize | 01, 02, 03, 04, 05 |
| `core/settings/**`, `core/effect/**`, `core/device/**`, `core/session/**`, `feature/mode-humanistic/**`, `feature/mode-photo/**`, `app/src/**` | read-only for all packages | no package may edit without new user approval |
| `docs/plans/2026-05-25-color-lab*.md`, `docs/plans/2026-05-25-rendering-2-0*.md`, `docs/plans/2026-05-25-humanistic*.md`, `docs/plans/2026-05-24-vivo-x300*.md`, `docs/plans/2026-05-22-vivo-x300*.md` | read-only for all packages | no package may edit without new user approval |

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-current-iq-gap-audit.md](packages/01-current-iq-gap-audit.md) | code/product audit agent | none | safe with 02 | Audit current Color Lab/Humanistic/rendering path against the real-device IQ symptoms |
| [02-style-target-scorecard.md](packages/02-style-target-scorecard.md) | product/style research agent | none | safe with 01 | Define realistic style targets and an image-quality scorecard without vendor overclaiming |
| [03-feasible-rendering-pipeline-design.md](packages/03-feasible-rendering-pipeline-design.md) | architecture design agent | 01, 02 | no | Design feasible exposure/noise/highlight/color/sharpness controls within existing boundaries |
| [04-real-device-capture-protocol.md](packages/04-real-device-capture-protocol.md) | QA planning agent | 03 | no | Define capture protocol, sample naming, scorecard, and failure thresholds for vivo comparison |
| [05-implementation-roadmap.md](packages/05-implementation-roadmap.md) | planning agent | 04 | no | Convert findings into scoped implementation packages and stop gates |
| [99-finalize.md](packages/99-finalize.md) | orchestrator | all functional packages | — | Final verification, integration branch merge, and cleanup |
