# Watermark 2.0 Reversible Watermark Productization

## Goal

Make reversible watermark behavior a trustworthy Watermark 2.0 capability: OpenCamera-generated watermarked JPEGs should remain normal visible photos while carrying an extractable watermark-free payload through the existing OCWM archive format.

## Context

- User request: include reversible watermark in Watermark 2.0.
- Verified facts:
  - `docs/plans/2026-05-24-reversible-watermark-archive-index.md` defines the OCWM archive plan.
  - `PhotoWatermarkPostProcessor.kt` currently imports `OcwmJpegContainer`, builds `ReversibleWatermarkArchiveManifest`, embeds archive bytes, and reports archive warnings through watermark pipeline notes.
  - `PhotoWatermarkArchiveEditorTest.kt` verifies archive construction and extraction for valid/invalid inputs.
  - `scripts/extract_ocwm_original.py` can extract the embedded original JPEG.
  - `scripts/verify_reversible_watermark_archive.sh` runs core media tests, app archive tests, Python syntax, and extractor end-to-end comparison.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/OcwmJpegContainer.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkArchiveEditorTest.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/OcwmJpegContainerTest.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/OcwmExtractorCompatibilityTest.kt`
  - `scripts/extract_ocwm_original.py`
  - `scripts/verify_reversible_watermark_archive.sh`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-en/strings.xml`
- Non-goals:
  - Do not change the OCWM binary format unless tests expose a real defect.
  - Do not support third-party watermark removal.
  - Do not add video/RAW reversible payloads in this package.

## Implementation Scope

- Confirm archive embedding applies consistently to all still-photo watermark templates, including `pure-text`, `blur-four-border`, and any new `professional-bottom-bar`.
- Add product-visible copy only if there is already a suitable settings/status location; do not build a full editor.
- If adding UI copy, phrase it honestly: reversible archive applies to OpenCamera-generated still-photo watermarks and can be extracted by the script/tooling.
- Preserve archive warnings in pipeline notes and do not hide failures.

## Steps

1. Inspect `AndroidPhotoWatermarkEditor.apply()` to confirm original bytes are captured immediately before watermark rendering and embedded after visible write/EXIF restore.
2. Add or update tests so every Watermark 2.0 template path either embeds an archive or emits a clear archive warning.
3. If `professional-bottom-bar` is added, include it in archive tests and extractor compatibility fixtures.
4. Add concise Settings/Watermark Lab copy only if it can be backed by the current behavior.
5. Run reversible archive verification and focused watermark verification.

## Acceptance Criteria

- For OpenCamera still-photo watermark output, a normal gallery can still open the visible watermarked JPEG.
- The same JPEG can contain OCWM `APP15` chunks with a byte-identical pre-watermark JPEG payload.
- Extraction verifies the payload SHA-256 before writing output.
- Pipeline notes distinguish rendered watermark success from archive warnings/failures.
- Reversible behavior remains independent of UI, mode plugin, session, coordinator, and adapter ownership.

## Verification Commands

```bash
rtk ./scripts/verify_reversible_watermark_archive.sh
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Risks And Notes

- `ExifInterface.saveAttributes()` can strip custom segments if archive embedding happens too early. Keep OCWM embedding after EXIF restore unless a test proves otherwise.
- Reversible archive increases JPEG size. Do not add compression/patch optimization in this package; the approved design stores the full pre-watermark JPEG.
- Final user-facing trust depends on extraction working outside Android, so keep the Python extractor smoke test in the acceptance path.
