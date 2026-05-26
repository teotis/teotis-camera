#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="/Volumes/Extreme_SSD/project/open_camera"
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

now_utc() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

die() {
  echo "ERROR: $*" >&2
  exit 1
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
  (cd "$REPO_ROOT" && git rev-parse --git-dir >/dev/null 2>&1) || die "not a git repository: $REPO_ROOT"

  awk -F '\t' '
    NR == 1 { next }
    $1 == "" { print "empty package id"; bad = 1 }
    seen[$1]++ { print "duplicate package id: " $1; bad = 1 }
    $10 == "1" { finalize++ }
    END { if (finalize != 1) { print "expected exactly one finalize row"; bad = 1 } exit bad }
  ' "$GRAPH" || die "graph validation failed"

  awk -F '\t' '
    NR == FNR { if (FNR > 1) pkg[$1] = 1; next }
    NR == 1 { next }
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

field_from_graph() {
  local package_id="$1"
  local field="$2"
  awk -F '\t' -v id="$package_id" -v f="$field" '
    NR == 1 {
      for (i = 1; i <= NF; i++) idx[$i] = i
      next
    }
    $1 == id { print $idx[f]; exit }
  ' "$GRAPH"
}

field_from_state() {
  local package_id="$1"
  local field="$2"
  awk -F '\t' -v id="$package_id" -v f="$field" '
    NR == 1 {
      for (i = 1; i <= NF; i++) idx[$i] = i
      next
    }
    $1 == id { print $idx[f]; exit }
  ' "$STATE"
}

update_state() {
  local package_id="$1"
  local state="$2"
  local launched_at="$3"
  local completed_at="$4"
  local last_error="$5"

  local tmp
  tmp="$(mktemp "${STATE}.XXXXXX")"
  awk -F '\t' -v OFS='\t' \
    -v id="$package_id" \
    -v new_state="$state" \
    -v launched="$launched_at" \
    -v completed="$completed_at" \
    -v error="$last_error" '
    NR == 1 {
      for (i = 1; i <= NF; i++) idx[$i] = i
      print
      next
    }
    $1 == id {
      if (new_state != "-") $idx["state"] = new_state
      if (launched != "-") $idx["launched_at"] = launched
      if (completed != "-") $idx["completed_at"] = completed
      if (error != "-") $idx["last_error"] = error
    }
    { print }
  ' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

markdown_status() {
  local status_file="$1"
  awk -F ': ' '/^- \*\*Status\*\*/ { print $2; exit }' "$status_file" | tr -d '\r'
}

validate_markdown_state() {
  local package_id="$1"
  local status_file md state
  status_file="$PLAN_DIR/$(field_from_graph "$package_id" status_file)"
  [ -f "$status_file" ] || die "missing status file for $package_id: $status_file"
  md="$(markdown_status "$status_file")"
  state="$(field_from_state "$package_id" state)"

  case "$md" in
    completed|blocked|stale|invalid|finalizing|finalized)
      [ "$md" = "$state" ] || {
        update_state "$package_id" invalid - - "markdown status $md disagrees with state $state"
        die "status/state mismatch for $package_id: markdown=$md state=$state"
      }
      ;;
  esac
}

functional_packages() {
  awk -F '\t' 'NR > 1 && $10 != "1" { print $1 }' "$GRAPH"
}

finalize_package() {
  awk -F '\t' 'NR > 1 && $10 == "1" { print $1; exit }' "$GRAPH"
}

deps_completed() {
  local package_id="$1"
  local deps dep state
  deps="$(field_from_graph "$package_id" dependencies)"
  [ -z "$deps" ] && return 0
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    dep="$(echo "$dep" | awk '{$1=$1; print}')"
    [ -z "$dep" ] && continue
    state="$(field_from_state "$dep" state)"
    [ "$state" = "completed" ] || [ "$state" = "finalized" ] || return 1
  done
  return 0
}

any_stop_state() {
  awk -F '\t' 'NR > 1 && ($2 == "blocked" || $2 == "stale" || $2 == "invalid") { print $1 ":" $2 ":" $13 }' "$STATE"
}

all_functional_completed() {
  local pkg state
  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    state="$(field_from_state "$pkg" state)"
    [ "$state" = "completed" ] || return 1
  done < <(functional_packages)
  return 0
}

build_prompt() {
  local package_id="$1"
  local package_doc status_file deps
  package_doc="$PLAN_DIR/$(field_from_graph "$package_id" package_doc)"
  status_file="$PLAN_DIR/$(field_from_graph "$package_id" status_file)"
  deps="$(field_from_graph "$package_id" dependencies)"
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

Read INDEX.md, your package doc, AGENTS.md, and /Users/dingren/.codex/RTK.md before any command. Use rtk for all shell commands. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

You may edit only allowed paths from the package doc. Do not edit INDEX.md or another package status file. Do not force-push, hard reset, delete remote branches, add secrets, use network, or delete branches/worktrees not recorded by this orchestration.

Before calling advance, set coordinator status to completed or blocked, fill evidence, and update your row in state.tsv consistently.

Tail step:
bash "$PLAN_DIR/launchers/orchestrate.sh" advance --from "$package_id"
PROMPT
}

launch_package() {
  local package_id="$1"
  local current name prompt output status launched
  current="$(field_from_state "$package_id" state)"
  case "$current" in
    ready|pending) ;;
    *) echo "skip $package_id: state=$current"; return 0 ;;
  esac

  name="stage7-session-hang-${package_id}"
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
    update_state "$package_id" blocked - - "launch failed"
    if [ "$CLAUDE_PERMISSION_MODE" = "auto" ] && echo "$output" | grep -qi "requires opting in"; then
      echo "Run once interactively: claude --permission-mode auto" >&2
    fi
    return "$status"
  fi

  echo "$output"
  update_state "$package_id" launched "$launched" - -
}

print_agents_hint() {
  echo
  echo "View background sessions with:"
  echo "  claude agents --cwd \"$REPO_ROOT\""
}

launch_ready_functional() {
  local launched_count=0
  local pkg state stop
  stop="$(any_stop_state)"
  [ -z "$stop" ] || die "stop state present: $stop"

  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    validate_markdown_state "$pkg"
    state="$(field_from_state "$pkg" state)"
    if { [ "$state" = "ready" ] || [ "$state" = "pending" ]; } && deps_completed "$pkg"; then
      if [ "$launched_count" -ge "$MAX_PARALLEL" ]; then
        break
      fi
      launch_package "$pkg"
      launched_count=$((launched_count + 1))
    fi
  done < <(functional_packages)

  if [ "$launched_count" -gt 0 ]; then
    print_agents_hint
  else
    echo "No ready functional packages to launch."
  fi
}

launch_finalize_if_ready() {
  local pkg state
  pkg="$(finalize_package)"
  validate_markdown_state "$pkg"
  if all_functional_completed; then
    state="$(field_from_state "$pkg" state)"
    case "$state" in
      pending|ready)
        launch_package "$pkg"
        print_agents_hint
        ;;
      launched|in_progress|finalizing|finalized|completed)
        echo "$pkg already state=$state"
        ;;
      *)
        die "$pkg is not launchable: state=$state"
        ;;
    esac
  else
    echo "Functional packages are not all completed; finalize not launched."
  fi
}

cmd_start() {
  preflight
  with_lock
  launch_ready_functional
}

cmd_advance() {
  local from_hint=""
  if [ "${1:-}" = "--from" ]; then
    from_hint="${2:-}"
    shift 2 || true
  fi
  preflight
  with_lock
  [ -z "$from_hint" ] || echo "Advance requested by: $from_hint"

  local pkg
  while IFS= read -r pkg; do
    [ -z "$pkg" ] && continue
    validate_markdown_state "$pkg"
  done < <(awk -F '\t' 'NR > 1 { print $1 }' "$GRAPH")

  if all_functional_completed; then
    launch_finalize_if_ready
  else
    launch_ready_functional
  fi
}

cmd_status() {
  preflight
  printf "%-48s %-12s %-38s %-55s %-18s %-18s %s\n" "PACKAGE" "STATE" "BRANCH" "WORKTREE" "VERIFY" "INTEGRATION" "LAST_ERROR"
  awk -F '\t' '
    NR == 1 { next }
    {
      printf "%-48s %-12s %-38s %-55s %-18s %-18s %s\n", $1, $2, $6, $7, $10, $11, $13
    }
  ' "$STATE"
}

cmd_retry() {
  local package_id="${1:-}"
  [ -n "$package_id" ] || die "retry requires a package id"
  preflight
  with_lock

  local state
  state="$(field_from_state "$package_id" state)"
  case "$state" in
    blocked|stale|invalid)
      echo "Resetting $package_id from $state to ready"
      update_state "$package_id" ready "" "" ""
      ;;
    *)
      die "refusing to retry $package_id in state=$state; only blocked, stale, or invalid may be retried"
      ;;
  esac
}

cmd_finalize() {
  preflight
  with_lock
  launch_finalize_if_ready
}

case "${1:-}" in
  start)
    shift
    cmd_start "$@"
    ;;
  advance)
    shift
    cmd_advance "$@"
    ;;
  status)
    shift
    cmd_status "$@"
    ;;
  retry)
    shift
    cmd_retry "$@"
    ;;
  finalize)
    shift
    cmd_finalize "$@"
    ;;
  -h|--help|"")
    usage
    ;;
  *)
    usage
    die "unknown command: $1"
    ;;
esac
