# Google Motion Photo Container Writer

> For text-only agents: this is a deterministic media-container task. Implement JPEG-based Google Motion Photo writing and tests from byte fixtures. Do not touch camera runtime until the writer is stable.

## Goal

Create a Google Motion Photo compatible JPEG output from:

```text
processed primary JPEG still + short MP4 motion segment
```

The first pass should produce a single JPEG-based Motion Photo file. HEIC/AVIF, Ultra HDR gainmap ordering, audio, metadata scoring tracks, and visual quality are out of scope.

## Official Format Reference

Use [Android Developers Motion Photo format 1.0](https://developer.android.com/media/platform/motion-photo-format?hl=en) as the source of truth.

Writer target for the first pass:

- Primary item: `image/jpeg`, semantic `Primary`.
- Secondary item: `video/mp4`, semantic `MotionPhoto`, appended at the end of the file.
- Camera XMP:
  - `Camera:MotionPhoto="1"`
  - `Camera:MotionPhotoVersion="1"`
  - `Camera:MotionPhotoPresentationTimestampUs="<timestamp or -1>"`
- Container XMP:
  - one `Primary` item
  - one `MotionPhoto` item
  - `Item:Length` for the video item equals the exact MP4 byte length
- Do not write old `MicroVideoOffset` as the primary locator.

## Current Baseline

- Current app writes JPEG still plus `.live.json` sidecar.
- `LivePhotoBundle.motionPath` is currently planned but not populated with real MP4 bytes.
- `OcwmJpegContainer.kt` contains JPEG segment parsing/insertion ideas, but it is for a private OCWM archive and must not be reused as the Motion Photo wire format.

## Required Behavior

- The writer validates that input still bytes are JPEG.
- The writer injects or replaces an XMP APP1 segment containing Motion Photo metadata.
- The writer appends the MP4 bytes as the final bytes in the file.
- The writer records video item length exactly.
- The writer can parse its own output enough for tests to verify:
  - XMP exists
  - motion fields exist
  - MP4 bytes are final
  - no bytes appear after the motion segment
- If JPEG or MP4 input is invalid, fail before replacing the saved still.

## Suggested Files

Core pure writer:

- Create `core/media/src/main/kotlin/com/opencamera/core/media/MotionPhotoJpegContainer.kt`
- Create `core/media/src/test/kotlin/com/opencamera/core/media/MotionPhotoJpegContainerTest.kt`

Optional app file helper:

- Create `app/src/main/java/com/opencamera/app/camera/live/MotionPhotoFileMaterializer.kt`
- Update `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`

## Suggested Core API

```kotlin
data class MotionPhotoContainerSpec(
    val stillMimeType: String = "image/jpeg",
    val motionMimeType: String = "video/mp4",
    val presentationTimestampUs: Long = -1L,
    val motionLengthBytes: Long
)

object MotionPhotoJpegContainer {
    fun write(
        jpegBytes: ByteArray,
        motionBytes: ByteArray,
        spec: MotionPhotoContainerSpec
    ): ByteArray
}
```

Keep this API pure. File IO belongs in app-layer materializer.

## XMP Requirements

The XMP packet must include at least these namespaces:

```xml
xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
xmlns:Container="http://ns.google.com/photos/1.0/container/"
xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
```

The generated RDF should express:

```xml
Camera:MotionPhoto="1"
Camera:MotionPhotoVersion="1"
Camera:MotionPhotoPresentationTimestampUs="-1"
```

And a `Container:Directory` with an ordered sequence:

```xml
<rdf:li Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0"/>
<rdf:li Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="12345"/>
```

Use the actual MP4 byte length instead of `12345`.

## JPEG Segment Strategy

Recommended first implementation:

1. Validate SOI marker `FF D8`.
2. Build a standard XMP APP1 segment:
   - marker `FF E1`
   - 2-byte big-endian length including the length bytes
   - payload prefix `http://ns.adobe.com/xap/1.0/\u0000`
   - UTF-8 XMP packet
3. Insert the APP1 segment after SOI and after any existing APP0/JFIF segment.
4. If an existing APP1 XMP segment already contains Motion Photo fields, replace it.
5. Leave EXIF APP1 segments intact.
6. Append MP4 bytes after the JPEG bytes.

If segment replacement is too risky for the first pass, implement insert-only and add a test that rejects input already containing `Camera:MotionPhoto=1`. Replacement can be a second small loop.

## Implementation Steps

1. Add byte fixture helpers in tests.
   - Minimal JPEG: SOI, APP0, SOS, fake entropy bytes, EOI.
   - Minimal MP4 marker bytes can be fake for core tests, but include a simple `ftyp` header-like prefix so validation can distinguish empty/non-MP4 input.

2. Write failing tests:
   - `write rejects non jpeg`.
   - `write rejects empty motion`.
   - `write appends motion bytes at end`.
   - `write includes Camera MotionPhoto fields`.
   - `write includes Container primary and motion items`.
   - `write uses exact motion length`.
   - `write keeps output jpeg prefix valid`.

3. Implement XMP builder.
   - Escape XML attribute values.
   - Keep the packet deterministic.
   - Do not include device-specific product strings in the core writer.

4. Implement JPEG APP1 insertion.
   - Keep helper functions private or internal.
   - Add malformed segment tests for short length, missing SOS, and existing EOI.

5. Add app materializer.
   - Read processed still bytes from `MediaOutputHandle.filePath` or content URI.
   - Read motion MP4 bytes from a temporary file.
   - Write the combined bytes to a new Motion Photo target.
   - Avoid replacing the original still until the combined file is fully written.

6. Update `LivePhotoBundle`.
   - Add fields only if necessary, for example `containerPath` or `containerHandle`.
   - Prefer preserving `stillPath` as the primary visible saved media path if the combined Motion Photo replaces the still.
   - If replacement is not possible under scoped storage, save the Motion Photo as a new `Images` item and point `thumbnailHandle` to it.

## Tests

Core:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.MotionPhotoJpegContainerTest
```

App:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
```

Expected app assertions:

- A fake still file plus fake motion file produce a single output handle.
- Materializer does not delete the source still on writer failure.
- Cleanup deletes temporary motion files after success and failure.
- Pipeline notes include `motion-photo:container=google-jpeg`.

## Storage Notes

Scoped storage is the main risk. The implementation should prefer this order:

1. If the JPEG still output has an editable `contentUri`, open it after CameraX save and replace bytes atomically when possible.
2. If direct replacement is not reliable, create a new MediaStore image item with `_MP.jpg` display name, write combined bytes, then mark the original still as temporary or clean it if the transaction says it is safe.
3. If neither path works, keep the still, delete temp motion, and return `STILL_ONLY_FALLBACK` with `motion-photo:container=failed:<reason>`.

Do not write the Motion Photo as a generic `MediaStore.Files` sidecar if the goal is Google Photos/gallery recognition. It should be an image item.

## Non-Goals

- Do not write old MicroVideo V1 metadata as the primary format.
- Do not implement HEIC/AVIF Motion Photo.
- Do not implement Ultra HDR gainmap ordering in this pass.
- Do not implement video scoring metadata tracks.
- Do not judge whether Google Photos plays the file until real-device manual validation.
