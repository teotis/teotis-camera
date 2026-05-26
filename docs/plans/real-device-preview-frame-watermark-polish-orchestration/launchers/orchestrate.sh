#!/usr/bin/env bash
set -euo pipefail

# Compute REPO_ROOT dynamically from the script's location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAN_DIR/../../.." && pwd)"
REPO_ROOT="$(git -C "$REPO_ROOT" rev-parse --show-toplevel 2>/dev/null || echo "$REPO_ROOT")"

GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK="$PLAN_DIR/status/.orchestrate.lock"

ORCHESTRATION_MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"

# --- Lock helpers ---
acquire_lock() {
  local attempts=0
  while [ $attempts -lt 30 ]; do
    if mkdir "$LOCK" 2>/dev/null; then
      trap 'rm -rf "$LOCK"' EXIT
      return 0
    fi
    attempts=$((attempts + 1))
    sleep 1
  done
  echo "ERROR: Could not acquire lock at $LOCK after 30 seconds" >&2
  exit 1
}

release_lock() {
  rm -rf "$LOCK"
  trap - EXIT
}

# --- Read package list from graph ---
read_packages() {
  awk -F'\t' 'FNR > 1 && $1 != "" { print $1 }' "$GRAPH"
}

read_functional_packages() {
  awk -F'\t' 'FNR > 1 && $1 != "" && $NF != "1" { print $1 }' "$GRAPH"
}

read_finalize_package() {
  awk -F'\t' 'FNR > 1 && $NF == "1" { print $1 }' "$GRAPH"
}

# --- Read state.tsv for a package ---
get_state() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $2 }' "$STATE"
}

# --- Update state.tsv for a package ---
set_state() {
  local pkg="$1"
  local new_state="$2"
  local tmp="$STATE.tmp"
  awk -F'\t' -v p="$pkg" -v s="$new_state" 'BEGIN{OFS="\t"} $1 == p {$2=s} {print}' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

# --- Get package doc path from graph ---
get_package_doc() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $2 }' "$GRAPH"
}

# --- Get status file path from graph ---
get_status_file() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $3 }' "$GRAPH"
}

# --- Get dependencies from graph ---
get_dependencies() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $4 }' "$GRAPH"
}

# --- Get wave from graph ---
get_wave() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $7 }' "$GRAPH"
}

# --- Get branch from graph ---
get_branch() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $8 }' "$GRAPH"
}

# --- Check if all functional packages are completed ---
all_functional_completed() {
  local all_done=1
  while IFS= read -r pkg; do
    local st
    st="$(get_state "$pkg")"
    if [ "$st" != "completed" ]; then
      all_done=0
      break
    fi
  done < <(read_functional_packages)
  echo "$all_done"
}

# --- Check if a package is ready to launch ---
is_ready() {
  local pkg="$1"
  local st
  st="$(get_state "$pkg")"
  if [ "$st" != "pending" ]; then
    echo 0
    return
  fi
  local deps
  deps="$(get_dependencies "$pkg")"
  if [ -z "$deps" ]; then
    echo 1
    return
  fi
  IFS=',' read -ra dep_list <<< "$deps"
  for dep in "${dep_list[@]}"; do
    dep_st="$(get_state "$dep")"
    if [ "$dep_st" != "completed" ] && [ "$dep_st" != "finalized" ]; then
      echo 0
      return
    fi
  done
  echo 1
}

# --- Count currently running packages ---
count_running() {
  local count=0
  while IFS= read -r pkg; do
    local st
    st="$(get_state "$pkg")"
    if [ "$st" = "launched" ] || [ "$st" = "in_progress" ]; then
      count=$((count + 1))
    fi
  done < <(read_packages)
  echo "$count"
}

# --- Launch a package as a Claude Code background agent ---
launch_package() {
  local pkg="$1"
  local pkg_doc
  pkg_doc="$(get_package_doc "$pkg")"
  local status_file
  status_file="$(get_status_file "$pkg")"
  local branch
  branch="$(get_branch "$pkg")"
  local name="agent-${pkg}"

  local prompt="Read $PLAN_DIR/INDEX.md and $PLAN_DIR/$pkg_doc. Implement package $pkg. Write evidence to $PLAN_DIR/$status_file when done. Do NOT edit INDEX.md or other status files. Run repo shell commands through rtk and use isolated Gradle in worktrees."

  echo "Launching $name ..."

  local claude_args=(
    --bg
    --name "$name"
    "$prompt"
  )

  if [ -n "${CLAUDE_MODEL:-}" ]; then
    claude_args+=(--model "$CLAUDE_MODEL")
  fi
  if [ -n "${CLAUDE_PERMISSION_MODE:-}" ]; then
    claude_args+=(--permission-mode "$CLAUDE_PERMISSION_MODE")
  fi
  if [ -n "${CLAUDE_SETTING_SOURCES:-}" ]; then
    claude_args+=(--setting-sources "$CLAUDE_SETTING_SOURCES")
  fi

  set +e
  local output
  output="$(claude "${claude_args[@]}" 2>&1)"
  local status=$?
  set -e

  if [ "$status" -ne 0 ]; then
    echo "$output" >&2
    set_state "$pkg" "blocked"
    echo "ERROR: Failed to launch $name" >&2
    return "$status"
  fi

  echo "$output"
  local now
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  set_state "$pkg" "launched"

  # Update launched_at in state.tsv
  local tmp="$STATE.tmp"
  awk -F'\t' -v p="$pkg" -v t="$now" 'BEGIN{OFS="\t"} $1 == p {$4=t} {print}' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"

  echo "  $name launched at $now"
}

# --- Subcommand: start ---
cmd_start() {
  echo "=== Orchestration Start ==="
  echo "Plan: $PLAN_DIR"
  echo "Graph: $GRAPH"
  echo "State: $STATE"
  echo "Max parallel: $ORCHESTRATION_MAX_PARALLEL"
  echo

  # Preflight: validate graph
  local pkg_ids=()
  while IFS= read -r pkg; do
    pkg_ids+=("$pkg")
  done < <(read_packages)

  if [ ${#pkg_ids[@]} -eq 0 ]; then
    echo "ERROR: No packages found in graph" >&2
    exit 1
  fi

  # Check for 99-finalize
  local finalize_pkg
  finalize_pkg="$(read_finalize_package)"
  if [ -z "$finalize_pkg" ]; then
    echo "ERROR: No finalize package found in graph" >&2
    exit 1
  fi

  # Preflight: validate dependencies reference existing packages
  while IFS= read -r pkg; do
    local deps
    deps="$(get_dependencies "$pkg")"
    if [ -n "$deps" ]; then
      IFS=',' read -ra dep_list <<< "$deps"
      for dep in "${dep_list[@]}"; do
        local found=0
        for check_pkg in "${pkg_ids[@]}"; do
          if [ "$check_pkg" = "$dep" ]; then
            found=1
            break
          fi
        done
        if [ "$found" -eq 0 ]; then
          echo "ERROR: Package '$pkg' depends on '$dep' which does not exist in graph" >&2
          exit 1
        fi
      done
    fi
  done < <(read_packages)

  echo "Graph preflight OK: ${#pkg_ids[@]} packages found"
  echo

  # Launch ready packages
  cmd_advance_inner "start"
}

# --- Subcommand: advance ---
cmd_advance() {
  local from_pkg=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --from)
        from_pkg="$2"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done

  if [ -n "$from_pkg" ]; then
    echo "=== Advance (triggered by $from_pkg) ==="
  else
    echo "=== Advance (manual) ==="
  fi

  cmd_advance_inner "advance"
}

# --- Core advance logic ---
cmd_advance_inner() {
  local mode="$1"
  acquire_lock

  local running
  running="$(count_running)"
  local launched=0

  # Check if finalize is already done
  local finalize_pkg
  finalize_pkg="$(read_finalize_package)"
  local finalize_st
  finalize_st="$(get_state "$finalize_pkg")"
  if [ "$finalize_st" = "finalized" ]; then
    echo "All packages finalized. Orchestration complete."
    release_lock
    return 0
  fi

  # Check for blocked/stale/invalid packages
  while IFS= read -r pkg; do
    local st
    st="$(get_state "$pkg")"
    if [ "$st" = "blocked" ] || [ "$st" = "stale" ] || [ "$st" = "invalid" ]; then
      echo "WARNING: Package '$pkg' is in state '$st'. Use 'retry $pkg' to reset."
    fi
  done < <(read_packages)

  # Launch ready functional packages
  while IFS= read -r pkg; do
    # Skip finalize package
    local is_fin
    is_fin="$(awk -F'\t' -v p="$pkg" '$1 == p { print $NF }' "$GRAPH")"
    if [ "$is_fin" = "1" ]; then
      continue
    fi

    local ready
    ready="$(is_ready "$pkg")"
    if [ "$ready" -eq 1 ] && [ "$running" -lt "$ORCHESTRATION_MAX_PARALLEL" ]; then
      launch_package "$pkg"
      launched=$((launched + 1))
      running=$((running + 1))
    fi
  done < <(read_functional_packages)

  # Check if all functional packages are completed -> launch finalize
  local all_done
  all_done="$(all_functional_completed)"
  if [ "$all_done" -eq 1 ]; then
    local fin_ready
    fin_ready="$(is_ready "$finalize_pkg")"
    if [ "$fin_ready" -eq 1 ]; then
      echo
      echo "All functional packages completed. Launching finalize..."
      launch_package "$finalize_pkg"
      launched=$((launched + 1))
    fi
  fi

  if [ "$launched" -eq 0 ] && [ "$mode" = "start" ]; then
    echo "No packages ready to launch."
  elif [ "$launched" -eq 0 ]; then
    echo "No new packages ready to launch."
  else
    echo
    echo "Launched $launched package(s)."
    echo
    echo "View agents with:"
    echo "  claude agents --cwd \"$REPO_ROOT\""
  fi

  release_lock
}

# --- Subcommand: status ---
cmd_status() {
  echo "=== Orchestration Status ==="
  echo
  printf "%-35s %-12s %-20s %-20s %-15s %-10s %s\n" "PACKAGE" "STATE" "LAUNCHED" "COMPLETED" "VERIFICATION" "INTEGRATION" "LAST ERROR"
  printf "%-35s %-12s %-20s %-20s %-15s %-10s %s\n" "---" "---" "---" "---" "---" "---" "---"
  while IFS=$'\t' read -r pkg state launched_at completed_at agent branch worktree base_commit commit_hash verification integration cleanup last_error; do
    [ "$pkg" = "package_id" ] && continue
    printf "%-35s %-12s %-20s %-20s %-15s %-10s %s\n" "$pkg" "$state" "${launched_at:--}" "${completed_at:--}" "${verification:--}" "${integration:--}" "${last_error:--}"
  done < "$STATE"
  echo
  echo "Graph: $GRAPH"
  echo "State: $STATE"
}

# --- Subcommand: retry ---
cmd_retry() {
  local pkg="$1"
  if [ -z "$pkg" ]; then
    echo "ERROR: Usage: orchestrate.sh retry <package-id>" >&2
    echo "Valid package IDs: $(read_packages | tr '\n' ' ')" >&2
    exit 1
  fi

  local st
  st="$(get_state "$pkg")"
  if [ "$st" != "blocked" ] && [ "$st" != "stale" ] && [ "$st" != "invalid" ]; then
    echo "ERROR: Package '$pkg' is in state '$st'. Only blocked/stale/invalid packages can be retried." >&2
    exit 1
  fi

  echo "Resetting package '$pkg' from '$st' to 'pending'..."
  set_state "$pkg" "pending"
  echo "Done. Run 'advance' or 'start' to relaunch."
}

# --- Subcommand: finalize ---
cmd_finalize() {
  echo "=== Finalize ==="
  local finalize_pkg
  finalize_pkg="$(read_finalize_package)"
  local st
  st="$(get_state "$finalize_pkg")"
  if [ "$st" = "finalized" ]; then
    echo "Already finalized."
    return 0
  fi
  if [ "$st" = "blocked" ] || [ "$st" = "stale" ] || [ "$st" = "invalid" ]; then
    echo "WARNING: Finalize package is in state '$st'. Use 'retry $finalize_pkg' first."
    return 1
  fi
  cmd_advance_inner "finalize"
}

# --- Main ---
main() {
  local cmd="${1:-help}"
  shift || true

  case "$cmd" in
    start)
      cmd_start
      ;;
    advance)
      cmd_advance "$@"
      ;;
    status)
      cmd_status
      ;;
    retry)
      cmd_retry "${1:-}"
      ;;
    finalize)
      cmd_finalize
      ;;
    help|--help|-h)
      echo "Usage: orchestrate.sh <command> [options]"
      echo
      echo "Commands:"
      echo "  start                    Launch first wave of ready packages"
      echo "  advance [--from <pkg>]   Check and launch newly ready packages"
      echo "  status                   Print package state table"
      echo "  retry <package-id>       Reset blocked/stale/invalid package to pending"
      echo "  finalize                 Run or re-run the finalize package"
      echo
      echo "Environment:"
      echo "  ORCHESTRATION_MAX_PARALLEL  Max parallel agents (default: 10)"
      echo "  CLAUDE_MODEL                Claude model override"
      echo "  CLAUDE_PERMISSION_MODE      Permission mode override"
      echo "  CLAUDE_SETTING_SOURCES      Setting sources override"
      ;;
    *)
      echo "ERROR: Unknown command '$cmd'. Run 'orchestrate.sh help' for usage." >&2
      exit 1
      ;;
  esac
}

main "$@"
