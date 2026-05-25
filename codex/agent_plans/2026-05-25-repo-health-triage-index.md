# Repo Health Triage Handoff Index

日期：2026-05-25

## Package Goal

把大量外部 agent 并行推进后暴露出的 git、测试和构建问题收敛成可执行修复包。当前目标不是继续叠 feature，而是先恢复可诊断的工作区状态、让 Stage 7 门禁能跑到底，并清掉已复现的 app focused test 红条。

## User Request

用户指出项目当前可能因为大量外部 agent 推进功能实现而存在混乱，尤其是 git、测试和编译方面，要求 Codex 排查并打包问题。

## Verified Findings

- 普通 `rtk git status --short --branch` 失败，错误为 `fatal: not a git repository: /Volumes/Extreme_SSD/project/codex_camera/.git/worktrees/orientation-adaptive-camera-ui`。
- `rtk git status --short --branch --ignore-submodules` 可以运行，说明主仓库仍可读，但被嵌套 Git/worktree 目录干扰。
- `.claude/worktrees/orientation-adaptive-camera-ui/.git` 指向旧工作区 `/Volumes/Extreme_SSD/project/codex_camera/.git/worktrees/orientation-adaptive-camera-ui`，当前仓库的 `.gitmodules` 没有对应映射，`rtk git submodule status` 因此失败。
- 当前分支是 `feat/document-batch-session-contracts`，并带有大量 tracked 修改与大量 untracked handoff docs、worktrees、public export 目录；外部 agent 继续动手前需要先隔离/确认工作区边界。
- `rtk ./scripts/verify_stage_7_observability.sh` 失败在 `:core:device:compileTestKotlin`，`DefaultDeviceShotRequestTranslatorTest.kt` 仍引用已不在当前 device request contract 上的 `stillCaptureQuality` 字段/构造参数。
- `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest` 失败 6 项：2 个 coordinator still quality/resolution rebind 断言，4 个 saved mask fail-soft/notes 断言。
- `rtk ./gradlew --no-daemon :app:assembleDebug` 通过，说明当前阻断重点是 git 状态和测试门禁，不是 APK debug 编译本身。

## Package Documents

| Work Package | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Git Worktree Hygiene And Status Repair](2026-05-25-git-worktree-hygiene-and-status-repair.md) | Text/code agent with user approval for destructive cleanup | planned | 修复普通 `git status` / `git submodule status` 被嵌套 worktree 和旧 gitdir 指针干扰的问题。 |
| [Stage 7 Device Still Quality Test Repair](2026-05-25-stage7-device-still-quality-test-repair.md) | Text/code agent | planned | 复用既有包，修复 `DefaultDeviceShotRequestTranslatorTest.kt` 编译阻断。 |
| [Color Lab Postprocess Fail-Soft And Test Repair](2026-05-25-color-lab-postprocess-failsoft-test-repair.md) | Text/code agent | planned | 复用既有包，修复 app focused test 当前 6 个失败项。 |

## Recommended Execution Order

1. 先执行 git/worktree hygiene。外部 agent 在普通 `git status` 不可靠时继续提交/合并，风险很高。
2. 再修 `Stage 7 Device Still Quality Test Repair`。它阻止 Stage 7 脚本跑到 app 层。
3. 再修 `Color Lab Postprocess Fail-Soft And Test Repair`。它覆盖当前 app focused red bar 和拍照后处理 fail-soft 风险。
4. 最后由 Codex 重新跑 focused commands、`:app:assembleDebug` 和 Stage 7 gate，并更新本索引状态。

## Related Recent Plans Used As References

- [Document Mode V2 Repair Handoff Index](2026-05-25-document-v2-repair-index.md)
- [Color Lab Validation Blocker Follow-Up Index](2026-05-25-color-lab-validation-blocker-followup-index.md)
- [Stage 7 Device Still Quality Test Repair](2026-05-25-stage7-device-still-quality-test-repair.md)
- [Color Lab Postprocess Fail-Soft And Test Repair](2026-05-25-color-lab-postprocess-failsoft-test-repair.md)

## Codex-Retained Work

- Final validation and acceptance after agents report fixes.
- Any destructive cleanup of worktree folders must be approved by the user or performed by the user; implementation agents should not silently delete external agent work.
- Visual/real-device acceptance remains outside this repo-health package.

## Verification Commands

```bash
rtk git status --short --branch
rtk git submodule status
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Current Status

- `planned` on 2026-05-25.
- This package records verified repo-health blockers and handoff routing only; no runtime code changes are included.
