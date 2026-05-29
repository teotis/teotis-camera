# Agent Prompts

## Package: 01-dev-log-tag-system — Dev Log 标签系统 + ColorLab 滑动 + 链路耗时

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/packages/01-dev-log-tag-system.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/status/01-dev-log-tag-system.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/launchers/orchestrate.sh scratch-path 01-dev-log-tag-system`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package's status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

## Context

This package continues work already partially implemented on main branch. The following changes exist as uncommitted modifications:

### Already Done

1. **SessionTrace.kt**: `DevLogTag` enum (15 tags), `SessionTraceEvent.tags` field, `InMemorySessionTrace.record()` with tags parameter
2. **PerformanceLinkEvent.kt**: `createPerformanceLinkRecorder()` factory function
3. **AppContainer.kt**: `linkRecorder` created and passed to `DefaultCameraSession`
4. **MainActivity.kt**: `devLogRenderModel()` calls pass `linkEvents = container.linkRecorder.snapshot()`
5. **MainActivityRenderer.kt**: ColorLab mode disables `isNestedScrollingEnabled` and sets `OVER_SCROLL_NEVER`
6. **SessionUiRenderModel.kt**: Tag-based filtering, `inferredTags()` fallback, LINK content with human-readable summary + machine format, removed "最后耗时" from summaryText, updated KEY tab to include PERFORMANCE+TIMING, CORE tab to include RESOURCE, added `LinkEventStatus` import

### To Do

1. **Fix the failing test**: `DevLogRenderModelTest.key tab shows only key events` fails. Investigate root cause (likely the new `formatEvents()` adds tag labels like `[生命周期]`, altering the content string but `contains` checks should still pass for event names).
2. **Verify all 21 tests pass**
3. **Verify core:session tests pass**
4. **Commit the changes**

## Steps

1. Read the current state of uncommitted changes:
   ```bash
   git diff --stat HEAD
   git diff HEAD -- app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt
   ```

2. Run the failing test to see the exact failure:
   ```bash
   ./gradlew :app:testDebugUnitTest --tests "com.opencamera.app.DevLogRenderModelTest.key tab shows only key events" --info 2>&1 | tail -50
   ```

3. Fix the test failure. Common causes:
   - `inferredTags()` not assigning correct tags to test events
   - `formatEvents()` adding tag suffix that confuses assertion
   - Missing tags for events like `preview.first.frame` (needs PERFORMANCE tag for KEY tab)

4. After fixing, run all tests:
   ```bash
   ./gradlew :app:testDebugUnitTest --tests "com.opencamera.app.DevLogRenderModelTest"
   ./gradlew :core:session:test
   ./gradlew :app:compileDebugKotlin
   ```

5. Commit with conventional commit message (Chinese):
   ```bash
   git add <changed files>
   git commit -m "feat: 添加 DevLogTag 标签系统、ColorLab 滑动禁用、链路耗时分析"
   ```

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/launchers/orchestrate.sh mark-state 01-dev-log-tag-system completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/launchers/orchestrate.sh mark-state 01-dev-log-tag-system blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/launchers/orchestrate.sh advance --from 01-dev-log-tag-system
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration/launchers/orchestrate.sh advance
```
