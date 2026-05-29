# UI 中文国际化清理 - Orchestration Index

## Goal
全面排查并清理应用用户可接触 UI 范围内所有未翻译的英文文本，确保中文环境下用户体验的一致性。
排查范围覆盖：strings.xml 资源、8 个 feature mode 插件、app 模块枚举标签/Toast/内容描述、core 模块展示标签。

## User Entry Points
- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy
- Main checkout: /Volumes/Extreme_SSD/project/open_camera
- Coordinator plan root: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup
- Mainline branch: main
- Integration branch: agent/ui-i18n-cleanup/integration
- Functional package branches: `agent/ui-i18n-cleanup/<package-id>`
- Implementation isolation: one worktree per functional package.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches.

## Authorization
Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash <plan-root>/launchers/orchestrate.sh mark-state ...`.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path.
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after all finalize steps succeed.

Forbidden without explicit user approval:
- force-push, hard reset, delete unrecorded branches/worktrees, delete remote branches, add secrets, edit outside allowed paths.

## Dependency Graph
| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-strings-xml | none | status | completed | 1 |
| 02-feature-mode-i18n | 01-strings-xml | status+code | 01 completed (new string IDs available) | 2 |
| 03-app-core-i18n | 01-strings-xml | status+code | 01 completed (new string IDs available) | 2 |
| 99-finalize | 01-strings-xml, 02-feature-mode-i18n, 03-app-core-i18n | status+code | all functional packages completed | final |

## Merge Strategy
- Functional merge order: 01-strings-xml, then {02-feature-mode-i18n, 03-app-core-i18n} in any order
- Code dependency policy: 02 and 03 depend on new string IDs from 01; merge 01 first, then merge 02 and 03
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

## Capability Preflight
| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-strings-xml | autonomous | Claude Code | n/a | grep confirms no English in values/strings.xml; build passes | none | normal graph |
| 02-feature-mode-i18n | autonomous | Claude Code | n/a | grep confirms no hardcoded English in feature/*Plugin.kt; build passes | none | normal graph |
| 03-app-core-i18n | autonomous | Claude Code | n/a | grep confirms no hardcoded UI English in app/ and core/; build passes | none | normal graph |
| real-device-visual-qa | external-assist | user | requires physical device to visually verify no English in UI | diff summary, changed-file list, APK path | user visual confirmation on device | release only |

## Audit Summary (80+ issues across 3 dimensions)

### Scale
- **8 feature mode plugin files** — each with 5–25 hardcoded English UI strings (titles, shutter labels, status headlines, profile/preset labels)
- **2 app module files** — SessionUiRenderModel.kt (19 filter control enum labels, 4 mode labels, 4 filter level labels), MainActivity.kt (4 Toast messages)
- **4 core module files** — DeviceContracts.kt, MediaTypes.kt, LivePhotoContracts.kt (lens labels, quality labels, live photo display labels)
- **2 strings.xml files** — values/strings.xml (1 English format string), values-en/strings.xml (needs sync)

### Key Reference: Existing Chinese Translations in strings.xml
Most enum labels already have Chinese equivalents in `values/strings.xml`. The fix is to reference `R.string.xxx` via context instead of using the hardcoded English enum `.name` property. Key mappings:

| Enum Hardcoded English | strings.xml Key | Chinese |
|---|---|---|
| "Photo" | button_photo_mode | 拍照 |
| "Humanistic" | button_humanistic_mode | 人文 |
| "Portrait" | button_portrait_mode | 人像 |
| "Video" | button_video_mode | 视频 |
| "Document" | button_document_mode | 文档 |
| "Scenery" | button_night_mode | 风景 |
| "Pro" | button_pro_mode | 专业 |
| "Full Clear" | button_fullclear_mode | 全清 |
| "Exposure" | filter_ctrl_exposure | 曝光 |
| "Soft Glow" | filter_ctrl_soft_glow | 柔光 |
| "Halo" | filter_ctrl_halo | 光晕 |
| "Grain" | filter_ctrl_grain | 颗粒 |
| "Sharpness" | filter_ctrl_sharpness | 锐度 |
| "Vignette" | filter_ctrl_vignette | 暗角 |
| "Highlights" | filter_ctrl_highlights | 高光 |
| "Shadows" | filter_ctrl_shadows | 阴影 |
| "Warm Boost" | filter_ctrl_warm_boost | 暖色增强 |
| "Cool Boost" | filter_ctrl_cool_boost | 冷色增强 |
| "Temp Shift" | filter_ctrl_temp_shift | 色温偏移 |
| "Tint Shift" | filter_ctrl_tint_shift | 色调偏移 |
| "Fast" | button_still_fast | 快速 |
| "Max" | button_still_max | 最高 |
| "Off" | label_off | 关 |
| "On" | label_on | 开 |
| "Wide" | (new needed) | 广角 |
| "Telephoto" | (new needed) | 长焦 |
| "Periscope" | (new needed) | 潜望 |
