#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel)"
PLAN_DIR="$REPO_ROOT/docs/plans/zoom-cockpit-v2-productization-orchestration"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK="$PLAN_DIR/status/.orchestrate.lock"
MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"

# ─── helpers ────────────────────────────────────────────────────────────────────

log()  { echo "[orchestrate] $*"; }
err()  { echo "[orchestrate] ERROR: $*" >&2; }
die()  { err "$@"; exit 1; }

acquire_lock() {
  local attempts=0
  while ! mkdir "$LOCK" 2>/dev/null; do
    attempts=$((attempts + 1))
    if [ "$attempts" -ge 60 ]; then
      die "Could not acquire lock after 60s. If stale, remove: $LOCK"
    fi
    sleep 1
  done
  trap 'rm -rf "$LOCK"' EXIT
}

release_lock() {
  rm -rf "$LOCK"
  trap - EXIT
}

# Read a column value from a TSV by package_id (first column).
# Usage: tsv_get <file> <package_id> <column_index>
tsv_get() {
  local file="$1" pid="$2" col="$3"
  awk -F'\t' -v pid="$pid" -v col="$col" 'FNR > 1 && $1 == pid { print $col; exit }' "$file"
}

# Read state.tsv column by package_id. Column indices:
# 1=package_id 2=state 3=launched_at 4=completed_at 5=agent 6=branch 7=worktree
# 8=base_commit 9=commit_hash 10=verification 11=integration 12=cleanup 13=last_error
state_get() {
  tsv_get "$STATE" "$1" "$2"
}

# Replace a row in state.tsv for a given package_id.
state_set_row() {
  local pid="$1" new_row="$2"
  local tmp="${STATE}.tmp.$$"
  awk -F'\t' -v pid="$pid" -v row="$new_row" '
    FNR == 1 { print; next }
    $1 == pid { print row; next }
    { print }
  ' "$STATE" > "$tmp" && mv "$tmp" "$STATE"
}

# Update a single column in state.tsv for a package_id.
state_set() {
  local pid="$1" col="$2" val="$3"
  local tmp="${STATE}.tmp.$$"
  awk -F'\t' -v pid="$pid" -v col="$col" -v val="$val" '
    BEGIN { OFS="\t" }
    FNR == 1 { print; next }
    $1 == pid { $col = val; print; next }
    { print }
  ' "$STATE" > "$tmp" && mv "$tmp" "$STATE"
}

# Get all functional package IDs (finalize != 1) in wave order.
functional_packages() {
  awk -F'\t' 'FNR > 1 && $NF != 1 { print $1 }' "$GRAPH"
}

# Get all package IDs.
all_package_ids() {
  awk -F'\t' 'FNR > 1 { print $1 }' "$GRAPH"
}

# Get dependencies for a package from the graph (comma-separated).
graph_deps() {
  tsv_get "$GRAPH" "$1" 4
}

# Get dependency type for a package from the graph.
graph_dep_type() {
  tsv_get "$GRAPH" "$1" 5
}

# Get wave for a package.
graph_wave() {
  tsv_get "$GRAPH" "$1" 6
}

# Get branch for a package.
graph_branch() {
  tsv_get "$GRAPH" "$1" 7
}

# Get worktree for a package.
graph_worktree() {
  tsv_get "$GRAPH" "$1" 8
}

# Check if package is finalize.
is_finalize() {
  local pid="$1"
  local fin
  fin=$(tsv_get "$GRAPH" "$pid" 10)
  [ "$fin" = "1" ]
}

# Count currently running (launched or in_progress) packages.
running_count() {
  awk -F'\t' '
    FNR > 1 && ($2 == "launched" || $2 == "in_progress") { count++ }
    END { print count+0 }
  ' "$STATE"
}

# Check if all functional packages are completed.
all_functional_completed() {
  local pid state_val
  for pid in $(functional_packages); do
    state_val=$(state_get "$pid" 2)
    if [ "$state_val" != "completed" ]; then
      return 1
    fi
  done
  return 0
}

# Check if all dependencies of a package are completed.
deps_completed() {
  local pid="$1"
  local deps
  deps=$(graph_deps "$pid")
  if [ -z "$deps" ]; then
    return 0
  fi
  local IFS=','
  for dep in $deps; do
    dep=$(echo "$dep" | xargs) # trim
    local dep_state
    dep_state=$(state_get "$dep" 2)
    if [ "$dep_state" != "completed" ] && [ "$dep_state" != "finalized" ]; then
      return 1
    fi
  done
  return 0
}

# Determine if a package is ready to launch: pending and all deps completed.
is_ready() {
  local pid="$1"
  local st
  st=$(state_get "$pid" 2)
  if [ "$st" != "pending" ]; then
    return 1
  fi
  deps_completed "$pid"
}

# Launch a Claude Code background agent for a package.
launch_package() {
  local pid="$1"
  local pkg_doc="$PLAN_DIR/$(tsv_get "$GRAPH" "$pid" 2)"
  local status_file="$PLAN_DIR/$(tsv_get "$GRAPH" "$pid" 3)"
  local branch
  branch=$(graph_branch "$pid")
  local worktree_path="$REPO_ROOT/$(graph_worktree "$pid")"
  local name="zoom-v2-${pid}"
  local now
  now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

  local prompt
  prompt="Read $PLAN_DIR/INDEX.md and $pkg_doc. Implement package $pid. Write evidence to $status_file when done. Do NOT edit INDEX.md or other status files. When done, run: bash $PLAN_DIR/launchers/orchestrate.sh advance --from $pid"

  log "Launching $name (wave $(graph_wave "$pid"))"

  local claude_args=(--bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --setting-sources "$CLAUDE_SETTING_SOURCES")
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    claude_args+=(--permission-mode "$CLAUDE_PERMISSION_MODE")
  fi

  set +e
  local output
  output="$(claude "${claude_args[@]}" "$prompt" 2>&1)"
  local status=$?
  set -e

  if [ "$status" -ne 0 ]; then
    err "Failed to launch $name:"
    echo "$output" >&2
    state_set "$pid" 2 "blocked"
    state_set "$pid" 13 "launch_failed: $(echo "$output" | head -1)"
    return 1
  fi

  # Update state to launched
  state_set "$pid" 2 "launched"
  state_set "$pid" 3 "$now"
  state_set "$pid" 5 "$name"
  state_set "$pid" 6 "$branch"
  state_set "$pid" 7 "$worktree_path"

  log "Launched $name"
}

# ─── subcommands ────────────────────────────────────────────────────────────────

cmd_start() {
  log "Preflight check..."
  [ -f "$GRAPH" ] || die "package-graph.tsv not found at $GRAPH"
  [ -f "$STATE" ] || die "state.tsv not found at $STATE"

  acquire_lock

  local ready_count=0
  local running
  running=$(running_count)
  local available=$((MAX_PARALLEL - running))

  if [ "$available" -le 0 ]; then
    log "Already at max parallel ($MAX_PARALLEL). $running agents running."
    release_lock
    return 0
  fi

  log "Ready to launch up to $available packages (max_parallel=$MAX_PARALLEL, running=$running)"

  local pid
  for pid in $(functional_packages); do
    if [ "$ready_count" -ge "$available" ]; then
      break
    fi
    if is_ready "$pid"; then
      launch_package "$pid" || true
      ready_count=$((ready_count + 1))
    fi
  done

  if [ "$ready_count" -eq 0 ]; then
    log "No packages ready to launch."
    if all_functional_completed; then
      log "All functional packages completed. Run 'finalize' or wait for advance to trigger 99-finalize."
    fi
  else
    log "Launched $ready_count package(s)."
  fi

  release_lock

  echo
  echo "View agents with:"
  echo "  claude agents --cwd \"$REPO_ROOT\""
}

cmd_advance() {
  local from_hint=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --from) from_hint="$2"; shift 2 ;;
      *) shift ;;
    esac
  done

  if [ -n "$from_hint" ]; then
    log "advance called from: $from_hint"
  fi

  acquire_lock

  # Check for blocked/stale/invalid packages
  local pid st
  for pid in $(all_package_ids); do
    st=$(state_get "$pid" 2)
    case "$st" in
      blocked|stale|invalid)
        log "Package $pid is $st. Cannot advance automatically. Use 'retry $pid' after fixing."
        release_lock
        return 1
        ;;
    esac
  done

  # Check if 99-finalize should be launched
  if all_functional_completed; then
    local finalize_state
    finalize_state=$(state_get "99-finalize" 2)
    if [ "$finalize_state" = "pending" ]; then
      log "All functional packages completed. Launching 99-finalize."
      launch_package "99-finalize" || true
      release_lock
      echo
      echo "View agents with:"
      echo "  claude agents --cwd \"$REPO_ROOT\""
      return 0
    elif [ "$finalize_state" = "finalized" ]; then
      log "99-finalize already finalized. Orchestration complete."
      release_lock
      return 0
    elif [ "$finalize_state" = "launched" ] || [ "$finalize_state" = "in_progress" ]; then
      log "99-finalize is already running."
      release_lock
      return 0
    fi
  fi

  # Launch newly ready functional packages
  local ready_count=0
  local running
  running=$(running_count)
  local available=$((MAX_PARALLEL - running))

  if [ "$available" -le 0 ]; then
    log "Already at max parallel ($MAX_PARALLEL). $running agents running."
    release_lock
    return 0
  fi

  for pid in $(functional_packages); do
    if [ "$ready_count" -ge "$available" ]; then
      break
    fi
    if is_ready "$pid"; then
      launch_package "$pid" || true
      ready_count=$((ready_count + 1))
    fi
  done

  if [ "$ready_count" -eq 0 ]; then
    log "No new packages ready to launch."
  else
    log "Launched $ready_count new package(s)."
  fi

  release_lock

  echo
  echo "View agents with:"
  echo "  claude agents --cwd \"$REPO_ROOT\""
}

cmd_status() {
  echo "=== Zoom Cockpit V2 Orchestration Status ==="
  echo
  printf "%-45s %-12s %-10s %-10s %-12s %-12s %s\n" \
    "PACKAGE" "STATE" "LAUNCHED" "COMPLETED" "VERIFIED" "INTEGRATION" "LAST_ERROR"
  printf "%-45s %-12s %-10s %-10s %-12s %-12s %s\n" \
    "-------" "-----" "--------" "---------" "---------" "-----------" "----------"

  local pid
  for pid in $(all_package_ids); do
    local st launched completed verified integration last_err
    st=$(state_get "$pid" 2)
    launched=$(state_get "$pid" 3)
    completed=$(state_get "$pid" 4)
    verified=$(state_get "$pid" 10)
    integration=$(state_get "$pid" 11)
    last_err=$(state_get "$pid" 13)
    [ -z "$launched" ] && launched="—"
    [ -z "$completed" ] && completed="—"
    [ -z "$verified" ] && verified="—"
    [ -z "$integration" ] && integration="—"
    [ -z "$last_err" ] && last_err="—"
    printf "%-45s %-12s %-10s %-10s %-12s %-12s %s\n" \
      "$pid" "$st" "$launched" "$completed" "$verified" "$integration" "$last_err"
  done

  echo
  local running
  running=$(running_count)
  echo "Running: $running / $MAX_PARALLEL (max parallel)"
}

cmd_retry() {
  local pid="${1:-}"
  [ -n "$pid" ] || die "Usage: orchestrate.sh retry <package-id>"

  # Validate package exists
  local found=0
  local p
  for p in $(all_package_ids); do
    if [ "$p" = "$pid" ]; then
      found=1
      break
    fi
  done
  [ "$found" -eq 1 ] || die "Unknown package: $pid"

  acquire_lock

  local st
  st=$(state_get "$pid" 2)
  case "$st" in
    blocked|stale|invalid)
      log "Resetting $pid from $st to pending."
      local prev_error
      prev_error=$(state_get "$pid" 13)
      state_set "$pid" 2 "pending"
      state_set "$pid" 3 ""
      state_set "$pid" 4 ""
      state_set "$pid" 5 ""
      state_set "$pid" 9 ""
      state_set "$pid" 10 ""
      state_set "$pid" 13 ""
      log "Previous error was: $prev_error"
      log "Package $pid reset to pending. Run 'start' or 'advance' to relaunch."
      ;;
    pending)
      log "Package $pid is already pending. Use 'start' or 'advance' to launch it."
      ;;
    launched|in_progress)
      log "Package $pid is currently $st. Cannot retry a running package."
      ;;
    completed|finalized)
      log "Package $pid is already $st. Cannot retry without explicit user override."
      ;;
  esac

  release_lock
}

cmd_finalize() {
  acquire_lock

  local finalize_state
  finalize_state=$(state_get "99-finalize" 2)

  case "$finalize_state" in
    finalized)
      log "99-finalize already finalized. Idempotent no-op."
      release_lock
      return 0
      ;;
    launched|in_progress)
      log "99-finalize is currently $finalize_state. Wait for it to complete."
      release_lock
      return 0
      ;;
    blocked|stale|invalid)
      log "99-finalize is $finalize_state. Resetting to pending for retry."
      state_set "99-finalize" 2 "pending"
      state_set "99-finalize" 13 ""
      ;;
  esac

  if ! all_functional_completed; then
    die "Not all functional packages are completed. Cannot launch finalize."
  fi

  launch_package "99-finalize" || true
  release_lock

  echo
  echo "View agents with:"
  echo "  claude agents --cwd \"$REPO_ROOT\""
}

# ─── main ───────────────────────────────────────────────────────────────────────

command="${1:-}"
shift 2>/dev/null || true

case "$command" in
  start)    cmd_start "$@" ;;
  advance)  cmd_advance "$@" ;;
  status)   cmd_status "$@" ;;
  retry)    cmd_retry "$@" ;;
  finalize) cmd_finalize "$@" ;;
  *)
    echo "Usage: orchestrate.sh {start|advance|status|retry|finalize}"
    echo
    echo "  start              Launch all ready packages up to max parallel"
    echo "  advance            Check and launch newly ready packages or finalize"
    echo "  status             Print orchestration status table"
    echo "  retry <package>    Reset blocked/stale/invalid package to pending"
    echo "  finalize           Run or re-run 99-finalize"
    exit 2
    ;;
esac
