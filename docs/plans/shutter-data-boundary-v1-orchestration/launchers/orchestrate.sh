#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(git -C "$PLAN_DIR" rev-parse --show-toplevel)"
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
  bash launchers/orchestrate.sh start
  bash launchers/orchestrate.sh advance [--from <package-id>]
  bash launchers/orchestrate.sh status
  bash launchers/orchestrate.sh retry <package-id>
  bash launchers/orchestrate.sh finalize
USAGE
}

die() {
  echo "ERROR: $*" >&2
  exit 1
}

now_utc() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

with_lock() {
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    die "orchestrator lock is held at $LOCK_DIR"
  fi
  trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT
}

preflight() {
  [ -f "$GRAPH" ] || die "missing package graph: $GRAPH"
  [ -f "$STATE" ] || die "missing state ledger: $STATE"
  command -v awk >/dev/null 2>&1 || die "awk not found"
  command -v claude >/dev/null 2>&1 || die "claude CLI not found"
  git -C "$REPO_ROOT" rev-parse --git-dir >/dev/null 2>&1 || die "not a git repository: $REPO_ROOT"

  awk -F '\t' '
    FNR == 1 { next }
    $1 == "" { print "empty package id"; bad = 1 }
    seen[$1]++ { print "duplicate package id: " $1; bad = 1 }
    $10 == "1" { finalize++ }
    END { if (finalize != 1) { print "expected exactly one finalize row"; bad = 1 } exit bad }
  ' "$GRAPH" || die "graph validation failed"

  awk -F '\t' '
    NR == FNR { if (FNR > 1) pkg[$1] = 1; next }
    FNR == 1 { next }
    {
      n = split($4, deps, ",")
      for (i = 1; i <= n; i++) {
        dep = deps[i]
        gsub(/^ +| +$/, "", dep)
        if (dep != "" && !(dep in pkg)) {
          print "missing dependency " dep " for " $1
          bad = 1
        }
      }
    }
    END { exit bad }
  ' "$GRAPH" "$GRAPH" || die "dependency validation failed"
}

graph_field() {
  local package_id="$1"
  local field="$2"
  awk -F '\t' -v id="$package_id" -v field="$field" '
    FNR == 1 { for (i = 1; i <= NF; i++) idx[$i] = i; next }
    $1 == id { print $idx[field]; exit }
  ' "$GRAPH"
}

state_field() {
  local package_id="$1"
  local field="$2"
  awk -F '\t' -v id="$package_id" -v field="$field" '
    FNR == 1 { for (i = 1; i <= NF; i++) idx[$i] = i; next }
    $1 == id { print $idx[field]; exit }
  ' "$STATE"
}

set_state_field() {
  local package_id="$1"
  local field="$2"
  local value="$3"
  local tmp
  tmp="$(mktemp "${STATE}.XXXXXX")"
  awk -F '\t' -v OFS='\t' -v id="$package_id" -v field="$field" -v value="$value" '
    FNR == 1 { for (i = 1; i <= NF; i++) idx[$i] = i; print; next }
    $1 == id { $idx[field] = value; print; next }
    { print }
  ' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

markdown_status() {
  local status_file="$1"
  awk -F ': ' '/^- \*\*Status\*\*/ { print $2; exit }' "$status_file" | tr -d '\r'
}

functional_packages() {
  awk -F '\t' 'FNR > 1 && $10 != "1" { print $1 }' "$GRAPH"
}

all_packages() {
  awk -F '\t' 'FNR > 1 { print $1 }' "$GRAPH"
}

finalize_package() {
  awk -F '\t' 'FNR > 1 && $10 == "1" { print $1; exit }' "$GRAPH"
}

validate_status_state() {
  local package_id status_rel status_file md st
  status_rel="$(graph_field "$package_id" status_file)"
  status_file="$PLAN_DIR/$status_rel"
  [ -f "$status_file" ] || die "missing status file for $package_id: $status_file"
  md="$(markdown_status "$status_file")"
  st="$(state_field "$package_id" state)"
  case "$md" in
    completed|blocked|stale|invalid|finalizing|finalized)
      if [ "$md" != "$st" ]; then
        set_state_field "$package_id" state invalid
        set_state_field "$package_id" last_error "markdown status $md disagrees with state $st"
        die "status/state mismatch for $package_id: markdown=$md state=$st"
      fi
      ;;
  esac
}

deps_completed() {
  local package_id deps dep dep_state
  deps="$(graph_field "$package_id" dependencies)"
  [ -z "$deps" ] && return 0
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    dep="$(echo "$dep" | awk '{$1=$1; print}')"
    [ -z "$dep" ] && continue
    dep_state="$(state_field "$dep" state)"
    [ "$dep_state" = "completed" ] || [ "$dep_state" = "finalized" ] || return 1
  done
  return 0
}

all_functional_completed() {
  local pkg st
  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    st="$(state_field "$pkg" state)"
    [ "$st" = "completed" ] || return 1
  done < <(functional_packages)
  return 0
}

any_stop_state() {
  awk -F '\t' 'FNR > 1 && ($2 == "blocked" || $2 == "stale" || $2 == "invalid") { print $1 ":" $2 ":" $13 }' "$STATE"
}

running_count() {
  awk -F '\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress" || $2 == "finalizing") { c++ } END { print c + 0 }' "$STATE"
}

is_ready() {
  local package_id="$1"
  local st
  st="$(state_field "$package_id" state)"
  [ "$st" = "pending" ] || [ "$st" = "ready" ] || return 1
  deps_completed "$package_id"
}

build_prompt() {
  local package_id="$1"
  local package_doc status_file deps
  package_doc="$PLAN_DIR/$(graph_field "$package_id" package_doc)"
  status_file="$PLAN_DIR/$(graph_field "$package_id" status_file)"
  deps="$(graph_field "$package_id" dependencies)"
  [ -z "$deps" ] && deps="none"
  cat <<PROMPT
You are executing OpenCamera orchestration package $package_id.

Repository: $REPO_ROOT
INDEX: $PLAN_DIR/INDEX.md
Package doc: $package_doc
Coordinator status: $status_file
Coordinator state: $STATE
Orchestrator: $PLAN_DIR/launchers/orchestrate.sh
Dependencies: $deps

Read INDEX.md, your package doc, AGENTS.md, and /Users/dingren/.codex/RTK.md before any command. Use rtk for all shell commands. Create or reuse only your assigned worktree and branch. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

You may edit only allowed paths from the package doc. Do not edit INDEX.md or another package status file. Do not force-push, hard reset, delete remote branches, add secrets, use network, or delete branches/worktrees not recorded by this orchestration.

Before calling advance, set coordinator status to completed or blocked, fill evidence, and update your row in state.tsv consistently.

Tail step:
bash "$PLAN_DIR/launchers/orchestrate.sh" advance --from "$package_id"
PROMPT
}

launch_package() {
  local package_id="$1"
  local name prompt output status launched
  name="shutter-boundary-${package_id}"
  prompt="$(build_prompt "$package_id")"
  launched="$(now_utc)"
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
    set_state_field "$package_id" state blocked
    set_state_field "$package_id" last_error "launch failed: exit $status"
    return "$status"
  fi
  echo "$output"
  set_state_field "$package_id" state launched
  set_state_field "$package_id" launched_at "$launched"
  set_state_field "$package_id" agent "$name"
}

launch_ready() {
  local launched=0 current pkg stop
  stop="$(any_stop_state)"
  [ -z "$stop" ] || die "stop state present: $stop"
  current="$(running_count)"
  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    validate_status_state "$pkg"
    if [ "$current" -ge "$MAX_PARALLEL" ]; then
      break
    fi
    if is_ready "$pkg"; then
      launch_package "$pkg"
      current=$((current + 1))
      launched=$((launched + 1))
    fi
  done < <(functional_packages)
  echo "Launched $launched package(s)."
  if [ "$launched" -gt 0 ]; then
    echo "View agents with:"
    echo "  claude agents --cwd \"$REPO_ROOT\""
  fi
}

cmd_start() {
  preflight
  with_lock
  echo "=== Shutter Data Boundary V1 Orchestration ==="
  echo "Plan: $PLAN_DIR"
  echo "Repo: $REPO_ROOT"
  echo "Max parallel: $MAX_PARALLEL"
  launch_ready
}

cmd_advance() {
  preflight
  with_lock
  local from_hint=""
  if [ "${1:-}" = "--from" ]; then
    from_hint="${2:-}"
  fi
  [ -z "$from_hint" ] || echo "Advance requested from: $from_hint"
  local stop
  stop="$(any_stop_state)"
  [ -z "$stop" ] || die "stop state present: $stop"
  if all_functional_completed; then
    local fp st
    fp="$(finalize_package)"
    st="$(state_field "$fp" state)"
    case "$st" in
      pending|ready) launch_package "$fp" ;;
      launched|in_progress|finalizing|finalized) echo "Finalize already $st." ;;
      *) die "finalize package is $st" ;;
    esac
  else
    launch_ready
  fi
}

cmd_status() {
  preflight
  printf "%-32s %-12s %-42s %-80s %-12s %-16s %s\n" "PACKAGE" "STATE" "BRANCH" "WORKTREE" "VERIFY" "INTEGRATION" "LAST_ERROR"
  awk -F '\t' '
    FNR > 1 {
      printf "%-32s %-12s %-42s %-80s %-12s %-16s %s\n", $1, $2, $6, $7, $10, $11, $13
    }
  ' "$STATE"
}

cmd_retry() {
  preflight
  with_lock
  local package_id="${1:-}"
  [ -n "$package_id" ] || die "retry requires package id"
  local st
  st="$(state_field "$package_id" state)"
  case "$st" in
    blocked|stale|invalid)
      echo "Resetting $package_id from $st to pending."
      set_state_field "$package_id" state pending
      set_state_field "$package_id" last_error ""
      ;;
    *)
      die "cannot retry $package_id in state $st; valid retry states: blocked, stale, invalid"
      ;;
  esac
}

cmd_finalize() {
  preflight
  with_lock
  local fp
  fp="$(finalize_package)"
  if ! all_functional_completed; then
    die "cannot finalize until all functional packages are completed"
  fi
  launch_package "$fp"
}

case "${1:-}" in
  start) shift; cmd_start "$@" ;;
  advance) shift; cmd_advance "$@" ;;
  status) shift; cmd_status "$@" ;;
  retry) shift; cmd_retry "$@" ;;
  finalize) shift; cmd_finalize "$@" ;;
  -h|--help|help|"") usage ;;
  *) usage; die "unknown command: $1" ;;
esac
