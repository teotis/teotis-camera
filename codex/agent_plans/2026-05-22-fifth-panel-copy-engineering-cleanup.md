# Fifth Feedback: Panel Copy And Engineering Text Cleanup

> **For text-only agents:** This is mostly render-model and string-resource cleanup. Keep implementation details available in Dev only. Use `rtk` for all commands.

## Goal

Remove engineering/raw parameter language from user-facing panels while preserving explicit support/degraded/unsupported semantics.

## Problems To Fix

- Settings panel shows `Supported` as product copy.
- Style panel exposes raw render parameters, e.g. `B 20 | C 0.92 | S 1.37 | W 24 | Mono...`.
- Dev log is useful but visually dominates normal acceptance.
- Product panels feel like data dumps instead of camera controls.

## Required Behavior

- User-facing panels use concise Chinese product copy.
- Internal render parameters appear only in Dev diagnostics.
- Support state should be user-readable:
  - `可用`,
  - `当前设备不支持`,
  - `拍摄中暂不可改`,
  - `已降级：...`.
- Style cards show:
  - style name,
  - short category or intent,
  - current/selected state,
  - optional one-line visual description.
- Do not show raw `B/C/S/W/Mono/Vig` strings outside Dev.

## Suggested Implementation

1. Find raw string builders in:
   - `SessionUiRenderModel.kt`
   - `AppTextResolver.kt`
   - settings/page render models.

2. Split summary fields:
   - `userSummary`: concise product-facing text.
   - `debugSummary`: raw values for Dev only.

3. Settings support copy:
   - Replace `Supported` fallback with `可用` / `Available`.
   - Do not display capability implementation wording unless in Dev.

4. Style list:
   - Replace compact raw render summary with a natural-language summary.
   - Keep raw compact summary accessible to Dev log or diagnostics export.

5. Tests:
   - User-facing render models must not contain `Supported`, `B `, `C `, `Mono`, `Vig`, or pipe-delimited raw spec fragments.
   - Dev diagnostics may still contain raw data.

## Files To Inspect Or Modify

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`

## Acceptance Criteria

- No user-facing panel card shows raw render-spec internals.
- Settings rows no longer show English `Supported` in Chinese UI.
- Dev log remains capable of showing raw diagnostics.
- Tests protect against raw parameter leakage.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual/multimodal follow-up: open settings/style panels on vivo X300 and confirm they read like product UI.
