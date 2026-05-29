# UI 中文国际化清理 - Orchestration Index

## Goal
全面排查并清理应用用户可接触 UI 范围内所有未翻译的英文文本，确保中文环境下用户体验的一致性。排查范围覆盖：strings.xml 资源、布局 XML 硬编码、Kotlin 源码硬编码字符串。

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

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration.

Forbidden without explicit user approval:
- force-push, hard reset, delete unrecorded branches/worktrees, delete remote branches, add secrets, edit outside allowed paths.

## Dependency Graph
| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-strings-xml | none | status | completed | 1 |
| 02-layout-xml | none | status | completed | 1 |
| 03-kotlin-hardcoded | none | status | completed | 1 |
| 99-finalize | 01-strings-xml, 02-layout-xml, 03-kotlin-hardcoded | status+code | all functional packages completed | final |

## Merge Strategy
- Functional merge order: 01-strings-xml, 02-layout-xml, 03-kotlin-hardcoded
- Code dependency policy: status dependency (each package is independent)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions
- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.

## Capability Preflight
| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-strings-xml | autonomous | Claude Code | n/a | build passes, grep confirms no English in values/strings.xml | none | normal graph |
| 02-layout-xml | autonomous | Claude Code | n/a | grep confirms no hardcoded English android:text | none | normal graph |
| 03-kotlin-hardcoded | autonomous | Claude Code | n/a | grep confirms no hardcoded English UI strings | none | normal graph |
| visual-qa | external-assist | user | requires device/emulator to visually verify no English in UI | checklist of changed files, diff summary | screenshot/video of Chinese UI | release only |

## Already Identified Issues (Investigation Summary)

### A. strings.xml (values/strings.xml) - 英文残留
| Line | Key | Current Value | Expected Chinese |
|---|---|---|---|
| 285 | format_color_tone | `Color: %.2f, Tone: %.2f` | `颜色: %.2f, 色调: %.2f` |

### B. Layout XML (activity_main.xml) - 硬编码英文
| Line | Attribute | Current Value | Expected Chinese |
|---|---|---|---|
| 852 | android:text | `Depth` | `景深` (应使用 @string 引用) |

### C. Kotlin 源码 - 硬编码英文字符串
| File | Line | Current Value | Expected Chinese |
|---|---|---|---|
| MainActivity.kt | 788 | `"Debug log exported: ${file.absolutePath}"` | `"调试日志已导出: ${file.absolutePath}"` |
| MainActivity.kt | 791 | `"Export failed"` | `"导出失败"` |
| MainActivity.kt | 801 | `"Cleanup failed"` | `"清理失败"` |
| MainActivity.kt | 811 | `"Cleanup failed"` | `"清理失败"` |
| SessionUiRenderModel.kt | 189 | `"Soft Glow"` | `"柔光"` |
| SessionUiRenderModel.kt | 196 | `"Warm Boost"` | `"暖色增强"` |
| SessionUiRenderModel.kt | 197 | `"Cool Boost"` | `"冷色增强"` |
| SessionUiRenderModel.kt | 198 | `"Temp Shift"` | `"色温偏移"` |
| SessionUiRenderModel.kt | 199 | `"Tint Shift"` | `"色调偏移"` |
| SessionCockpitRenderModel.kt | 743 | `"Zoom unavailable"` | `"变焦不可用"` |

### D. AppTextResolver.kt 中需要确认的 fallback 英文
AppTextResolver 的 `str(resId, fallback)` 模式中，fallback 英文字符串在 strings.xml 有对应中文时不会显示。但以下几项中文翻译缺失或不一致，fallback 可能会意外暴露：
- `filterSectionFilters()` 和 `filterSectionAdvanced()` 在 AppTextResolver 中未定义但 strings.xml 有 `filter_section_filters` / `filter_section_advanced`
- `buttonQuickMore()` 同样有 strings.xml 但 AppTextResolver 中无对应方法
