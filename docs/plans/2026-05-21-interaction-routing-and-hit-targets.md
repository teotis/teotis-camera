# Interaction Routing and Hit Targets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix wrong tap routing for zoom/mode controls and make recording/gallery interactions give immediate, correct feedback.

**Architecture:** Keep UI as intent dispatch only. `MainActivity` may map a button tap to `SessionIntent.ApplyZoomRatio(ratio)` or open an external gallery intent, but must not mutate session runtime state directly. Session Kernel remains the owner of mode/recording state.

**Tech Stack:** Android Views/XML, Kotlin, CameraX, existing `SessionUiRenderModel`, `DefaultCameraSession`, and app unit tests.

---

## Evidence and Scope

User issues covered:

- `1`: tapping zoom options selects the next option instead of the tapped option.
- `7`: tapping Portrait sometimes switches to Humanistic, likely from narrow/overlapping mode hit targets.
- `7/8`: video recording interaction has poor feedback.
- `9`: tapping thumbnail does not open gallery.

Observed code:

- `MainActivity.renderZoomCapsules()` currently dispatches `SessionIntent.ZoomRatioToggled` for every capsule, ignoring `capsule.ratio`.
- `ZoomCapsuleRenderModel` already carries `ratio`, and `SessionIntent.ApplyZoomRatio` already exists.
- Mode buttons are individual fixed `Button`s inside a horizontal track. Screenshot shows labels wrap vertically because controls are too narrow/tall.
- `shutterButton.text = state.modeSnapshot.uiSpec.shutterLabel`; recording state is only indirectly visible through mode snapshot and bottom text.
- `previewThumbnail` has no click listener.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

Optional, only if session behavior needs better recording transition semantics:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Tasks

- [ ] **Step 1: Add a failing render-model test for zoom capsule target ratios**

In `SessionUiRenderModelTest`, strengthen the existing zoom-capsule test so it checks exact ratio and active state:

```kotlin
assertEquals(listOf(0.6f, 1f, 2f, 5f), controls.zoomCapsules.map { it.ratio })
assertEquals(listOf(false, true, false, false), controls.zoomCapsules.map { it.isActive })
```

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected: pass or expose the ratio/active mismatch before UI wiring. If it already passes, proceed to the UI dispatch fix.

- [ ] **Step 2: Dispatch exact zoom ratio from each capsule**

In `MainActivity.renderZoomCapsules()`, replace:

```kotlin
dispatch(SessionIntent.ZoomRatioToggled)
```

with:

```kotlin
dispatch(SessionIntent.ApplyZoomRatio(capsule.ratio))
```

Keep the existing bottom `buttonZoomRatio` behavior as `ZoomRatioToggled`; only the visible preset capsules should select exact values.

- [ ] **Step 3: Make mode-track controls stable touch targets**

In `activity_main.xml` and `themes.xml`, update mode-track styling so labels stay horizontal and hit targets do not collapse:

- Set `Widget.OpenCamera.ModeTrackButton` min width to about `58dp`.
- Keep min height at least `40dp`.
- Add `android:singleLine=true`, `android:maxLines=1`, and `android:ellipsize=end` to each mode button or to style where supported.
- Increase horizontal spacing from `4dp` to `8dp`.
- Keep `modeTrackScroll` height stable; avoid placing it inside or under `bottomSheet`.

Acceptance: Chinese labels `拍照 / 文档 / 人文 / 人像 / 专业 / 视频` stay one line; English labels may abbreviate but must not wrap into vertical letter stacks.

- [ ] **Step 4: Add mode-track regression tests at render-model level**

In `SessionUiRenderModelTest`, add a test that `modeTrackRenderModel()` preserves order and identity:

```kotlin
val model = modeTrackRenderModel(defaultSessionState(activeMode = ModeId.PORTRAIT), TestAppTextResolver())
assertEquals(
    listOf(ModeId.PHOTO, ModeId.DOCUMENT, ModeId.NIGHT, ModeId.HUMANISTIC, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO),
    model.items.map { it.modeId }
)
assertTrue(model.items.first { it.modeId == ModeId.PORTRAIT }.isActive)
assertFalse(model.items.first { it.modeId == ModeId.HUMANISTIC }.isActive)
```

This does not prove Android hit testing, but it prevents later render-model reorder mistakes.

- [ ] **Step 5: Improve recording button feedback without adding a new state owner**

In `MainActivity.render(state)`, derive shutter visual feedback from `state.recordingStatus`:

- `REQUESTING`: disable shutter briefly or set text to localized "准备录制"/"Starting".
- `RECORDING`: set text to localized "停止"/"Stop"; use a strong recording tint.
- `STOPPING`: disable shutter and set text to localized "保存中"/"Saving".
- `IDLE`: use mode `shutterLabel`.

Use string resources, not hard-coded text. Add keys:

```xml
<string name="button_recording_starting">准备录制</string>
<string name="button_recording_stop">停止</string>
<string name="button_recording_saving">保存中</string>
```

and English equivalents.

Do not change `DefaultCameraSession` unless a test proves state transition is wrong. The existing Session Kernel already uses `REQUESTING -> RECORDING -> STOPPING -> IDLE`.

- [ ] **Step 6: Add thumbnail click-to-gallery behavior**

In `MainActivity.bindActions()`, add `previewThumbnail.setOnClickListener { ... }`:

- Prefer `latestSessionState?.presentation?.latestThumbnailSource?.renderUriOrNull()`.
- If no render URI exists, fallback to `latestCapturePath` or `latestVideoPath` only if it can be converted to a valid file URI.
- Use `Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)` with photo/video mime based on `latestSavedMediaType`.
- Add `FLAG_GRANT_READ_URI_PERMISSION`.
- Guard with `runCatching { startActivity(intent) }`; on failure set `captureOutput.text` to a localized "无法打开相册" message.

Important: do not dispatch a session intent for this. Opening gallery is app shell behavior, not runtime camera state.

- [ ] **Step 7: Verify focused behavior**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual smoke on device:

- Tap `0.7x`, `1.0x`, `2.0x`, `5.0x`; each tap selects that exact ratio.
- Tap Portrait; active mode becomes `PORTRAIT`, not `HUMANISTIC`.
- Enter Video; tap shutter; button shows recording start/stop/saving states.
- Tap saved thumbnail; Android gallery/viewer opens the last saved media.

## Non-Goals

- Do not redesign the whole camera UI here; leave broader panel and cockpit design to the visual-system plan.
- Do not add a second state machine in `MainActivity`.
- Do not change CameraX zoom execution; `SessionEffect.ApplyZoomRatio` and adapter support already exist.
