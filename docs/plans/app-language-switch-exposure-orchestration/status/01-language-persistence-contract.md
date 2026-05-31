# 01-language-persistence-contract

## State

`completed`

## Evidence

- **Worktree**: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/app-language-switch-exposure-01`
- **Branch**: `agent/app-language-switch-exposure/01-language-persistence-contract`
- **Base commit**: `65367bf9`
- **Commit**: `5234b116`
- **Changed files**:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
  - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-en/strings.xml`
  - `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`
- **Verification**:
  - `PersistedSettingsSerializerTest`: BUILD SUCCESSFUL — all tests pass
  - `SessionUiRenderModelTest`: 1 pre-existing failure (`session summary includes native output and action context` at line 246) — unrelated to this package's changes
- **Risks**: `SessionUiRenderModelTest` has a pre-existing failure unrelated to language persistence; not a blocker for this package.
