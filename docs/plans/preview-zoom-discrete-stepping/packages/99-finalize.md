# Package 99: Finalize

## Goal

集成所有 functional packages 的代码变更，运行集成验证，合入 mainline，清理临时的分支和工作树。

## Allowed Paths

- All coordinator files under `docs/plans/preview-zoom-discrete-stepping/`
- Integration branch `agent/preview-zoom-discrete-stepping/integration`
- Mainline `main` for final merge (local only, no push)
- Recorded package branches and worktrees for cleanup

## Acceptance Criteria

1. **预检**：运行 `verify-finalize` 确认所有 functional packages:
   - state = `completed`
   - branch 存在
   - commit hash 存在且有效
   - worktree 干净（如有）
   - changed files 在 allowed paths 内

2. **集成合并**：
   - 创建/更新 integration branch `agent/preview-zoom-discrete-stepping/integration`
   - 按 Merge Strategy 顺序合并 package branches：
     1. `01-analyze-preview-zoom-strategy`
     2. `02-implement-discrete-preview-zoom`
     3. `03-fix-overlay-frame-geometry`
   - 如有冲突，记录冲突文件并 stop（不清理）

3. **编译验证**：
   ```bash
   rtk ./scripts/run_isolated_gradle.sh :core:device:compileDebugKotlin
   rtk ./scripts/run_isolated_gradle.sh :core:session:compileDebugKotlin
   rtk ./scripts/run_isolated_gradle.sh :app:compileDebugKotlin
   ```

4. **测试验证**：
   ```bash
   rtk ./scripts/run_isolated_gradle.sh :core:session:testDebugUnitTest
   rtk ./scripts/run_isolated_gradle.sh :app:testDebugUnitTest --tests="*PreviewOverlay*"
   ```

5. **Mainline 合并**：所有验证通过后，将 integration branch 合并到 mainline（本地 non-force merge）

6. **清理**：删除所有 recorded 本地 branches/worktrees

7. **报告**：写 `FINAL_REPORT.md` 和 `status/99-finalize.md`

## Verification Commands

```bash
# Run from coordinator plan root
bash launchers/orchestrate.sh verify-finalize

# Full build verification
rtk ./scripts/run_isolated_gradle.sh :core:device:compileDebugKotlin :core:session:compileDebugKotlin :app:compileDebugKotlin

# Test verification
rtk ./scripts/run_isolated_gradle.sh :core:session:testDebugUnitTest :app:testDebugUnitTest

# APK build (for real-device QA)
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Failure Rules

- 任何步骤失败 → 标记 `99-finalize` 为 `blocked`
- 记录失败阶段、命令、分支、冲突文件、日志摘要和恢复建议
- 保留所有 branches/worktrees，不做清理
- 如 package 状态与当前工作区不一致，先在干净的 detached worktree 中验证记录的 commit

## Success Rules

- 标记 `99-finalize` 为 `finalized`
- 记录 integration branch、mainline merge commit、验证总结、清理结果
- 重新运行 finalize 后必须幂等，报告 `already finalized`

## External QA Gate

Finalize 成功后，`real-device-zoom-preview-qa` gate 仍需要用户在实际设备上验证：
- 安装 APK
- 测试变焦时预览窗是否离散跳跃
- 测试 16:9 画幅框不再超出预览窗
- 验证各焦段画幅框大小是否合理
