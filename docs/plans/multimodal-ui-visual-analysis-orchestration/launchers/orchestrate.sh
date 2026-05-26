#!/bin/bash

# Multimodal UI Visual Analysis Orchestration Script
# This script manages the execution of multimodal analysis agents

set -e

# Get the absolute path of the script
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(dirname "$SCRIPT_DIR")"
REPO_ROOT="$(cd "$PLAN_DIR/../.." && pwd)"

# Configuration
ORCHESTRATION_MAX_PARALLEL=${ORCHESTRATION_MAX_PARALLEL:-10}
GRAPH_FILE="$PLAN_DIR/launchers/package-graph.tsv"
STATE_FILE="$PLAN_DIR/status/state.tsv"
LOCK_FILE="$PLAN_DIR/status/.orchestrate.lock"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Acquire lock
acquire_lock() {
    if [ -f "$LOCK_FILE" ]; then
        local lock_pid=$(cat "$LOCK_FILE")
        if kill -0 "$lock_pid" 2>/dev/null; then
            log_error "Another orchestration is running (PID: $lock_pid)"
            exit 1
        else
            log_warning "Stale lock file found, removing"
            rm -f "$LOCK_FILE"
        fi
    fi
    echo $$ > "$LOCK_FILE"
}

# Release lock
release_lock() {
    rm -f "$LOCK_FILE"
}

# Get package status
get_package_status() {
    local package_id=$1
    awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $2}' "$STATE_FILE"
}

# Set package status
set_package_status() {
    local package_id=$1
    local status=$2
    local temp_file=$(mktemp)
    awk -F'\t' -v pkg="$package_id" -v st="$status" '{
        if ($1 == pkg) {
            $2 = st
        }
        print
    }' "$STATE_FILE" > "$temp_file"
    mv "$temp_file" "$STATE_FILE"
}

# Check if package is ready
is_package_ready() {
    local package_id=$1
    local status=$(get_package_status "$package_id")

    if [ "$status" != "pending" ]; then
        return 1
    fi

    # Check dependencies
    local deps=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $4}' "$GRAPH_FILE")
    if [ -z "$deps" ]; then
        return 0
    fi

    IFS=',' read -ra DEP_ARRAY <<< "$deps"
    for dep in "${DEP_ARRAY[@]}"; do
        local dep_status=$(get_package_status "$dep")
        if [ "$dep_status" != "completed" ]; then
            return 1
        fi
    done

    return 0
}

# Launch package agent
launch_package() {
    local package_id=$1
    local package_doc=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $2}' "$GRAPH_FILE")
    local prompt_file="$PLAN_DIR/launchers/agent-prompts.md"

    log_info "Launching package: $package_id"

    # Extract prompt for this package
    local prompt=$(sed -n "/^## Package: ${package_id}/,/^## Package:/p" "$prompt_file" | head -n -1)

    # Launch Claude Code background agent
    claude --bg --name "multimodal-${package_id}" \
        --directory "$REPO_ROOT" \
        "$prompt"

    # Update state
    set_package_status "$package_id" "launched"

    log_success "Package $package_id launched"
}

# Start orchestration
start() {
    log_info "Starting multimodal UI visual analysis orchestration"

    acquire_lock

    # Check if all packages are already completed
    local all_completed=true
    while IFS=$'\t' read -r package_id rest; do
        if [ "$package_id" = "package_id" ]; then
            continue
        fi
        local status=$(get_package_status "$package_id")
        if [ "$status" != "completed" ]; then
            all_completed=false
            break
        fi
    done < "$GRAPH_FILE"

    if [ "$all_completed" = true ]; then
        log_success "All packages already completed"
        release_lock
        return
    fi

    # Find and launch ready packages (Wave 1)
    local launched=0
    while IFS=$'\t' read -r package_id rest; do
        if [ "$package_id" = "package_id" ]; then
            continue
        fi

        local wave=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $6}' "$GRAPH_FILE")
        if [ "$wave" != "1" ]; then
            continue
        fi

        if is_package_ready "$package_id"; then
            launch_package "$package_id"
            launched=$((launched + 1))

            if [ $launched -ge $ORCHESTRATION_MAX_PARALLEL ]; then
                log_warning "Reached max parallel limit ($ORCHESTRATION_MAX_PARALLEL)"
                break
            fi
        fi
    done < "$GRAPH_FILE"

    if [ $launched -eq 0 ]; then
        log_warning "No packages ready to launch"
    fi

    release_lock

    log_success "Orchestration started"
    echo ""
    echo "View agents with: claude agents"
    echo "Check status with: bash $PLAN_DIR/launchers/orchestrate.sh status"
}

# Advance orchestration
advance() {
    local from_package=$1

    log_info "Advancing orchestration"

    acquire_lock

    # Find and launch newly ready packages
    local launched=0
    while IFS=$'\t' read -r package_id rest; do
        if [ "$package_id" = "package_id" ]; then
            continue
        fi

        local status=$(get_package_status "$package_id")
        if [ "$status" = "completed" ]; then
            continue
        fi

        if is_package_ready "$package_id"; then
            launch_package "$package_id"
            launched=$((launched + 1))

            if [ $launched -ge $ORCHESTRATION_MAX_PARALLEL ]; then
                log_warning "Reached max parallel limit ($ORCHESTRATION_MAX_PARALLEL)"
                break
            fi
        fi
    done < "$GRAPH_FILE"

    # Check if all functional packages are completed (ready for finalize)
    local all_functional_completed=true
    while IFS=$'\t' read -r package_id rest; do
        if [ "$package_id" = "package_id" ]; then
            continue
        fi

        local finalize=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $10}' "$GRAPH_FILE")
        if [ "$finalize" = "1" ]; then
            continue
        fi

        local status=$(get_package_status "$package_id")
        if [ "$status" != "completed" ]; then
            all_functional_completed=false
            break
        fi
    done < "$GRAPH_FILE"

    if [ "$all_functional_completed" = true ]; then
        log_success "All functional packages completed, ready for finalize"
    fi

    release_lock

    if [ $launched -eq 0 ]; then
        log_warning "No new packages launched"
    fi

    log_success "Advance completed"
    echo ""
    echo "View agents with: claude agents"
    echo "Check status with: bash $PLAN_DIR/launchers/orchestrate.sh status"
}

# Show status
status() {
    log_info "Orchestration Status"
    echo ""
    echo "Package ID | Status | Branch | Verification | Integration | Last Error"
    echo "-----------+--------+--------+--------------+-------------+------------"

    while IFS=$'\t' read -r package_id rest; do
        if [ "$package_id" = "package_id" ]; then
            continue
        fi

        local status=$(get_package_status "$package_id")
        local branch=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $7}' "$GRAPH_FILE")
        local verification=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $10}' "$STATE_FILE")
        local integration=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $11}' "$STATE_FILE")
        local last_error=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $13}' "$STATE_FILE")

        echo "$package_id | $status | $branch | $verification | $integration | $last_error"
    done < "$GRAPH_FILE"
}

# Retry package
retry() {
    local package_id=$1

    if [ -z "$package_id" ]; then
        log_error "Package ID required"
        exit 1
    fi

    local status=$(get_package_status "$package_id")

    if [ "$status" != "blocked" ] && [ "$status" != "stale" ] && [ "$status" != "invalid" ]; then
        log_error "Package $package_id is not in a retryable state (current: $status)"
        exit 1
    fi

    log_info "Retrying package: $package_id"

    acquire_lock

    # Reset status to pending
    set_package_status "$package_id" "pending"

    # Launch the package
    launch_package "$package_id"

    release_lock

    log_success "Package $package_id retried"
    echo ""
    echo "View agents with: claude agents"
}

# Finalize orchestration
finalize() {
    log_info "Finalizing orchestration"

    # Check if all functional packages are completed
    local all_functional_completed=true
    while IFS=$'\t' read -r package_id rest; do
        if [ "$package_id" = "package_id" ]; then
            continue
        fi

        local finalize=$(awk -F'\t' -v pkg="$package_id" '$1 == pkg {print $10}' "$GRAPH_FILE")
        if [ "$finalize" = "1" ]; then
            continue
        fi

        local status=$(get_package_status "$package_id")
        if [ "$status" != "completed" ]; then
            all_functional_completed=false
            log_error "Package $package_id is not completed (status: $status)"
            break
        fi
    done < "$GRAPH_FILE"

    if [ "$all_functional_completed" = false ]; then
        log_error "Cannot finalize: not all functional packages are completed"
        exit 1
    fi

    # Launch finalize package
    launch_package "M99"

    log_success "Finalize launched"
    echo ""
    echo "View agents with: claude agents"
    echo "Check status with: bash $PLAN_DIR/launchers/orchestrate.sh status"
}

# Main
case "${1:-}" in
    start)
        start
        ;;
    advance)
        advance "$2"
        ;;
    status)
        status
        ;;
    retry)
        retry "$2"
        ;;
    finalize)
        finalize
        ;;
    *)
        echo "Usage: $0 {start|advance|status|retry|finalize}"
        echo ""
        echo "Commands:"
        echo "  start     - Start orchestration (launch Wave 1 packages)"
        echo "  advance   - Advance orchestration (launch newly ready packages)"
        echo "  status    - Show orchestration status"
        echo "  retry     - Retry a blocked/stale/invalid package"
        echo "  finalize  - Launch finalize package"
        exit 1
        ;;
esac
