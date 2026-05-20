#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:device:test \
  --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:mode:test \
  --tests com.opencamera.core.mode.ModeCatalogContractsTest

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:session:test \
  --tests com.opencamera.core.session.DefaultCameraSessionTest

./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest \
  --tests com.opencamera.app.SessionSettingsManagerTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterManualRequestTest

./gradlew --no-daemon :app:assembleDebug
