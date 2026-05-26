#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel)"
PLAN_DIR="$REPO_ROOT/docs/plans/scene-mask-honesty-repair-orchestration"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK_DIR="$PLAN_DIR/status/.orchestrate.lock"
MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"

usage() {
  cat <<USAGE
Usage:
  bash "$0" start
  bash "$0" advance [--from <package-id>]
  bash "$0" status
  bash "$0" retry <package-id>
  bash "$0" finalize
USAGE
}

with_lock() {
  local command_name="$1"
  shift
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "ERROR: orchestration lock exists at $LOCK_DIR" >&2
    exit 1
  fi
  trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT
  "$@"
  rmdir "$LOCK_DIR" 2>/dev/null || true
  trap - EXIT
}

preflight() {
  cd "$REPO_ROOT"
  command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
  [ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: missing INDEX.md"; exit 1; }
  [ -f "$GRAPH" ] || { echo "ERROR: missing package graph"; exit 1; }
  [ -f "$STATE" ] || { echo "ERROR: missing state.tsv"; exit 1; }

  awk -F '\t' '
    FNR == 1 { next }
    NF < 10 { print "ERROR: malformed graph row: " $0; exit 1 }
    seen[$1]++ { print "ERROR: duplicate package id: " $1; exit 1 }
    { ids[$1]=1; deps[$1]=$4; finalize += ($10 == "1") }
    END {
      if (finalize != 1) { print "ERROR: graph must contain exactly one finalize row"; exit 1 }
      for (pkg in deps) {
        if (deps[pkg] == "") continue
        n = split(deps[pkg], d, ",")
        for (i = 1; i <= n; i++) {
          if (!(d[i] in ids)) { print "ERROR: " pkg " depends on missing " d[i]; exit 1 }
        }
      }
    }
  ' "$GRAPH"
}

state_of() {
  local pkg="$1"
  awk -F '\t' -v pkg="$pkg" 'FNR == 1 { next } $1 == pkg { print $2; found=1 } END { if (!found) exit 1 }' "$STATE"
}

field_of_graph() {
  local pkg="$1"
  local index="$2"
  awk -F '\t' -v pkg="$pkg" -v idx="$index" 'FNR == 1 { next } $1 == pkg { print $idx; found=1 } END { if (!found) exit 1 }' "$GRAPH"
}

update_state_field() {
  local pkg="$1"
  local field="$2"
  local value="$3"
  local tmp
  tmp="$(mktemp)"
  awk -F '\t' -v OFS='\t' -v pkg="$pkg" -v field="$field" -v value="$value" '
    FNR == 1 {
      for (i = 1; i <= NF; i++) if ($i == field) idx = i
      print
      next
    }
    $1 == pkg && idx { $idx = value }
    { print }
  ' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

set_state() {
  local pkg="$1"
  local new_state="$2"
  update_state_field "$pkg" "state" "$new_state"
}

dependencies_completed() {
  local deps="$1"
  [ -z "$deps" ] && return 0
  local dep dep_state
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    dep_state="$(state_of "$dep" || true)"
    [ "$dep_state" = "completed" ] || [ "$dep_state" = "finalized" ] || return 1
  done
  return 0
}

stop_on_bad_states() {
  local bad
  bad="$(awk -F '\t' 'FNR > 1 && ($2 == "blocked" || $2 == "stale" || $2 == "invalid") { print $1 ":" $2 ":" $13 }' "$STATE")"
  if [ -n "$bad" ]; then
    echo "ERROR: stopped because a package is blocked/stale/invalid:" >&2
    echo "$bad" >&2
    exit 1
  fi
}

running_count() {
  awk -F '\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress" || $2 == "finalizing") { c++ } END { print c + 0 }' "$STATE"
}

functional_completed() {
  awk -F '\t' '
    FNR == NR {
      if (FNR > 1 && $10 != "1") functional[$1]=1
      next
    }
    FNR == 1 { next }
    $1 in functional && $2 != "completed" { missing++ }
    END { exit (missing == 0 ? 0 : 1) }
  ' "$GRAPH" "$STATE"
}

ready_packages() {
  awk -F '\t' '
    FNR == NR {
      if (FNR > 1 && $10 != "1") {
        deps[$1]=$4
        wave[$1]=$6
        order[++n]=$1
      }
      next
    }
    FNR == 1 { next }
    { state[$1]=$2 }
    END {
      for (i = 1; i <= n; i++) {
        pkg=order[i]
        if (state[pkg] != "pending" && state[pkg] != "ready") continue
        ok=1
        if (deps[pkg] != "") {
          split(deps[pkg], d, ",")
          for (j in d) if (state[d[j]] != "completed") ok=0
        }
        if (ok) print pkg
      }
    }
  ' "$GRAPH" "$STATE"
}

launch_package() {
  local pkg="$1"
  local package_doc status_file branch worktree name prompt output status_arg
  package_doc="$PLAN_DIR/$(field_of_graph "$pkg" 2)"
  status_file="$PLAN_DIR/$(field_of_graph "$pkg" 3)"
  branch="$(field_of_graph "$pkg" 7)"
  worktree="$(field_of_graph "$pkg" 8)"
  name="agent-${pkg}"
  prompt="Read $PLAN_DIR/INDEX.md and $package_doc. Implement package $pkg in branch $branch using worktree $worktree. Write coordinator evidence to $status_file and update $STATE. Do NOT edit INDEX.md or other package status files. When done, run: bash $PLAN_DIR/launchers/orchestrate.sh advance --from $pkg"

  echo "Launching $name"
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    output="$(claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --permission-mode "$CLAUDE_PERMISSION_MODE" --setting-sources "$CLAUDE_SETTING_SOURCES" "$prompt" 2>&1)" || {
      status_arg=$?
      echo "$output" >&2
      update_state_field "$pkg" "last_error" "launch_failed"
      set_state "$pkg" "blocked"
      return "$status_arg"
    }
  else
    output="$(claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --setting-sources "$CLAUDE_SETTING_SOURCES" "$prompt" 2>&1)" || {
      status_arg=$?
      echo "$output" >&2
      update_state_field "$pkg" "last_error" "launch_failed"
      set_state "$pkg" "blocked"
      return "$status_arg"
    }
  fi
  echo "$output"
  set_state "$pkg" "launched"
  update_state_field "$pkg" "launched_at" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  update_state_field "$pkg" "agent" "$name"
  update_state_field "$pkg" "branch" "$branch"
  update_state_field "$pkg" "worktree" "$worktree"
}

launch_ready() {
  local launched=0
  local running available pkg
  running="$(running_count)"
  available=$((MAX_PARALLEL - running))
  [ "$available" -gt 0 ] || { echo "No launch capacity. running=$running max=$MAX_PARALLEL"; return 0; }

  while IFS= read -r pkg; do
    [ -n "$pkg" ] || continue
    [ "$launched" -lt "$available" ] || break
    launch_package "$pkg"
    launched=$((launched + 1))
  done < <(ready_packages)

  if [ "$launched" -gt 0 ]; then
    echo
    echo "View agents with:"
    echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
  else
    echo "No newly ready functional packages."
  fi
}

launch_finalize() {
  local pkg="99-finalize"
  local current
  current="$(state_of "$pkg")"
  if [ "$current" = "finalized" ]; then
    echo "99-finalize already finalized."
    return 0
  fi
  if [ "$current" = "launched" ] || [ "$current" = "finalizing" ]; then
    echo "99-finalize already launched."
    return 0
  fi
  set_state "$pkg" "finalizing"
  launch_package "$pkg"
}

cmd_start_or_advance() {
  preflight
  stop_on_bad_states
  if functional_completed; then
    launch_finalize
  else
    launch_ready
  fi
}

cmd_status() {
  preflight
  printf "%-42s %-12s %-44s %-64s %-14s %-14s %s\n" "PACKAGE" "STATE" "BRANCH" "WORKTREE" "VERIFY" "INTEGRATION" "LAST_ERROR"
  awk -F '\t' 'FNR > 1 { printf "%-42s %-12s %-44s %-64s %-14s %-14s %s\n", $1, $2, $6, $7, $10, $11, $13 }' "$STATE"
}

cmd_retry() {
  local pkg="${1:-}"
  [ -n "$pkg" ] || { echo "ERROR: retry requires package id"; exit 1; }
  preflight
  local current
  current="$(state_of "$pkg")" || { echo "ERROR: unknown package $pkg"; exit 1; }
  case "$current" in
    blocked|stale|invalid)
      echo "Resetting $pkg from $current to pending. Previous error:"
      awk -F '\t' -v pkg="$pkg" 'FNR > 1 && $1 == pkg { print $13 }' "$STATE"
      set_state "$pkg" "pending"
      update_state_field "$pkg" "last_error" "-"
      ;;
    *)
      echo "ERROR: retry only resets blocked, stale, or invalid packages. $pkg is $current" >&2
      exit 1
      ;;
  esac
}

main() {
  local cmd="${1:-}"
  shift || true
  case "$cmd" in
    start)
      with_lock "$cmd" cmd_start_or_advance
      ;;
    advance)
      if [ "${1:-}" = "--from" ]; then
        echo "Advance requested from ${2:-unknown}; readiness is recomputed from state."
      fi
      with_lock "$cmd" cmd_start_or_advance
      ;;
    status)
      cmd_status
      ;;
    retry)
      with_lock "$cmd" cmd_retry "${1:-}"
      ;;
    finalize)
      with_lock "$cmd" launch_finalize
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"

