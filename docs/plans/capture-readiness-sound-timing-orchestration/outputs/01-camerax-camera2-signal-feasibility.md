# CameraX/Camera2 Signal Feasibility — Research Output

## Executive Summary

**Answer**: CameraX/Camera2 CAN provide a signal earlier than the current `OnImageSavedCallback → DataReceived` boundary, but the cleanest path requires Camera2 interop with session capture callbacks. The recommended V1 approach is a two-tier strategy: (1) immediately move shutter sound from `activeShot`-appearance to `DataReceived`, and (2) introduce a new `CaptureFrameAcquired` device event via Camera2 interop session capture callbacks for a future readiness boundary.

CameraX 1.3.4 does NOT offer a public API to receive both an in-memory captured callback AND a file-saved callback from a single `takePicture()` call. Each feasible earlier-callback approach involves tradeoffs documented below.

---

## 1. Current Callback Chain (in repo terms)

### 1.1 Timeline

```
User presses shutter button
  │
  ├─ MainActivity dispatches SessionIntent.ShutterPressed
  │   └─ CaptureRecordingSessionProcessor.handleShutterPressed()
  │       └─ Sets activeShot, captureStatus=REQUESTED
  │
  ├─ ⚡ maybePlayShutterSound() fires when activeShot first appears
  │   (MainActivity.kt:442-451 — TOO EARLY, request-start feedback)
  │
  ├─ CameraSessionCoordinator → CameraXCaptureAdapter.captureStillImage()
  │   ├─ emits DeviceEvent.ShotStarted (CameraXCaptureAdapter.kt:1736)
  │   ├─ captureSinglePhoto() calls ImageCapture.takePicture(
  │   │       OutputFileOptions, executor, OnImageSavedCallback)
  │   │   (CameraXCaptureAdapter.kt:2162-2165)
  │   │
  │   ├─ [Camera internals: exposure → sensor readout → JPEG encode → file write]
  │   │
  │   └─ onImageSaved() fires:
  │       ├─ returns PhotoCaptureOutcome.Success (with timing data)
  │       ├─ emits DeviceEvent.DataReceived (CameraXCaptureAdapter.kt:1774-1777)
  │       └─ For ordinary STILL_CAPTURE: launches emitShotCompleted()
  │           off critical path (CameraXCaptureAdapter.kt:1791-1809)
  │
  ├─ CameraSessionCoordinator → SessionIntent.DataReceived
  │   └─ CaptureRecordingSessionProcessor.handleDataReceived()
  │       ├─ For ordinary still: clears activeShot, sets DATA_RECEIVED
  │       │   (CaptureRecordingSessionProcessor.kt:313-334)
  │       └─ For multi-frame/live: keeps activeShot blocked (safe guard)
  │
  └─ emitShotCompleted() (background coroutine) → SessionIntent.ShotCompleted
```

### 1.2 Key Files and Functions

| Layer | File | Function | Role |
|-------|------|----------|------|
| UI | `app/.../MainActivity.kt:442` | `maybePlayShutterSound()` | Plays sound when `activeShot` appears |
| UI | `app/.../SessionCockpitRenderModel.kt:174` | `shutterDisabledReason()` | Blocks shutter while `activeShot != null` for PHOTO |
| UI | `app/.../SessionCockpitRenderModel.kt:191` | `shutterVisualState()` | Shows loading/SAVING/BACKGROUND_SAVING |
| Adapter | `app/.../CameraXCaptureAdapter.kt:1728` | `captureStillImage()` | Emits `ShotStarted`, calls capture |
| Adapter | `app/.../CameraXCaptureAdapter.kt:2156` | `captureSinglePhoto()` | Calls `ImageCapture.takePicture(OutputFileOptions, ...)` |
| Adapter | `app/.../CameraXCaptureAdapter.kt:1774` | (in `executeShot`) | Emits `DataReceived` on success |
| Session | `core/.../CaptureRecordingSessionProcessor.kt:313` | `handleDataReceived()` | Clears `activeShot` for ordinary still |
| Session | `core/.../CaptureRecordingSessionProcessor.kt:307` | `canRearmOnDataReceived()` | Guards against early re-arm for risky modes |
| Contract | `core/.../DeviceContracts.kt:622` | `DeviceEvent.DataReceived` | Current readiness-adjacent device event |

### 1.3 Current Signaling Gap

- **Shutter sound**: fires at `activeShot` appearance (SessionIntent.ShutterPressed time)
- **Shutter re-arm**: happens at `DataReceived` (onImageSaved time) for ordinary still
- **Gap**: sound is request-time feedback; re-arm waits for JPEG save completion
- **Timing difference**: on a typical device, 100-500ms between ShutterPressed and onImageSaved

---

## 2. CameraX 1.3.4 API Surface Analysis

### 2.1 Two Public `takePicture()` Overloads

```java
// Path A (current): save-to-file, callback on save complete
public void takePicture(OutputFileOptions, Executor, OnImageSavedCallback)

// Path B (alternative): in-memory, callback on capture
public void takePicture(Executor, OnImageCapturedCallback)
```

| Aspect | Path A (current) | Path B |
|--------|-----------------|--------|
| When callback fires | After JPEG encode + file I/O | After processing, before JPEG encode |
| Image data | File path / URI | `ImageProxy` in memory |
| Who saves JPEG | CameraX | Caller (must write `ImageProxy` → file) |
| Pipeline processing | Crop + rotate + JPEG encode + save | Crop + rotate (JPEG encode NOT done) |
| `ImageProxy` format | N/A (file) | Can be YUV_420_888 or RGBA depending on pipeline |

### 2.2 Private Method (Unusable)

```java
private void takePictureInternal(
    Executor executor,
    OnImageCapturedCallback onImageCapturedCallback,  // can be null
    OnImageSavedCallback onImageSavedCallback,        // can be null
    OutputFileOptions outputFileOptions               // can be null
)
```

This private method supports both callbacks simultaneously — which is exactly what we want (in-memory signal + CameraX handles save). **But it's private.** Reflection-based access is rejected: fragile across CameraX versions, breaks with R8/proguard, and introduces undefined behavior.

### 2.3 Camera2Interop API (Available via `ImageCapture.Builder`)

```java
// Camera2Interop.Extender<T> where T = ImageCapture.Builder
public Extender<T> setSessionCaptureCallback(
    CameraCaptureSession.CaptureCallback callback  // Camera2 HAL-level callback
)
public Extender<T> setSessionStateCallback(
    CameraCaptureSession.StateCallback callback
)
public <V> Extender<T> setCaptureRequestOption(
    CaptureRequest.Key<V> key, V value
)
```

`setSessionCaptureCallback()` adds a `CameraCaptureSession.CaptureCallback` to the session containing the ImageCapture use case. This callback fires at the Camera2 HAL level — after sensor readout completes — which is **earlier than `onImageSaved`** by the JPEG encode + file I/O duration.

### 2.4 Camera2 Callback Timing Hierarchy

| Milestone | Android API | When | Relative timing |
|-----------|------------|------|----------------|
| Exposure start | `onCaptureStarted()` | Sensor begins exposure | Earliest |
| Frame acquired | `onCaptureCompleted()` | Sensor readout + metadata ready | ~30-100ms after exposure start |
| JPEG encoded | (internal pipeline) | JPEG compression done | ~50-200ms after capture completed |
| File saved | `onImageSaved()` | File written to disk/URI | ~5-50ms after JPEG encode |

---

## 3. Feasible Earlier Milestone Options

### 3.1 Option A: Camera2Interop Session Capture Callback (RECOMMENDED for V2)

**Mechanism**: Add a `CameraCaptureSession.CaptureCallback` via `Camera2Interop.Extender.setSessionCaptureCallback()` on the `ImageCapture.Builder`. When `onCaptureCompleted()` fires with `CaptureResult.CONTROL_CAPTURE_INTENT == CONTROL_CAPTURE_INTENT_STILL_CAPTURE`, emit a new `DeviceEvent.CaptureFrameAcquired`.

**Filtering strategy**: Check `TotalCaptureResult.get(CaptureResult.CONTROL_CAPTURE_INTENT)` for `CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE`. Optionally further filter by checking for JPEG output presence (e.g., `CaptureResult.JPEG_QUALITY != null`).

**Implementation sketch** (for package 04):

```kotlin
// In buildStillImageCapture()
val extender = Camera2Interop.Extender(builder)
extender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) {
        val captureIntent = result.get(CaptureResult.CONTROL_CAPTURE_INTENT)
        if (captureIntent == CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE) {
            // Frame acquired by hardware — emit early signal
            adapterScope.launch {
                _events.emit(DeviceEvent.CaptureFrameAcquired(
                    shotId = pendingStillShotId,
                    timestampNanos = result.get(CaptureResult.SENSOR_TIMESTAMP)
                ))
            }
        }
    }
})
```

**Pros**:
- Does NOT change the image saving pipeline (`takePicture(OutputFileOptions, ...)` stays intact)
- No `ImageProxy` ownership responsibility
- `onCaptureCompleted()` fires 50-200ms earlier than `onImageSaved` on typical devices
- Uses public, stable `Camera2Interop` API (available since CameraX 1.0)
- `CONTROL_CAPTURE_INTENT` is a reliable differentiator between still capture and preview/AF frames

**Cons**:
- Callback fires for ALL session captures (preview, AF triggers, AE precapture) — must filter each time
- AE/AF precapture triggers ALSO have `CONTROL_CAPTURE_INTENT_STILL_CAPTURE`, so the callback may fire multiple times per shot (for the AF trigger, AE trigger, and final capture frame)
- Multiple `onCaptureCompleted()` invocations per shot means we need deduplication logic (e.g., track by `pendingShotId` or only count the last one)
- The callback runs on Camera2 internal thread — must dispatch to adapter scope immediately
- Timing varies by device HAL implementation

**Mitigation for multiple triggers**: Accept that `onCaptureCompleted` fires multiple times and use the LAST one as the `CaptureFrameAcquired` signal. The AF trigger callback and AE trigger callback come first; the final JPEG capture callback comes last (and is the one we care about). Track by shotId and only emit once per shotId.

**Files to touch**:
- `app/.../CameraXCaptureAdapter.kt`: Add session capture callback in `buildStillImageCapture()` (around line 3103)
- `core/.../DeviceContracts.kt`: Add `DeviceEvent.CaptureFrameAcquired`
- `core/.../SessionContracts.kt`: Add `SessionIntent.CaptureFrameAcquired`
- `app/.../CameraSessionCoordinator.kt`: Map the new device event to session intent
- `core/.../CaptureRecordingSessionProcessor.kt`: Handle `SessionIntent.CaptureFrameAcquired`

### 3.2 Option B: OnImageCapturedCallback (HIGHER RISK, earlier signal)

**Mechanism**: Switch from `takePicture(OutputFileOptions, executor, OnImageSavedCallback)` to `takePicture(executor, OnImageCapturedCallback)`. In `onCaptureSuccess(ImageProxy)`, emit readiness, then save `ImageProxy` to file ourselves.

**Pros**:
- Exact frame identification (callback fires once per still capture)
- Earliest possible CameraX signal (before JPEG encode)
- Clean public API, no filtering needed

**Cons**:
- **Must take over JPEG encoding and file saving** — CameraX won't save the file
- `ImageProxy` format varies: YUV_420_888 without effects, RGBA with effects, possibly others
- JPEG encoding from `ImageProxy` has quality/performance implications
- Need to handle Content URI writing (MediaStore) ourselves
- Handles `ExifInterface` metadata (rotation, GPS) — currently done by CameraX
- Increased crash surface: file I/O errors, OOM on large images
- The `ImageProxy.toBitmap()` convenience method creates an intermediate bitmap — memory-inefficient
- **Risk**: if our save pipeline has a bug, the photo is lost (no CameraX fallback)

**Verdict**: Rejected for V1 due to save-pipeline ownership risk. Viable as a V2 option if timing measurements show `onImageSaved` delay is unacceptable.

### 3.3 Option C: Camera2 Raw Capture Pipeline (REJECTED)

**Mechanism**: Abandon CameraX `ImageCapture` entirely and use Camera2 `CameraDevice.createCaptureSession()` directly for still capture.

**Verdict**: Rejected. Would require duplicating CameraX's preview/video binding, rotation handling, and lifecycle management. Architectural overkill for a timing optimization.

### 3.4 Option D: CameraX 1.4+ Upgrade (FUTURE)

CameraX 1.4+ may offer improved dual-callback support. The private `takePictureInternal` suggests the API team recognizes the use case. Upgrading CameraX is a separate effort and should not gate this feature.

---

## 4. Recommended V1 Implementation Path

### 4.1 Two-Tier Strategy

#### Tier 1: Move Shutter Sound to DataReceived (immediate, zero risk)

**Current behavior**: `maybePlayShutterSound()` fires when `activeShot` first appears (at `SessionIntent.ShutterPressed` time).

**Recommended**: Move shutter sound to fire at `SessionIntent.DataReceived` (when image data is saved).

**Rationale**:
- `DataReceived` is the current "frame data is available" milestone
- No new CameraX/Camera2 callbacks needed
- Purely a session-level timing change
- Shutter sound aligns with "the capture data exists" rather than "we asked for a capture"
- Zero risk to the capture pipeline

**Files to touch**:
- `app/.../MainActivity.kt:442-451`: Change `maybePlayShutterSound()` trigger from `activeShot` appearance to `DataReceived`
- Session state tracking: may need a `lastDataReceivedShotId` field instead of relying on `activeShot`

#### Tier 2: Add CaptureFrameAcquired via Camera2Interop (V1 implementation, V2 readiness boundary)

**Mechanism**: Option A from Section 3 above.

**New device event**: `DeviceEvent.CaptureFrameAcquired(shotId, timestampNanos)`

**New session intent**: `SessionIntent.CaptureFrameAcquired(shotId, timestampNanos)`

**Session handling**: `CaptureRecordingSessionProcessor` records the timestamp but does NOT clear `activeShot` or change `captureStatus` — `DataReceived` remains the re-arm gate for safety. The event is purely for observability and future readiness-boundary migration.

**Rationale for keeping DataReceived as re-arm gate**:
- `onCaptureCompleted` fires multiple times per shot (AF/AE triggers + final capture)
- Re-arming on the wrong `onCaptureCompleted` could allow overlapping captures
- `DataReceived` (onImageSaved) is the proven safe re-arm point
- Future packages (04/05) can evaluate using `CaptureFrameAcquired` as the readiness boundary after timing measurements

### 4.2 Naming Convention

For new local contracts, use:

| Name | Meaning | Camera2 equivalent |
|------|---------|-------------------|
| `CaptureFrameAcquired` | Hardware has read out the still frame | `onCaptureCompleted` with `STILL_CAPTURE` intent |
| `CaptureDataReceived` | JPEG is saved and available | `onImageSaved` (current `DataReceived`) |
| `CaptureReadiness` | (Future) User may relax; next shot accepted | Policy decision based on `CaptureFrameAcquired` or `DataReceived` |

Do NOT introduce `FrameAcquired`, `CaptureCommitted`, or `CaptureReady` for V1 — use `CaptureFrameAcquired` as the single new event type for the Camera2-level signal.

---

## 5. Failure Modes and Safety Guards

### 5.1 What Makes an Earlier Callback Unsafe for Re-arm

| Failure mode | Risk | Mitigation |
|-------------|------|------------|
| AF/AE trigger `onCaptureCompleted` fires before the actual frame | Re-arm on AF trigger, allow second shot before first is captured | Filter by checking `CaptureResult` keys for actual frame data; deduplicate by shotId |
| Device HAL skips `onCaptureCompleted` for some captures | Signal never fires, shutter stays blocked | Timeout fallback: if `CaptureFrameAcquired` doesn't arrive within N ms of `ShotStarted`, fall through to `DataReceived` |
| Flash mode changes capture sequence (torch, preflash) | Extra `onCaptureCompleted` callbacks for flash metering | Conservative flash guard: do not use `CaptureFrameAcquired` as re-arm signal when flash is ON |
| Multi-frame / HDR / night mode | Complex burst sequences with many callbacks | Keep current conservative path for `ShotKind.MULTI_FRAME_CAPTURE` and `ShotKind.LIVE_PHOTO` — only use new signal for `STILL_CAPTURE` |
| High-resolution (48MP/108MP) | Longer sensor readout, remosaic step | `onCaptureCompleted` still fires after readout; timing is device-dependent; no additional risk |
| Recording transition | Active recording changes session behavior | Never re-arm during recording (existing guard in `shutterDisabledReason`) |

### 5.2 Specific Safety Rules for Implementation

1. `CaptureFrameAcquired` is an **observability event only** in V1 — it does NOT change `activeShot`, `captureStatus`, or any session state that gates shutter re-arm.
2. `DataReceived` remains the single re-arm gate for all photo shot kinds.
3. Multi-frame, live photo, and flash modes remain conservative (re-arm only at `ShotCompleted`).
4. The Camera2 session callback must NOT throw or block — all work must be dispatched to the adapter coroutine scope immediately.
5. The `ImageCapture.Builder` with session capture callback must be tested on at least 3 different device manufacturers before using for re-arm decisions.

---

## 6. Rejected Options Summary

| Option | Reason for rejection |
|--------|---------------------|
| Private `takePictureInternal` via reflection | Fragile across CameraX versions; breaks with R8/proguard |
| Dual `takePicture` calls (one with `OnImageCapturedCallback`, one with `OnImageSavedCallback`) | Creates two independent capture pipelines; double exposure; undefined behavior |
| Camera2 raw capture pipeline | Architectural overkill; duplicates CameraX preview/lifecycle/video binding |
| Using `onCaptureStarted` as readiness | Exposure just started; frame not yet read out — too early, unsafe |
| CameraX `ImageAnalysis` for capture detection | Wrong use case; ImageAnalysis is for preview frames, not still capture quality |
| Polling `ImageCapture` state | No public state API exposes capture progress |

---

## 7. Exact Files and Functions for Implementation Packages

### Package 04 (`adapter-earliest-ready-signal`) should touch:

1. **`app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`**
   - `buildStillImageCapture()` (~line 3103): Add `Camera2Interop.Extender.setSessionCaptureCallback()`
   - New function `emitCaptureFrameAcquired()`: Emit `DeviceEvent.CaptureFrameAcquired`
   - New field `pendingStillShotId`: Track current still capture shotId for callback filtering

2. **`core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`**
   - Add `data class CaptureFrameAcquired(val shotId: String, val timestampNanos: Long) : DeviceEvent` (~line 622)

3. **`core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`**
   - Add `data class CaptureFrameAcquired(val shotId: String, val timestampNanos: Long) : SessionIntent` (~line 318)

4. **`app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`**
   - Map `DeviceEvent.CaptureFrameAcquired → SessionIntent.CaptureFrameAcquired` (~line 140)

5. **`core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`**
   - Handle `SessionIntent.CaptureFrameAcquired` (observability only in V1)

### Package 05 (`shutter-sound-and-visible-rearm`) should touch:

1. **`app/src/main/java/com/opencamera/app/MainActivity.kt`**
   - `maybePlayShutterSound()` (~line 442): Change trigger from `activeShot` appearance to `DataReceived`
   - New field `lastDataReceivedShotId` for deduplication

2. **`app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`**
   - `shutterVisualState()` (~line 191): May add visual distinction for `CaptureFrameAcquired` vs `DataReceived` timing

### Package 06 (`real-device-timing-protocol`) should measure:

1. `ShotStarted → CaptureFrameAcquired` latency (if implemented)
2. `ShotStarted → DataReceived` latency (baseline)
3. `DataReceived → ShotCompleted` latency (postprocess duration)
4. End-to-end: `ShutterPressed → shutter re-arm enabled` latency

---

## 8. Fallback Recommendation

If the Camera2Interop session capture callback proves unreliable on any test device (callback not firing, wrong timing, excessive spam), the **fallback is to rely solely on DataReceived** for both shutter sound and re-arm. This is the current state with one change: move sound from `activeShot` appearance to `DataReceived`.

This fallback:
- Works on ALL devices with CameraX 1.3.4
- Requires zero Camera2 interop code
- Purely changes session-level event routing
- Is an improvement over the current "sound at request time" behavior

If even this fallback is unacceptable (timing measurements show `onImageSaved` is too late for good UX), the next step is an `OnImageCapturedCallback`-based approach (Option B), which gives the earliest possible signal at the cost of taking over JPEG saving.

---

## 9. Verification Evidence

### Code Search

```bash
rg -n "takePicture|OnImageSavedCallback|OnImageCapturedCallback|Camera2Interop|DataReceived|maybePlayShutterSound" app/src/main/java core -S
```

**Key findings**:
- `app/.../CameraXCaptureAdapter.kt:2162`: `capture.takePicture(outputOptions, executor, OnImageSavedCallback)`
- `app/.../CameraXCaptureAdapter.kt:1774`: `DeviceEvent.DataReceived` emitted in `onImageSaved` path
- `core/.../CaptureRecordingSessionProcessor.kt:313`: `handleDataReceived()` clears `activeShot` for ordinary still
- `app/.../MainActivity.kt:442`: `maybePlayShutterSound()` triggers on `activeShot` appearance
- `app/.../CameraXCaptureAdapter.kt:339`: `Camera2Interop.Extender(builder)` already in use for manual capture params

### CameraX API Verification (decompiled from camera-core-1.3.4-api.jar)

```
ImageCapture.takePicture(Executor, OnImageCapturedCallback)         // In-memory callback
ImageCapture.takePicture(OutputFileOptions, Executor, OnImageSavedCallback)  // File-saved callback
ImageCapture.takePictureInternal(...)                               // PRIVATE — both callbacks

OnImageCapturedCallback:
  onCaptureSuccess(ImageProxy)  // After processing, before JPEG encode
  onError(ImageCaptureException)

Camera2Interop.Extender:
  setSessionCaptureCallback(CameraCaptureSession.CaptureCallback)  // HAL-level callback
  setSessionStateCallback(CameraCaptureSession.StateCallback)
  setCaptureRequestOption(CaptureRequest.Key<V>, V)
```

### Build Verification

```bash
./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

Baseline compile verified (no source changes made in this package).

---

## 10. Conclusion

CameraX 1.3.4 exposes enough surface area through `Camera2Interop` to add a `CaptureFrameAcquired` signal that fires meaningfully earlier than the current `DataReceived`/`onImageSaved` boundary. The recommended V1 implementation adds this signal as an observability event without changing the re-arm gate, while immediately moving the shutter sound from request time (`activeShot` appearance) to data-available time (`DataReceived`). This preserves safety and defers the readiness-boundary decision to future packages after real-device timing data is collected.
