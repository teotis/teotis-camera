#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

rtk ./gradlew --no-daemon \
  :core:media:test \
  --tests com.opencamera.core.media.ShotExecutorTest

rtk ./gradlew --no-daemon \
  :core:device:test \
  --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest

rtk ./gradlew --no-daemon \
  :core:session:test \
  --tests com.opencamera.core.session.DefaultCameraSessionTest

rtk ./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest

rtk ./gradlew --no-daemon :app:assembleDebug
