#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd "${script_dir}/.." && pwd)"
shared_build_root="${OPENCAMERA_BUILD_ROOT:-${CODEX_BUILD_ROOT:-$HOME/.codex-build/OpenCamera}}"

volatile_paths=(
  "${shared_build_root}/app/intermediates/merged_res_blame_folder/debug/mergeDebugResources"
  "${shared_build_root}/app/kotlin"
  "${shared_build_root}/app/snapshot"
  "${shared_build_root}/core/mode/kotlin"
  "${shared_build_root}/core/mode/snapshot"
  "${shared_build_root}/core/session/kotlin"
  "${shared_build_root}/core/session/snapshot"
  "${shared_build_root}/feature/mode-humanistic/kotlin"
  "${shared_build_root}/feature/mode-humanistic/snapshot"
  "${shared_build_root}/feature/mode-night/kotlin"
  "${shared_build_root}/feature/mode-night/snapshot"
)

reset_volatile_state() {
  local removed_count=0
  local candidate
  local hashed_root

  for candidate in "${volatile_paths[@]}"; do
    if [[ -e "${candidate}" ]]; then
      rm -rf "${candidate}"
      printf 'Removed volatile build state: %s\n' "${candidate}"
      removed_count=$((removed_count + 1))
    fi
  done

  for hashed_root in "${shared_build_root}"/*; do
    if [[ ! -d "${hashed_root}" ]]; then
      continue
    fi
    for candidate in \
      "${hashed_root}/app/intermediates/merged_res_blame_folder/debug/mergeDebugResources" \
      "${hashed_root}/app/kotlin" \
      "${hashed_root}/app/snapshot" \
      "${hashed_root}/core/mode/kotlin" \
      "${hashed_root}/core/mode/snapshot" \
      "${hashed_root}/core/session/kotlin" \
      "${hashed_root}/core/session/snapshot" \
      "${hashed_root}/feature/mode-humanistic/kotlin" \
      "${hashed_root}/feature/mode-humanistic/snapshot" \
      "${hashed_root}/feature/mode-night/kotlin" \
      "${hashed_root}/feature/mode-night/snapshot"; do
      if [[ -e "${candidate}" ]]; then
        rm -rf "${candidate}"
        printf 'Removed volatile build state: %s\n' "${candidate}"
        removed_count=$((removed_count + 1))
      fi
    done
  done

  if [[ "${removed_count}" -eq 0 ]]; then
    printf 'No volatile stage-6b1 build state found under %s\n' "${shared_build_root}"
  fi
}

assert_no_appledouble_noise() {
  local matches=()
  while IFS= read -r path; do
    matches+=("${path}")
  done < <(cd "${project_root}" && find feature/mode-humanistic scripts -name '._*' | sort)

  if [[ "${#matches[@]}" -gt 0 ]]; then
    printf 'Found unexpected AppleDouble files:\n' >&2
    printf '  %s\n' "${matches[@]}" >&2
    exit 1
  fi
}

remove_appledouble_noise() {
  local removed_any=0
  while IFS= read -r path; do
    rm -f "${project_root}/${path}"
    printf 'Removed AppleDouble noise: %s\n' "${path}"
    removed_any=1
  done < <(cd "${project_root}" && find feature/mode-humanistic scripts -name '._*' | sort)

  if [[ "${removed_any}" -eq 0 ]]; then
    printf 'No AppleDouble noise found under feature/mode-humanistic or scripts\n'
  fi
}

run_gradle() {
  printf '\n==> ./gradlew %s --no-daemon\n' "$*"
  (
    cd "${project_root}"
    export OPENCAMERA_BUILD_ROOT="${shared_build_root}"
    ./gradlew "$@" --no-daemon
  )
}

reset_volatile_state
remove_appledouble_noise
assert_no_appledouble_noise

run_gradle :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
run_gradle :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest
run_gradle :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
run_gradle :app:assembleDebug
