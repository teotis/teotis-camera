# Scene Mask Foundation Research - Orchestration Index

## Goal

研究并固化 OpenCamera 的 `Scene Mask / 主体识别基础能力` 设计方案：把 OPPO/vivo 式人像虚化、层次人像、主体保护、背景调色和景深滑杆转译成 OpenCamera 可验证、可降级、不过度宣称的能力合同。输出必须基于当前仓库已有 `SceneMask` 实现和外部官方资料，明确哪些能力是 `SUPPORTED`、`DEGRADED`、`UNSUPPORTED`，并给出下一步是否进入实现修复的判断。

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/scene-mask-foundation-research/integration`
- Functional package branches: `agent/scene-mask-foundation-research/<package-id>`
- Implementation isolation: one worktree per functional package unless the package stays strictly read-only and writes only its assigned coordinator status/state files.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands through `rtk`.
- Commit local package changes if they create implementation artifacts; pure research status updates do not require package commits.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-foundation-research-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy when package branches exist.
- Run integration verification.
- Merge the verified integration branch back to mainline only when there are package code branches to integrate and verification passes.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths
- edit runtime code or tests from this research orchestration unless the user separately authorizes implementation

## Research Baseline

- 当前仓库已经有 `core/media/.../SceneMaskContracts.kt`、`PreviewSceneMaskSource.kt`、`MlKitSelfiePreviewSceneMaskSource.kt`、`SavedPhotoSceneMaskProvider.kt`、`MlKitSavedPhotoSceneMaskProvider.kt`、mask-aware photo/portrait editor 接口和相关测试。研究 agents 必须先核实现状，不能假设它们尚未存在。
- 既有方案入口是 [`../2026-05-25-scene-mask-segmentation-index.md`](../2026-05-25-scene-mask-segmentation-index.md)，但当前代码落地可能已经偏离原计划，尤其要核查预览 mask、成片 mask、诊断诚实性、输出写回和边缘质量。
- 官方资料快照：
  - ML Kit Selfie Segmentation Android: beta，API 23+，bundled 约 4.5MB，Pixel 4 latency 约 25-65ms，支持 stream/single-image 和 raw-size mask。Source: https://developers.google.com/ml-kit/vision/selfie-segmentation/android
  - ML Kit Subject Segmentation Android: beta，API 24+，unbundled 约 200KB 但依赖 Google Play services 下载模型，可输出 foreground / multi-subject masks，官方说明当前只支持 static images，Pixel 7 Pro 平均约 200ms。Source: https://developers.google.com/ml-kit/vision/subject-segmentation/android
  - MediaPipe Image Segmenter Android: 可输出 category mask / confidence mask，适合未来通用语义扩展。Source: https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android
  - CameraX `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`: 只交付最新帧，分析跟不上时丢帧，适合 preview segmentation 防止积压。Source: https://developer.android.com/reference/androidx/camera/core/ImageAnalysis
  - OPPO 官方人像资料强调 scene layers、subject/scene separation、skin protection、adjustable bokeh；OpenCamera 可借鉴产品语义，但不能宣称等同厂商私有 pipeline。Sources: https://www.oppo.com/en/newsroom/stories/oppo-reno11-series-portrait-expert-lofficiel/ and https://www.oppo.com/en/newsroom/press/oppo-launches-reno15-series/
  - vivo X300 官方页面强调 ZEISS Multifocal Portrait 和不同焦段/样式 bokeh；OpenCamera 可映射为 profile + subject/background style + depth strength，但不能把 2D mask 伪装成真 depth。Source: https://www.vivo.com/en/products/activity/x300
- Claude Code local version previously checked for launcher design: `2.1.142 (Claude Code)`.

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-backend-capability-matrix | none | status | ready at start | 1 |
| 02-current-implementation-audit | none | status | ready at start | 1 |
| 03-product-architecture-design | 01-backend-capability-matrix, 02-current-implementation-audit | status | dependencies completed | 2 |
| 04-verification-real-device-protocol | 02-current-implementation-audit, 03-product-architecture-design | status | dependencies completed | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-backend-capability-matrix -> 02-current-implementation-audit -> 03-product-architecture-design -> 04-verification-real-device-protocol`
- Code dependency policy: status dependency by default; if a package creates code artifacts after separate user approval, downstream packages must base on the integration branch after upstream merge.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes; if no package code branches exist, finalize writes `FINAL_REPORT.md` and records no-op integration.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- A package claims `SUPPORTED` for Scene Mask behavior without source/test evidence.

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-backend-capability-matrix.md](packages/01-backend-capability-matrix.md) | research agent | none | safe with 02 | Compare ML Kit Selfie, ML Kit Subject, MediaPipe, CameraX constraints, and honesty labels |
| [02-current-implementation-audit.md](packages/02-current-implementation-audit.md) | code audit agent | none | safe with 01 | Audit current repo `SceneMask` implementation and tests against the intended contract |
| [03-product-architecture-design.md](packages/03-product-architecture-design.md) | design agent | 01, 02 | no | Translate OPPO/vivo-like product goals into OpenCamera architecture and UI/metadata semantics |
| [04-verification-real-device-protocol.md](packages/04-verification-real-device-protocol.md) | QA planning agent | 02, 03 | no | Define local gates, visual QA protocol, metrics, and failure examples |
| [99-finalize.md](packages/99-finalize.md) | finalize agent | all functional packages | no | Final integration, report, and cleanup decision |

