# Package: 03-language-switch-verification

## Mission

Verify the integrated language-switch behavior after packages 01 and 02 complete, produce release-confidence instructions, and avoid claiming physical-device QA.

## Allowed Paths

- `app/src/test/java/com/opencamera/app/**`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/**`
- `docs/plans/app-language-switch-exposure-orchestration/status/03-language-switch-verification.md`
- `docs/plans/app-language-switch-exposure-orchestration/scratch/**`

## Forbidden Paths

- Runtime source changes unless a failing verification reveals a tiny test-only seam is impossible without one; if runtime change is required, mark blocked instead of expanding scope.
- `feature/**`
- Other package status files
- `INDEX.md`

## Acceptance Criteria

- Re-run persistence and app render-model tests from packages 01 and 02.
- Run `:app:assembleDebug`.
- Confirm `values/strings.xml` and `values-en/strings.xml` keep matching string-name sets for any keys touched by this plan.
- Record the debug APK path and a copy-paste `adb install` command for real-device QA.
- Write a manual QA checklist covering: open Settings, Common tab shows Language, switch to English, panel text updates, restart app, language remains English, switch back to Chinese, restart app, language remains Chinese.
- State clearly that real-device language-switch QA is external-assist and release-confidence only.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionUiRenderContractsTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- Commit hash if tests/checklist files changed; otherwise record `no-code-change verification`.
- Verification command results.
- APK path under `~/.codex-build/OpenCamera/app/outputs/apk/debug/` or the actual Gradle output path.
- Manual QA checklist and expected evidence.

## Unlock Conditions

- Mark completed only when local verification passes and manual QA instructions are complete.
- If build/test fails, mark blocked with the exact command and failing test/log summary.
