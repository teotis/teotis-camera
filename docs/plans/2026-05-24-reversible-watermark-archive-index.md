# 2026-05-24 Reversible Watermark Archive Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are written for text-only agents and do not require screenshot or video analysis.

## Goal

Make OpenCamera still-photo watermarks reversible in a self-contained archival JPEG: the visible file remains a normal watermarked `.jpg`, and the same file also carries an embedded, directly extractable, watermark-free JPEG generated immediately before watermark rendering.

## Product Decision

The approved product direction is:

- Prefer long-term self-contained archives over small patch-only payloads.
- Do not depend on app-private sidecars for the primary reversible format.
- Store a complete watermark-free JPEG, not only a watermarked-area patch.
- Keep parsing easy enough that a future standalone script can recover the watermark-free image without the Android app.
- Support only OpenCamera-generated watermarks. Do not attempt arbitrary third-party watermark removal.

## Current Code Facts

- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` currently owns still-photo watermark rendering.
- `AndroidPhotoWatermarkEditor.apply()` reads the already-saved JPEG bytes, renders the watermark, writes a new JPEG, then restores selected EXIF attributes.
- `app/src/main/java/com/opencamera/app/AppContainer.kt` runs postprocessors in this order:
  - multi-frame placeholder
  - document auto crop
  - frame ratio
  - portrait render
  - algorithm/filter render
  - photo watermark
  - selfie mirror
  - pipeline metadata
- Therefore the reversible archive's "original" must mean the exact JPEG bytes that enter `PhotoWatermarkPostProcessor`, after upstream edits and before the visible watermark is burned in.
- Current app dependency includes `androidx.exifinterface:exifinterface:1.3.7`.

## Recommended Work Packages

1. [OCWM JPEG Container Format](./2026-05-24-reversible-watermark-ocwm-container.md)
   - Add pure Kotlin contracts and encoder/decoder for an `APP15` chunked JPEG payload named `OCWM`.
   - Define manifest schema and tests using synthetic JPEG byte arrays.
   - This package should not touch Android, CameraX, UI, or real bitmap rendering.

2. [Watermark Pipeline Embedding](./2026-05-24-reversible-watermark-pipeline-embedding.md)
   - Modify the Android photo watermark editor to embed the pre-watermark JPEG into the final watermarked JPEG.
   - Preserve current watermark behavior and pipeline diagnostics.
   - Add focused app tests around processor notes and editor integration seams.

3. [Extraction Script And Verification](./2026-05-24-reversible-watermark-extraction-verification.md)
   - Add a no-dependency command-line extractor that can recover the embedded original JPEG from a single image.
   - Add fixture-generation tests that prove a generated archived JPEG can be parsed outside Android.
   - Add focused Gradle verification and a script-level smoke check.

## Recommended Sequence

Execute package 1 first. It establishes the binary container and manifest contract with no Android behavior risk.

Execute package 2 second. It plugs the contract into the current postprocessor after EXIF restore so `ExifInterface.saveAttributes()` cannot strip the new custom segment.

Execute package 3 last. It proves the format satisfies the long-term "拿到图片就可以分析，解析原图出来" requirement.

## Target Artifact Shape

```text
normal JPEG file
  SOI
  APP0/APP1/APP2 existing metadata
  APP1 XMP optional OpenCamera summary
  APP15 OCWM chunk 0
  APP15 OCWM chunk 1
  APP15 OCWM chunk N
  SOS visible watermarked JPEG scan data
  EOI

embedded OCWM payload
  watermark-free JPEG bytes from before PhotoWatermarkPostProcessor rendering
```

The visible JPEG must remain viewable in existing galleries. The embedded payload may be ignored by third-party apps without affecting visible rendering.

## Non-Goals

- Do not build AI watermark removal.
- Do not remove third-party watermarks.
- Do not expose a UI setting in this implementation package.
- Do not support video watermark archives in this package.
- Do not support RAW or DNG archive payloads in this package.
- Do not change the order of existing postprocessors except for the internal write/read/embed order inside the watermark editor.
- Do not enter a new product stage without user approval.

## Global Acceptance

- A watermarked JPEG produced by OpenCamera can be opened normally as a watermarked photo.
- The same JPEG contains `APP15` chunks with the `OCWM\0` magic identifier.
- A standalone script can extract a byte-identical watermark-free JPEG payload from that single watermarked JPEG.
- The extractor verifies the embedded payload SHA-256 against the manifest before writing output.
- Existing watermark rendering tests still pass.
- Stage 7 observability verification still passes after implementation:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Conflict Warnings

- Do not store the only recovery payload in app-private storage. Sidecars can be added separately, but this approved plan requires self-contained JPEG archives.
- Do not base the archival format on patch coordinates as the primary mechanism. Patch recovery is smaller but less robust for long-term extraction.
- Do not rely on `ExifInterface` to preserve custom JPEG segments after calling `saveAttributes()`. Embed `OCWM` after EXIF restore.
- Do not let UI, session, or coordinator code become responsible for deciding archive bytes. This belongs to the media postprocessor/editor path.
- Do not change `PipelineMetadataPostProcessor` into a binary metadata writer. It should remain a diagnostic notes processor.
