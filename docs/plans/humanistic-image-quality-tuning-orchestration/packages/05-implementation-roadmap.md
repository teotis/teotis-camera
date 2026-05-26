# Package 05 — Implementation Roadmap

## Package ID

`05-implementation-roadmap`

## Goal

Convert packages 01-04 into a scoped implementation roadmap for a later user-approved round. This package should not implement code. It should decide which improvements are worth turning into handoff packages, in what order, and what must remain Codex/user visual QA.

## Allowed Paths

- Read-only: all package status files in this orchestration directory, `docs/plans/**`, `core/**`, `feature/**`, `app/src/**`, `scripts/**`, `codex/documentation.md`.
- Writable: `docs/plans/humanistic-image-quality-tuning-orchestration/status/05-implementation-roadmap.md` only.

## Forbidden Paths

- Do not edit runtime code, tests, resources, shared docs, `INDEX.md`, package docs, other status files, or `codex/documentation.md`.
- Do not mark Stage 7 complete.

## Dependencies

Wait for packages 01-04.

## Parallel Safety

Not safe to run before dependencies. Writes only its own status file.

## Tasks

1. Read package 01-04 status files.
2. Identify the smallest valuable implementation sequence, likely ordered as:
   - reliability/honesty blockers first;
   - recipe/style semantics next;
   - saved JPEG tone/highlight/shadow improvements;
   - preview approximation honesty;
   - real-device sample pass.
3. Propose 2-5 future handoff packages with:
   - goal;
   - allowed/forbidden paths;
   - acceptance criteria;
   - focused verification commands;
   - real-device acceptance owner;
   - stop gates.
4. Identify which items should be rejected or deferred because they imply vendor parity or excessive complexity.
5. Define a final recommendation: `READY_FOR_IMPLEMENTATION`, `PARTIAL_NEEDS_SAMPLES`, or `BLOCKED_BY_RELIABILITY`.

## Acceptance Criteria

- Status file contains a clear ordered roadmap with no more than five implementation packages.
- Each future package is small enough for an implementation agent and has concrete verification commands.
- Roadmap preserves architecture boundaries and Gradle build isolation rules.
- Roadmap keeps visual/taste judgment with Codex/user, not external text-only agents.
- Roadmap explicitly states what should not be attempted in the next round.

## Verification Commands

```bash
rtk git status --short
rtk rg -n "verify_stage_7_observability|ColorLabSpecTest|PreviewEffectAdapterTest|PhotoAlgorithmPostProcessorTest|CameraSessionCoordinatorTest|DefaultDeviceShotRequestTranslatorTest|SessionUiRenderModelTest" docs/plans scripts core app feature
```

## Expected Evidence Pack

- Worktree path and branch, if any.
- Commands run and short output summary.
- Proposed implementation package list.
- Rejected/deferred items.
- Final recommendation.
- Unresolved risks and device sample dependencies.
- Self-certification that only the allowed status file was touched.

## Stop Gates

Stop and ask before editing package docs, launching implementation, or expanding scope into a new stage.
