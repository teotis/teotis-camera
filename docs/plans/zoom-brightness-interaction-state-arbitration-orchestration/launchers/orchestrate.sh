#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAN_DIR" && git rev-parse --show-toplevel)"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATUS_DIR="$PLAN_DIR/status"
LOCK_FILE="$STATUS_DIR/.orchestrate.lock"
ORCHESTRATION_MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$GRAPH" ] || { echo "ERROR: package-graph.tsv not found at $GRAPH"; exit 1; }

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"

# --- Lock helpers ---
acquire_lock() {
  local attempts=0
  while [ -f "$LOCK_FILE" ]; do
    local lock_age=$(( $(date +%s) - $(stat -f %m "$LOCK_FILE" 2>/dev/null || echo 0) ))
    if [ "$lock_age" -gt 300 ]; then
      echo "WARN: stale lock (${lock_age}s old), removing"
      rm -f "$LOCK_FILE"
      break
    fi
    attempts=$((attempts + 1))
    if [ "$attempts" -gt 30 ]; then
      echo "ERROR: could not acquire lock after 30 attempts"
      exit 1
    fi
    sleep 1
  done
  echo "$$" > "$LOCK_FILE"
}

release_lock() {
  rm -f "$LOCK_FILE"
}

trap release_lock EXIT

# --- State helpers ---
read_state() {
  local pkg="$1"
  awk -F'\t' -v p="$pkg" '$1 == p { print $2 }' "$STATUS_DIR/state.tsv"
}

write_state_field() {
  local pkg="$1"
  local col="$2"
  local val="$3"
  local tmp="$STATUS_DIR/state.tsv.tmp"
  awk -F'\t' -v p="$pkg" -v c="$col" -v v="$val" '
    BEGIN { OFS="\t" }
    FNR == 1 { for (i=1; i<=NF; i++) if ($i == c) ci=i; print; next }
    $1 == p { if (ci > 0) $ci = v; print; next }
    { print }
  ' col="$col" "$GRAPH" "$STATUS_DIR/state.tsv" > "$tmp"
  # The above uses $GRAPH only for header; re-do with just state.tsv
  awk -F'\t' -v p="$pkg" -v c="$col" -v v="$val" '
    BEGIN { OFS="\t"; ci=0 }
    NR == 1 { for (i=1; i<=NF; i++) if ($i == c) ci=i; print; next }
    $1 == p { if (ci > 0) $ci = v; print; next }
    { print }
  ' "$STATUS_DIR/state.tsv" > "$tmp"
  mv "$tmp" "$STATUS_DIR/state.tsv"
}

# --- Graph helpers ---
get_graph_value() {
  local pkg="$1"
  local col="$2"
  awk -F'\t' -v p="$pkg" -v c="$col" '
    FNR == 1 { for (i=1; i<=NF; i++) h[i]=$i; next }
    $1 == p { for (i=1; i<=NF; i++) if (h[i]==c) { print $i; exit } }
  ' "$GRAPH" "$GRAPH"
}

get_all_functional_packages() {
  awk -F'\t' 'FNR == 1 { next } $NF != "1" { print $1 }' "$GRAPH" "$GRAPH"
}

get_all_packages() {
  awk -F'\t' 'FNR == 1 { next } { print $1 }' "$GRAPH" "$GRAPH"
}

get_dependencies() {
  local pkg="$1"
  get_graph_value "$pkg" "dependencies"
}

get_finalize_package() {
  awk -F'\t' 'FNR == 1 { next } $NF == "1" { print $1; exit }' "$GRAPH" "$GRAPH"
}

# --- Readiness check ---
is_ready() {
  local pkg="$1"
  local state
  state=$(read_state "$pkg")
  [ "$state" = "pending" ] || return 1
  local deps
  deps=$(get_dependencies "$pkg")
  [ -z "$deps" ] && return 0
  IFS=',' read -ra dep_list <<< "$deps"
  for dep in "${dep_list[@]}"; do
    local dep_state
    dep_state=$(read_state "$dep")
    [ "$dep_state" = "completed" ] || return 1
  done
  return 0
}

all_functional_completed() {
  local pkg
  for pkg in $(get_all_functional_packages); do
    local state
    state=$(read_state "$pkg")
    [ "$state" = "completed" ] || return 1
  done
  return 0
}

count_active() {
  local count=0
  local pkg
  for pkg in $(get_all_packages); do
    local state
    state=$(read_state "$pkg")
    case "$state" in
      launched|in_progress|finalizing) count=$((count + 1)) ;;
    esac
  done
  echo "$count"
}

# --- Launch helper ---
launch_package() {
  local pkg="$1"
  local pkg_doc
  pkg_doc=$(get_graph_value "$pkg" "package_doc")
  local status_file
  status_file=$(get_graph_value "$pkg" "status_file")
  local branch
  branch=$(get_graph_value "$pkg" "branch")
  local name="agent-${pkg}"

  local prompt="Read $PLAN_DIR/INDEX.md and $PLAN_DIR/$pkg_doc. Execute package $pkg. Write evidence to $PLAN_DIR/$status_file when done. Do NOT edit INDEX.md or other status files.

Before calling advance, you must:
- Set coordinator status to completed or blocked.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row in $PLAN_DIR/status/state.tsv consistently.

Tail step:
\`\`\`bash
bash $PLAN_DIR/launchers/orchestrate.sh advance --from $pkg
\`\`\`"

  echo "Launching $name"
  set +e
  local output
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
  local status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    echo "$output" >&2
    write_state_field "$pkg" "last_error" "launch failed: $(echo "$output" | tail -1)"
    return "$status"
  fi
  echo "$output"
  local now
  now=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  write_state_field "$pkg" "state" "launched"
  write_state_field "$pkg" "launched_at" "$now"
  write_state_field "$pkg" "branch" "$branch"
}

# --- Subcommands ---
cmd_start() {
  acquire_lock
  echo "=== Orchestration Start ==="
  echo "Plan: $PLAN_DIR"
  echo "Graph: $GRAPH"
  echo

  # Preflight: validate graph
  local pkg
  for pkg in $(get_all_packages); do
    local deps
    deps=$(get_dependencies "$pkg")
    if [ -n "$deps" ]; then
      IFS=',' read -ra dep_list <<< "$deps"
      for dep in "${dep_list[@]}"; do
        local found=0
        for p in $(get_all_packages); do
          [ "$p" = "$dep" ] && found=1
        done
        [ "$found" = 1 ] || { echo "ERROR: $pkg depends on $dep which is not in graph"; release_lock; exit 1; }
      done
    fi
  done

  local active
  active=$(count_active)
  local launched=0

  for pkg in $(get_all_functional_packages); do
    if [ "$active" -ge "$ORCHESTRATION_MAX_PARALLEL" ]; then
      echo "Max parallel ($ORCHESTRATION_MAX_PARALLEL) reached, stopping"
      break
    fi
    if is_ready "$pkg"; then
      launch_package "$pkg"
      launched=$((launched + 1))
      active=$((active + 1))
    fi
  done

  if [ "$launched" -eq 0 ]; then
    echo "No packages ready to launch."
  fi

  release_lock
  echo
  echo "View agents with: claude agents --cwd \"$REPO_ROOT\""
}

cmd_advance() {
  local from_pkg=""
  while [ $# -gt 0 ]; do
    case "$1" in
      --from) from_pkg="$2"; shift 2 ;;
      *) shift ;;
    esac
  done

  acquire_lock
  echo "=== Orchestration Advance ==="
  [ -n "$from_pkg" ] && echo "Triggered by: $from_pkg"
  echo

  # Check for blocked/stale/invalid
  local pkg
  for pkg in $(get_all_packages); do
    local state
    state=$(read_state "$pkg")
    case "$state" in
      blocked|stale|invalid)
        echo "STOP: $pkg is $state. Resolve before advancing."
        release_lock
        exit 1
        ;;
    esac
  done

  # Check if finalize should run
  if all_functional_completed; then
    local finalize_pkg
    finalize_pkg=$(get_finalize_package)
    local fin_state
    fin_state=$(read_state "$finalize_pkg")
    if [ "$fin_state" = "pending" ]; then
      echo "All functional packages completed. Launching $finalize_pkg"
      launch_package "$finalize_pkg"
      release_lock
      echo
      echo "View agents with: claude agents --cwd \"$REPO_ROOT\""
      return 0
    fi
  fi

  # Launch newly ready functional packages
  local active
  active=$(count_active)
  local launched=0

  for pkg in $(get_all_functional_packages); do
    if [ "$active" -ge "$ORCHESTRATION_MAX_PARALLEL" ]; then
      echo "Max parallel ($ORCHESTRATION_MAX_PARALLEL) reached"
      break
    fi
    if is_ready "$pkg"; then
      launch_package "$pkg"
      launched=$((launched + 1))
      active=$((active + 1))
    fi
  done

  if [ "$launched" -eq 0 ]; then
    echo "No new packages ready to launch."
    # Show what's blocking
    for pkg in $(get_all_functional_packages); do
      local state
      state=$(read_state "$pkg")
      if [ "$state" = "pending" ]; then
        local deps
        deps=$(get_dependencies "$pkg")
        if [ -n "$deps" ]; then
          echo "  $pkg waiting on: $deps"
        fi
      fi
    done
  fi

  release_lock
  echo
  echo "View agents with: claude agents --cwd \"$REPO_ROOT\""
}

cmd_status() {
  acquire_lock
  echo "=== Orchestration Status ==="
  echo
  printf "%-45s %-12s %-10s %-40s %-20s\n" "PACKAGE" "STATE" "WAVE" "BRANCH" "LAST ERROR"
  printf "%-45s %-12s %-10s %-40s %-20s\n" "-------" "-----" "----" "------" "----------"
  local pkg
  for pkg in $(get_all_packages); do
    local state wave branch error
    state=$(read_state "$pkg")
    wave=$(get_graph_value "$pkg" "wave")
    branch=$(get_graph_value "$pkg" "branch")
    error=$(awk -F'\t' -v p="$pkg" '$1 == p { print $NF }' "$STATUS_DIR/state.tsv")
    printf "%-45s %-12s %-10s %-40s %-20s\n" "$pkg" "$state" "$wave" "$branch" "${error:--}"
  done
  echo
  # Consistency check
  local inconsistent=0
  for pkg in $(get_all_packages); do
    local state_md state_tsv
    state_tsv=$(read_state "$pkg")
    state_md=$(grep -i '^\- \*\*Status\*\*:' "$PLAN_DIR/status/${pkg}.md" 2>/dev/null | head -1 | sed 's/.*: *//' | tr '[:upper:]' '[:lower:]' | xargs)
    if [ -n "$state_md" ] && [ "$state_md" != "$state_tsv" ] && [ "$state_md" != "pending" ]; then
      echo "WARNING: $pkg state mismatch — markdown='$state_md' vs tsv='$state_tsv'"
      inconsistent=$((inconsistent + 1))
    fi
  done
  [ "$inconsistent" -gt 0 ] && echo "WARN: $inconsistent state mismatches detected"
  release_lock
}

cmd_retry() {
  local pkg="${1:-}"
  [ -z "$pkg" ] && { echo "Usage: orchestrate.sh retry <package-id>"; exit 1; }

  acquire_lock
  local state
  state=$(read_state "$pkg")
  case "$state" in
    blocked|stale|invalid)
      echo "Resetting $pkg from $state to pending"
      local error
      error=$(awk -F'\t' -v p="$pkg" '$1 == p { print $NF }' "$STATUS_DIR/state.tsv")
      echo "Prior error: ${error:--}"
      write_state_field "$pkg" "state" "pending"
      write_state_field "$pkg" "last_error" ""
      echo "State reset. Run 'advance' to relaunch."
      ;;
    *)
      echo "Cannot retry $pkg (current state: $state). Only blocked/stale/invalid can be retried."
      ;;
  esac
  release_lock
}

cmd_finalize() {
  acquire_lock
  local finalize_pkg
  finalize_pkg=$(get_finalize_package)
  local fin_state
  fin_state=$(read_state "$finalize_pkg")

  if [ "$fin_state" = "finalized" ]; then
    echo "Already finalized. Idempotent."
    release_lock
    return 0
  fi

  if ! all_functional_completed; then
    echo "Cannot finalize: not all functional packages are completed."
    for pkg in $(get_all_functional_packages); do
      local state
      state=$(read_state "$pkg")
      [ "$state" != "completed" ] && echo "  $pkg: $state"
    done
    release_lock
    exit 1
  fi

  echo "Launching $finalize_pkg"
  launch_package "$finalize_pkg"
  release_lock
  echo
  echo "View agents with: claude agents --cwd \"$REPO_ROOT\""
}

# --- Main ---
CMD="${1:-}"
shift 2>/dev/null || true

case "$CMD" in
  start)    cmd_start ;;
  advance)  cmd_advance "$@" ;;
  status)   cmd_status ;;
  retry)    cmd_retry "$@" ;;
  finalize) cmd_finalize ;;
  *)
    echo "Usage: orchestrate.sh {start|advance|status|retry|finalize}"
    echo
    echo "  start    - Launch first wave of ready packages"
    echo "  advance  - Launch newly ready packages (called by package tail step)"
    echo "  status   - Print package state table"
    echo "  retry    - Reset a blocked/stale/invalid package to pending"
    echo "  finalize - Run or re-run the 99-finalize package"
    exit 2
    ;;
esac
