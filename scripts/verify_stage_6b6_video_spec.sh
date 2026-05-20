#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew --no-daemon \
  :core:settings:test \
  --tests com.opencamera.core.settings.PersistedSettingsSerializerTest

./gradlew --no-daemon \
  :core:device:test \
  --tests com.opencamera.core.device.VideoSpecSelectionTest

./gradlew --no-daemon \
  :core:session:test \
  --tests com.opencamera.core.session.DefaultCameraSessionTest

./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionUiRenderModelTest

./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionSettingsManagerTest

./gradlew --no-daemon \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterRecordingQualityTest

./gradlew --no-daemon :app:assembleDebug
