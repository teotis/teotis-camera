# Reversible Watermark Extraction And Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Provide a standalone extractor and verification loop that proves a single archived watermarked JPEG can yield the embedded watermark-free JPEG without the Android app.

**Architecture:** Keep extraction independent from Android. The command-line script parses JPEG markers, finds `APP15` segments with `OCWM\0`, reassembles chunks, verifies SHA-256, and writes the embedded payload. The script exists as a compatibility reference for the archive format.

**Tech Stack:** Python 3 standard library for the extractor, Kotlin/JVM tests for archive fixture generation, Gradle focused tests, existing Stage 7 verification script.

---

## File Structure

- Create `scripts/extract_ocwm_original.py`
  - No third-party dependencies.
  - Accepts input JPEG and output path.
  - Verifies embedded payload SHA-256 before writing.
- Create `core/media/src/test/kotlin/com/opencamera/core/media/OcwmExtractorCompatibilityTest.kt`
  - Generates a small archived JPEG fixture using `OcwmJpegContainer`.
  - Writes fixture files under the Gradle test temp directory only.
- Optionally add `scripts/verify_reversible_watermark_archive.sh`
  - Runs focused tests and a script smoke check. Use only if the repo already accepts new verification scripts for similar features.

## Extractor Contract

Command:

```bash
rtk python3 scripts/extract_ocwm_original.py input-watermarked.jpg output-original.jpg
```

Expected success output:

```text
extracted ocwm original: output-original.jpg
payload-sha256: <lowercase-hex>
```

Expected failure examples:

```text
error: ocwm archive not found
error: ocwm chunk missing
error: ocwm payload sha256 mismatch
error: unsupported ocwm version 2
```

The script must exit with status `0` on success and non-zero on failure.

## Implementation Tasks

### Task 1: Add Python extractor

**Files:**
- Create `scripts/extract_ocwm_original.py`

- [ ] **Step 1: Write the script with marker scanning**

Implement:

- read bytes from input path
- verify JPEG starts with `0xFF 0xD8`
- scan marker segments until `SOS`
- collect `APP15` payloads whose first five bytes are `OCWM\0`
- parse binary fields using `struct.unpack(">B B I I Q I", segment_payload[5:27])` after the magic
- read manifest JSON from chunk 0
- sort chunks by `chunkIndex`
- verify all indexes from `0` through `chunkCount - 1` exist
- concatenate payload slices
- verify length and SHA-256
- write output file

Use only `argparse`, `hashlib`, `json`, `struct`, `sys`, and `pathlib`.

- [ ] **Step 2: Run syntax check**

```bash
rtk python3 -m py_compile scripts/extract_ocwm_original.py
```

Expected result: command exits successfully.

### Task 2: Add compatibility fixture test

**Files:**
- Create `core/media/src/test/kotlin/com/opencamera/core/media/OcwmExtractorCompatibilityTest.kt`

- [ ] **Step 1: Add Kotlin test that writes fixture bytes**

Create a test that:

- builds a synthetic visible JPEG byte array
- builds a synthetic original JPEG byte array
- embeds original into visible using `OcwmJpegContainer.embedArchive()`
- asserts `OcwmJpegContainer.extractArchive()` returns the original payload

This test proves Kotlin-side generation works before invoking Python.

- [ ] **Step 2: Run compatibility test**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmExtractorCompatibilityTest
```

Expected result: compatibility test passes.

### Task 3: Add script smoke verification

**Files:**
- Create `scripts/verify_reversible_watermark_archive.sh`

- [ ] **Step 1: Add a shell script that runs focused checks**

The script should:

- run `:core:media:test` for OCWM tests
- run `:app:testDebugUnitTest` for watermark archive tests
- run `rtk python3 -m py_compile scripts/extract_ocwm_original.py`

If a fixture-generation command is added later, include it in this script only after it exists in the repo.

- [ ] **Step 2: Run the script**

```bash
rtk ./scripts/verify_reversible_watermark_archive.sh
```

Expected result: all focused checks pass.

### Task 4: Run final Stage 7 gate

**Files:**
- No source changes in this task.

- [ ] **Step 1: Run Stage 7 verification**

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Expected result: script passes. If it fails with a transient Gradle build-directory issue under `~/.codex-build/OpenCamera`, rerun the smallest failing task serially before treating it as a product regression.

## Acceptance

- `scripts/extract_ocwm_original.py` can extract a valid OCWM payload from a single JPEG.
- The extractor fails closed on missing chunks, unsupported versions, malformed JPEGs, and hash mismatches.
- The extractor has no dependency on Android, Gradle, or OpenCamera runtime code.
- Focused verification passes:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.OcwmJpegContainerTest --tests com.opencamera.core.media.OcwmExtractorCompatibilityTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest
rtk python3 -m py_compile scripts/extract_ocwm_original.py
rtk ./scripts/verify_stage_7_observability.sh
```
