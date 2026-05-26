# Status Files

This directory contains the status files for each package in the Deep Audit & Optimization Orchestration.

## Files

- `state.tsv` - Machine-readable state ledger (source of truth)
- `01-architecture-structural-analysis.md` - Status for architecture structural analysis
- `02-technical-debt-audit.md` - Status for technical debt audit
- `03-module-dependency-analysis.md` - Status for module dependency analysis
- `04-code-quality-metrics.md` - Status for code quality metrics
- `05-session-kernel-deep-dive.md` - Status for Session Kernel deep dive
- `06-performance-optimization-analysis.md` - Status for performance optimization analysis
- `07-test-coverage-gap-analysis.md` - Status for test coverage gap analysis
- `08-architecture-boundary-violations.md` - Status for architecture boundary violations
- `09-optimization-roadmap.md` - Status for optimization roadmap
- `10-executable-action-plan.md` - Status for executable action plan
- `99-finalize.md` - Status for finalization

## State Values

- `pending` - Not yet started
- `ready` - Dependencies met, ready to launch
- `launched` - Agent launched, waiting for work to begin
- `in_progress` - Agent actively working
- `completed` - Work completed successfully
- `blocked` - Work blocked by an issue
- `stale` - Work stale, needs retry
- `invalid` - Invalid state, needs intervention
- `finalizing` - Finalization in progress
- `finalized` - Finalization completed

## Usage

Package agents should:
1. Read their status file at the start
2. Update their status file during work
3. Update `state.tsv` when complete
4. Call `orchestrate.sh advance --from <package-id>`

The orchestration script reads `state.tsv` to determine which packages are ready to launch.
