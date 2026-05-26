#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(git -C "$PLAN_DIR" rev-parse --show-toplevel)"

GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK="$PLAN_DIR/status/.orchestrate.lock"

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
ORCHESTRATION_MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$GRAPH" ] || { echo "ERROR: package-graph.tsv not found at $GRAPH"; exit 1; }
[ -f "$STATE" ] || { echo "ERROR: state.tsv not found at $STATE"; exit 1; }

# --- Lock helpers ---

acquire_lock() {
  local attempts=0
  while [ $attempts -lt 30 ]; do
    if mkdir "$LOCK" 2>/dev/null; then
      echo "$$" > "$LOCK/pid"
      trap 'release_lock' EXIT
      return 0
    fi
    # Check for stale lock (older than 5 minutes)
    if [ -f "$LOCK/pid" ]; then
      local lock_pid
      lock_pid="$(cat "$LOCK/pid" 2>/dev/null || echo "")"
      if [ -n "$lock_pid" ] && ! kill -0 "$lock_pid" 2>/dev/null; then
        rm -rf "$LOCK"
        continue
      fi
    fi
    sleep 1
    attempts=$((attempts + 1))
  done
  echo "ERROR: could not acquire lock at $LOCK" >&2
  exit 1
}

release_lock() {
  rm -rf "$LOCK"
}

# --- TSV helpers ---

read_state_row() {
  local pkg="$1"
  awk -F'\t' -v pkg="$pkg" 'FNR > 1 && $1 == pkg { found=1; for(i=1;i<=NF;i++) printf "%s\t", $i; print ""; exit } END { if (!found) exit 1 }' "$STATE"
}

read_state_field() {
  local pkg="$1"
  local col="$2"
  awk -F'\t' -v pkg="$pkg" -v col="$col" '
    FNR == 1 { for(i=1;i<=NF;i++) if($i==col) ci=i; next }
    FNR > 1 && $1 == pkg { print $ci; exit }
  ' "$STATE"
}

update_state_field() {
  local pkg="$1"
  local col="$2"
  local val="$3"
  local tmp="$STATE.tmp"
  awk -F'\t' -v pkg="$pkg" -v col="$col" -v val="$val" '
    BEGIN { OFS="\t" }
    FNR == 1 { for(i=1;i<=NF;i++) if($i==col) ci=i; print; next }
    FNR > 1 && $1 == pkg { $ci=val; print; next }
    { print }
  ' "$STATE" > "$tmp" && mv "$tmp" "$STATE"
}

read_graph_deps() {
  local pkg="$1"
  awk -F'\t' -v pkg="$pkg" 'FNR > 1 && $1 == pkg { print $4; exit }' "$GRAPH"
}

read_graph_finalize() {
  local pkg="$1"
  awk -F'\t' -v pkg="$pkg" 'FNR > 1 && $1 == pkg { print $10; exit }' "$GRAPH"
}

all_functional_packages() {
  awk -F'\t' 'FNR > 1 && $10 != "1" { print $1 }' "$GRAPH"
}

all_packages() {
  awk -F'\t' 'FNR > 1 { print $1 }' "$GRAPH"
}

# --- Readiness check ---

is_ready() {
  local pkg="$1"
  local state
  state="$(read_state_field "$pkg" "state")" || return 1

  # Already launched or beyond
  case "$state" in
    pending) ;;
    *) return 1 ;;
  esac

  # Check dependencies
  local deps
  deps="$(read_graph_deps "$pkg")"
  if [ -z "$deps" ]; then
    return 0
  fi

  IFS=',' read -ra dep_list <<< "$deps"
  for dep in "${dep_list[@]}"; do
    local dep_state
    dep_state="$(read_state_field "$dep" "state")" || return 1
    if [ "$dep_state" != "completed" ] && [ "$dep_state" != "finalized" ]; then
      return 1
    fi
  done
  return 0
}

# --- Launch a package agent ---

launch_package() {
  local pkg="$1"
  local name="vivo-x300-${pkg}"
  local package_doc="$PLAN_DIR/packages/${pkg}.md"
  local status_file="$PLAN_DIR/status/${pkg}.md"
  local state_file="$STATE"
  local orchestrator="$PLAN_DIR/launchers/orchestrate.sh"

  [ -f "$package_doc" ] || { echo "ERROR: package doc missing: $package_doc"; return 1; }
  [ -f "$status_file" ] || { echo "ERROR: status file missing: $status_file"; return 1; }

  local prompt="You are executing OpenCamera research/design package ${pkg}.

Repository: $REPO_ROOT
INDEX: $PLAN_DIR/INDEX.md
Package doc: $package_doc
Coordinator status: $status_file
Coordinator state: $state_file
Orchestrator: $orchestrator

Read INDEX.md, your package doc, AGENTS.md, and /Users/dingren/.codex/RTK.md before any command. This orchestration is research/design only: runtime code and tests are read-only. Use rtk for all shell commands. In a worktree, run Gradle through rtk ./scripts/run_isolated_gradle.sh as required by AGENTS.md. Edit only your assigned status file. Do not edit INDEX.md or any other status file. Do not force-push, hard reset, delete branches/worktrees, use network, add secrets, or expand scope. If dependencies are incomplete or a stop gate is hit, write blocked evidence to your status file and stop.

Before calling advance, you must:
- Set coordinator status to completed or blocked.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row consistently.

Tail step:
bash $orchestrator advance --from $pkg"

  echo "Launching $name"
  local output status
  set +e
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    output="$(claude \
      --bg \
      --name "$name" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --permission-mode "$CLAUDE_PERMISSION_MODE" \
      --setting-sources "$CLAUDE_SETTING_SOURCES" \
      "$prompt" 2>&1)"
  else
    output="$(claude \
      --bg \
      --name "$name" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --setting-sources "$CLAUDE_SETTING_SOURCES" \
      "$prompt" 2>&1)"
  fi
  status=$?
  set -e

  if [ "$status" -ne 0 ]; then
    echo "$output" >&2
    if [ "$CLAUDE_PERMISSION_MODE" = "auto" ] && grep -qi "requires opting in" <<<"$output"; then
      echo >&2
      echo "ERROR: Claude Code requires one interactive auto-mode opt-in before --bg can use --permission-mode auto." >&2
      echo "Run once interactively: claude --permission-mode auto" >&2
    fi
    update_state_field "$pkg" "state" "blocked"
    update_state_field "$pkg" "last_error" "launch failed: exit $status"
    return "$status"
  fi
  echo "$output"

  local now
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  update_state_field "$pkg" "state" "launched"
  update_state_field "$pkg" "launched_at" "$now"
  update_state_field "$pkg" "agent" "$name"
}

# --- Count running ---

count_launched_not_done() {
  local count=0
  for pkg in $(all_packages); do
    local state
    state="$(read_state_field "$pkg" "state")" || continue
    case "$state" in
      launched|in_progress) count=$((count + 1)) ;;
    esac
  done
  echo "$count"
}

# --- Subcommands ---

cmd_start() {
  acquire_lock
  echo "=== Vivo X300 Pixel/Shutter Lifecycle Orchestration ==="
  echo "Plan: $PLAN_DIR"
  echo "Repo: $REPO_ROOT"
  echo "Claude Code: $(claude --version || true)"
  echo "Model: $CLAUDE_MODEL"
  echo "Max parallel: $ORCHESTRATION_MAX_PARALLEL"
  echo

  # Preflight: validate graph
  echo "Preflight: validating package graph..."
  local pkg_ids=""
  while IFS=$'\t' read -r pkg_id _ _ _ _ _ _ _ _ _; do
    [ "$pkg_id" = "package_id" ] && continue
    if echo "$pkg_ids" | grep -qw "$pkg_id"; then
      echo "ERROR: duplicate package ID: $pkg_id" >&2
      release_lock; exit 1
    fi
    pkg_ids="$pkg_ids $pkg_id"
  done < "$GRAPH"
  echo "Graph OK: $(echo $pkg_ids | wc -w | tr -d ' ') packages"
  echo

  # Launch ready packages
  local launched=0
  local running
  running="$(count_launched_not_done)"

  for pkg in $(all_functional_packages); do
    if [ "$running" -ge "$ORCHESTRATION_MAX_PARALLEL" ]; then
      echo "Max parallel ($ORCHESTRATION_MAX_PARALLEL) reached, stopping."
      break
    fi
    if is_ready "$pkg"; then
      launch_package "$pkg" || true
      launched=$((launched + 1))
      running=$((running + 1))
    fi
  done

  if [ "$launched" -eq 0 ]; then
    echo "No packages ready to launch."
  fi

  echo
  echo "View agents: claude agents --cwd \"$REPO_ROOT\""
  echo "Check status: bash \"$orchestrator\" status"
  echo "Advance: bash \"$orchestrator\" advance"
  release_lock
}

cmd_advance() {
  acquire_lock
  local from_pkg=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --from) from_pkg="$2"; shift 2 ;;
      *) shift ;;
    esac
  done

  if [ -n "$from_pkg" ]; then
    echo "Advance triggered by: $from_pkg"
  fi

  # Check for blocked/stale/invalid
  local has_blockers=0
  for pkg in $(all_packages); do
    local state
    state="$(read_state_field "$pkg" "state")" || continue
    case "$state" in
      blocked|stale|invalid)
        echo "WARNING: package $pkg is $state"
        has_blockers=1
        ;;
    esac
  done

  # Count functional completed
  local all_func_done=1
  local func_total=0
  local func_completed=0
  for pkg in $(all_functional_packages); do
    func_total=$((func_total + 1))
    local state
    state="$(read_state_field "$pkg" "state")" || state="pending"
    if [ "$state" = "completed" ]; then
      func_completed=$((func_completed + 1))
    else
      all_func_done=0
    fi
  done

  echo "Functional packages: $func_completed/$func_total completed"

  # Launch ready packages
  local launched=0
  local running
  running="$(count_launched_not_done)"

  for pkg in $(all_functional_packages); do
    if [ "$running" -ge "$ORCHESTRATION_MAX_PARALLEL" ]; then
      break
    fi
    if is_ready "$pkg"; then
      launch_package "$pkg" || true
      launched=$((launched + 1))
      running=$((running + 1))
    fi
  done

  # Check if finalize should run
  if [ "$all_func_done" -eq 1 ]; then
    local finalize_state
    finalize_state="$(read_state_field "99-finalize" "state")" || finalize_state="pending"
    if [ "$finalize_state" = "pending" ]; then
      echo "All functional packages completed. Launching 99-finalize."
      launch_package "99-finalize" || true
      launched=$((launched + 1))
    elif [ "$finalize_state" = "blocked" ]; then
      echo "99-finalize is blocked. Run 'retry 99-finalize' after fixing the issue."
    else
      echo "99-finalize state: $finalize_state"
    fi
  fi

  if [ "$launched" -eq 0 ]; then
    echo "No new packages launched."
  fi

  echo
  echo "View agents: claude agents --cwd \"$REPO_ROOT\""
  echo "Check status: bash \"$PLAN_DIR/launchers/orchestrate.sh\" status"
  release_lock
}

cmd_status() {
  acquire_lock
  printf "%-45s %-12s %-20s %-20s %-30s %s\n" "PACKAGE" "STATE" "LAUNCHED" "COMPLETED" "BRANCH" "LAST_ERROR"
  printf "%-45s %-12s %-20s %-20s %-30s %s\n" "---" "---" "---" "---" "---" "---"
  for pkg in $(all_packages); do
    local state launched completed branch error
    state="$(read_state_field "$pkg" "state")" || state="?"
    launched="$(read_state_field "$pkg" "launched_at")" || launched="-"
    completed="$(read_state_field "$pkg" "completed_at")" || completed="-"
    branch="$(read_state_field "$pkg" "branch")" || branch="-"
    error="$(read_state_field "$pkg" "last_error")" || error="-"
    [ -z "$launched" ] && launched="-"
    [ -z "$completed" ] && completed="-"
    [ -z "$branch" ] && branch="-"
    [ -z "$error" ] && error="-"
    printf "%-45s %-12s %-20s %-20s %-30s %s\n" "$pkg" "$state" "$launched" "$completed" "$branch" "$error"
  done
  release_lock
}

cmd_retry() {
  local pkg="${1:-}"
  if [ -z "$pkg" ]; then
    echo "ERROR: usage: orchestrate.sh retry <package-id>" >&2
    echo "Valid targets: $(all_packages | tr '\n' ' ')" >&2
    exit 1
  fi

  # Validate package exists in graph
  local found=0
  for p in $(all_packages); do
    if [ "$p" = "$pkg" ]; then found=1; break; fi
  done
  if [ "$found" -eq 0 ]; then
    echo "ERROR: unknown package: $pkg" >&2
    echo "Valid targets: $(all_packages | tr '\n' ' ')" >&2
    exit 1
  fi

  acquire_lock

  local state
  state="$(read_state_field "$pkg" "state")" || state="pending"

  case "$state" in
    blocked|stale|invalid)
      echo "Resetting $pkg from $state to pending"
      local prev_error
      prev_error="$(read_state_field "$pkg" "last_error")" || prev_error=""
      update_state_field "$pkg" "state" "pending"
      update_state_field "$pkg" "last_error" ""
      if [ -n "$prev_error" ]; then
        echo "Previous error: $prev_error"
      fi
      # Now try to advance
      release_lock
      cmd_advance
      ;;
    *)
      echo "Package $pkg is in state '$state'. Only blocked/stale/invalid can be retried." >&2
      release_lock
      exit 1
      ;;
  esac
}

cmd_finalize() {
  acquire_lock

  local finalize_state
  finalize_state="$(read_state_field "99-finalize" "state")" || finalize_state="pending"

  case "$finalize_state" in
    finalized)
      echo "99-finalize already finalized. Idempotent re-run: no action."
      release_lock
      return 0
      ;;
    blocked)
      echo "99-finalize is blocked. Resetting to pending for re-run."
      update_state_field "99-finalize" "state" "pending"
      update_state_field "99-finalize" "last_error" ""
      ;;
  esac

  # Check all functional packages completed
  local all_done=1
  for pkg in $(all_functional_packages); do
    local state
    state="$(read_state_field "$pkg" "state")" || state="pending"
    if [ "$state" != "completed" ]; then
      echo "ERROR: $pkg is not completed (state: $state). Cannot finalize." >&2
      all_done=0
    fi
  done

  if [ "$all_done" -eq 0 ]; then
    release_lock
    exit 1
  fi

  echo "Launching 99-finalize"
  launch_package "99-finalize" || true
  echo
  echo "View agents: claude agents --cwd \"$REPO_ROOT\""
  release_lock
}

# --- Main ---

CMD="${1:-}"
shift 2>/dev/null || true

case "$CMD" in
  start)    cmd_start "$@" ;;
  advance)  cmd_advance "$@" ;;
  status)   cmd_status "$@" ;;
  retry)    cmd_retry "$@" ;;
  finalize) cmd_finalize "$@" ;;
  *)
    echo "Usage: orchestrate.sh {start|advance|status|retry|finalize}"
    echo
    echo "  start             Launch first ready wave of packages"
    echo "  advance [--from]  Check dependencies and launch next wave or finalize"
    echo "  status            Print package state table"
    echo "  retry <pkg>       Reset blocked/stale/invalid package and re-advance"
    echo "  finalize          Run or re-run 99-finalize"
    exit 1
    ;;
esac
