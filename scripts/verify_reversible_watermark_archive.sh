#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

echo "=== OCWM container tests ==="
rtk ./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:media:test \
  --tests com.opencamera.core.media.OcwmJpegContainerTest \
  --tests com.opencamera.core.media.OcwmExtractorCompatibilityTest

echo ""
echo "=== Watermark archive app tests ==="
rtk ./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest \
  --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest \
  --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest

echo ""
echo "=== Python extractor syntax check ==="
rtk python3 -m py_compile scripts/extract_ocwm_original.py

echo ""
echo "=== Python extractor end-to-end test ==="
FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT
OCWM_FIXTURE_DIR="$FIXTURE_DIR" rtk ./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:media:test \
  --tests com.opencamera.core.media.OcwmExtractorCompatibilityTest
if [ ! -f "$FIXTURE_DIR/test-archived.jpg" ] || [ ! -f "$FIXTURE_DIR/test-original.jpg" ]; then
  echo "ERROR: OCWM fixture generation failed" >&2
  exit 1
fi
rtk python3 scripts/extract_ocwm_original.py "$FIXTURE_DIR/test-archived.jpg" "$FIXTURE_DIR/extracted.jpg"
cmp "$FIXTURE_DIR/test-original.jpg" "$FIXTURE_DIR/extracted.jpg"
echo "Python extractor produced byte-identical payload: $FIXTURE_DIR/extracted.jpg ($(wc -c < "$FIXTURE_DIR/extracted.jpg") bytes)"

echo ""
echo "=== All reversible watermark archive checks passed ==="
