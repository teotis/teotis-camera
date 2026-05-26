# Status Tracking

This directory contains status tracking files for the multimodal UI visual analysis orchestration.

## Files

- `state.tsv` - Machine-readable state ledger
- `package-status-template.md` - Template for package status files
- `M01.md` - Status for M01: UI Layout Component Analysis
- `M02.md` - Status for M02: Interaction Flow Analysis
- `M03.md` - Status for M03: Visual Perception Analysis
- `M04.md` - Status for M04: Feature Completeness Analysis
- `M05.md` - Status for M05: Consistency Analysis
- `M06.md` - Status for M06: Accessibility Analysis
- `M07.md` - Status for M07: Optimization Opportunities
- `M08.md` - Status for M08: Optimization Proposals
- `M99.md` - Status for M99: Final Report

## State Legend

- `pending` - Package not yet started
- `launched` - Package agent launched
- `in_progress` - Package agent working
- `completed` - Package completed successfully
- `blocked` - Package blocked by an issue
- `stale` - Package status is outdated
- `invalid` - Package status is invalid
- `finalizing` - Finalize package in progress
- `finalized` - Finalize completed successfully

## Usage

Check status with:
```bash
bash docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh status
```

View agents with:
```bash
claude agents
```
