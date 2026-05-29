# Package 03: 测试覆盖

## Package ID
`03-test-coverage`

## Goal
运行全项目单元测试，统计各模块测试通过/失败/跳过情况，分析测试文件分布，产出测试覆盖报告。

## Allowed Paths
- 所有 `src/` 目录（只读）
- `docs/plans/code-quality-scan-orchestration/scratch/03-test-coverage/` (输出报告)
- `docs/plans/code-quality-scan-orchestration/status/03-test-coverage.md`

## Forbidden Paths
- 不修改任何源代码或测试文件
- 不添加新测试（只扫描现有测试）

## Dependencies
无（Wave 1 首发）

## Acceptance Criteria

1. **运行全部单元测试**: 执行 `./gradlew test` 并收集结果
2. **测试结果统计**: 按模块统计 passed/failed/skipped/ignored 数量
3. **测试文件分布**: 列出每个模块的测试文件数量和测试类数量
4. **缺失测试检测**: 找出没有对应测试的源文件（src/main 有文件但 src/test 无对应文件的模块）
5. **测试失败详情**: 如果有测试失败，记录失败的测试名、错误信息、堆栈摘要
6. 将所有结果写入 `scratch/03-test-coverage/test-coverage-report.md`

## Verification Commands

```bash
# 运行全部测试
rtk ./gradlew --no-daemon test 2>&1 | tail -100

# 按模块运行测试（如果全量太慢）
rtk ./gradlew --no-daemon :core:session:test 2>&1 | tail -30
rtk ./gradlew --no-daemon :core:device:test 2>&1 | tail -30
rtk ./gradlew --no-daemon :core:media:test 2>&1 | tail -30
rtk ./gradlew --no-daemon :app:testDebugUnitTest 2>&1 | tail -30

# 测试文件统计
rtk find . -path "*/src/test/*" -name "*.kt" -not -path "*/build/*" | wc -l
```

## Expected Evidence
- `scratch/03-test-coverage/test-results.md` (各模块测试结果)
- `scratch/03-test-coverage/test-distribution.md` (测试文件分布)
- `scratch/03-test-coverage/missing-tests.md` (缺失测试列表)
- `scratch/03-test-coverage/test-coverage-report.md` (合并报告)

## Branch/Worktree Policy
- Branch: `agent/quality-scan/03-test-coverage`
- Worktree: `docs/plans/code-quality-scan-orchestration/scratch/03-test-coverage/worktree`

## Unlock Conditions
无依赖，可在 start 时立即启动

## Special Notes
- 测试运行可能较慢（10-30 分钟），需要耐心等待
- 使用 `--no-daemon` 避免 Gradle daemon 干扰
- 如果某个模块测试卡住，记录超时并继续其他模块
- 注意构建隔离：在 worktree 中运行时使用 `./scripts/run_isolated_gradle.sh`
