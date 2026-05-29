# Package 01 - Session Test Hang Diagnosis And Repair

## Package ID

`01-session-test-hang-diagnosis-and-repair`

## Goal

Make the Stage 7 session verification deterministic again. The observed mainline symptom is:

- `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest` passed.
- `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest` reached `> Task :core:session:test` and remained silent until manually killed.
- one full Stage 7 script rerun also reached `:core:session:test` and then hung.

## Allowed Paths

- `core/session/src/test/**`
- `core/session/src/main/**` only after confirming the hang root cause is production session behavior
- `scripts/run_isolated_gradle.sh` only if the root cause is build-root isolation behavior
- `docs/plans/stage7-session-test-hang-orchestration/status/01-session-test-hang-diagnosis-and-repair.md`
- `docs/plans/stage7-session-test-hang-orchestration/status/state.tsv` only your own row

## Forbidden Paths

- UI remediation code under `app/src/main/java/com/opencamera/app/**` unless the hang root cause directly requires it
- unrelated feature modules
- any other package status file
- `docs/plans/stage7-session-test-hang-orchestration/INDEX.md`

## Branch And Worktree Policy

- Branch: `agent/stage7-session-test-hang/01-session-test-hang-diagnosis-and-repair`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/stage7-session-test-hang-01-session-test-hang-diagnosis-and-repair`
- Create or reuse only this worktree/branch.
- Do not rely on worktree-local status files; write coordinator status to `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/01-session-test-hang-diagnosis-and-repair.md`.
- Update your row in `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/status/state.tsv` before calling `advance`.

## Unlock Conditions

- Mark coordinator status as `completed` only after the required verification commands pass or after a justified blocked result is recorded.
- Set `state.tsv` to `completed` or `blocked`.
- Record worktree, branch, base commit, commit hash, changed files, verification commands/results, process evidence for any hang, and unresolved risks.
- Call the shared advancement command after writing evidence:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh advance --from 01-session-test-hang-diagnosis-and-repair
```

## Required Investigation

1. Read `AGENTS.md`, `<HOME>/.codex/RTK.md`, this package, and the orchestration index.
2. Create or reuse an isolated worktree.
3. In the worktree, run Gradle through `rtk ./scripts/run_isolated_gradle.sh`.
4. Reproduce the hang with the smallest command first:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

5. If it hangs, capture process evidence before killing only the owned Gradle/test JVM processes.
6. Narrow the hang to one test method if possible using `--tests com.opencamera.core.session.DefaultCameraSessionTest.<methodName>`.
7. Check for leaked coroutines, schedulers, unclosed fake devices, test dispatchers, infinite retries, or tests waiting on a state/event that never arrives.
8. Repair the smallest root cause. Prefer test-fixture cleanup if the product behavior is correct; modify production code only if the hang reveals a real session lifecycle defect.

## Acceptance Criteria

- The exact focused command for `DefaultCameraSessionTest` completes without manual kill.
- `SessionDiagnosticsTest` still passes.
- The repair does not weaken assertions by deleting meaningful coverage.
- Any timeout added to a test must fail loudly with useful diagnostic context, not mask the hang.
- No UI/product behavior changes are included unless directly justified by the root cause.

## Verification Commands

Run these from the isolated worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
```

If those pass, optionally run the Stage 7 script when the machine is idle:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Evidence Required

Write to `status/01-session-test-hang-diagnosis-and-repair.md`:

- worktree path and branch
- exact reproduction command and whether it reproduced
- process evidence for any hang
- root cause
- changed files and diff stat
- verification command results
- commit hash or PR link
- unresolved risks
- self-certification that only allowed paths were touched

## Stop Gates

Stop and ask before:

- killing a process not clearly owned by your run
- rewriting broad session architecture
- changing UI or mode plugin behavior as a side effect
- claiming the issue is fixed without an isolated rerun of `DefaultCameraSessionTest`
