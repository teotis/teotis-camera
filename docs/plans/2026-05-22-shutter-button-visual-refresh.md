# Shutter Button Visual Refresh Plan

> **For agentic workers:** This is a text-only implementation handoff. Implement deterministic drawable/layout/state changes and tests. Final visual taste validation belongs to the multimodal QA plan. Run shell commands through `rtk`.

**Goal:** Replace the current plain purple/text shutter button with a more camera-like shutter control that has clear button affordance, capture/recording states, and no awkward text wrapping.

**Architecture:** The shutter remains a UI command surface that dispatches existing session intents. No capture behavior changes. State still comes from `SessionState` and `CameraCockpitRenderModel`.

**Tech Stack:** Android XML drawables/selectors, View `Button` or `ImageButton`, Kotlin render-model tests.

---

## Evidence

Current code facts:

- `activity_main.xml` uses a normal `Button` with `@drawable/bg_shutter_circle`.
- `bg_shutter_circle.xml` is a simple oval filled with `@color/oc_text_primary`.
- `MainActivity.render()` writes the mode shutter label into the button.
- Screenshots show the button as a flat purple disk with text such as `Capt...`, which weakens camera-button affordance.

## Required Behavior

- Photo idle shutter should look like a shutter button:
  - circular outer ring,
  - filled or bordered inner circle,
  - pressed state,
  - disabled state.
- Recording state should look distinct:
  - red accent,
  - stop-square or clear stop visual if possible.
- Text should not wrap inside the shutter button.
- The accessible content description should still expose the action label.
- The visual button must keep at least `72dp` touch target.

## Files

Modify:

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/drawable/bg_shutter_circle.xml`
- `app/src/main/res/drawable/bg_shutter_recording.xml`
- Add drawables if needed:
  - `app/src/main/res/drawable/bg_shutter_photo.xml`
  - `app/src/main/res/drawable/bg_shutter_photo_pressed.xml`
  - `app/src/main/res/drawable/bg_shutter_photo_disabled.xml`
  - `app/src/main/res/drawable/bg_shutter_selector.xml`
  - `app/src/main/res/drawable/bg_shutter_recording_selector.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

## Implementation Tasks

- [ ] **Step 1: Stop rendering long text inside the shutter**

In `MainActivity.render()`:

```kotlin
shutterButton.contentDescription = shutterLabel
shutterButton.text = ""
```

Keep the action label in render model and accessibility. If product wants a tiny icon-like label later, keep it separate from this plan.

Add or update `CameraCockpitRenderModelTest`:

```kotlin
assertEquals("Shutter", model.bottomCockpit.shutterLabel)
```

UI code should use this as content description, not visible multiline text.

- [ ] **Step 2: Create a layered photo shutter drawable**

Replace or add `bg_shutter_photo.xml`:

```xml
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="oval">
            <solid android:color="@android:color/transparent" />
            <stroke android:width="3dp" android:color="@color/oc_text_primary" />
        </shape>
    </item>
    <item android:left="8dp" android:right="8dp" android:top="8dp" android:bottom="8dp">
        <shape android:shape="oval">
            <solid android:color="@color/oc_text_primary" />
        </shape>
    </item>
</layer-list>
```

Create pressed/disabled variants:

- pressed: inner fill slightly dimmed or ring accent.
- disabled: lower alpha via color resource or separate muted color.

- [ ] **Step 3: Create selector drawables**

`bg_shutter_selector.xml`:

```xml
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:drawable="@drawable/bg_shutter_photo_disabled" />
    <item android:state_pressed="true" android:drawable="@drawable/bg_shutter_photo_pressed" />
    <item android:drawable="@drawable/bg_shutter_photo" />
</selector>
```

Do the same for recording if `bg_shutter_recording.xml` is not already adequate.

- [ ] **Step 4: Apply state-specific backgrounds**

In `MainActivity.render()`:

```kotlin
if (state.recordingStatus != RecordingStatus.IDLE) {
    shutterButton.setBackgroundResource(R.drawable.bg_shutter_recording_selector)
} else {
    shutterButton.setBackgroundResource(R.drawable.bg_shutter_selector)
}
```

Remove text color changes that only mattered for visible text. Keep enabled state unchanged.

- [ ] **Step 5: Lock dimensions and text wrapping**

In `activity_main.xml`:

```xml
android:minWidth="@dimen/touch_target_shutter"
android:minHeight="@dimen/touch_target_shutter"
android:includeFontPadding="false"
android:maxLines="1"
android:ellipsize="end"
```

Even with empty visible text, these guardrails prevent layout surprises if a label is accidentally restored.

- [ ] **Step 6: Verify resources compile**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Manual Smoke

- Photo idle shutter is circular, ringed, and has no visible clipped text.
- Pressing shutter shows pressed state.
- Recording state uses the recording drawable.
- Disabled shutter looks disabled.
- TalkBack/content description still announces the capture/recording action.

## Non-Goals

- Do not change capture intent dispatch.
- Do not implement custom drawing code for the shutter unless XML drawables cannot satisfy compile/layout requirements.
- Do not tune final visual taste in this text-only plan.

