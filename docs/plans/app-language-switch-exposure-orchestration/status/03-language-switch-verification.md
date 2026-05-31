# 03-language-switch-verification

## State

`completed`

## Evidence

- **Worktree**: `.claude/worktrees/app-language-switch-exposure-03`
- **Branch**: `agent/app-language-switch-exposure/03-language-switch-verification`
- **Base commit**: pending
- **Commit hash**: `037433ec`
- **Changed files**:
  - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt` (merge conflict resolution, languageDisplayName delegates to languageDisplayValue)
  - `app/src/main/res/values/strings.xml` (removed orphan language_display_name_zh)
  - `app/src/main/res/values-en/strings.xml` (removed orphan language_display_name_zh, added missing label_language)
- **Verification results**:
  - PersistedSettingsSerializerTest: BUILD SUCCESSFUL (all tests pass)
  - SessionUiRenderModelTest + SessionUiRenderContractsTest: 1 pre-existing unrelated failure (session summary includes native output and action context), 103/104 pass
  - assembleDebug: BUILD SUCCESSFUL
  - String name sets (values/strings.xml vs values-en/strings.xml): fully matched (diff empty)
- **APK path**: `/Users/dingren/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk`
- **adb install command**: `adb install /Users/dingren/.codex-build/OpenCamera/app/outputs/apk/debug/app-debug.apk`
- **Manual QA checklist**: Written to `scratch/03-language-switch-verification/manual-qa-checklist.md`
- **Real-device QA**: External-assist only, release-confidence only

## Outcome

`ready-for-external-gate`

## Risks

- 1 pre-existing test failure unrelated to language switch work (noted in package 01 verification)
- Real-device visual confirmation of language switch re-render remains an external gate
