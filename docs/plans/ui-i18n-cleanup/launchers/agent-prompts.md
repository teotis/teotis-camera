# Agent Prompts

## Package: 01-strings-xml - strings.xml 资源审计与补全

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/01-strings-xml.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/01-strings-xml.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 01-strings-xml`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You are executing Package 01-strings-xml. Read the package doc for full details. Summary:

1. Fix `format_color_tone` in `app/src/main/res/values/strings.xml` from `Color: %.2f, Tone: %.2f` to `颜色: %.2f, 色调: %.2f`
2. Add ~80 new string resources to `values/strings.xml` (Chinese) with all keys listed in Part B of the package doc
3. Add the same ~80 new string keys to `values-en/strings.xml` with English values (Part C of the package doc)
4. Run verification commands to confirm:
   - No English text in values/strings.xml (exempt app_name)
   - Key sets identical between values/ and values-en/
   - No duplicate keys
   - XML well-formed

You may edit only:
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

Do not edit any Kotlin files, layout XML, or other resources.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`
- Fill evidence fields

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 01-strings-xml completed --commit <commit-sha> --verification "<command: result>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 01-strings-xml
```

---

## Package: 02-layout-xml - Feature Mode 插件 i18n 清理

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/02-layout-xml.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/02-layout-xml.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 02-layout-xml`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You are executing Package 02-layout-xml. Read the package doc for full details. Summary:

Fix hardcoded English UI strings in all 8 feature mode plugin files:
- DocumentModePlugin.kt (20+ strings)
- FullClearModePlugin.kt (4 strings)
- HumanisticModePlugin.kt (20+ strings)
- NightModePlugin.kt (10 strings)
- PhotoModePlugin.kt (10+ strings)
- PortraitModePlugin.kt (10+ strings)
- ProModePlugin.kt (10+ strings)
- VideoModePlugin.kt (10 strings)

For each file:
1. Read the ModeContext API to determine if Android Context is accessible
2. If accessible: use `context.getString(R.string.xxx)` for all labels/headlines/titles
3. If NOT accessible: replace English hardcoded strings directly with Chinese text
4. Replace profile/preset labels with Chinese equivalents

You may edit only files under:
- `feature/mode-*/src/main/kotlin/` (8 plugin files specifically)

Do not edit:
- `app/src/main/res/values/strings.xml` (handled by Package 01)
- Any test files
- Any files under `app/` or `core/`

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 02-layout-xml completed --commit <commit-sha> --verification "<command: result>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 02-layout-xml
```

---

## Package: 03-kotlin-hardcoded - App + Core 模块 i18n 清理

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/03-kotlin-hardcoded.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/03-kotlin-hardcoded.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 03-kotlin-hardcoded`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You are executing Package 03-kotlin-hardcoded. Read the package doc for full details. Summary:

Fix hardcoded English UI strings in app and core modules:

**App module:**
- SessionUiRenderModel.kt: ModeLabel enum (4), FilterControlKind enum (12), FilterAdvancedControl.Level enum (4), "Focus" string
- MainActivity.kt: 4 Toast messages
- CockpitSurfaceRenderer.kt: 2 contentDescription strings

**Core module:**
- DeviceContracts.kt: LensType labels (3), ZoomControlKind labels (3), DeviceRuntimeIssueKind labels (8)
- MediaTypes.kt: quality labels (2)
- LivePhotoContracts.kt: live photo labels (8)

For enums: add `val labelResId: Int` property pointing to `R.string.xxx` (defined by Package 01)
For Toast/strings: use `getString(R.string.xxx)`
For core module: if Android R class unavailable, use Chinese hardcoded strings

You may edit only:
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaTypes.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt`

Do not edit strings.xml or feature/ files.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 03-kotlin-hardcoded completed --commit <commit-sha> --verification "<command: result>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 03-kotlin-hardcoded
```

---

## Package: 99-finalize - 集成验证与合并

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You are executing Package 99-finalize. Read the package doc for full details. Summary:

1. Run `bash launchers/orchestrate.sh verify-finalize` for pre-merge check
2. Create integration branch: `agent/ui-i18n-cleanup/integration`
3. Merge package branches in order: 01-strings-xml, 02-layout-xml, 03-kotlin-hardcoded
4. Run integration verification
5. If external QA not required (release not blocked): merge to mainline
6. Clean up local package branches/worktrees
7. Write FINAL_REPORT.md and status/99-finalize.md

On failure: record stage, command, branch, conflict files, log summary, recovery suggestion.
On success: mark finalized, record merge commits, verification summary, cleanup results.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 99-finalize finalized --commit <merge-commit-sha> --verification "<verification summary>"
```
