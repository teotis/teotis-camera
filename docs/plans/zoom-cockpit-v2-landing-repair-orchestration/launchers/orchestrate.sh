#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel)"
PLAN_DIR="$REPO_ROOT/docs/plans/zoom-cockpit-v2-landing-repair-orchestration"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK="$PLAN_DIR/status/.orchestrate.lock"
MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"

log() { echo "[orchestrate] $*"; }
die() { echo "[orchestrate] ERROR: $*" >&2; exit 1; }

acquire_lock() {
  local attempts=0
  while ! mkdir "$LOCK" 2>/dev/null; do
    attempts=$((attempts + 1))
    [ "$attempts" -lt 60 ] || die "Could not acquire lock. If stale, remove: $LOCK"
    sleep 1
  done
  trap 'rm -rf "$LOCK"' EXIT
}

tsv_get() {
  local file="$1" pid="$2" col="$3"
  awk -F'\t' -v pid="$pid" -v col="$col" 'FNR > 1 && $1 == pid { print $col; exit }' "$file"
}

state_get() { tsv_get "$STATE" "$1" "$2"; }
graph_get() { tsv_get "$GRAPH" "$1" "$2"; }

state_set() {
  local pid="$1" col="$2" val="$3" tmp="${STATE}.tmp.$$"
  awk -F'\t' -v pid="$pid" -v col="$col" -v val="$val" '
    BEGIN { OFS="\t" }
    FNR == 1 { print; next }
    $1 == pid { $col = val; print; next }
    { print }
  ' "$STATE" > "$tmp" && mv "$tmp" "$STATE"
}

all_packages() { awk -F'\t' 'FNR > 1 { print $1 }' "$GRAPH"; }
functional_packages() { awk -F'\t' 'FNR > 1 && $10 != 1 { print $1 }' "$GRAPH"; }

running_count() {
  awk -F'\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress" || $2 == "finalizing") { n++ } END { print n+0 }' "$STATE"
}

deps_completed() {
  local pid="$1" deps dep dep_state
  deps="$(graph_get "$pid" 4)"
  [ -n "$deps" ] || return 0
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    dep="$(echo "$dep" | xargs)"
    dep_state="$(state_get "$dep" 2)"
    [ "$dep_state" = "completed" ] || [ "$dep_state" = "finalized" ] || return 1
  done
}

is_ready() {
  local pid="$1"
  [ "$(state_get "$pid" 2)" = "pending" ] && deps_completed "$pid"
}

all_functional_completed() {
  local pid
  for pid in $(functional_packages); do
    [ "$(state_get "$pid" 2)" = "completed" ] || return 1
  done
}

validate_graph() {
  [ -f "$GRAPH" ] || die "missing graph: $GRAPH"
  [ -f "$STATE" ] || die "missing state: $STATE"
  local duplicate
  duplicate="$(awk -F'\t' 'FNR > 1 { seen[$1]++ } END { for (id in seen) if (seen[id] > 1) print id }' "$GRAPH")"
  [ -z "$duplicate" ] || die "duplicate package ids: $duplicate"
  local missing
  missing="$(awk -F'\t' 'FNR == NR { if (FNR > 1) ids[$1]=1; next } FNR > 1 { split($4,d,","); for (i in d) { gsub(/^ +| +$/,"",d[i]); if (d[i] != "" && !(d[i] in ids)) print $1 " -> " d[i] } }' "$GRAPH" "$GRAPH")"
  [ -z "$missing" ] || die "missing dependencies: $missing"
  local finalize_count
  finalize_count="$(awk -F'\t' 'FNR > 1 && $10 == 1 { n++ } END { print n+0 }' "$GRAPH")"
  [ "$finalize_count" = "1" ] || die "expected exactly one finalize package, found $finalize_count"
}

launch_package() {
  local pid="$1" now name prompt status_file package_doc output rc
  now="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  name="zoom-v2-repair-${pid}"
  package_doc="$PLAN_DIR/$(graph_get "$pid" 2)"
  status_file="$PLAN_DIR/$(graph_get "$pid" 3)"
  prompt="Read $PLAN_DIR/INDEX.md and $package_doc. Implement package $pid. Write evidence to $status_file and update $STATE when done. Do NOT edit INDEX.md or other package status files. Tail call: bash $PLAN_DIR/launchers/orchestrate.sh advance --from $pid"
  local args=(--bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --setting-sources "$CLAUDE_SETTING_SOURCES")
  [ -z "$CLAUDE_PERMISSION_MODE" ] || args+=(--permission-mode "$CLAUDE_PERMISSION_MODE")
  log "launching $name"
  set +e
  output="$(claude "${args[@]}" "$prompt" 2>&1)"
  rc=$?
  set -e
  if [ "$rc" -ne 0 ]; then
    echo "$output" >&2
    state_set "$pid" 2 "blocked"
    state_set "$pid" 13 "launch_failed"
    return "$rc"
  fi
  state_set "$pid" 2 "launched"
  state_set "$pid" 3 "$now"
  state_set "$pid" 5 "$name"
  state_set "$pid" 6 "$(graph_get "$pid" 7)"
  state_set "$pid" 7 "$REPO_ROOT/$(graph_get "$pid" 8)"
  echo "$output"
}

launch_ready() {
  local running available launched=0 pid
  running="$(running_count)"
  available=$((MAX_PARALLEL - running))
  [ "$available" -gt 0 ] || { log "max parallel reached"; return 0; }
  for pid in $(functional_packages); do
    [ "$launched" -lt "$available" ] || break
    if is_ready "$pid"; then
      launch_package "$pid" || true
      launched=$((launched + 1))
    fi
  done
  if [ "$launched" -eq 0 ] && all_functional_completed && [ "$(state_get 99-finalize 2)" = "pending" ]; then
    launch_package "99-finalize" || true
    launched=1
  fi
  log "launched $launched package(s)"
  print_agents_hint
}

print_agents_hint() {
  echo "View agents:"
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
}

cmd_status() {
  validate_graph
  printf "%-45s %-12s %-18s %-65s %s\n" "PACKAGE" "STATE" "VERIFICATION" "BRANCH" "LAST_ERROR"
  awk -F'\t' 'FNR > 1 { printf "%-45s %-12s %-18s %-65s %s\n", $1, $2, $10, $6, $13 }' "$STATE"
}

cmd_start() { validate_graph; acquire_lock; launch_ready; }
cmd_advance() { validate_graph; acquire_lock; launch_ready; }

cmd_retry() {
  local pid="${1:-}"
  [ -n "$pid" ] || die "retry requires package id"
  validate_graph
  acquire_lock
  local st
  st="$(state_get "$pid" 2)"
  case "$st" in
    blocked|stale|invalid)
      log "resetting $pid from $st to pending"
      state_set "$pid" 2 "pending"
      state_set "$pid" 13 ""
      ;;
    *)
      die "retry only supports blocked/stale/invalid packages; $pid is $st"
      ;;
  esac
  launch_ready
}

cmd_finalize() {
  validate_graph
  acquire_lock
  if ! all_functional_completed; then
    die "cannot finalize: functional packages are not all completed"
  fi
  if [ "$(state_get 99-finalize 2)" = "pending" ]; then
    launch_package "99-finalize"
  else
    log "99-finalize is $(state_get 99-finalize 2)"
  fi
  print_agents_hint
}

case "${1:-status}" in
  start) cmd_start ;;
  advance) shift || true; cmd_advance "$@" ;;
  status) cmd_status ;;
  retry) shift; cmd_retry "$@" ;;
  finalize) cmd_finalize ;;
  *) die "usage: $0 {start|advance|status|retry <package-id>|finalize}" ;;
esac

