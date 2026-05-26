# Reversible Watermark OCWM Container Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Add a pure Kotlin JPEG `APP15` container named `OCWM` that embeds a complete watermark-free JPEG payload inside a visible watermarked JPEG.

**Architecture:** `core:media` owns the format contract because it is pure media-pipeline data, not Android UI or CameraX behavior. The encoder inserts chunked `APP15` segments before the JPEG `SOS` marker. The decoder scans marker segments, reassembles chunks, parses a small JSON manifest, and verifies payload integrity.

**Tech Stack:** Kotlin/JVM, `java.security.MessageDigest`, `kotlin.test`, existing Gradle test tasks.

---

## File Structure

- Create `core/media/src/main/kotlin/com/opencamera/core/media/ReversibleWatermarkArchive.kt`
  - Owns `ReversibleWatermarkArchiveManifest`, `ReversibleWatermarkArchive`, SHA-256 helpers, and manifest JSON encode/decode.
- Create `core/media/src/main/kotlin/com/opencamera/core/media/OcwmJpegContainer.kt`
  - Owns JPEG marker scanning, `APP15` chunk writing, chunk reading, payload verification, and extraction.
- Create `core/media/src/test/kotlin/com/opencamera/core/media/OcwmJpegContainerTest.kt`
  - Tests synthetic JPEG insertion/extraction, multi-chunk reassembly, hash mismatch failure, and JPEGs without archive.

## Format Contract

Use JPEG `APP15` marker `0xFFEF`.

Every OCWM segment payload begins with this binary header:

```text
magic              5 bytes   ASCII "OCWM\0"
formatVersion      1 byte    value 1
flags              1 byte    value 0 for v1
chunkIndex         4 bytes   unsigned big-endian
chunkCount         4 bytes   unsigned big-endian
totalPayloadLength 8 bytes   unsigned big-endian
manifestLength     4 bytes   unsigned big-endian, non-zero only on chunk 0
manifestUtf8       variable  UTF-8 JSON, present only on chunk 0
payloadSlice       variable  raw embedded JPEG bytes
```

The JPEG segment length field is the standard 2-byte big-endian JPEG length and includes the length field itself. Keep each segment length at or below `65535`. Use a conservative `MAX_OCWM_SEGMENT_PAYLOAD_BYTES = 60000` so tests and future metadata growth remain below the JPEG limit.

The visible JPEG must keep its original scan data. Insert OCWM chunks after existing metadata segments and before the first `SOS` marker.

## Manifest Schema

The manifest JSON must be deterministic and compact. Use stable key ordering in the encoder:

```json
{
  "schema": "org.opencamera.reversible-watermark",
  "version": 1,
  "container": "jpeg-app15-ocwm",
  "payloadKind": "embedded-original-jpeg",
  "payloadMimeType": "image/jpeg",
  "payloadCompression": "none",
  "pipelineStage": "after-upstream-postprocessors-before-watermark",
  "watermarkTemplateId": "classic-overlay",
  "visibleImageSha256": "lowercase-hex",
  "payloadSha256": "lowercase-hex",
  "payloadLength": 123456,
  "originalWidth": 4000,
  "originalHeight": 3000
}
```

The pure container package does not need to know how to measure JPEG dimensions. It accepts `originalWidth` and `originalHeight` from the caller and allows `0` when dimensions are unavailable.

## Implementation Tasks

### Task 1: Add archive contracts and deterministic manifest JSON

**Files:**
- Create `core/media/src/main/kotlin/com/opencamera/core/media/ReversibleWatermarkArchive.kt`
- Test `core/media/src/test/kotlin/com/opencamera/core/media/OcwmJpegContainerTest.kt`

- [ ] **Step 1: Add failing tests for manifest round-trip**

Add tests that build a manifest, encode it, decode it, and assert every field is preserved. Also assert key ordering by comparing the exact JSON string for a small manifest.

- [ ] **Step 2: Run the focused test**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmJpegContainerTest
```

Expected result before implementation: compile failure because the new types do not exist.

- [ ] **Step 3: Implement contracts**

Create:

```kotlin
data class ReversibleWatermarkArchiveManifest(
    val schema: String = "org.opencamera.reversible-watermark",
    val version: Int = 1,
    val container: String = "jpeg-app15-ocwm",
    val payloadKind: String = "embedded-original-jpeg",
    val payloadMimeType: String = "image/jpeg",
    val payloadCompression: String = "none",
    val pipelineStage: String = "after-upstream-postprocessors-before-watermark",
    val watermarkTemplateId: String,
    val visibleImageSha256: String,
    val payloadSha256: String,
    val payloadLength: Long,
    val originalWidth: Int = 0,
    val originalHeight: Int = 0
)
```

Add `toJson()` and `fromJson()` in the same file. Use a small explicit JSON encoder/decoder for this fixed schema instead of adding a dependency. Escape `\`, `"`, newline, carriage return, and tab. Reject missing required keys, unsupported `schema`, unsupported `version`, unsupported `container`, and negative `payloadLength`.

- [ ] **Step 4: Run test to verify pass**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmJpegContainerTest
```

Expected result: manifest tests pass.

### Task 2: Add OCWM encoder

**Files:**
- Modify `core/media/src/main/kotlin/com/opencamera/core/media/OcwmJpegContainer.kt`
- Modify `core/media/src/test/kotlin/com/opencamera/core/media/OcwmJpegContainerTest.kt`

- [ ] **Step 1: Add failing tests for inserting chunks before SOS**

Create a minimal synthetic JPEG:

```text
FF D8
FF E0 00 04 00 00
FF DA 00 04 00 00
11 22 33
FF D9
```

Call `OcwmJpegContainer.embedArchive(visibleJpeg, archive)` and assert:

- output starts with `FF D8`
- existing `APP0` remains before `APP15`
- `APP15` appears before `SOS`
- visible scan bytes `11 22 33` remain after `SOS`

- [ ] **Step 2: Implement encoder**

Create an `object OcwmJpegContainer` with:

```kotlin
data class EmbeddedArchive(
    val manifest: ReversibleWatermarkArchiveManifest,
    val payload: ByteArray
)

fun embedArchive(
    visibleJpeg: ByteArray,
    archive: EmbeddedArchive
): ByteArray
```

Validate:

- `visibleJpeg` begins with `0xFF 0xD8`
- `payload` is not empty
- `archive.manifest.payloadLength == payload.size.toLong()`
- `archive.manifest.payloadSha256 == sha256Hex(payload)`

Find the first `SOS` marker and insert all `APP15` chunks immediately before it. If the JPEG has no `SOS`, return an error result or throw `IllegalArgumentException("jpeg-sos-missing")`; tests should assert this message.

- [ ] **Step 3: Run tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmJpegContainerTest
```

Expected result: encoder tests pass.

### Task 3: Add OCWM decoder and verification

**Files:**
- Modify `core/media/src/main/kotlin/com/opencamera/core/media/OcwmJpegContainer.kt`
- Modify `core/media/src/test/kotlin/com/opencamera/core/media/OcwmJpegContainerTest.kt`

- [ ] **Step 1: Add failing tests for extraction**

Test cases:

- single-chunk payload extracts exactly
- multi-chunk payload extracts exactly
- missing archive returns `null`
- missing chunk fails with `IllegalArgumentException("ocwm-chunk-missing")`
- payload hash mismatch fails with `IllegalArgumentException("ocwm-payload-sha256-mismatch")`
- unsupported version fails with `IllegalArgumentException("ocwm-version-unsupported")`

- [ ] **Step 2: Implement decoder**

Add:

```kotlin
fun extractArchive(jpeg: ByteArray): EmbeddedArchive?
```

Scan marker segments from `SOI` to `SOS`. For `APP15` segments whose payload begins with `OCWM\0`, parse the binary header. Group chunks by manifest payload hash. For v1 there should be one archive group. Sort by `chunkIndex`, verify `chunkCount`, concatenate payload slices, verify `totalPayloadLength`, parse manifest from chunk 0, and verify `payloadSha256`.

- [ ] **Step 3: Run tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmJpegContainerTest
```

Expected result: all OCWM container tests pass.

## Acceptance

- `OcwmJpegContainer.embedArchive()` can embed a complete JPEG payload into another JPEG without changing visible scan data.
- `OcwmJpegContainer.extractArchive()` can recover the payload exactly.
- Error messages are stable and covered by tests.
- No Android or app module dependency is introduced into `core:media`.
- Focused verification passes:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmJpegContainerTest
```
