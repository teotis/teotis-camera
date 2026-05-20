#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/.." && pwd)"

run_gradle() {
  printf '\n==> ./gradlew %s --no-daemon\n' "$*"
  (
    cd "${project_root}"
    ./gradlew "$@" --no-daemon
  )
}

run_gradle :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest \
  --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
run_gradle :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
run_gradle :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
run_gradle :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
run_gradle :app:assembleDebug
