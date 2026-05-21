# Interaction Grammar

## Core Principle

Every visible control should map to the action the user expects from that exact control. OpenCamera 2.0 must remove hidden cycling, ambiguous hit targets, and panels that require hunting for a close button.

## Main Gesture Map

```text
Tap preview         -> focus at tapped point
Double tap preview  -> switch lens, if supported and not blocked
Pinch preview       -> continuous zoom through ApplyZoomRatio
Tap zoom chip       -> select that exact ratio
Tap mode chip       -> switch to that exact mode
Swipe mode row      -> scroll only
Tap thumbnail       -> open latest saved media
Tap outside panel   -> close current panel
Tap shutter/photo   -> capture photo
Tap shutter/video   -> start recording
Tap shutter/record  -> stop recording
Long press shutter  -> optional burst/hold-to-record only if explicitly implemented later
```

## Panel Route Rules

2.0 should use one active app-shell panel route:

```kotlin
enum class CockpitPanelRoute {
    NONE,
    LENS_LAB,
    TONE_LAB,
    QUICK,
    PORTRAIT_LAB,
    WATERMARK_LAB_SELECTOR,
    WATERMARK_LAB_DETAIL,
    PRO_CONTROLS,
    DEV_LOG
}
```

Rules:

- Only one route is active at a time.
- Opening a panel closes the previous panel.
- Outside tap sets route to `NONE`.
- Android back first closes the active panel; only then exits or delegates to normal back behavior.
- Route belongs to app shell, not Session Kernel.
- Nested detail route may carry an id, for example selected watermark template id.

## Tap Target Rules

- Visible body may be smaller, but effective target should be at least 44dp.
- Adjacent controls need at least 8dp visual gap or clear hit-slop separation.
- Do not overlap mode row and zoom row touch regions.
- Do not place invisible large buttons on top of visible neighboring controls.
- Avoid index-based routing when stable ids exist.

Mode tap acceptance:

- Record `ACTION_DOWN` target item.
- If pointer moves beyond scroll threshold, gesture becomes scroll.
- On `ACTION_UP`, switch only if still inside the same item and scroll did not activate.
- Dispatch `SessionIntent.SwitchMode(targetModeId)`.

Zoom chip tap acceptance:

- On click, dispatch `SessionIntent.ApplyZoomRatio(capsule.ratio)`.
- Do not dispatch `ZoomRatioToggled` from preset chips.
- Cycle behavior may remain on a separate compact zoom-cycle button only if visually distinct.

## Preview Gesture Arbitration

Priority order:

1. Active panel consumes its own gestures.
2. Panel scrim consumes outside tap to dismiss.
3. Mode/zoom/bottom controls consume their own taps and horizontal scrolling.
4. Preview receives focus, double tap, and pinch only when no panel is open.

Rules:

- When a panel is open, preview gestures are paused.
- Tone palette gestures affect only Tone Lab values.
- Pinch zoom should not open or close panels.
- Scrolling a panel should not change mode or zoom.

## Disabled And Conflict Rules

Controls that would break an in-flight flow must be disabled:

| State | Disabled Controls | Reason Copy |
| --- | --- | --- |
| Countdown | mode, lens, panel actions that change capture config | `倒计时中，暂不能切换` |
| Saving photo | shutter repeat if pipeline cannot queue, mode/lens config | `正在保存上一张` |
| Recording requesting | shutter double tap, mode/lens config | `正在准备录制` |
| Recording | mode switch, unsupported lens switch, some panel actions | `录制中暂不可用` |
| Recording stopping | shutter, mode/lens config | `正在停止并保存` |
| Preview recovering | shutter and camera config | `相机恢复中` |
| Permission missing | camera controls | `需要相机权限` |
| Unsupported capability | specific control | `当前设备不支持` |
| Degraded capability | control remains enabled if safe | `已按设备能力降级` |

Disabled controls:

- show lower opacity
- keep their layout slot
- provide reason through status strip or toast when tapped
- should not silently do nothing

## Feedback Rules

Every state-changing action gets short feedback:

- Mode switch: top status `已切换到人像` or active chip change.
- Lens switch: status `已切换到前置镜头` / `已切换到后置镜头`.
- Zoom exact select: active zoom chip updates immediately; status `2x`.
- Pinch zoom: compact zoom value overlay, fades out.
- Capture: shutter press animation; status `保存中`; thumbnail updates after saved media.
- Recording start: status changes to `录制 00:00`; shutter changes to stop state.
- Recording stop: status `停止录制中` then saved feedback.
- Panel action: row value updates or shows disabled/degraded reason.

Feedback should not block the preview or require modal dialogs.

## Thumbnail Interaction

Tap thumbnail:

- Open latest saved media URI if available.
- Photo uses image MIME.
- Video uses video MIME.
- If no saved media exists, show `暂无最近媒体`.
- If Android viewer cannot open it, show `无法打开相册`.
- Do not open preview snapshots as gallery media after a real capture has happened.

Ownership:

- Opening gallery is app shell behavior.
- Saved media source is produced by Media Pipeline and session presentation state.
- UI must not synthesize saved media from preview frames.

## Recording Interaction

States:

```text
IDLE -> REQUESTING -> RECORDING -> STOPPING -> IDLE
```

UI mapping:

- `IDLE` in video mode: red record shutter, label/content description `开始录像`.
- `REQUESTING`: disable repeated tap, show `准备录制`.
- `RECORDING`: red stop state, label/content description `停止`.
- `STOPPING`: disabled stop state, show `保存中`.

Acceptance:

- User can tell within one glance whether recording is active.
- A tap on the recording shutter cannot accidentally start a second recording.
- Stop feedback appears immediately.

## Labs And Panel Interactions

Shared rules:

- Tap panel header close closes the panel.
- Tap outside closes the panel.
- Tap tab switches panel section.
- Tap enabled row applies next value or opens row detail depending on row type.
- Tap disabled row shows reason.
- Long press on a row may show help only if implemented consistently; not required for 2.0.

Tone Lab:

- Palette drag changes tone/tint continuously or on release.
- Filter chip tap selects exact filter.
- Save custom action clearly indicates whether a custom copy was created.

Lens Lab:

- Changing capture-affecting settings may trigger reconfigure; show a short status.
- Unsupported rows remain visible with unsupported badge.

Watermark Lab:

- Selector item tap selects template.
- Detail item tap opens detail for that template.
- Style rows update only selected template style.

Pro Controls:

- Slider/stepper changes should be explicit and reversible.
- Auto/manual toggle must show current owner.
- Unsupported manual controls show unsupported reason from capability model.

Dev Log:

- Copy/export buttons give feedback.
- Log filtering should not mutate session state.

## Gesture Policy Tests

Add or update tests around the pure policy layer if available:

- Zoom chip tap creates exact `ApplyZoomRatio`.
- Mode tap uses `ModeId`, not index.
- Horizontal scroll on mode row does not switch mode.
- Outside tap closes active panel.
- Active panel blocks preview focus/pinch.
- Disabled row produces disabled reason action, not runtime change.

Suggested command:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
```

If no gesture policy exists yet, create a small app-layer pure Kotlin policy rather than burying all rules in anonymous `OnClickListener` blocks.

## Acceptance Checklist

- User taps a visible target and gets that target's action.
- Swipe and tap are distinguishable on mode track.
- Panels close without hunting.
- Recording has immediate, unmistakable feedback.
- Disabled state explains itself.
- Thumbnail opens the saved media, not a preview snapshot.
