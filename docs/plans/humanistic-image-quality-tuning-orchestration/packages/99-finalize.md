# Package 99 — Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Humanistic Image Quality Tuning orchestration. Verify every functional package, merge package branches into the integration branch, run integration verification, merge back to mainline, and clean up.

## Allowed Paths

- Read-only: entire repository.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/99-finalize.md`, `docs/plans/humanistic-image-quality-tuning-orchestration/FINAL_REPORT.md`.

## Forbidden Paths

- Do not edit runtime code or tests.
- Do not edit functional package agents' status files.
- Do not declare visual acceptance without real-device samples.
- Do not mark Stage 7 complete.

## Dependencies

All functional packages (01, 02, 03, 04, 05) must be `completed`.

## Finalize Steps

### Step 1: Read and Validate

1. Read `INDEX.md` and all package docs.
2. Read all `status/*.md` files.
3. Read `status/state.tsv`.
4. For each functional package, verify:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete (worktree, branch, base commit, commit hash, changed files, verification commands/results, risks)
   - branch, worktree, base commit, commit hash recorded in state.tsv
   - verification commands passed or failure is explicitly justified

### Step 2: Decide Whether Merging Is Allowed

- If any functional package is not `completed`, set status to `blocked` and record which packages are missing.
- If evidence is incomplete or contradictions exist, set status to `blocked` and record issues.

### Step 3: Create or Update Integration Branch

```bash
git checkout -B integration/humanistic-iq-tuning main
```

### Step 4: Merge Functional Package Branches

Merge in order: 01, 02, 03, 04, 05.

```bash
git merge --no-ff agent/humanistic-iq-tuning/01-current-iq-gap-audit -m "merge: 01-current-iq-gap-audit"
git merge --no-ff agent/humanistic-iq-tuning/02-style-target-scorecard -m "merge: 02-style-target-scorecard"
git merge --no-ff agent/humanistic-iq-tuning/03-feasible-rendering-pipeline-design -m "merge: 03-feasible-rendering-pipeline-design"
git merge --no-ff agent/humanistic-iq-tuning/04-real-device-capture-protocol -m "merge: 04-real-device-capture-protocol"
git merge --no-ff agent/humanistic-iq-tuning/05-implementation-roadmap -m "merge: 05-implementation-roadmap"
```

If a package has no branch (ran on main or no commits), skip its merge.

### Step 5: Handle Conflicts

- Stop and record conflict without cleaning anything.
- Record conflict files, branch, and recovery suggestion.
- Set status to `blocked`.

### Step 6: Integration Verification

```bash
git status --short
rtk rg -n "vendor|vivo|system camera|parity|PerceptualColorRecipe|PreviewColorTransform|PhotoAlgorithmPostProcessor|Humanistic" docs/plans/humanistic-image-quality-tuning-orchestration
```

Optional baseline tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

### Step 7: Merge to Mainline

Only after verification passes:

```bash
git checkout main
git merge --no-ff integration/humanistic-iq-tuning -m "merge: humanistic-iq-tuning orchestration"
```

### Step 8: Write Reports

Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.

### Step 9: Cleanup

Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

```bash
# Only if all prior steps succeeded
git branch -d agent/humanistic-iq-tuning/01-current-iq-gap-audit
git branch -d agent/humanistic-iq-tuning/02-style-target-scorecard
# ... etc
```

## Acceptance Criteria

- Every functional package verified: acceptance criteria addressed, files within allowed paths, evidence complete.
- Integration branch created and all package branches merged in order.
- Integration verification passed.
- Mainline merge completed.
- `FINAL_REPORT.md` and `status/99-finalize.md` written.
- Cleanup completed (or skipped with justification).

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
