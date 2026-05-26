#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
PLAN_DIR="$REPO_ROOT/docs/plans/humanistic-image-quality-tuning-orchestration"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK="$PLAN_DIR/status/.orchestrate.lock"

ORCHESTRATION_MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"

# --- helpers ---

log() { echo "[orchestrate] $*"; }
err() { echo "[orchestrate] ERROR: $*" >&2; }

acquire_lock() {
  local waited=0
  while [ -f "$LOCK" ]; do
    local pid
    pid="$(cat "$LOCK" 2>/dev/null || true)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      if [ "$waited" -ge 30 ]; then
        err "Lock held by PID $pid for >30s. Remove $LOCK if stale."
        exit 1
      fi
      sleep 1
      waited=$((waited + 1))
    else
      log "Removing stale lock (PID $pid no longer running)"
      rm -f "$LOCK"
    fi
  done
  echo $$ > "$LOCK"
}

release_lock() {
  rm -f "$LOCK"
}

trap release_lock EXIT

# Read package list from graph (skip header, skip finalize)
functional_packages() {
  awk -F'\t' 'FNR > 1 && $10 != "1" { print $1 }' "$GRAPH"
}

all_package_ids() {
  awk -F'\t' 'FNR > 1 { print $1 }' "$GRAPH"
}

# Get field from graph by package_id. Column indices: 1=id, 2=doc, 3=status, 4=deps, 5=dep_type, 6=wave, 7=branch, 8=worktree, 9=manual, 10=finalize
graph_field() {
  local pkg="$1" col="$2"
  awk -F'\t' -v p="$pkg" -v c="$col" 'FNR > 1 && $1 == p { print $c }' "$GRAPH"
}

# Get state field from state.tsv. Column indices: 1=id, 2=state, 3=launched_at, 4=completed_at, ...
state_field() {
  local pkg="$1" col="$2"
  awk -F'\t' -v p="$pkg" -v c="$col" 'FNR > 1 && $1 == p { print $c }' "$STATE"
}

# Update a field in state.tsv
update_state() {
  local pkg="$1" col="$2" val="$3"
  local tmp="$STATE.tmp"
  awk -F'\t' -v p="$pkg" -v c="$col" -v v="$val" '
    BEGIN { OFS="\t" }
    FNR == 1 { print; next }
    $1 == p { $c = v }
    { print }
  ' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

# Read markdown status from status/<id>.md
md_status() {
  local pkg="$1"
  local sfile="$PLAN_DIR/status/${pkg}.md"
  if [ -f "$sfile" ]; then
    grep -m1 '^\- \*\*Status\*\*:' "$sfile" 2>/dev/null | sed 's/.*: *//' || echo "unknown"
  else
    echo "missing"
  fi
}

# Check if a package is ready: all dependencies completed in state.tsv
is_ready() {
  local pkg="$1"
  local deps
  deps="$(graph_field "$pkg" 4)"
  if [ -z "$deps" ]; then
    return 0
  fi
  IFS=',' read -ra dep_arr <<< "$deps"
  for dep in "${dep_arr[@]}"; do
    local dep_state
    dep_state="$(state_field "$dep" 2)"
    if [ "$dep_state" != "completed" ] && [ "$dep_state" != "finalized" ]; then
      return 1
    fi
  done
  return 0
}

# Count currently running (launched/in_progress) agents
running_count() {
  awk -F'\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress") { count++ } END { print count+0 }' "$STATE"
}

# Launch a Claude Code background agent
launch_agent() {
  local pkg="$1"
  local pkg_doc="$PLAN_DIR/$(graph_field "$pkg" 2)"
  local status_file="$PLAN_DIR/$(graph_field "$pkg" 3)"
  local name="agent-${pkg}"

  local prompt
  prompt="Read $PLAN_DIR/INDEX.md and $pkg_doc. Execute package $pkg. Write evidence to $status_file when done. Do NOT edit INDEX.md, runtime code, tests, package docs, or other status files."

  log "Launching $name"
  local output status
  set +e
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    output="$(claude \
      --bg \
      --name "$name" \
      --model "$CLAUDE_MODEL" \
      --permission-mode "$CLAUDE_PERMISSION_MODE" \
      --setting-sources "$CLAUDE_SETTING_SOURCES" \
      "$prompt" 2>&1)"
  else
    output="$(claude \
      --bg \
      --name "$name" \
      --model "$CLAUDE_MODEL" \
      --setting-sources "$CLAUDE_SETTING_SOURCES" \
      "$prompt" 2>&1)"
  fi
  status=$?
  set -e

  if [ "$status" -ne 0 ]; then
    err "Failed to launch $name: $output"
    update_state "$pkg" 2 "blocked"
    update_state "$pkg" 13 "launch failed: $(echo "$output" | head -1)"
    return 1
  fi

  echo "$output"
  update_state "$pkg" 2 "launched"
  update_state "$pkg" 3 "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  update_state "$pkg" 5 "$name"
  log "$name launched successfully"
}

# --- subcommands ---

cmd_start() {
  log "Starting orchestration"
  log "Plan: $PLAN_DIR"
  log "Graph: $GRAPH"
  log "State: $STATE"

  # Preflight
  [ -f "$GRAPH" ] || { err "Package graph not found: $GRAPH"; exit 1; }
  [ -f "$STATE" ] || { err "State file not found: $STATE"; exit 1; }
  command -v claude >/dev/null 2>&1 || { err "claude CLI not found"; exit 1; }

  acquire_lock

  local launched=0
  local max="$ORCHESTRATION_MAX_PARALLEL"
  local running
  running="$(running_count)"

  for pkg in $(functional_packages); do
    if [ "$launched" -ge "$max" ]; then
      log "Max parallel ($max) reached, stopping"
      break
    fi
    local st
    st="$(state_field "$pkg" 2)"
    if [ "$st" = "launched" ] || [ "$st" = "in_progress" ] || [ "$st" = "completed" ]; then
      log "$pkg: already $st, skipping"
      continue
    fi
    if is_ready "$pkg"; then
      launch_agent "$pkg"
      launched=$((launched + 1))
    else
      log "$pkg: not ready (waiting on dependencies)"
    fi
  done

  if [ "$launched" -eq 0 ]; then
    log "No packages ready to launch"
  else
    log "Launched $launched package(s)"
  fi

  log "View agents with: claude agents --cwd \"$REPO_ROOT\""
  release_lock
}

cmd_advance() {
  local from_pkg="${1:-unknown}"
  log "Advance called from: $from_pkg"

  acquire_lock

  # Sync state from markdown status files
  for pkg in $(all_package_ids); do
    local md_st
    md_st="$(md_status "$pkg")"
    local ts_st
    ts_st="$(state_field "$pkg" 2)"
    if [ "$md_st" = "completed" ] && [ "$ts_st" != "completed" ] && [ "$ts_st" != "finalized" ]; then
      log "Syncing $pkg: state.tsv=$ts_st -> completed (from markdown status)"
      update_state "$pkg" 2 "completed"
      update_state "$pkg" 4 "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    elif [ "$md_st" = "blocked" ] && [ "$ts_st" != "blocked" ]; then
      log "Syncing $pkg: state.tsv=$ts_st -> blocked (from markdown status)"
      update_state "$pkg" 2 "blocked"
    elif [ "$md_st" = "finalized" ] && [ "$ts_st" != "finalized" ]; then
      log "Syncing $pkg: state.tsv=$ts_st -> finalized (from markdown status)"
      update_state "$pkg" 2 "finalized"
    fi
  done

  # Check for blocked/stale/invalid
  for pkg in $(all_package_ids); do
    local st
    st="$(state_field "$pkg" 2)"
    if [ "$st" = "blocked" ] || [ "$st" = "stale" ] || [ "$st" = "invalid" ]; then
      log "WARNING: $pkg is $st"
    fi
  done

  # Check if all functional packages are completed
  local all_done=true
  for pkg in $(functional_packages); do
    local st
    st="$(state_field "$pkg" 2)"
    if [ "$st" != "completed" ]; then
      all_done=false
      break
    fi
  done

  if [ "$all_done" = true ]; then
    local fin_st
    fin_st="$(state_field "99-finalize" 2)"
    if [ "$fin_st" = "completed" ] || [ "$fin_st" = "finalized" ]; then
      log "All packages completed and 99-finalize already $fin_st"
    else
      log "All functional packages completed. Launching 99-finalize."
      launch_agent "99-finalize"
    fi
  else
    # Launch newly ready packages
    local launched=0
    local max="$ORCHESTRATION_MAX_PARALLEL"

    for pkg in $(functional_packages); do
      if [ "$launched" -ge "$max" ]; then
        log "Max parallel ($max) reached, stopping"
        break
      fi
      local st
      st="$(state_field "$pkg" 2)"
      if [ "$st" = "launched" ] || [ "$st" = "in_progress" ] || [ "$st" = "completed" ]; then
        continue
      fi
      if is_ready "$pkg"; then
        launch_agent "$pkg"
        launched=$((launched + 1))
      fi
    done

    if [ "$launched" -eq 0 ]; then
      log "No new packages ready to launch"
    else
      log "Launched $launched new package(s)"
    fi
  fi

  log "View agents with: claude agents --cwd \"$REPO_ROOT\""
  release_lock
}

cmd_status() {
  acquire_lock

  # Sync state from markdown first (read-only sync)
  for pkg in $(all_package_ids); do
    local md_st
    md_st="$(md_status "$pkg")"
    local ts_st
    ts_st="$(state_field "$pkg" 2)"
    if [ "$md_st" = "completed" ] && [ "$ts_st" != "completed" ] && [ "$ts_st" != "finalized" ]; then
      update_state "$pkg" 2 "completed"
      update_state "$pkg" 4 "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    fi
  done

  printf "%-45s %-12s %-20s %-20s %-10s\n" "PACKAGE" "STATE" "LAUNCHED" "COMPLETED" "ERROR"
  printf "%-45s %-12s %-20s %-20s %-10s\n" "---" "---" "---" "---" "---"
  for pkg in $(all_package_ids); do
    local st launched completed err
    st="$(state_field "$pkg" 2)"
    launched="$(state_field "$pkg" 3)"
    completed="$(state_field "$pkg" 4)"
    err="$(state_field "$pkg" 13)"
    printf "%-45s %-12s %-20s %-20s %-10s\n" "$pkg" "$st" "${launched:--}" "${completed:--}" "${err:--}"
  done

  release_lock
}

cmd_retry() {
  local pkg="$1"
  [ -n "$pkg" ] || { err "Usage: orchestrate.sh retry <package-id>"; exit 1; }

  acquire_lock

  local st
  st="$(state_field "$pkg" 2)"
  if [ "$st" != "blocked" ] && [ "$st" != "stale" ] && [ "$st" != "invalid" ]; then
    err "$pkg is $st — can only retry blocked/stale/invalid packages"
    release_lock
    exit 1
  fi

  local last_err
  last_err="$(state_field "$pkg" 13)"
  log "Retrying $pkg (previous error: ${last_err:--})"

  # Reset state to pending
  update_state "$pkg" 2 "pending"
  update_state "$pkg" 3 ""
  update_state "$pkg" 4 ""
  update_state "$pkg" 5 ""
  update_state "$pkg" 13 ""

  # Also reset markdown status
  local sfile="$PLAN_DIR/status/${pkg}.md"
  if [ -f "$sfile" ]; then
    sed -i '' 's/^\- \*\*Status\*\*: .*/- **Status**: pending/' "$sfile"
  fi

  # Try to launch
  if is_ready "$pkg"; then
    launch_agent "$pkg"
  else
    log "$pkg reset to pending but dependencies not yet met"
  fi

  release_lock
}

cmd_finalize() {
  log "Running finalize"
  acquire_lock

  local fin_st
  fin_st="$(state_field "99-finalize" 2)"
  if [ "$fin_st" = "finalized" ]; then
    log "99-finalize already finalized, nothing to do"
    release_lock
    return 0
  fi

  # Check all functional packages completed
  for pkg in $(functional_packages); do
    local st
    st="$(state_field "$pkg" 2)"
    if [ "$st" != "completed" ]; then
      err "Cannot finalize: $pkg is $st (must be completed)"
      release_lock
      exit 1
    fi
  done

  launch_agent "99-finalize"
  release_lock
}

# --- main ---

case "${1:-help}" in
  start)
    cmd_start
    ;;
  advance)
    shift
    from=""
    while [ $# -gt 0 ]; do
      case "$1" in
        --from) from="$2"; shift 2 ;;
        *) shift ;;
      esac
    done
    cmd_advance "$from"
    ;;
  status)
    cmd_status
    ;;
  retry)
    cmd_retry "${2:-}"
    ;;
  finalize)
    cmd_finalize
    ;;
  help|--help|-h)
    echo "Usage: orchestrate.sh {start|advance|status|retry|finalize}"
    echo ""
    echo "  start     Launch ready packages (wave 1)"
    echo "  advance   Check state and launch next ready packages or finalize"
    echo "  status    Print package state table"
    echo "  retry     Reset and relaunch a blocked/stale/invalid package"
    echo "  finalize  Run or re-run 99-finalize"
    ;;
  *)
    err "Unknown command: $1"
    echo "Run: orchestrate.sh help"
    exit 1
    ;;
esac
