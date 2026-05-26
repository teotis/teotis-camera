# Reversible Watermark Pipeline Embedding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Embed the pre-watermark JPEG into the final watermarked JPEG produced by `PhotoWatermarkPostProcessor`.

**Architecture:** Keep ownership inside the existing media postprocessor path. `PhotoWatermarkPostProcessor` decides whether watermark work exists. `AndroidPhotoWatermarkEditor` reads the pre-watermark JPEG, renders the visible watermark, restores EXIF, then embeds the OCWM archive as the final write step. UI, session, coordinator, and mode plugins do not inspect or create archive bytes.

**Tech Stack:** Kotlin, Android `BitmapFactory`/`Canvas`, AndroidX `ExifInterface`, pure `core:media` OCWM container from package 1, existing app unit tests.

---

## File Structure

- Modify `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - Add archive embedding after EXIF restore.
  - Add pipeline notes for archive success, skip, and failure.
- Modify `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
  - Cover archive notes at the processor seam using fake editors.
- Create `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkArchiveEditorTest.kt`
  - Use a JVM-friendly editor seam or small helper to validate archive construction without requiring Android bitmap rendering.

## Behavioral Contract

For every rendered JPEG watermark:

- The visible output remains the current watermarked JPEG.
- The embedded original payload is the exact byte array read before watermark rendering starts.
- The embedded payload is written after EXIF restoration to avoid a later `ExifInterface.saveAttributes()` call stripping unknown `APP15` segments.
- If archive embedding fails after the visible watermark succeeds, keep the visible watermarked image and add a warning note. Do not fail the capture solely because archival embedding failed.

Pipeline notes:

```text
watermark:rendered:<templateId>
watermark:archive:embedded
watermark:archive:warning:<reason>
```

Use one of these warning reasons:

```text
archive-input-empty
archive-visible-unavailable
archive-embed-failed
archive-write-failed
```

## Implementation Tasks

### Task 1: Extend editor result with archive warning

**Files:**
- Modify `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- Modify `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`

- [ ] **Step 1: Add failing processor-note tests**

Add two tests:

- fake editor returns `PhotoWatermarkApplied()` and result contains only `watermark:rendered:<templateId>`
- fake editor returns `PhotoWatermarkApplied(warning = "archive-embed-failed")` and result contains `watermark:warning:archive-embed-failed`

The current `PhotoWatermarkApplied.warning` already exists, so this test should pass or require only naming alignment. Keep the warning note format already used by current code unless package 2 intentionally narrows it.

- [ ] **Step 2: Run focused test**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
```

Expected result: current tests plus the new warning test pass after alignment.

### Task 2: Add archive construction helper

**Files:**
- Modify `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- Create `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkArchiveEditorTest.kt`

- [ ] **Step 1: Add failing helper tests**

Create tests for a helper named `buildWatermarkArchive()`:

- payload is the pre-watermark byte array
- manifest `watermarkTemplateId` matches the rendered template
- manifest `payloadSha256` matches the payload
- manifest `visibleImageSha256` matches the post-EXIF visible JPEG passed to the helper
- empty original bytes return warning `archive-input-empty`

- [ ] **Step 2: Implement helper near the editor**

Add an internal helper:

```kotlin
internal fun buildWatermarkArchive(
    originalBytes: ByteArray,
    visibleBytes: ByteArray,
    templateId: String,
    originalWidth: Int,
    originalHeight: Int
): OcwmJpegContainer.EmbeddedArchive?
```

Return `null` for empty `originalBytes` or empty `visibleBytes`. The caller maps `null` to a stable warning. The manifest `pipelineStage` must be `after-upstream-postprocessors-before-watermark`.

- [ ] **Step 3: Run helper tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
```

Expected result: helper tests pass.

### Task 3: Embed archive after EXIF restore

**Files:**
- Modify `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- Modify `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkArchiveEditorTest.kt`

- [ ] **Step 1: Add test for final write ordering seam**

If direct `AndroidPhotoWatermarkEditor` testing is impractical on JVM because of Android `Bitmap`, extract a small internal helper:

```kotlin
internal fun embedArchiveAfterVisibleWrite(
    originalBytes: ByteArray,
    visibleBytesAfterExifRestore: ByteArray?,
    templateId: String,
    originalWidth: Int,
    originalHeight: Int
): Pair<ByteArray?, String?>
```

Test:

- `visibleBytesAfterExifRestore == null` returns `null` bytes and warning `archive-visible-unavailable`
- valid visible JPEG plus original JPEG returns bytes from `OcwmJpegContainer.embedArchive()`
- extracted payload from returned bytes equals original bytes

- [ ] **Step 2: Wire helper into `AndroidPhotoWatermarkEditor.apply()`**

Current flow:

```text
read sourceBytes
decode sourceBytes
render watermark
compress visible JPEG
write visible JPEG
restore EXIF
return PhotoWatermarkApplied
```

Target flow:

```text
read sourceBytes
decode sourceBytes
render watermark
compress visible JPEG
write visible JPEG
restore EXIF
read visible bytes after EXIF restore
embed OCWM archive with sourceBytes as payload
write archived visible JPEG
return PhotoWatermarkApplied(warning = null or archive warning)
```

Do not call `restorePreservedExif()` after embedding OCWM.

- [ ] **Step 3: Run app watermark tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
```

Expected result: processor and archive helper tests pass.

### Task 4: Preserve existing behavior and bridge expectations

**Files:**
- Modify `app/src/test/java/com/opencamera/app/camera/AlgorithmProcessorBridgesTest.kt` only if the new archive note changes expected bridge output.

- [ ] **Step 1: Run bridge tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest
```

Expected result: bridge tests pass. If they fail because `watermark:` notes now include archive notes, update assertions to accept the complete note list while preserving existing `watermark:rendered:<templateId>` behavior.

- [ ] **Step 2: Run focused watermark and media tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest
```

Expected result: all focused app tests pass.

## Acceptance

- `AndroidPhotoWatermarkEditor` embeds an OCWM archive after EXIF restoration.
- The embedded payload is byte-identical to the JPEG read before watermark rendering.
- Visible watermark rendering behavior remains unchanged.
- Archive failures degrade to warning notes and do not turn a successful visible watermark into capture failure.
- No session/UI/coordinator code owns archive bytes.
- Focused verification passes:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest
```
