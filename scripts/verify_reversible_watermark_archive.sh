#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "=== OCWM container tests ==="
./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:media:test \
  --tests com.opencamera.core.media.OcwmJpegContainerTest \
  --tests com.opencamera.core.media.OcwmExtractorCompatibilityTest

echo ""
echo "=== Watermark archive app tests ==="
./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest \
  --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest \
  --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest

echo ""
echo "=== Python extractor syntax check ==="
python3 -m py_compile scripts/extract_ocwm_original.py

echo ""
echo "=== All reversible watermark archive checks passed ==="
