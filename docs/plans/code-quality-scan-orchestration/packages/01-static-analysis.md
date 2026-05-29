# Package 01: 静态分析

## Package ID
`01-static-analysis`

## Goal
在全项目范围运行 detekt、Android Lint、ktlint 三项静态分析工具，生成只读报告。

## Allowed Paths
- `build.gradle.kts` (临时添加 detekt/ktlint 插件用于扫描)
- `app/build.gradle.kts`, `core/*/build.gradle.kts`, `feature/*/build.gradle.kts` (临时添加插件)
- `gradle/` (可能需要添加 detekt 配置)
- `docs/plans/code-quality-scan-orchestration/scratch/01-static-analysis/` (输出报告)
- `docs/plans/code-quality-scan-orchestration/status/01-static-analysis.md`

## Forbidden Paths
- 不修改任何 `src/` 下的源代码
- 不修改测试文件
- 不执行任何自动修复命令（detekt --auto-correct, ktlint --format 等）

## Dependencies
无（Wave 1 首发）

## Acceptance Criteria

1. **detekt 报告**: 运行 `./gradlew detekt` 并收集报告，按模块分类列出发现的问题数量和严重级别
2. **Android Lint 报告**: 运行 `./gradlew :app:lintDebug` 并收集报告，列出 warning/error 详情
3. **ktlint 报告**: 运行 ktlint 检查所有 Kotlin 文件，列出格式问题
4. 所有工具以报告模式运行，不执行自动修复
5. 将三份报告合并写入 `scratch/01-static-analysis/static-analysis-report.md`

## Verification Commands

```bash
# detekt
rtk ./gradlew --no-daemon detekt 2>&1 | tail -50

# Android Lint
rtk ./gradlew --no-daemon :app:lintDebug 2>&1 | tail -50

# ktlint (通过 ktlint-gradle 插件或独立 CLI)
rtk ./gradlew --no-daemon ktlintCheck 2>&1 | tail -50
```

## Expected Evidence
- `scratch/01-static-analysis/detekt-report.md` 或 detekt XML/HTML 报告路径
- `scratch/01-static-analysis/lint-report.md` 或 lint HTML 报告路径
- `scratch/01-static-analysis/ktlint-report.md`
- `scratch/01-static-analysis/static-analysis-report.md` (合并报告)

## Branch/Worktree Policy
- Branch: `agent/quality-scan/01-static-analysis`
- Worktree: `docs/plans/code-quality-scan-orchestration/scratch/01-static-analysis/worktree`

## Unlock Conditions
无依赖，可在 start 时立即启动

## Special Notes
- detekt 和 ktlint 需要临时添加 Gradle 插件到 build 文件
- 扫描完成后，应将 build 文件恢复原状（git checkout）
- 报告只记录发现的问题，不包含修复建议的执行
- 如果某个工具安装失败，记录错误并继续其他工具的扫描
