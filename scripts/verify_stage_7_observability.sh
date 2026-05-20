#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

./gradlew --no-daemon -Pkotlin.incremental=false \
  :core:session:test \
  --tests com.opencamera.core.session.DefaultCameraSessionTest \
  --tests com.opencamera.core.session.SessionDiagnosticsTest

./gradlew --no-daemon -Pkotlin.incremental=false \
  :app:testDebugUnitTest \
  --tests com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitorTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest \
  --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest \
  --tests com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitorTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.camera.CameraSessionCoordinatorTest

./gradlew --no-daemon :app:assembleDebug
