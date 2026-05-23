#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ADAPTER="app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt"

if grep -n "CameraX FocusMeteringAction not yet implemented" "$ADAPTER"; then
  echo "Tap-focus CameraX execution is still stubbed." >&2
  exit 1
fi

if ! grep -n "startFocusAndMetering" "$ADAPTER" >/dev/null; then
  echo "Tap-focus CameraX execution does not call startFocusAndMetering." >&2
  exit 1
fi

if ! grep -n "FocusMeteringAction\.FLAG_AF\|FocusMeteringAction\.FLAG_AE" "$ADAPTER" >/dev/null; then
  echo "Tap-focus CameraX execution does not configure AF/AE metering flags." >&2
  exit 1
fi

rtk ./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.PreviewMeteringActionPlannerTest \
  --tests com.opencamera.app.camera.CameraSessionCoordinatorTest

rtk ./gradlew --no-daemon :app:assembleDebug
