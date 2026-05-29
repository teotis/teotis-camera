#!/usr/bin/env bash
# orchestrate-template v1.0.0 — see skills/agent-orchestration-planner/scripts/orchestrate-template.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
GRAPH="$PLAN_ROOT/launchers/package-graph.tsv"
STATE="$PLAN_ROOT/status/state.tsv"
EVENTS="$PLAN_ROOT/status/events.jsonl"
SCRATCH="$PLAN_ROOT/scratch"
PROMPTS="$PLAN_ROOT/launchers/agent-prompts.md"
LOCK_DIR="$PLAN_ROOT/status/.orchestrate.lock"
MAX_PARALLEL="${ORCHESTRATION_MAX_PARALLEL:-10}"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
STATE_HEADER="package_id	state	launched_at	completed_at	agent	branch	worktree	base_commit	commit_hash	verification	integration	cleanup	last_error	failed_command	conflict_files	log_summary	recovery_hint"
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

json_escape() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\t'/\\t}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  printf '%s' "$value"
}

json_pair() {
  local key="$1"
  local value="$2"
  printf ',"%s":"%s"' "$(json_escape "$key")" "$(json_escape "$value")"
}

tsv_safe() {
  local value="$1"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  value="${value//$'\r'/ }"
  printf '%s' "$value" | sed 's/[[:space:]][[:space:]]*/ /g; s/^[[:space:]]*//; s/[[:space:]]*$//'
}

emit_event() {
  local event="$1"
  local package_id="${2:-}"
  local extra="${3:-}"
  mkdir -p "$(dirname "$EVENTS")"
  printf '{"ts":"%s","event":"%s","package_id":"%s"%s}\n' \
    "$(timestamp)" "$(json_escape "$event")" "$(json_escape "$package_id")" "$extra" >> "$EVENTS"
}

ensure_scratch_root() {
  mkdir -p "$SCRATCH"
  if [ ! -f "$SCRATCH/.gitignore" ]; then
    {
      printf '*\n'
      printf '!.gitignore\n'
    } > "$SCRATCH/.gitignore"
  fi
}

scratch_path_for() {
  local package_id="$1"
  printf '%s/%s\n' "$SCRATCH" "$package_id"
}

failure_fingerprint() {
  local message="$1"
  message="${message%%; see *}"
  message="${message//$'\t'/ }"
  message="${message//$'\n'/ }"
  printf '%s' "$message" | sed 's/[[:space:]][[:space:]]*/ /g; s/^[[:space:]]*//; s/[[:space:]]*$//'
}

terminal_failure_count() {
  local package_id="$1"
  local fingerprint="$2"
  local package_json fingerprint_json line count=0
  [ -f "$EVENTS" ] || {
    printf '0\n'
    return
  }
  package_json="$(json_escape "$package_id")"
  fingerprint_json="$(json_escape "$fingerprint")"
  while IFS= read -r line; do
    if [[ "$line" == *'"event":"terminal_failure"'* ]] &&
      [[ "$line" == *"\"package_id\":\"$package_json\""* ]] &&
      [[ "$line" == *"\"fingerprint\":\"$fingerprint_json\""* ]]; then
      count=$((count + 1))
    fi
  done < "$EVENTS"
  printf '%s\n' "$count"
}

enforce_retry_breaker() {
  local package_id="$1"
  local last_error fingerprint count
  last_error="$(state_field "$package_id" last_error || true)"
  [ -n "$last_error" ] && [ "$last_error" != "pending" ] || return 0
  fingerprint="$(failure_fingerprint "$last_error")"
  [ -n "$fingerprint" ] || return 0
  count="$(terminal_failure_count "$package_id" "$fingerprint")"
  if [ "$count" -ge 3 ]; then
    emit_event "retry_blocked" "$package_id" "$(json_pair "fingerprint" "$fingerprint")$(json_pair "failure_count" "$count")"
    die "retry breaker open for $package_id after $count repeated failures: $fingerprint"
  fi
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
    pending|ready|manual_required|launched|in_progress|completed|blocked|stale|invalid|finalizing|finalized)
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
      if (NF != 17) {
        printf("state row has %d fields, expected 17: %s\n", NF, $0) > "/dev/stderr"
        bad = 1
      }
      if (!($1 in ids)) {
        printf("state has unknown package: %s\n", $1) > "/dev/stderr"
        bad = 1
      }
      seen[$1] = 1
      if ($2 !~ /^(pending|ready|manual_required|launched|in_progress|completed|blocked|stale|invalid|finalizing|finalized)$/) {
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
  local failed_command="${14:-__KEEP__}"
  local conflict_files="${15:-__KEEP__}"
  local log_summary="${16:-__KEEP__}"
  local recovery_hint="${17:-__KEEP__}"
  local now tmp old_state event_extra

  valid_state "$new_state" || die "invalid state: $new_state"
  old_state="$(state_field "$package_id" state || true)"
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
    -v last_error="$last_error" \
    -v failed_command="$failed_command" \
    -v conflict_files="$conflict_files" \
    -v log_summary="$log_summary" \
    -v recovery_hint="$recovery_hint" '
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
        else if (new_state == "completed" || new_state == "finalized") $idx["last_error"] = ""
        if (failed_command != "__KEEP__") $idx["failed_command"] = failed_command
        else if (new_state == "completed" || new_state == "finalized") $idx["failed_command"] = ""
        if (conflict_files != "__KEEP__") $idx["conflict_files"] = conflict_files
        else if (new_state == "completed" || new_state == "finalized") $idx["conflict_files"] = ""
        if (log_summary != "__KEEP__") $idx["log_summary"] = log_summary
        else if (new_state == "completed" || new_state == "finalized") $idx["log_summary"] = ""
        if (recovery_hint != "__KEEP__") $idx["recovery_hint"] = recovery_hint
        else if (new_state == "completed" || new_state == "finalized") $idx["recovery_hint"] = ""
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
  event_extra="$(json_pair "old_state" "$old_state")$(json_pair "new_state" "$new_state")"
  if [ "$last_error" != "__KEEP__" ]; then
    event_extra="$event_extra$(json_pair "last_error" "$last_error")"
  fi
  if [ "$failed_command" != "__KEEP__" ]; then
    event_extra="$event_extra$(json_pair "failed_command" "$failed_command")"
  fi
  if [ "$conflict_files" != "__KEEP__" ]; then
    event_extra="$event_extra$(json_pair "conflict_files" "$conflict_files")"
  fi
  if [ "$log_summary" != "__KEEP__" ]; then
    event_extra="$event_extra$(json_pair "log_summary" "$log_summary")"
  fi
  if [ "$recovery_hint" != "__KEEP__" ]; then
    event_extra="$event_extra$(json_pair "recovery_hint" "$recovery_hint")"
  fi
  emit_event "state_changed" "$package_id" "$event_extra"
}

set_error_state() {
  local package_id="$1"
  local new_state="$2"
  local message="$3"
  local failed_command="${4:-}"
  local conflict_files="${5:-}"
  local log_summary="${6:-}"
  local recovery_hint="${7:-}"
  local fingerprint
  message="$(tsv_safe "$message")"
  failed_command="$(tsv_safe "$failed_command")"
  conflict_files="$(tsv_safe "$conflict_files")"
  log_summary="$(tsv_safe "$log_summary")"
  recovery_hint="$(tsv_safe "$recovery_hint")"
  set_state_fields "$package_id" "$new_state" "__KEEP__" "__NOW__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "$message" "$failed_command" "$conflict_files" "$log_summary" "$recovery_hint"
  fingerprint="$(failure_fingerprint "$message")"
  emit_event "terminal_failure" "$package_id" "$(json_pair "state" "$new_state")$(json_pair "error" "$message")$(json_pair "fingerprint" "$fingerprint")$(json_pair "failed_command" "$failed_command")$(json_pair "conflict_files" "$conflict_files")$(json_pair "log_summary" "$log_summary")$(json_pair "recovery_hint" "$recovery_hint")"
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
  local id state manual
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    state="$(state_field "$id" state)"
    manual="$(graph_field "$id" manual)"
    if { [ "$state" = "pending" ] || [ "$state" = "ready" ]; } && [ "$manual" != "1" ] && deps_completed "$id"; then
      printf '%s\n' "$id"
    fi
  done < <(functional_package_ids)
}

mark_ready_manual_packages() {
  local id state manual
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    state="$(state_field "$id" state)"
    manual="$(graph_field "$id" manual)"
    if { [ "$state" = "pending" ] || [ "$state" = "ready" ] || [ "$state" = "manual_required" ]; } &&
      [ "$manual" = "1" ] && deps_completed "$id"; then
      if [ "$state" != "manual_required" ]; then
        set_state_fields "$id" "manual_required" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" ""
      fi
      log "manual package ready: $id"
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

validate_permission_mode() {
  case "$CLAUDE_PERMISSION_MODE" in
    ""|default|acceptEdits|plan)
      return 0
      ;;
    auto)
      [ "${CLAUDE_AUTO_MODE_OPTED_IN:-}" = "1" ] ||
        die "CLAUDE_PERMISSION_MODE=auto requires CLAUDE_AUTO_MODE_OPTED_IN=1 after running claude --permission-mode auto interactively"
      ;;
    bypassPermissions)
      [ "${CLAUDE_BYPASS_PERMISSIONS_APPROVED:-}" = "1" ] ||
        die "CLAUDE_PERMISSION_MODE=bypassPermissions requires CLAUDE_BYPASS_PERMISSIONS_APPROVED=1; this repo must not silently grant bypass permissions"
      ;;
    *)
      die "unsupported CLAUDE_PERMISSION_MODE: $CLAUDE_PERMISSION_MODE"
      ;;
  esac
}

parse_session_id() {
  awk '
    /backgrounded/ {
      for (i = 1; i <= NF; i++) {
        if ($i == "backgrounded" || $i == "·" || $i == "-" || $i == "•") continue
        if ($i ~ /^[[:alnum:]_-]{6,}$/) {
          print $i
          exit
        }
      }
    }
  '
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
    set_error_state "$package_id" "invalid" "prompt section missing" "prompt_for_package $package_id" "" "agent-prompts.md has no matching package section" "Regenerate launchers/agent-prompts.md for this package."
    die "prompt section missing for $package_id"
  fi

  if ! command -v claude >/dev/null 2>&1; then
    rm -f "$prompt_file"
    set_error_state "$package_id" "invalid" "claude command not found" "command -v claude" "" "Claude CLI is unavailable in PATH" "Run doctor --environment in the same shell and fix PATH before retry."
    die "claude command not found"
  fi

  validate_permission_mode
  version_output="$(claude --version 2>&1 || true)"
  log "claude: $version_output"
  ensure_worktree "$package_id"
  log "launching $package_id"
  log "  branch: $branch"
  log "  worktree: $worktree"
  emit_event "launch_requested" "$package_id" "$(json_pair "branch" "$branch")$(json_pair "worktree" "$worktree")$(json_pair "finalize" "$finalize")"

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
    set_error_state "$package_id" "invalid" "claude launch failed; see $launch_log" "claude --bg --name $name" "" "Launch output saved to $launch_log" "Inspect the launch log and Claude environment, then retry."
    printf '%s\n' "$launch_output" >&2
    die "claude launch failed for $package_id"
  fi

  printf '%s\n' "$launch_output"
  session_id="$(printf '%s\n' "$launch_output" | parse_session_id)"
  if [ -z "$session_id" ]; then
    set_error_state "$package_id" "invalid" "missing background session id; see $launch_log" "parse_session_id" "" "Launch output did not contain a parseable background session id; see $launch_log" "Check the Claude CLI output format before retry."
    die "missing background session id for $package_id"
  fi

  if ! claude logs "$session_id" >/dev/null 2>&1; then
    set_error_state "$package_id" "stale" "claude session $session_id logs are not readable; see $launch_log" "claude logs $session_id" "" "Session was launched but logs could not be read" "Open claude agents/logs manually, then mark completed or retry."
    die "claude session $session_id logs are not readable"
  fi

  if [ "$finalize" = "1" ]; then
    new_state="finalizing"
  else
    new_state="launched"
  fi
  set_state_fields "$package_id" "$new_state" "__NOW__" "__KEEP__" "$session_id" "$branch" "$worktree" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__"
  emit_event "launch_succeeded" "$package_id" "$(json_pair "session_id" "$session_id")$(json_pair "new_state" "$new_state")$(json_pair "branch" "$branch")$(json_pair "worktree" "$worktree")"
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
  mark_ready_manual_packages

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
  printf '%-36s %-12s %-54s %-22s %-14s %-30s %-30s %s\n' "PACKAGE" "STATE" "BRANCH" "VERIFICATION" "INTEGRATION" "LAST_ERROR" "FAILED_COMMAND" "RECOVERY_HINT"
  awk -F '\t' 'FNR > 1 {
    printf "%-36s %-12s %-54s %-22s %-14s %-30s %-30s %s\n", $1, $2, $6, $10, $11, $13, $14, $17
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
  local failed_command="__KEEP__" conflict_files="__KEEP__" log_summary="__KEEP__" recovery_hint="__KEEP__"
  local fingerprint event_failed_command event_conflict_files event_log_summary event_recovery_hint
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
      --failed-command) shift; failed_command="${1:-}" ;;
      --conflict-files) shift; conflict_files="${1:-}" ;;
      --log-summary) shift; log_summary="${1:-}" ;;
      --recovery-hint) shift; recovery_hint="${1:-}" ;;
      *) die "unknown mark-state option: $1" ;;
    esac
    shift || true
  done
  preflight_all
  acquire_lock
  [ "$verification" = "__KEEP__" ] || verification="$(tsv_safe "$verification")"
  [ "$integration" = "__KEEP__" ] || integration="$(tsv_safe "$integration")"
  [ "$cleanup" = "__KEEP__" ] || cleanup="$(tsv_safe "$cleanup")"
  [ "$error" = "__KEEP__" ] || error="$(tsv_safe "$error")"
  [ "$failed_command" = "__KEEP__" ] || failed_command="$(tsv_safe "$failed_command")"
  [ "$conflict_files" = "__KEEP__" ] || conflict_files="$(tsv_safe "$conflict_files")"
  [ "$log_summary" = "__KEEP__" ] || log_summary="$(tsv_safe "$log_summary")"
  [ "$recovery_hint" = "__KEEP__" ] || recovery_hint="$(tsv_safe "$recovery_hint")"
  set_state_fields "$package_id" "$new_state" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "$base" "$commit" "$verification" "$integration" "$cleanup" "$error" "$failed_command" "$conflict_files" "$log_summary" "$recovery_hint"
  if { [ "$new_state" = "blocked" ] || [ "$new_state" = "stale" ] || [ "$new_state" = "invalid" ]; } &&
    [ "$error" != "__KEEP__" ] && [ -n "$error" ]; then
    fingerprint="$(failure_fingerprint "$error")"
    [ "$failed_command" = "__KEEP__" ] && event_failed_command="" || event_failed_command="$failed_command"
    [ "$conflict_files" = "__KEEP__" ] && event_conflict_files="" || event_conflict_files="$conflict_files"
    [ "$log_summary" = "__KEEP__" ] && event_log_summary="" || event_log_summary="$log_summary"
    [ "$recovery_hint" = "__KEEP__" ] && event_recovery_hint="" || event_recovery_hint="$recovery_hint"
    emit_event "terminal_failure" "$package_id" "$(json_pair "state" "$new_state")$(json_pair "error" "$error")$(json_pair "fingerprint" "$fingerprint")$(json_pair "failed_command" "$event_failed_command")$(json_pair "conflict_files" "$event_conflict_files")$(json_pair "log_summary" "$event_log_summary")$(json_pair "recovery_hint" "$event_recovery_hint")"
  fi
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
    printf '%s\t%s\t\t\t\t%s\t%s\t\t\tpending\tpending\tpending\t\t\t\t\t\n' "$id" "$md_state" "$branch" "$worktree" >> "$tmp"
  done < <(all_package_ids)
  mv "$tmp" "$STATE"
  log "repaired state ledger from graph and markdown status files"
}

cmd_retry() {
  local package_id="$1"
  local state last_error failed_command conflict_files log_summary recovery_hint
  [ -n "$package_id" ] || die "usage: retry <package-id>"
  preflight_all
  graph_field "$package_id" package_doc >/dev/null || die "unknown package: $package_id"
  state="$(state_field "$package_id" state)"
  case "$state" in
    blocked|stale|invalid)
      acquire_lock
      enforce_retry_breaker "$package_id"
      last_error="$(state_field "$package_id" last_error || true)"
      failed_command="$(state_field "$package_id" failed_command || true)"
      conflict_files="$(state_field "$package_id" conflict_files || true)"
      log_summary="$(state_field "$package_id" log_summary || true)"
      recovery_hint="$(state_field "$package_id" recovery_hint || true)"
      log "retrying $package_id from state $state"
      if [ -n "$last_error$failed_command$conflict_files$log_summary$recovery_hint" ]; then
        log "prior failure context:"
        log "  last_error: ${last_error:-none}"
        log "  failed_command: ${failed_command:-none}"
        log "  conflict_files: ${conflict_files:-none}"
        log "  log_summary: ${log_summary:-none}"
        log "  recovery_hint: ${recovery_hint:-none}"
      fi
      emit_event "retry_requested" "$package_id" "$(json_pair "from_state" "$state")$(json_pair "last_error" "$last_error")$(json_pair "failed_command" "$failed_command")$(json_pair "conflict_files" "$conflict_files")$(json_pair "log_summary" "$log_summary")$(json_pair "recovery_hint" "$recovery_hint")"
      set_state_fields "$package_id" "pending" "" "" "" "__KEEP__" "__KEEP__" "__KEEP__" "__KEEP__" "pending" "pending" "pending"
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
  if [ "${1:-}" = "--environment" ]; then
    printf 'repo_root=%s\n' "$REPO_ROOT"
    printf 'plan_root=%s\n' "$PLAN_ROOT"
    if command -v claude >/dev/null 2>&1; then
      printf 'claude_path=%s\n' "$(command -v claude)"
      printf 'claude_version=%s\n' "$(claude --version 2>&1 || true)"
      if claude agents --help >/dev/null 2>&1; then
        printf 'claude_agents_help=available\n'
      else
        printf 'claude_agents_help=unavailable\n'
      fi
    else
      printf 'claude_path=missing\n'
      printf 'claude_version=missing\n'
      printf 'claude_agents_help=unavailable\n'
    fi
    printf 'permission_mode=%s\n' "${CLAUDE_PERMISSION_MODE:-default}"
    printf 'setting_sources=%s\n' "$CLAUDE_SETTING_SOURCES"
    return 0
  fi
  preflight_all
  status_consistency_ok
  printf 'doctor: ok\n'
}

cmd_scratch_path() {
  local package_id="${1:-}"
  local path
  [ -n "$package_id" ] || die "usage: scratch-path <package-id>"
  preflight_graph
  graph_field "$package_id" package_doc >/dev/null || die "unknown package: $package_id"
  ensure_scratch_root
  path="$(scratch_path_for "$package_id")"
  mkdir -p "$path"
  emit_event "scratch_path_requested" "$package_id" "$(json_pair "path" "$path")"
  printf '%s\n' "$path"
}

verify_package_evidence() {
  local package_id="$1"
  local state branch worktree commit bad=0
  state="$(state_field "$package_id" state)"
  branch="$(state_field "$package_id" branch)"
  worktree="$(state_field "$package_id" worktree)"
  commit="$(state_field "$package_id" commit_hash)"

  if [ "$state" != "completed" ] && [ "$state" != "finalized" ]; then
    printf '%s not completed: %s\n' "$package_id" "$state" >&2
    bad=1
  fi
  if [ -z "$commit" ] || [ "$commit" = "pending" ]; then
    printf '%s missing commit_hash\n' "$package_id" >&2
    bad=1
  elif ! git -C "$REPO_ROOT" cat-file -e "$commit^{commit}" >/dev/null 2>&1; then
    printf '%s commit_hash does not exist: %s\n' "$package_id" "$commit" >&2
    bad=1
  fi
  if [ -z "$branch" ] || [ "$branch" = "pending" ]; then
    printf '%s missing branch\n' "$package_id" >&2
    bad=1
  elif ! git -C "$REPO_ROOT" rev-parse --verify "$branch" >/dev/null 2>&1; then
    printf '%s branch does not exist: %s\n' "$package_id" "$branch" >&2
    bad=1
  fi
  if [ -n "$worktree" ] && [ "$worktree" != "pending" ] &&
    git -C "$worktree" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    if [ -n "$(git -C "$worktree" status --porcelain)" ]; then
      printf '%s worktree is dirty: %s\n' "$package_id" "$worktree" >&2
      bad=1
    fi
  fi
  return "$bad"
}

cmd_verify_package() {
  local package_id="${1:-}"
  [ -n "$package_id" ] || die "usage: verify-package <package-id>"
  preflight_all
  status_consistency_ok
  verify_package_evidence "$package_id"
}

cmd_verify_finalize() {
  local id bad=0
  preflight_all
  status_consistency_ok
  while IFS= read -r id; do
    [ -n "$id" ] || continue
    if ! verify_package_evidence "$id"; then
      bad=1
    fi
  done < <(functional_package_ids)
  [ "$bad" -eq 0 ] || exit 1
  printf 'verify-finalize: ok\n'
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
  mark-state <package-id> <state> [--base <sha>] [--commit <sha>] [--verification <text>] [--integration <text>] [--cleanup <text>] [--error <text>] [--failed-command <text>] [--conflict-files <text>] [--log-summary <text>] [--recovery-hint <text>]
  repair-state
  doctor [--environment]
  verify-package <package-id>
  verify-finalize
  scratch-path <package-id>
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
    shift || true
    cmd_doctor "$@"
    ;;
  verify-package)
    shift || true
    cmd_verify_package "${1:-}"
    ;;
  verify-finalize)
    cmd_verify_finalize
    ;;
  scratch-path)
    shift || true
    cmd_scratch_path "${1:-}"
    ;;
  *)
    usage
    exit 1
    ;;
esac
