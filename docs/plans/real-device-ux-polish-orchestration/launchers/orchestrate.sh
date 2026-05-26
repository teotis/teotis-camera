#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(git -C "$PLAN_DIR" rev-parse --show-toplevel)"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK_DIR="$PLAN_DIR/status/.orchestrate.lock"

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
ORCHESTRATION_MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"

timestamp() {
  date "+%Y-%m-%dT%H:%M:%S%z"
}

usage() {
  cat <<EOF
Usage:
  bash launchers/orchestrate.sh start
  bash launchers/orchestrate.sh advance [--from <package-id>]
  bash launchers/orchestrate.sh status
  bash launchers/orchestrate.sh retry <package-id>
  bash launchers/orchestrate.sh finalize
EOF
}

with_lock() {
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "ERROR: orchestration lock is held at $LOCK_DIR" >&2
    exit 1
  fi
  trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT
}

require_files() {
  [ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: missing INDEX.md" >&2; exit 1; }
  [ -f "$GRAPH" ] || { echo "ERROR: missing package graph: $GRAPH" >&2; exit 1; }
  [ -f "$STATE" ] || { echo "ERROR: missing state ledger: $STATE" >&2; exit 1; }
  [ -f "$PLAN_DIR/launchers/agent-prompts.md" ] || { echo "ERROR: missing agent prompts" >&2; exit 1; }
}

graph_field() {
  local package_id="$1"
  local column="$2"
  awk -F '\t' -v id="$package_id" -v col="$column" '
    NR == 1 {
      for (i = 1; i <= NF; i++) idx[$i] = i
      next
    }
    $1 == id {
      print $idx[col]
      found = 1
      exit
    }
    END { if (!found) exit 1 }
  ' "$GRAPH"
}

state_field() {
  local package_id="$1"
  local column="$2"
  awk -F '\t' -v id="$package_id" -v col="$column" '
    NR == 1 {
      for (i = 1; i <= NF; i++) idx[$i] = i
      next
    }
    $1 == id {
      print $idx[col]
      found = 1
      exit
    }
    END { if (!found) exit 1 }
  ' "$STATE"
}

set_state_fields() {
  local package_id="$1"
  local new_state="$2"
  local launched_at="${3:-__KEEP__}"
  local completed_at="${4:-__KEEP__}"
  local verification="${5:-__KEEP__}"
  local integration="${6:-__KEEP__}"
  local cleanup="${7:-__KEEP__}"
  local last_error="${8:-__KEEP__}"
  local tmp
  tmp="$(mktemp)"
  awk -F '\t' -v OFS='\t' \
    -v id="$package_id" \
    -v new_state="$new_state" \
    -v launched="$launched_at" \
    -v completed="$completed_at" \
    -v verification="$verification" \
    -v integration="$integration" \
    -v cleanup="$cleanup" \
    -v last_error="$last_error" '
    NR == 1 {
      for (i = 1; i <= NF; i++) idx[$i] = i
      print
      next
    }
    $1 == id {
      $idx["state"] = new_state
      if (launched != "__KEEP__") $idx["launched_at"] = launched
      if (completed != "__KEEP__") $idx["completed_at"] = completed
      if (verification != "__KEEP__") $idx["verification"] = verification
      if (integration != "__KEEP__") $idx["integration"] = integration
      if (cleanup != "__KEEP__") $idx["cleanup"] = cleanup
      if (last_error != "__KEEP__") $idx["last_error"] = last_error
      touched = 1
    }
    { print }
    END { if (!touched) exit 1 }
  ' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

all_package_ids() {
  awk -F '\t' 'NR > 1 { print $1 }' "$GRAPH"
}

functional_package_ids() {
  awk -F '\t' 'NR > 1 && $10 != "1" { print $1 }' "$GRAPH"
}

finalize_package_id() {
  awk -F '\t' 'NR > 1 && $10 == "1" { print $1 }' "$GRAPH"
}

normalize_status() {
  local raw="$1"
  raw="$(printf "%s" "$raw" | tr "[:upper:]" "[:lower:]" | xargs)"
  case "$raw" in
    done) echo "completed" ;;
    complete) echo "completed" ;;
    *) echo "$raw" ;;
  esac
}

markdown_status() {
  local package_id="$1"
  local status_file
  status_file="$PLAN_DIR/$(graph_field "$package_id" status_file)"
  if [ ! -f "$status_file" ]; then
    echo "missing"
    return
  fi
  local line value
  line="$(grep -E -m 1 '(\*\*Status\*\*:|## Status:)' "$status_file" || true)"
  if [ -z "$line" ]; then
    echo "unknown"
    return
  fi
  value="$(printf "%s" "$line" | sed -E 's/.*Status\*\*:[[:space:]]*//; s/## Status:[[:space:]]*//')"
  normalize_status "$value"
}

preflight_graph() {
  require_files
  local final_count
  final_count="$(awk -F '\t' 'NR > 1 && $10 == "1" { n++ } END { print n + 0 }' "$GRAPH")"
  [ "$final_count" = "1" ] || { echo "ERROR: graph must contain exactly one finalize row" >&2; exit 1; }

  local ids duplicates
  ids="$(all_package_ids)"
  duplicates="$(printf "%s\n" "$ids" | sort | uniq -d)"
  [ -z "$duplicates" ] || { echo "ERROR: duplicate package IDs: $duplicates" >&2; exit 1; }

  local id doc status_file deps dep
  while IFS= read -r id; do
    doc="$PLAN_DIR/$(graph_field "$id" package_doc)"
    status_file="$PLAN_DIR/$(graph_field "$id" status_file)"
    [ -f "$doc" ] || { echo "ERROR: missing package doc for $id: $doc" >&2; exit 1; }
    [ -f "$status_file" ] || { echo "ERROR: missing status file for $id: $status_file" >&2; exit 1; }
    state_field "$id" state >/dev/null || { echo "ERROR: state row missing for $id" >&2; exit 1; }
    deps="$(graph_field "$id" dependencies || true)"
    if [ -n "$deps" ]; then
      IFS=',' read -r -a dep_array <<< "$deps"
      for dep in "${dep_array[@]}"; do
        [ -z "$dep" ] && continue
        graph_field "$dep" package_doc >/dev/null || {
          echo "ERROR: $id depends on missing package $dep" >&2
          exit 1
        }
      done
    fi
  done <<< "$ids"
}

status_consistency_ok() {
  local id state md
  while IFS= read -r id; do
    state="$(state_field "$id" state)"
    md="$(markdown_status "$id")"
    case "$md" in
      unknown|missing)
        echo "INVALID: $id markdown status is $md"
        return 1
        ;;
    esac
    if [ "$state" = "done" ]; then
      echo "INVALID: $id state uses non-contract value 'done'"
      return 1
    fi
    if [ "$md" = "completed" ] && [ "$state" != "completed" ] && [ "$state" != "finalized" ]; then
      echo "INVALID: $id markdown completed but state is $state"
      return 1
    fi
    if [ "$state" = "completed" ] && [ "$md" != "completed" ]; then
      echo "INVALID: $id state completed but markdown is $md"
      return 1
    fi
  done <<< "$(all_package_ids)"
  return 0
}

deps_completed() {
  local package_id="$1"
  local deps dep dep_state
  deps="$(graph_field "$package_id" dependencies || true)"
  [ -z "$deps" ] && return 0
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    [ -z "$dep" ] && continue
    dep_state="$(state_field "$dep" state)"
    [ "$dep_state" = "completed" ] || return 1
  done
  return 0
}

any_bad_terminal_state() {
  awk -F '\t' 'NR > 1 && ($2 == "blocked" || $2 == "stale" || $2 == "invalid") { print $1 ":" $2 }' "$STATE"
}

all_functional_completed() {
  local id state
  while IFS= read -r id; do
    state="$(state_field "$id" state)"
    [ "$state" = "completed" ] || return 1
  done <<< "$(functional_package_ids)"
  return 0
}

ready_packages() {
  local id state
  while IFS= read -r id; do
    state="$(state_field "$id" state)"
    if { [ "$state" = "pending" ] || [ "$state" = "ready" ]; } && deps_completed "$id"; then
      echo "$id"
    fi
  done <<< "$(functional_package_ids)"
}

launch_package() {
  local package_id="$1"
  local package_doc status_file branch worktree name prompt output status now new_state
  package_doc="$PLAN_DIR/$(graph_field "$package_id" package_doc)"
  status_file="$PLAN_DIR/$(graph_field "$package_id" status_file)"
  branch="$(graph_field "$package_id" branch)"
  worktree="$(graph_field "$package_id" worktree)"
  name="ux-polish-${package_id}"
  now="$(timestamp)"

  if [ "$(graph_field "$package_id" finalize)" = "1" ]; then
    new_state="finalizing"
  else
    new_state="launched"
  fi

  prompt="You are executing Real Device UX Polish package $package_id for OpenCamera.

Repository: $REPO_ROOT
INDEX: $PLAN_DIR/INDEX.md
Package doc: $package_doc
Coordinator status: $status_file
Coordinator state: $STATE
Orchestrator: $PLAN_DIR/launchers/orchestrate.sh
Branch: $branch
Worktree: $worktree

Read AGENTS.md, INDEX.md, and your package doc before editing. Create or reuse only the assigned worktree/branch. Use rtk for shell commands. Edit only allowed paths. Do not edit INDEX.md or another package status file. Do not force-push, hard reset, delete unrecorded branches/worktrees, use network, add secrets, or expand scope.

Before finishing, set coordinator status and state consistently. Tail call:
bash $PLAN_DIR/launchers/orchestrate.sh advance --from $package_id"

  echo "Launching $name"
  set +e
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    output="$(claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --permission-mode "$CLAUDE_PERMISSION_MODE" --setting-sources "$CLAUDE_SETTING_SOURCES" "$prompt" 2>&1)"
  else
    output="$(claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --setting-sources "$CLAUDE_SETTING_SOURCES" "$prompt" 2>&1)"
  fi
  status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    echo "$output" >&2
    set_state_fields "$package_id" "blocked" "$now" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "launch failed"
    if [ "$CLAUDE_PERMISSION_MODE" = "auto" ] && grep -qi "requires opting in" <<< "$output"; then
      echo "ERROR: run once interactively first: claude --permission-mode auto" >&2
    fi
    return "$status"
  fi
  set_state_fields "$package_id" "$new_state" "$now" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" ""
  echo "$output"
}

print_agents_command() {
  echo
  echo "View background sessions with:"
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
  else
    echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
  fi
}

do_status() {
  preflight_graph
  printf "%-38s %-12s %-42s %-70s %-12s %-12s %-s\n" "PACKAGE" "STATE" "BRANCH" "WORKTREE" "VERIFY" "INTEGRATION" "LAST_ERROR"
  awk -F '\t' 'NR > 1 { printf "%-38s %-12s %-42s %-70s %-12s %-12s %-s\n", $1, $2, $6, $7, $10, $11, $13 }' "$STATE"
  echo
  status_consistency_ok >/dev/null && echo "Coordinator consistency: ok" || {
    echo "Coordinator consistency: invalid"
    status_consistency_ok || true
  }
}

launch_ready_or_finalize() {
  local bad ready count id final_id final_state
  preflight_graph
  bad="$(any_bad_terminal_state)"
  if [ -n "$bad" ]; then
    echo "STOP: blocked/stale/invalid package present:"
    echo "$bad"
    exit 1
  fi
  status_consistency_ok

  if all_functional_completed; then
    final_id="$(finalize_package_id)"
    final_state="$(state_field "$final_id" state)"
    case "$final_state" in
      pending|ready)
        launch_package "$final_id"
        print_agents_command
        return
        ;;
      finalizing|launched|in_progress)
        echo "$final_id is already running: $final_state"
        print_agents_command
        return
        ;;
      finalized)
        echo "$final_id is already finalized"
        return
        ;;
      *)
        echo "STOP: $final_id state is $final_state"
        exit 1
        ;;
    esac
  fi

  count=0
  ready="$(ready_packages || true)"
  if [ -z "$ready" ]; then
    echo "No ready packages to launch."
    return
  fi
  while IFS= read -r id; do
    [ -z "$id" ] && continue
    if [ "$count" -ge "$ORCHESTRATION_MAX_PARALLEL" ]; then
      break
    fi
    launch_package "$id"
    count=$((count + 1))
  done <<< "$ready"
  print_agents_command
}

do_retry() {
  local package_id="$1"
  preflight_graph
  graph_field "$package_id" package_doc >/dev/null || { echo "ERROR: unknown package $package_id" >&2; exit 1; }
  local state
  state="$(state_field "$package_id" state)"
  case "$state" in
    blocked|stale|invalid)
      echo "Retrying $package_id; prior state was $state"
      set_state_fields "$package_id" "pending" "" "" "pending" "pending" "pending" ""
      ;;
    *)
      echo "ERROR: retry only supports blocked, stale, or invalid packages; $package_id is $state" >&2
      exit 1
      ;;
  esac
}

cmd="${1:-}"
shift || true

case "$cmd" in
  start)
    with_lock
    launch_ready_or_finalize
    ;;
  advance)
    from=""
    if [ "${1:-}" = "--from" ]; then
      from="${2:-}"
    fi
    with_lock
    if [ -n "$from" ]; then
      echo "Advance requested from: $from"
    fi
    launch_ready_or_finalize
    ;;
  status)
    do_status
    ;;
  retry)
    [ -n "${1:-}" ] || { usage; exit 1; }
    with_lock
    do_retry "$1"
    ;;
  finalize)
    with_lock
    preflight_graph
    if ! all_functional_completed; then
      echo "ERROR: cannot finalize until all functional packages are completed" >&2
      exit 1
    fi
    launch_package "$(finalize_package_id)"
    print_agents_command
    ;;
  *)
    usage
    exit 1
    ;;
esac
