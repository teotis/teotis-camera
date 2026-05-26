#!/usr/bin/env bash
set -euo pipefail

# Deep Audit & Optimization Orchestration Script
# Usage: bash orchestrate.sh [start|advance|status|retry|finalize]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_ROOT="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$PLAN_ROOT/../../.." && pwd)"
GRAPH="$PLAN_ROOT/launchers/package-graph.tsv"
STATE="$PLAN_ROOT/status/state.tsv"
LOCK="$PLAN_ROOT/status/.orchestrate.lock"

ORCHESTRATION_MAX_PARALLEL=${ORCHESTRATION_MAX_PARALLEL:-10}

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Initialize state file if it doesn't exist
init_state() {
    if [[ ! -f "$STATE" ]]; then
        mkdir -p "$(dirname "$STATE")"
        echo -e "package_id\tstate\tlaunched_at\tcompleted_at\tagent\tbranch\tworktree\tbase_commit\tcommit_hash\tverification\tintegration\tcleanup\tlast_error" > "$STATE"
        # Read package IDs from graph (skip header)
        tail -n +2 "$GRAPH" | cut -f1 | while read -r pkg; do
            echo -e "$pkg\tpending" >> "$STATE"
        done
    fi
}

# Acquire lock
acquire_lock() {
    local lock_dir
    lock_dir="$(dirname "$LOCK")"
    mkdir -p "$lock_dir"
    local attempts=0
    while [[ $attempts -lt 30 ]]; do
        if mkdir "$LOCK" 2>/dev/null; then
            return 0
        fi
        sleep 1
        ((attempts++))
    done
    log_error "Could not acquire lock after 30 seconds"
    exit 1
}

# Release lock
release_lock() {
    rm -rf "$LOCK"
}

# Get package state
get_package_state() {
    local pkg="$1"
    awk -F'\t' -v pkg="$pkg" '$1 == pkg {print $2}' "$STATE"
}

# Set package state
set_package_state() {
    local pkg="$1"
    local new_state="$2"
    local temp_file
    temp_file=$(mktemp)
    awk -F'\t' -v pkg="$pkg" -v state="$new_state" '
        BEGIN {OFS="\t"}
        NR == 1 {print; next}
        $1 == pkg {$2 = state}
        {print}
    ' "$STATE" > "$temp_file"
    mv "$temp_file" "$STATE"
}

# Check if package is ready to launch
is_package_ready() {
    local pkg="$1"
    local deps
    deps=$(awk -F'\t' -v pkg="$pkg" '$1 == pkg {print $4}' "$GRAPH")

    if [[ -z "$deps" || "$deps" == "none" ]]; then
        return 0
    fi

    IFS=',' read -ra dep_array <<< "$deps"
    for dep in "${dep_array[@]}"; do
        dep=$(echo "$dep" | xargs)  # trim whitespace
        local dep_state
        dep_state=$(get_package_state "$dep")
        if [[ "$dep_state" != "completed" ]]; then
            return 1
        fi
    done
    return 0
}

# Count running agents
count_running_agents() {
    local count
    count=$(tail -n +2 "$STATE" | grep -c "launched\|in_progress" 2>/dev/null || echo "0")
    echo "$count" | tr -d '\n'
}

# Launch a package agent
launch_package() {
    local pkg="$1"
    local pkg_doc
    pkg_doc=$(awk -F'\t' -v pkg="$pkg" '$1 == pkg {print $2}' "$GRAPH")
    local status_file
    status_file=$(awk -F'\t' -v pkg="$pkg" '$1 == pkg {print $3}' "$GRAPH")
    local branch
    branch=$(awk -F'\t' -v pkg="$pkg" '$1 == pkg {print $7}' "$GRAPH")
    local worktree
    worktree=$(awk -F'\t' -v pkg="$pkg" '$1 == pkg {print $8}' "$GRAPH")

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Update state
    set_package_state "$pkg" "launched"

    # Create status file template
    mkdir -p "$(dirname "$PLAN_ROOT/$status_file")"
    cat > "$PLAN_ROOT/$status_file" << EOF
# Package: $pkg

## Status
- State: launched
- Launched at: $timestamp
- Agent: pending

## Evidence
- Worktree: pending
- Branch: pending
- Base commit: pending
- Commit hash: pending
- Changed files: pending
- Verification commands: pending
- Verification results: pending
- Risks: pending

## Analysis Results
[PENDING]

## Key Findings
[PENDING]

## Recommendations
[PENDING]

## Notes
- Launched by orchestrate.sh
- This is a pure analysis task - do NOT modify any source code
EOF

    # Launch Claude Code background agent
    local agent_name="deep-audit-$pkg"

    log_info "Launching agent: $agent_name"
    log_info "Package doc: $PLAN_ROOT/$pkg_doc"
    log_info "Status file: $PLAN_ROOT/$status_file"
    log_info "Branch: $branch"
    log_info "Worktree: $worktree"

    # Create worktree directory
    mkdir -p "$REPO_ROOT/$worktree"

    # Launch Claude Code in background
    cd "$REPO_ROOT"
    claude --bg --name "$agent_name" \
        --prompt "You are executing package $pkg for the Deep Audit & Optimization Orchestration.

Read the INDEX at $PLAN_ROOT/INDEX.md
Read your package doc at $PLAN_ROOT/$pkg_doc
Write your status to $PLAN_ROOT/$status_file
Update state at $PLAN_ROOT/status/state.tsv

**IMPORTANT**: This is a PURE ANALYSIS task. Do NOT modify any source code files. Only write your analysis findings to the status file.

After completing your analysis, run:
bash $PLAN_ROOT/launchers/orchestrate.sh advance --from $pkg" \
        &
}

# Start command
cmd_start() {
    log_info "Starting Deep Audit & Optimization Orchestration"
    log_info "Plan root: $PLAN_ROOT"
    log_info "Repo root: $REPO_ROOT"

    init_state
    acquire_lock

    local launched=0
    local running
    running=$(count_running_agents)

    # Find ready packages in wave 1
    while IFS=$'\t' read -r pkg _1 _2 _3 _4 wave _6 _7 _8 _9; do
        [[ "$pkg" == "package_id" ]] && continue  # skip header
        [[ "$wave" != "1" ]] && continue

        local pkg_state
        pkg_state=$(get_package_state "$pkg")

        if [[ "$pkg_state" == "pending" ]] && is_package_ready "$pkg"; then
            if [[ $running -lt $ORCHESTRATION_MAX_PARALLEL ]]; then
                launch_package "$pkg"
                ((launched++))
                ((running++))
            fi
        fi
    done < "$GRAPH"

    release_lock

    if [[ $launched -gt 0 ]]; then
        log_success "Launched $launched agents"
        log_info "Monitor agents with: claude agents --cwd $REPO_ROOT"
        log_info "Check status with: bash $PLAN_ROOT/launchers/orchestrate.sh status"
    else
        log_warn "No packages ready to launch"
    fi
}

# Advance command
cmd_advance() {
    local from_pkg="${1:-}"

    init_state
    acquire_lock

    log_info "Advancing orchestration${from_pkg:+ from $from_pkg}"

    # Check if all functional packages are completed
    local all_completed=true
    local any_blocked=false

    while IFS=$'\t' read -r pkg _1 _2 _3 _4 _5 _6 _7 _8 _9 finalize; do
        [[ "$pkg" == "package_id" ]] && continue  # skip header
        [[ "$finalize" == "1" ]] && continue  # skip finalize package

        local pkg_state
        pkg_state=$(get_package_state "$pkg")

        case "$pkg_state" in
            completed) ;;
            blocked|stale|invalid)
                any_blocked=true
                log_warn "Package $pkg is $pkg_state"
                ;;
            *)
                all_completed=false
                ;;
        esac
    done < "$GRAPH"

    if [[ "$any_blocked" == "true" ]]; then
        release_lock
        log_error "Some packages are blocked. Run 'status' for details."
        exit 1
    fi

    if [[ "$all_completed" == "true" ]]; then
        log_info "All functional packages completed. Launching finalize."
        launch_package "99-finalize"
        release_lock
        return
    fi

    # Launch ready packages
    local launched=0
    local running
    running=$(count_running_agents)

    while IFS=$'\t' read -r pkg _1 _2 _3 _4 wave _6 _7 _8 _9 finalize; do
        [[ "$pkg" == "package_id" ]] && continue  # skip header
        [[ "$finalize" == "1" ]] && continue  # skip finalize for now

        local pkg_state
        pkg_state=$(get_package_state "$pkg")

        if [[ "$pkg_state" == "pending" ]] && is_package_ready "$pkg"; then
            if [[ $running -lt $ORCHESTRATION_MAX_PARALLEL ]]; then
                launch_package "$pkg"
                ((launched++))
                ((running++))
            fi
        fi
    done < "$GRAPH"

    release_lock

    if [[ $launched -gt 0 ]]; then
        log_success "Launched $launched agents"
        log_info "Monitor agents with: claude agents --cwd $REPO_ROOT"
    else
        log_info "No new packages ready to launch"
    fi
}

# Status command
cmd_status() {
    init_state

    echo ""
    echo "=== Deep Audit & Optimization Orchestration Status ==="
    echo ""
    printf "%-40s %-15s %-25s %-25s\n" "Package" "State" "Launched" "Completed"
    printf "%-40s %-15s %-25s %-25s\n" "--------" "-----" "--------" "---------"

    while IFS=$'\t' read -r pkg state launched completed agent branch worktree base_commit commit_hash verification integration cleanup last_error; do
        [[ "$pkg" == "package_id" ]] && continue  # skip header

        local launched_display="${launched:--}"
        local completed_display="${completed:--}"

        # Color based on state
        local color="$NC"
        case "$state" in
            completed|finalized) color="$GREEN" ;;
            launched|in_progress|ready) color="$BLUE" ;;
            blocked|stale|invalid) color="$RED" ;;
            *) color="$YELLOW" ;;
        esac

        printf "${color}%-40s %-15s %-25s %-25s${NC}\n" \
            "$pkg" "$state" "$launched_display" "$completed_display"
    done < "$STATE"

    echo ""
}

# Retry command
cmd_retry() {
    local pkg="$1"

    if [[ -z "$pkg" ]]; then
        log_error "Usage: orchestrate.sh retry <package-id>"
        exit 1
    fi

    init_state
    acquire_lock

    local pkg_state
    pkg_state=$(get_package_state "$pkg")

    case "$pkg_state" in
        blocked|stale|invalid)
            log_info "Retrying package $pkg (was $pkg_state)"
            set_package_state "$pkg" "pending"
            release_lock
            cmd_advance
            ;;
        *)
            release_lock
            log_error "Cannot retry package $pkg in state $pkg_state"
            log_error "Only blocked, stale, or invalid packages can be retried"
            exit 1
            ;;
    esac
}

# Finalize command
cmd_finalize() {
    init_state
    acquire_lock

    log_info "Running finalize"

    # Check if finalize is already running or completed
    local finalize_state
    finalize_state=$(get_package_state "99-finalize")

    case "$finalize_state" in
        completed|finalized)
            release_lock
            log_info "Finalize already completed"
            return
            ;;
        launched|in_progress)
            release_lock
            log_info "Finalize already running"
            return
            ;;
    esac

    # Launch finalize
    launch_package "99-finalize"
    release_lock

    log_success "Launched finalize agent"
    log_info "Monitor with: claude agents --cwd $REPO_ROOT"
}

# Main entry point
main() {
    local command="${1:-}"
    shift || true

    case "$command" in
        start)
            cmd_start
            ;;
        advance)
            cmd_advance "$@"
            ;;
        status)
            cmd_status
            ;;
        retry)
            cmd_retry "$@"
            ;;
        finalize)
            cmd_finalize
            ;;
        *)
            echo "Usage: orchestrate.sh [start|advance|status|retry|finalize]"
            echo ""
            echo "Commands:"
            echo "  start     - Launch first wave of ready packages"
            echo "  advance   - Launch next ready packages (called by package agents)"
            echo "  status    - Show current orchestration status"
            echo "  retry     - Retry a blocked/stale/invalid package"
            echo "  finalize  - Run or re-run the finalize package"
            exit 1
            ;;
    esac
}

main "$@"
