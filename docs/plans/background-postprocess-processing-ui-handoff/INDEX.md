# Background Postprocess Processing UI - Package Index

## Goal

Design and implement a Stage 7-local UX loop for slow photo postprocessing: after the camera has received the frame but final saved-media postprocess is still running, the app must show an explicit processing state and warn the user not to exit until the final result is complete. The shutter lifecycle must stay aligned with the existing data-boundary work: ordinary still capture may re-arm after `DataReceived`, but the user must not be left thinking the final artifact is already done.

## Verified Context

- Existing shutter data-boundary plans already separate `DataReceived` from final `ShotCompleted`; see `docs/plans/shutter-data-boundary-v1-orchestration/INDEX.md`.
- `CaptureStatus` currently includes `SAVING`, `DATA_RECEIVED`, `COMPLETED`, and `FAILED` in `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`.
- `SessionCockpitRenderModel.shutterDisabledReason(...)` already allows the shutter to remain enabled when `activeShot == null` and capture status is `DATA_RECEIVED` or `SAVING`.
- `shutterVisualState(...)` has `BACKGROUND_SAVING`, but `primaryStatusRenderModel(...)` still renders generic status words instead of a user-facing "processing, do not exit" warning.
- `MainActivity.captureConfigDisabledReason(...)` still treats `SAVING` and `DATA_RECEIVED` as reasons to block capture configuration, which is probably correct while background finalization is pending.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read this index and all referenced package documents.
- Create one worktree and branch for the assigned package using `using-git-worktrees`, or reuse an existing package worktree if the user provides one.
- Make scope-bounded edits, add/update tests, and update docs as described in the package.
- Run the listed verification commands through `rtk`; in a worktree, use `rtk ./scripts/run_isolated_gradle.sh`.
- Continue fixing verification failures that remain inside the package scope.
- Commit locally after completing the package.

## Stop Gates - Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or declaring Stage 7 complete.
- Changing the shutter re-arm policy for special captures such as multi-frame, high-pixel, Live, Document, Portrait, or video.
- Moving camera runtime ownership out of the Session Kernel / Device Adapter / Media Pipeline boundaries.
- Adding destructive git operations: force-push, hard reset, deleting branches/worktrees not created by this package.
- Adding network access, external API calls, or secrets.
- Overwriting unrelated dirty changes outside this package.
- Fixing verification failures when the fix expands scope beyond this package.

## Completion Policy

After completing the package:
- Report changed files, verification commands, and test results.
- Commit the package branch locally.
- Do not delete branches or worktrees.
- Return to Codex for acceptance validation and, if available, real-device UX review.

## Package Documents

| Work Package | Target Agent | Dependency | Purpose |
|---|---|---|---|
| [01-background-postprocess-processing-ui.md](packages/01-background-postprocess-processing-ui.md) | implementation agent | none | Add session-owned pending finalization state and render a visible processing / do-not-exit UI hint. |
| [99-codex-acceptance.md](packages/99-codex-acceptance.md) | Codex retained | after 01 | Validate contract, focused tests, and real-device UX evidence without overclaiming. |

## Recommended Execution Order

1. Execute package `01-background-postprocess-processing-ui`.
2. Return to Codex for package `99-codex-acceptance`.
3. Run real-device smoke only if a device is available; otherwise mark device UX as pending.

## Launch Prompt

Copy this exact message to start an external Claude Code agent:

```text
/using-superpowers
执行授权：创建独立 worktree 和分支，读取方案，实施 scope 内修复，更新必要测试/文档，运行列出的验证命令，并继续修复 scope 内验证失败；完成后本地提交。不要为这些常规步骤询问确认。禁止 force-push、hard reset、删除其他包的分支或 worktree。只有遇到 Stop Gates 才停下询问。
核查现状，并恰当落地如下优化方案：/Volumes/Extreme_SSD/project/open_camera/docs/plans/background-postprocess-processing-ui-handoff/INDEX.md
```

## Known Repository State At Planning Time

- `rtk git status --short` emitted repeated `non-monotonic index .git/objects/pack/._pack-...idx` warnings before showing unrelated dirty files under `docs/plans/real-device-performance-link-logs-orchestration/status/`.
- This handoff does not authorize cleaning those unrelated files.
