#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
PLAN_DIR="$REPO_ROOT/docs/plans/zoom-brightness-rollback-implementation-orchestration"
GRAPH="$PLAN_DIR/launchers/package-graph.tsv"
STATE="$PLAN_DIR/status/state.tsv"
LOCK_DIR="$PLAN_DIR/status/.orchestrate.lock"
MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"

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

acquire_lock() {
  if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    echo "ERROR: orchestration lock exists: $LOCK_DIR" >&2
    exit 1
  fi
  trap 'rm -rf "$LOCK_DIR"' EXIT
}

graph_field() {
  local package_id="$1"
  local field="$2"
  awk -F'\t' -v id="$package_id" -v field="$field" '
    FNR == 1 {
      for (i = 1; i <= NF; i++) h[$i] = i
      next
    }
    $1 == id { print $h[field]; exit }
  ' "$GRAPH"
}

state_field() {
  local package_id="$1"
  local field="$2"
  awk -F'\t' -v id="$package_id" -v field="$field" '
    FNR == 1 {
      for (i = 1; i <= NF; i++) h[$i] = i
      next
    }
    $1 == id { print $h[field]; exit }
  ' "$STATE"
}

markdown_status() {
  local status_file="$1"
  if [ ! -f "$PLAN_DIR/$status_file" ]; then
    echo "missing"
    return
  fi
  awk '
    /^\- \*\*Status\*\*:/ {
      gsub(/\r/, "", $0)
      sub(/^- \*\*Status\*\*: /, "", $0)
      print $0
      exit
    }
    /^completed$/ { print "completed"; exit }
    /^blocked$/ { print "blocked"; exit }
  ' "$PLAN_DIR/$status_file"
}

update_state_row() {
  local package_id="$1"
  local new_state="$2"
  local launched_at="${3:-}"
  local completed_at="${4:-}"
  local agent="${5:-}"
  local branch="${6:-}"
  local worktree="${7:-}"
  local base_commit="${8:-}"
  local commit_hash="${9:-}"
  local verification="${10:-}"
  local integration="${11:-}"
  local cleanup="${12:-}"
  local last_error="${13:-}"
  local tmp
  tmp="$(mktemp)"
  awk -F'\t' -v OFS='\t' \
    -v id="$package_id" \
    -v s="$new_state" \
    -v launched="$launched_at" \
    -v completed="$completed_at" \
    -v agent="$agent" \
    -v branch="$branch" \
    -v worktree="$worktree" \
    -v base="$base_commit" \
    -v commit="$commit_hash" \
    -v verification="$verification" \
    -v integration="$integration" \
    -v cleanup="$cleanup" \
    -v err="$last_error" '
    FNR == 1 { print; next }
    $1 == id {
      print id, s, launched, completed, agent, branch, worktree, base, commit, verification, integration, cleanup, err
      next
    }
    { print }
  ' "$STATE" > "$tmp"
  mv "$tmp" "$STATE"
}

validate_graph() {
  [ -f "$GRAPH" ] || { echo "ERROR: missing graph $GRAPH" >&2; exit 1; }
  [ -f "$STATE" ] || { echo "ERROR: missing state $STATE" >&2; exit 1; }
  awk -F'\t' '
    FNR == 1 { next }
    seen[$1]++ { print "ERROR: duplicate package id " $1; exit 1 }
    END { if (NR < 2) { print "ERROR: empty graph"; exit 1 } }
  ' "$GRAPH"
  awk -F'\t' '
    FNR == 1 { next }
    { ids[$1]=1; deps[$1]=$4 }
    END {
      finalize=0
      for (id in ids) {
        split(deps[id], d, ",")
        for (i in d) if (d[i] != "" && !(d[i] in ids)) {
          print "ERROR: missing dependency " d[i] " for " id
          exit 1
        }
        if (id == "99-finalize") finalize++
      }
      if (finalize != 1) { print "ERROR: graph must contain exactly one 99-finalize"; exit 1 }
    }
  ' "$GRAPH"
}

is_functional() {
  [ "$(graph_field "$1" finalize)" != "1" ]
}

deps_completed() {
  local package_id="$1"
  local deps
  deps="$(graph_field "$package_id" dependencies)"
  [ -z "$deps" ] && return 0
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    [ -z "$dep" ] && continue
    [ "$(state_field "$dep" state)" = "completed" ] || return 1
    local status_file md
    status_file="$(graph_field "$dep" status_file)"
    md="$(markdown_status "$status_file")"
    [ "$md" = "completed" ] || return 1
  done
  return 0
}

any_blocked_or_invalid() {
  awk -F'\t' 'FNR > 1 && ($2 == "blocked" || $2 == "stale" || $2 == "invalid") { print $1 ":" $2 }' "$STATE"
}

running_count() {
  awk -F'\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress" || $2 == "finalizing") { c++ } END { print c+0 }' "$STATE"
}

launch_package() {
  local package_id="$1"
  local package_doc status_file branch worktree base now prompt name output status
  package_doc="$(graph_field "$package_id" package_doc)"
  status_file="$(graph_field "$package_id" status_file)"
  branch="$(graph_field "$package_id" branch)"
  worktree="$(graph_field "$package_id" worktree)"
  base="$(git -C "$REPO_ROOT" rev-parse --short HEAD)"
  now="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  name="zb-${package_id}"
  prompt="Read $PLAN_DIR/INDEX.md and $PLAN_DIR/$package_doc. Execute package $package_id. Write coordinator evidence to $PLAN_DIR/$status_file and update $PLAN_DIR/status/state.tsv. Do not edit INDEX.md or another status file. When done, run: bash $PLAN_DIR/launchers/orchestrate.sh advance --from $package_id"

  command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }

  echo "Launching $package_id as $name"
  set +e
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    output="$(claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --permission-mode "$CLAUDE_PERMISSION_MODE" --setting-sources "$CLAUDE_SETTING_SOURCES" "$prompt" 2>&1)"
  else
    output="$(claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" --setting-sources "$CLAUDE_SETTING_SOURCES" "$prompt" 2>&1)"
  fi
  status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    update_state_row "$package_id" "blocked" "$now" "" "$name" "$branch" "$worktree" "$base" "" "not-run" "pending" "pending" "$output"
    echo "$output" >&2
    return "$status"
  fi
  update_state_row "$package_id" "launched" "$now" "" "$name" "$branch" "$worktree" "$base" "" "pending" "pending" "pending" ""
  echo "$output"
}

launch_ready() {
  local launched=0
  local blocked
  blocked="$(any_blocked_or_invalid)"
  if [ -n "$blocked" ]; then
    echo "Stop: blocked/stale/invalid package exists:"
    echo "$blocked"
    return 1
  fi
  while IFS=$'\t' read -r package_id _; do
    [ "$package_id" = "package_id" ] && continue
    is_functional "$package_id" || continue
    local state
    state="$(state_field "$package_id" state)"
    case "$state" in
      pending|ready)
        if deps_completed "$package_id"; then
          if [ "$(running_count)" -ge "$MAX_PARALLEL" ]; then
            continue
          fi
          launch_package "$package_id"
          launched=$((launched + 1))
        fi
        ;;
    esac
  done < "$GRAPH"
  if [ "$launched" -eq 0 ]; then
    echo "No newly ready functional packages launched."
  fi
}

all_functional_completed() {
  while IFS=$'\t' read -r package_id _; do
    [ "$package_id" = "package_id" ] && continue
    is_functional "$package_id" || continue
    [ "$(state_field "$package_id" state)" = "completed" ] || return 1
    local status_file md
    status_file="$(graph_field "$package_id" status_file)"
    md="$(markdown_status "$status_file")"
    [ "$md" = "completed" ] || return 1
  done < "$GRAPH"
  return 0
}

start_cmd() {
  validate_graph
  acquire_lock
  launch_ready
  echo
  echo "View agents with:"
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
}

advance_cmd() {
  validate_graph
  acquire_lock
  launch_ready || true
  if all_functional_completed; then
    local fstate
    fstate="$(state_field 99-finalize state)"
    if [ "$fstate" = "pending" ] || [ "$fstate" = "ready" ]; then
      launch_package "99-finalize"
    else
      echo "99-finalize state: $fstate"
    fi
  fi
}

status_cmd() {
  validate_graph
  printf "%-44s %-12s %-12s %-34s %-20s %-20s %s\n" "PACKAGE" "STATE" "MD_STATUS" "BRANCH" "VERIFICATION" "INTEGRATION" "LAST_ERROR"
  while IFS=$'\t' read -r package_id _; do
    [ "$package_id" = "package_id" ] && continue
    local state status_file md branch verification integration err
    state="$(state_field "$package_id" state)"
    status_file="$(graph_field "$package_id" status_file)"
    md="$(markdown_status "$status_file")"
    branch="$(state_field "$package_id" branch)"
    verification="$(state_field "$package_id" verification)"
    integration="$(state_field "$package_id" integration)"
    err="$(state_field "$package_id" last_error)"
    if [ -n "$md" ] && [ "$md" != "pending" ] && [ "$md" != "$state" ] && ! { [ "$state" = "launched" ] && [ "$md" = "pending" ]; }; then
      state="invalid"
      err="markdown/state mismatch"
    fi
    printf "%-44s %-12s %-12s %-34s %-20s %-20s %s\n" "$package_id" "${state:-pending}" "${md:-pending}" "${branch:-}" "${verification:-}" "${integration:-}" "${err:-}"
  done < "$GRAPH"
}

retry_cmd() {
  local package_id="${1:-}"
  [ -n "$package_id" ] || { echo "ERROR: retry requires package id" >&2; exit 2; }
  local state
  state="$(state_field "$package_id" state)"
  case "$state" in
    blocked|stale|invalid)
      echo "Resetting $package_id from $state to pending"
      update_state_row "$package_id" "pending" "" "" "" "$(graph_field "$package_id" branch)" "$(graph_field "$package_id" worktree)" "" "" "pending" "pending" "pending" ""
      ;;
    *)
      echo "ERROR: refusing to retry $package_id in state $state" >&2
      exit 1
      ;;
  esac
}

finalize_cmd() {
  validate_graph
  acquire_lock
  if ! all_functional_completed; then
    echo "ERROR: cannot finalize until all functional packages are completed in state.tsv and Markdown status." >&2
    exit 1
  fi
  launch_package "99-finalize"
}

cmd="${1:-}"
shift || true
case "$cmd" in
  start) start_cmd ;;
  advance)
    if [ "${1:-}" = "--from" ]; then shift 2 || true; fi
    advance_cmd
    ;;
  status) status_cmd ;;
  retry) retry_cmd "${1:-}" ;;
  finalize) finalize_cmd ;;
  *) usage; exit 2 ;;
esac

