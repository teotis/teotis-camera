#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/.." && pwd)"
shared_build_root="${CODEX_BUILD_ROOT:-$HOME/.codex-build/OpenCamera}"

volatile_modules=(
  "core/settings"
  "core/media"
  "feature/mode-photo"
  "feature/mode-video"
)

reset_volatile_kotlin_state() {
  local removed_count=0
  local candidate
  local hashed_root
  local module

  for module in "${volatile_modules[@]}"; do
    for candidate in \
      "${shared_build_root}/${module}/kotlin" \
      "${shared_build_root}/${module}/snapshot"; do
      if [[ -d "${candidate}" ]]; then
        rm -rf "${candidate}"
        printf 'Removed volatile Kotlin state: %s\n' "${candidate}"
        removed_count=$((removed_count + 1))
      fi
    done

    for hashed_root in "${shared_build_root}"/*; do
      if [[ ! -d "${hashed_root}" ]]; then
        continue
      fi
      for candidate in \
        "${hashed_root}/${module}/kotlin" \
        "${hashed_root}/${module}/snapshot"; do
        if [[ -d "${candidate}" ]]; then
          rm -rf "${candidate}"
          printf 'Removed volatile Kotlin state: %s\n' "${candidate}"
          removed_count=$((removed_count + 1))
        fi
      done
    done
  done

  if [[ "${removed_count}" -eq 0 ]]; then
    printf 'No volatile Kotlin state directories found under %s\n' "${shared_build_root}"
  fi
}

run_gradle() {
  printf '\n==> ./gradlew %s --no-daemon\n' "$*"
  (
    cd "${project_root}"
    ./gradlew "$@" --no-daemon
  )
}

reset_volatile_kotlin_state

run_gradle :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
run_gradle :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionSettingsManagerTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
run_gradle :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
run_gradle :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
run_gradle :app:assembleDebug
