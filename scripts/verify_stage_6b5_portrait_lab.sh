#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew \
  :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest \
  :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest \
  :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest \
  :app:assembleDebug
