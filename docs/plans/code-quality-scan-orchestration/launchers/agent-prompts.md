# Agent Prompts

## Package: 01-static-analysis - 静态分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/packages/01-static-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/01-static-analysis.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh scratch-path 01-static-analysis`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

**重要：这是只读扫描任务。不要自动修复任何代码问题。**

### 执行步骤

1. 创建 scratch 目录
2. 在根 build.gradle.kts 和各子模块 build.gradle.kts 中临时添加 detekt 和 ktlint Gradle 插件
3. 运行 `rtk ./gradlew --no-daemon detekt`，收集报告
4. 运行 `rtk ./gradlew --no-daemon :app:lintDebug`，收集报告
5. 运行 `rtk ./gradlew --no-daemon ktlintCheck`，收集报告（如果 ktlint 插件配置失败，用 CLI 方式替代）
6. 将三份报告合并写入 `scratch/static-analysis-report.md`
7. **恢复 build 文件原状**：`git checkout -- build.gradle.kts app/build.gradle.kts core/*/build.gradle.kts feature/*/build.gradle.kts`
8. 更新 coordinator status
9. 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh mark-state 01-static-analysis completed --commit <commit-sha> --verification "detekt+lint+ktlint reports generated"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh advance --from 01-static-analysis
```

---

## Package: 02-arch-compliance - 架构合规

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/packages/02-arch-compliance.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/02-arch-compliance.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh scratch-path 02-arch-compliance`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

**重要：这是只读扫描任务。不要修改任何文件。**

### 执行步骤

1. 创建 scratch 目录
2. 解析所有 build.gradle.kts 的依赖声明，生成模块依赖图
3. 检查 import 语句，检测越层调用（feature 间依赖、core→app、低层→高层）
4. 统计超过 500 行的 Kotlin 文件
5. 统计超过 300 行的类/对象
6. 对 core:session、core:device、app 的核心类做圈复杂度分析
7. 将所有结果合并写入 `scratch/arch-compliance-report.md`
8. 更新 coordinator status
9. 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh mark-state 02-arch-compliance completed --commit <commit-sha> --verification "arch compliance report generated"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh advance --from 02-arch-compliance
```

---

## Package: 03-test-coverage - 测试覆盖

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/packages/03-test-coverage.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/03-test-coverage.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh scratch-path 03-test-coverage`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

**重要：这是只读扫描任务。不添加新测试，不修改任何文件。**

### 执行步骤

1. 创建 scratch 目录
2. 运行 `rtk ./gradlew --no-daemon test`，收集全部测试结果
3. 如果全量测试太慢，按模块分批运行：core:session, core:device, core:media, core:mode, core:settings, core:capability, core:effect, app:testDebugUnitTest, 各 feature 模块
4. 统计各模块 passed/failed/skipped/ignored
5. 统计每个模块的测试文件数量
6. 找出 src/main 有文件但 src/test 无对应文件的模块
7. 记录测试失败详情（测试名、错误信息、堆栈摘要）
8. 将所有结果合并写入 `scratch/test-coverage-report.md`
9. 更新 coordinator status
10. 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh mark-state 03-test-coverage completed --commit <commit-sha> --verification "test coverage report generated"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh advance --from 03-test-coverage
```

---

## Package: 99-finalize - 合并报告

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/status/state.tsv
**Scratch path**: not needed for finalize
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh

### 执行步骤

1. 读取 INDEX.md 了解全貌
2. 读取三个包的 scratch 报告
3. 合并为一份结构化报告 `docs/code-quality-scan-report.md`，包含：
   - 扫描概览（日期、范围、工具版本）
   - 静态分析结果
   - 架构合规结果
   - 测试覆盖结果
   - 问题汇总（按严重程度排序）
   - 改进建议
4. 更新 coordinator status
5. 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "final report generated at docs/code-quality-scan-report.md"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```

---
