# Agent Prompts — i18n Multi-Language System

---

## Package: 01-i18n-core-infrastructure — Core i18n Infrastructure

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/packages/01-i18n-core-infrastructure.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/01-i18n-core-infrastructure.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh scratch-path 01-i18n-core-infrastructure`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

## Task Summary

Fix three things in the i18n core infrastructure:

### 1. Persist `appLanguage` to SharedPreferences

In `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`:
- Add a key constant: `private const val KEY_APP_LANGUAGE = "common.appLanguage"`
- In `toMap()`: add `KEY_APP_LANGUAGE to settings.common.appLanguage.storageKey` to the returned map
- In `fromMap()`: restore `appLanguage` via `AppLanguage.fromStorageKey(values[KEY_APP_LANGUAGE]) ?: defaults.common.appLanguage` inside the `CommonSettings(...)` constructor call

### 2. Add Language Picker to Settings Panel

This involves changes across several files:

**2a. `AppTextResolver.kt`** — Add two helper methods:
```kotlin
open fun languageSettingLabel(): String = str(R.string.label_language, "Language")
open fun languageValueName(language: AppLanguage): String = when (language) {
    AppLanguage.ZH -> "中文"
    AppLanguage.EN -> "English"
}
```
Also fix the existing `languageDisplayName()` method (ZH branch currently returns `app_name` instead of "中文").

**2b. `SessionUiRenderContracts.kt`** — Check if `SettingsControlRenderModel` already supports everything needed. The existing `nextAction` field with `PersistedSettingsAction.UpdateAppLanguage(...)` should be sufficient.

**2c. `SessionUiRenderModel.kt`** — In `CommonSettingsSectionRenderModel`, add a new field:
```kotlin
val language: SettingsControlRenderModel
```
In `sessionSettingsPageRenderModel()`, build the language control:
```kotlin
language = SettingsControlRenderModel(
    label = text.languageSettingLabel(),
    value = text.languageValueName(settings.common.appLanguage),
    availability = SettingsControlAvailability.SUPPORTED,
    availabilityLabel = text.availabilityLabel(SettingsControlAvailability.SUPPORTED),
    supportLabel = "",
    nextAction = PersistedSettingsAction.UpdateAppLanguage(
        when (settings.common.appLanguage) {
            AppLanguage.ZH -> AppLanguage.EN
            AppLanguage.EN -> AppLanguage.ZH
        }
    )
)
```

**2d. `SettingsPanelRenderer.kt`** — In `renderPage()`, add a line to render the language control:
```kotlin
renderControl(views.language, model.commonSection.language, model.editingEnabled)
```
Place it after `selfieMirror` in the Common section.

**2e. `MainActivityViews.kt`** — You need a `Button` view binding for the language picker. Look at how existing controls like `gridMode`, `shutterSound`, `selfieMirror` are defined as `Button` properties inside the views class. Add a similar `language` button binding. If the layout XML needs updating, check `app/src/main/res/layout/activity_main.xml` for the pattern.

### 3. Fix `languageDisplayName()`

In `AppTextResolver.kt`, change line 591:
```kotlin
// Before:
AppLanguage.ZH -> str(R.string.app_name, "OpenCamera")
// After:
AppLanguage.ZH -> "中文"
```

## Verification

Run these commands and record results:
```bash
# Core settings tests
./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test

# App unit tests
./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest

# Full assemble
./gradlew --no-daemon :app:assembleDebug
```

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh mark-state 01-i18n-core-infrastructure completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh mark-state 01-i18n-core-infrastructure blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh advance --from 01-i18n-core-infrastructure
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh advance
```

---

## Package: 02-translation-audit-completeness — Translation Audit & Completeness

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/packages/02-translation-audit-completeness.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/02-translation-audit-completeness.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh scratch-path 02-translation-audit-completeness`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

## Task Summary

Two deliverables: a translation audit script + filling missing English strings.

### Task 1: Create `scripts/i18n_audit.py`

Write a Python 3 script using only stdlib (`xml.etree.ElementTree`, `argparse`, `json`, `sys`, `os`, `glob`). No pip dependencies.

Features:
- Scan `app/src/main/res/values*/strings.xml` 
- Use `values/strings.xml` (Chinese) as the reference
- For each other locale (e.g. `values-en`), report: missing keys, extra keys, empty values
- Print a summary table: locale, total keys, missing count, coverage %
- `--format json` flag for JSON output
- `--check <locale>` flag to check only one locale
- Exit code 0 if all complete, 1 if any missing

The script should correctly parse Android string resources including:
```xml
<string name="key">simple value</string>
<string name="key2">"quoted value with %d format"</string>
<string name="key3"><b>bold text</b></string>
```

Use `xml.etree.ElementTree.parse()` and iterate `<string>` elements, collecting `name` attributes.

### Task 2: Fill Missing English Strings

1. Run the audit script to identify all missing English keys
2. Edit `app/src/main/res/values-en/strings.xml` to add the missing strings
3. Known missing items include:
   - `document_batch_organizer_title` → "Organize Batch"
   - `document_batch_page_count` → "%d pages"
   - `document_batch_remove` → "Remove"
   - `document_batch_move_up` → "Move Up"
   - `document_batch_move_down` → "Move Down"
   - `document_batch_crop_applied` → "Cropped"
   - `document_batch_crop_skipped` → "Not Cropped"
   - `document_batch_crop_failed` → "Crop Failed"
4. Also add proper English values for:
   - `watermark_template_pure_text` → "Pure Text" (currently hardcoded Chinese fallback)
   - `watermark_template_blur_four_border` → "Blur Four Border" (currently hardcoded Chinese fallback)
5. Re-run the audit to confirm 100% coverage

## Verification

```bash
python3 scripts/i18n_audit.py
python3 scripts/i18n_audit.py --check en
python3 scripts/i18n_audit.py --format json
./gradlew --no-daemon :app:assembleDebug
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh mark-state 02-translation-audit-completeness completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh advance --from 02-translation-audit-completeness
```

---

## Package: 03-i18n-developer-workflow — Developer Workflow

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/packages/03-i18n-developer-workflow.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/03-i18n-developer-workflow.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh scratch-path 03-i18n-developer-workflow`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

## Task Summary

Three deliverables: bootstrap script, developer documentation, and advanced i18n assessment.

### Task 1: Create `scripts/i18n_bootstrap.sh`

A shell script that bootstraps a new language resource directory.

Usage: `bash scripts/i18n_bootstrap.sh <locale_code> <language_name> [--dry-run]`

Behavior:
- Check that `values-en/strings.xml` exists (the template source)
- If `app/src/main/res/values-<locale_code>/` already has a `strings.xml`, print a warning and exit (idempotency guard)
- Create `app/src/main/res/values-<locale_code>/strings.xml`
- For each `<string>` in the English template, write the same `name` attribute but with value `[<language_name>] <english_value>` as placeholder
- Print a numbered checklist of remaining manual steps:
  1. Add enum entry in `AppLanguage.kt`
  2. Add display name in `AppTextResolver.languageValueName()`
  3. Run `python3 scripts/i18n_audit.py` to verify
  4. Translate each placeholder value
  5. Run `./gradlew :app:assembleDebug`

Use `--dry-run` flag to print what would happen without creating files.

Implementation approach: use basic shell scripting. Parse XML with `grep`/`sed` since the format is simple and predictable. Alternatively, delegate XML parsing to a one-liner Python call via `python3 -c` (read the file, use ElementTree, output key:value pairs).

### Task 2: Write `I18N_WORKFLOW.md`

Write to `docs/plans/i18n-multi-language-system/I18N_WORKFLOW.md` (this is a scratch/docs artifact, not a package status file — it's part of the plan's documentation output).

Cover:
- Architecture overview (AppTextResolver → strings.xml → AppCompatDelegate)
- Step-by-step guide to add a new language (using the bootstrap script)
- String resource conventions (`values/` = default/Chinese, `values-<locale>/` = translations)
- How to use AppTextResolver in code
- Common pitfalls: hardcoded Chinese fallbacks, format placeholder consistency

Keep it concise and practical (~2-3 pages).

### Task 3: Advanced i18n Assessment

Write a brief assessment to scratch (use `scratch-path` to get the path, then write `i18n-advanced-assessment.md`). Cover:
1. **RTL readiness**: `AndroidManifest.xml` already has `supportsRtl="true"`. Most UI is programmatic, so RTL mainly requires ensuring layout params use `start`/`end` instead of `left`/`right`. Assessment: low effort for basic RTL support.
2. **Hot reload**: `AppCompatDelegate.setApplicationLocales()` provides instant switching. Current implementation in `render()` suffices. Complex edge case: Activities in back stack won't update until recreated — not relevant here (single-Activity app with panels).
3. **Plural strings**: Check if any existing strings need pluralization. The `document_batch_page_count` ("%d pages") could benefit from Android `plurals` resource, allowing "1 page" vs "2 pages" distinction. Recommend evaluating as a follow-up if needed.
4. **System UI**: Permissions dialogs, CameraX UI are system-managed and follow system language, not app language. This is expected Android behavior — document in workflow.

## Verification

```bash
bash scripts/i18n_bootstrap.sh --help
bash scripts/i18n_bootstrap.sh xx "Test" --dry-run
./gradlew --no-daemon :app:assembleDebug
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh mark-state 03-i18n-developer-workflow completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh advance --from 03-i18n-developer-workflow
```

---

## Package: 99-finalize — Integration & Finalize

Copy this prompt into an agent, or run `orchestrate.sh finalize` to auto-launch.

---

**Mode**: package executor (finalize)
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system/launchers/orchestrate.sh

This is the finalize package. Follow all acceptance criteria in the package doc:
1. Run `verify-finalize`
2. Verify all functional packages pass acceptance
3. Create/update integration branch `feat/i18n-multi-language`
4. Merge in order: 01 → 02 → 03
5. Run integration verification (all tests + assembleDebug + audit script)
6. Merge to mainline
7. Write FINAL_REPORT.md and status/99-finalize.md
8. Clean up local branches/worktrees

On failure: record in status, preserve branches, do not force-push.
On success: mark finalized, record merge commits, clean up.
Re-running after success must be idempotent.
