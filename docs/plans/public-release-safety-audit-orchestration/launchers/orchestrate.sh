#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
GRAPH="$PLAN_ROOT/launchers/package-graph.tsv"
STATE="$PLAN_ROOT/status/state.tsv"
PROMPTS="$PLAN_ROOT/launchers/agent-prompts.md"
LOCK_DIR="$PLAN_ROOT/status/.orchestrate.lock"
MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
STATE_HEADER="package_id	state	launched_at	completed_at	agent	branch	worktree	base_commit	commit_hash	verification	integration	cleanup	last_error"
GRAPH_HEADER="package_id	package_doc	status_file	dependencies	dependency_type	wave	branch	worktree	manual	finalize"
LOCK_ACQUIRED=0

log() {
  printf '[orchestrate] %s\n' "$*" >&2
}

die() {
  printf '[orchestrate] ERROR: %s\n' "$*" >&2
  exit 1
}

timestamp() {
  date -u "+%Y-%m-%dT%H:%M:%SZ"
}

acquire_lock() {
  local tries=0
  until mkdir "$LOCK_DIR" 2>/dev/null; do
    tries=$((tries + 1))
    if [ "$tries" -gt 30 ]; then
      die "could not acquire lock: $LOCK_DIR"
    fi
    sleep 1
  done
  printf '%s\n' "$$" > "$LOCK_DIR/pid"
  LOCK_ACQUIRED=1
}

release_lock() {
  if [ "$LOCK_ACQUIRED" -eq 1 ] &&
    [ -f "$LOCK_DIR/pid" ] &&
    [ "$(cat "$LOCK_DIR/pid" 2>/dev/null || true)" = "$$" ]; then
    rm -rf "$LOCK_DIR"
  fi
}

trap 'release_lock' EXIT

valid_state() {
  case "$1" in
    pending|ready|launched|in_progress|completed|blocked|stale|invalid|finalizing|finalized)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

graph_field() {
  local package_id="$1"
  local column="$2"
  awk -F '\t' -v id="$package_id" -v col="$column" '
    FNR == 1 {
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
    FNR == 1 {
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

all_package_ids() {
  awk -F '\t' 'FNR > 1 && NF { print $1 }' "$GRAPH"
}

functional_package_ids() {
  awk -F '\t' 'FNR > 1 && NF && $10 != "1" { print $1 }' "$GRAPH"
}

finalize_package_id() {
  awk -F '\t' 'FNR > 1 && NF && $10 == "1" { print $1; exit }' "$GRAPH"
}

status_file_for() {
  local package_id="$1"
  local rel
  rel="$(graph_field "$package_id" status_file)"
  printf '%s/%s\n' "$PLAN_ROOT" "$rel"
}

markdown_status() {
  local package_id="$1"
  local file line value
  file="$(status_file_for "$package_id")"
  if [ ! -f "$file" ]; then
    printf 'missing\n'
    return
  fi
  line="$(awk '
    /^## State/ { in_state = 1; next }
    in_state && /`/ {
      gsub(/`/, "", $0)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      print tolower($0)
      exit
    }
    /\*\*Status\*\*:/ {
      sub(/^.*\*\*Status\*\*:[[:space:]]*/, "", $0)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      print tolower($0)
      exit
    }
  ' "$file")"
  value="${line:-unknown}"
  case "$value" in
    done|complete) printf 'completed\n' ;;
    *) printf '%s\n' "$value" ;;
  esac
}

sync_markdown_state() {
  local package_id="$1"
  local new_state="$2"
  local file tmp
  file="$(status_file_for "$package_id")"
  [ -f "$file" ] || return 0
  tmp="$(mktemp)"
  awk -v new_state="$new_state" '
    /^## State/ {
      print
      in_state = 1
      changed = 0
      next
    }
    in_state && !changed && /`/ {
      print "`" new_state "`"
      changed = 1
      in_state = 0
      next
    }
    /\*\*Status\*\*:/ {
      print "**Status**: " new_state
      changed = 1
      next
    }
    { print }
    END {
      if (!changed) {
        print ""
        print "## State"
        print ""
        print "`" new_state "`"
      }
    }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

preflight_files() {
  [ -f "$PLAN_ROOT/INDEX.md" ] || die "missing INDEX.md"
  [ -f "$GRAPH" ] || die "missing package graph: $GRAPH"
  [ -f "$STATE" ] || die "missing state ledger: $STATE"
  [ -f "$PROMPTS" ] || die "missing agent prompts: $PROMPTS"
}

preflight_graph() {
  preflight_files
  [ "$(head -n 1 "$GRAPH")" = "$GRAPH_HEADER" ] || die "invalid graph header"
  awk -F '\t' '
    FNR == 1 { next }
    NF == 0 { next }
    NF != 10 {
      printf("graph row has %d fields, expected 10: %s\n", NF, $0) > "/dev/stderr"
      bad = 1
    }
    seen[$1]++ {
      printf("duplicate package id: %s\n", $1) > "/dev/stderr"
      bad = 1
    }
    $10 == "1" { finalize++ }
    END {
      if (finalize != 1) {
        printf("expected exactly one finalize row, got %d\n", finalize + 0) > "/dev/stderr"
        bad = 1
      }
      exit bad ? 1 : 0
    }
  ' "$GRAPH" || die "graph validation failed"

  awk -F '\t' '
    FNR == 1 { next }
    NF == 0 { next }
    { ids[$1] = 1; deps[$1] = $4 }
    END {
      for (id in ids) {
        split(deps[id], arr, ",")
        for (i in arr) {
          dep = arr[i]
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", dep)
          if (dep == "") continue
          if (!(dep in ids)) {
            printf("missing dependency: %s -> %s\n", id, dep) > "/dev/stderr"
            bad = 1
          }
          if (dep == id) {
            printf("self dependency: %s\n", id) > "/dev/stderr"
            bad = 1
          }
        }
      }
      exit bad ? 1 : 0
    }
  ' "$GRAPH" || die "graph dependency validation failed"
}

preflight_state() {
  [ -s "$STATE" ] || die "state ledger is empty; run repair-state"
  [ "$(head -n 1 "$STATE")" = "$STATE_HEADER" ] || die "invalid state header"
  awk -F '\t' '
    FNR == NR {
      if (FNR > 1 && NF) ids[$1] = 1
      next
    }
    FNR == 1 { next }
    NF == 0 { next }
    {
      if (NF != 13) {
        printf("state row has %d fields, expected 13: %s\n", NF, $0) > "/dev/stderr"
        bad = 1
      }
      if (!($1 in ids)) {
        printf("state has unknown package: %s\n", $1) > "/dev/stderr"
        bad = 1
      }
      seen[$1] = 1
      if ($2 !~ /^(pending|ready|launched|in_progress|completed|blocked|stale|invalid|finalizing|finalized)$/) {
        printf("invalid state for %s: %s\n", $1, $2) > "/dev/stderr"
        bad = 1
      }
    }
    END {
      for (id in ids) {
        if (!(id in seen)) {
          printf("state missing package: %s\n", id) > "/dev/stderr"
          bad = 1
        }
      }
      exit bad ? 1 : 0
    }
  ' "$GRAPH" "$STATE" || die "state validation failed"
}

preflight_all() {
  preflight_graph
  preflight_state
}

status_consistency_ok() {
  local id state md
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    state="$(state_field "$id" state)"
    md="$(markdown_status "$id")"
    case "$md" in
      missing|unknown)
        printf 'INVALID: %s markdown status is %s\n' "$id" "$md" >&2
        return 1
        ;;
    esac
    if [ "$state" = "completed" ] && [ "$md" != "completed" ]; then
      printf 'INVALID: %s state completed but markdown is %s\n' "$id" "$md" >&2
      return 1
    fi
    if [ "$md" = "completed" ] && [ "$state" != "completed" ] && [ "$state" != "finalized" ]; then
      printf 'INVALID: %s markdown completed but state is %s\n' "$id" "$state" >&2
      return 1
    fi
  done < <(all_package_ids)
  return 0
}

set_state_fields() {
  local package_id="$1"
  local new_state="$2"
  local launched_at="${3:-__KEEP__}"
  local completed_at="${4:-__KEEP__}"
  local agent="${5:-__KEEP__}"
  local branch="${6:-__KEEP__}"
  local worktree="${7:-__KEEP__}"
  local base_commit="${8:-__KEEP__}"
  local commit_hash="${9:-__KEEP__}"
  local verification="${10:-__KEEP__}"
  local integration="${11:-__KEEP__}"
  local cleanup="${12:-__KEEP__}"
  local last_error="${13:-__KEEP__}"
  local now tmp

  valid_state "$new_state" || die "invalid state: $new_state"
  now="$(timestamp)"
  tmp="$(mktemp)"
  awk -F '\t' -v OFS='\t' \
    -v id="$package_id" \
    -v new_state="$new_state" \
    -v now="$now" \
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
    -v last_error="$last_error" '
      FNR == 1 {
        for (i = 1; i <= NF; i++) idx[$i] = i
        print
        next
      }
      $1 == id {
        $idx["state"] = new_state
        if (launched == "__NOW__" || ((new_state == "launched" || new_state == "in_progress" || new_state == "finalizing") && launched == "__KEEP__")) $idx["launched_at"] = now
        else if (launched != "__KEEP__") $idx["launched_at"] = launched
        if (completed == "__NOW__" || ((new_state == "completed" || new_state == "blocked" || new_state == "stale" || new_state == "invalid" || new_state == "finalized") && completed == "__KEEP__")) $idx["completed_at"] = now
        else if (completed != "__KEEP__") $idx["completed_at"] = completed
        if (agent != "__KEEP__") $idx["agent"] = agent
        if (branch != "__KEEP__") $idx["branch"] = branch
        if (worktree != "__KEEP__") $idx["worktree"] = worktree
        if (base != "__KEEP__") $idx["base_commit"] = base
        if (commit != "__KEEP__") $idx["commit_hash"] = commit
        if (verification != "__KEEP__") $idx["verification"] = verification
        if (integration != "__KEEP__") $idx["integration"] = integration
        if (cleanup != "__KEEP__") $idx["cleanup"] = cleanup
        if (last_error != "__KEEP__") $idx["last_error"] = last_error
        touched = 1
      }
      { print }
      END { if (!touched) exit 1 }
    ' "$STATE" > "$tmp" || {
      rm -f "$tmp"
      die "unknown package in state ledger: $package_id"
    }
  mv "$tmp" "$STATE"
  sync_markdown_state "$package_id" "$new_state"
}

set_error_state() {
  local package_id="$1"
  local new_state="$2"
  local message="$3"
  set_state_fields "$package_id" "$new_state" "__KEEP__" "__NOW__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "$message"
}

deps_completed() {
  local package_id="$1"
  local deps dep state
  deps="$(graph_field "$package_id" dependencies || true)"
  [ -z "$deps" ] && return 0
  IFS=',' read -r -a dep_array <<< "$deps"
  for dep in "${dep_array[@]}"; do
    dep="${dep#"${dep%%[![:space:]]*}"}"
    dep="${dep%"${dep##*[![:space:]]}"}"
    [ -z "$dep" ] && continue
    state="$(state_field "$dep" state)"
    [ "$state" = "completed" ] || [ "$state" = "finalized" ] || return 1
  done
  return 0
}

any_bad_terminal_state() {
  awk -F '\t' 'FNR > 1 && ($2 == "blocked" || $2 == "stale" || $2 == "invalid") { print $1 ":" $2 }' "$STATE"
}

all_functional_completed() {
  local id
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    [ "$(state_field "$id" state)" = "completed" ] || return 1
  done < <(functional_package_ids)
  return 0
}

running_count() {
  awk -F '\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress" || $2 == "finalizing") { count++ } END { print count + 0 }' "$STATE"
}

ready_packages() {
  local id state
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    state="$(state_field "$id" state)"
    if { [ "$state" = "pending" ] || [ "$state" = "ready" ]; } && deps_completed "$id"; then
      printf '%s\n' "$id"
    fi
  done < <(functional_package_ids)
}

prompt_for_package() {
  local package_id="$1"
  awk -v id="$package_id" '
    $0 ~ "^## Package: " id "([[:space:]-]|$)" { found = 1; print; next }
    found && /^## Package: / { exit }
    found { print }
  ' "$PROMPTS"
}

ensure_worktree() {
  local package_id="$1"
  local branch worktree parent
  branch="$(graph_field "$package_id" branch)"
  worktree="$(graph_field "$package_id" worktree)"
  parent="$(dirname "$worktree")"
  mkdir -p "$parent"
  if git -C "$worktree" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    return 0
  fi
  git -C "$REPO_ROOT" worktree add -B "$branch" "$worktree" HEAD >/dev/null
}

launch_package() {
  local package_id="$1"
  local finalize name worktree branch prompt_file launch_log launch_output status session_id new_state version_output
  finalize="$(graph_field "$package_id" finalize)"
  name="$(basename "$PLAN_ROOT")-$package_id"
  worktree="$(graph_field "$package_id" worktree)"
  branch="$(graph_field "$package_id" branch)"
  prompt_file="$(mktemp)"
  launch_log="$PLAN_ROOT/status/launch-$package_id.log"

  prompt_for_package "$package_id" > "$prompt_file"
  if [ ! -s "$prompt_file" ]; then
    rm -f "$prompt_file"
    set_error_state "$package_id" "invalid" "prompt section missing"
    die "prompt section missing for $package_id"
  fi

  if ! command -v claude >/dev/null 2>&1; then
    rm -f "$prompt_file"
    set_error_state "$package_id" "invalid" "claude command not found"
    die "claude command not found"
  fi

  version_output="$(claude --version 2>&1 || true)"
  log "claude: $version_output"
  ensure_worktree "$package_id"
  log "launching $package_id"
  log "  branch: $branch"
  log "  worktree: $worktree"

  set +e
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    launch_output="$(
      cd "$worktree" &&
        claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" \
          --permission-mode "$CLAUDE_PERMISSION_MODE" --setting-sources "$CLAUDE_SETTING_SOURCES" \
          "$(cat "$prompt_file")" 2>&1
    )"
  else
    launch_output="$(
      cd "$worktree" &&
        claude --bg --name "$name" --model "$CLAUDE_MODEL" --effort "$CLAUDE_EFFORT" \
          --setting-sources "$CLAUDE_SETTING_SOURCES" "$(cat "$prompt_file")" 2>&1
    )"
  fi
  status=$?
  set -e
  printf '%s\n' "$launch_output" > "$launch_log"
  rm -f "$prompt_file"

  if [ "$status" -ne 0 ]; then
    set_error_state "$package_id" "invalid" "claude launch failed; see $launch_log"
    printf '%s\n' "$launch_output" >&2
    die "claude launch failed for $package_id"
  fi

  printf '%s\n' "$launch_output"
  session_id="$(printf '%s\n' "$launch_output" | awk '/backgrounded/ { print $2; exit }')"
  if [ -z "$session_id" ]; then
    set_error_state "$package_id" "invalid" "missing background session id; see $launch_log"
    die "missing background session id for $package_id"
  fi

  if ! claude logs "$session_id" >/dev/null 2>&1; then
    set_error_state "$package_id" "stale" "claude session $session_id logs are not readable; see $launch_log"
    die "claude session $session_id logs are not readable"
  fi

  if [ "$finalize" = "1" ]; then
    new_state="finalizing"
  else
    new_state="launched"
  fi
  set_state_fields "$package_id" "$new_state" "__NOW__" "__KEEP__" "$session_id" "$branch" "$worktree" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" ""
}

launch_ready_or_finalize() {
  local bad id launched active finalize_id finalize_state
  preflight_all
  bad="$(any_bad_terminal_state)"
  if [ -n "$bad" ]; then
    printf 'STOP: blocked/stale/invalid package present:\n%s\n' "$bad" >&2
    exit 1
  fi
  status_consistency_ok || exit 1

  if all_functional_completed; then
    finalize_id="$(finalize_package_id)"
    finalize_state="$(state_field "$finalize_id" state)"
    case "$finalize_state" in
      pending|ready)
        launch_package "$finalize_id"
        ;;
      finalizing|launched|in_progress)
        log "$finalize_id is already running: $finalize_state"
        ;;
      finalized)
        log "$finalize_id is already finalized"
        ;;
      *)
        die "$finalize_id state is $finalize_state"
        ;;
    esac
    print_agents_command
    return
  fi

  launched=0
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    if [ "$(running_count)" -ge "$MAX_PARALLEL" ]; then
      break
    fi
    launch_package "$id"
    launched=$((launched + 1))
  done < <(ready_packages)

  if [ "$launched" -eq 0 ]; then
    active="$(awk -F '\t' 'FNR > 1 && ($2 == "launched" || $2 == "in_progress" || $2 == "finalizing") { print $1 ":" $2 }' "$STATE")"
    if [ -n "$active" ]; then
      printf 'No ready packages to launch. Active packages:\n%s\n' "$active"
    else
      printf 'No ready packages to launch.\n'
    fi
  fi
  print_agents_command
}

print_agents_command() {
  printf '\nView background sessions with:\n'
  printf '  claude agents --cwd "%s"\n' "$REPO_ROOT"
}

cmd_status() {
  preflight_all
  printf '%-36s %-12s %-54s %-22s %-14s %s\n' "PACKAGE" "STATE" "BRANCH" "VERIFICATION" "INTEGRATION" "LAST_ERROR"
  awk -F '\t' 'FNR > 1 {
    printf "%-36s %-12s %-54s %-22s %-14s %s\n", $1, $2, $6, $10, $11, $13
  }' "$STATE"
  if status_consistency_ok; then
    printf '\nCoordinator consistency: ok\n'
  else
    printf '\nCoordinator consistency: invalid\n'
    exit 1
  fi
}

cmd_mark_state() {
  local package_id="${1:-}"
  local new_state="${2:-}"
  local base="__KEEP__" commit="__KEEP__" verification="__KEEP__" integration="__KEEP__" cleanup="__KEEP__" error="__KEEP__"
  [ -n "$package_id" ] || die "usage: mark-state <package-id> <state>"
  [ -n "$new_state" ] || die "usage: mark-state <package-id> <state>"
  shift 2 || true
  while [ "$#" -gt 0 ]; do
    case "$1" in
      --base) shift; base="${1:-}" ;;
      --commit) shift; commit="${1:-}" ;;
      --verification) shift; verification="${1:-}" ;;
      --integration) shift; integration="${1:-}" ;;
      --cleanup) shift; cleanup="${1:-}" ;;
      --error) shift; error="${1:-}" ;;
      *) die "unknown mark-state option: $1" ;;
    esac
    shift || true
  done
  preflight_all
  acquire_lock
  set_state_fields "$package_id" "$new_state" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "$base" "$commit" "$verification" "$integration" "$cleanup" "$error"
  log "marked $package_id as $new_state"
}

cmd_repair_state() {
  local tmp id branch worktree md_state
  preflight_graph
  acquire_lock
  tmp="$(mktemp)"
  printf '%s\n' "$STATE_HEADER" > "$tmp"
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    branch="$(graph_field "$id" branch)"
    worktree="$(graph_field "$id" worktree)"
    md_state="$(markdown_status "$id")"
    valid_state "$md_state" || md_state="pending"
    printf '%s\t%s\t\t\t\t%s\t%s\t\t\tpending\tpending\tpending\t\n' "$id" "$md_state" "$branch" "$worktree" >> "$tmp"
  done < <(all_package_ids)
  mv "$tmp" "$STATE"
  log "repaired state ledger from graph and markdown status files"
}

cmd_retry() {
  local package_id="$1"
  local state
  [ -n "$package_id" ] || die "usage: retry <package-id>"
  preflight_all
  graph_field "$package_id" package_doc >/dev/null || die "unknown package: $package_id"
  state="$(state_field "$package_id" state)"
  case "$state" in
    blocked|stale|invalid)
      acquire_lock
      log "retrying $package_id from state $state"
      set_state_fields "$package_id" "pending" "" "" "" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "pending" "pending" "pending" ""
      launch_package "$package_id"
      ;;
    *)
      die "retry only supports blocked, stale, or invalid packages; $package_id is $state"
      ;;
  esac
}

cmd_finalize() {
  preflight_all
  acquire_lock
  status_consistency_ok || exit 1
  if ! all_functional_completed; then
    die "cannot finalize until all functional packages are completed"
  fi
  launch_package "$(finalize_package_id)"
  print_agents_command
}

cmd_doctor() {
  preflight_all
  status_consistency_ok
  printf 'doctor: ok\n'
}

usage() {
  cat <<USAGE
Usage: bash launchers/orchestrate.sh <command>

Commands:
  start
  advance [--from <package-id>]
  status
  retry <package-id>
  finalize
  mark-state <package-id> <state> [--base <sha>] [--commit <sha>] [--verification <text>] [--integration <text>] [--cleanup <text>] [--error <text>]
  repair-state
  doctor
USAGE
}

case "${1:-}" in
  start)
    acquire_lock
    launch_ready_or_finalize
    ;;
  advance)
    shift || true
    if [ "${1:-}" = "--from" ]; then
      log "advance requested from ${2:-unknown}"
    fi
    acquire_lock
    launch_ready_or_finalize
    ;;
  status)
    cmd_status
    ;;
  retry)
    shift || true
    cmd_retry "${1:-}"
    ;;
  finalize)
    cmd_finalize
    ;;
  mark-state)
    shift || true
    cmd_mark_state "$@"
    ;;
  repair-state)
    cmd_repair_state
    ;;
  doctor)
    cmd_doctor
    ;;
  *)
    usage
    exit 1
    ;;
esac
