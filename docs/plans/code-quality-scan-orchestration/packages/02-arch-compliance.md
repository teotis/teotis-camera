# Package 02: 架构合规

## Package ID
`02-arch-compliance`

## Goal
分析项目模块依赖方向、检测越层调用、统计文件/类大小和圈复杂度，产出架构合规报告。

## Allowed Paths
- 所有 `src/` 目录（只读）
- 所有 `build.gradle.kts`（只读分析依赖声明）
- `docs/plans/code-quality-scan-orchestration/scratch/02-arch-compliance/` (输出报告)
- `docs/plans/code-quality-scan-orchestration/status/02-arch-compliance.md`

## Forbidden Paths
- 不修改任何文件
- 不执行任何自动修复

## Dependencies
无（Wave 1 首发）

## Acceptance Criteria

1. **模块依赖图**: 解析所有 build.gradle.kts 的 implementation/api 依赖声明，生成模块间依赖关系图
2. **越层调用检测**: 检查是否违反四层架构（Mode Plugin → Session Kernel → Device Adapter → Media Pipeline）：
   - feature 模块间互相依赖
   - core 模块依赖 app 模块
   - 低层模块依赖高层模块
3. **文件大小统计**: 找出超过 500 行的 Kotlin 文件，按行数排序
4. **类大小统计**: 找出超过 300 行的类/对象，按行数排序
5. **圈复杂度**: 对关键模块（core:session, core:device, app）的核心类做圈复杂度分析
6. 将所有结果写入 `scratch/02-arch-compliance/arch-compliance-report.md`

## Verification Commands

```bash
# 模块依赖分析
rtk ./gradlew --no-daemon :app:dependencies --configuration implementation 2>&1 | head -100

# 文件行数统计
rtk find . -name "*.kt" -not -path "*/build/*" -not -path "*/.gradle/*" | xargs wc -l | sort -rn | head -50

# Import 分析（越层调用检测）
rtk rg "^import com\.opencamera\.(feature\.\w+)" --no-filename | sort | uniq -c | sort -rn | head -30
```

## Expected Evidence
- `scratch/02-arch-compliance/dependency-graph.md` (模块依赖关系)
- `scratch/02-arch-compliance/layer-violations.md` (越层调用列表)
- `scratch/02-arch-compliance/file-size-stats.md` (大文件统计)
- `scratch/02-arch-compliance/arch-compliance-report.md` (合并报告)

## Branch/Worktree Policy
- Branch: `agent/quality-scan/02-arch-compliance`
- Worktree: `docs/plans/code-quality-scan-orchestration/scratch/02-arch-compliance/worktree`

## Unlock Conditions
无依赖，可在 start 时立即启动

## Special Notes
- 本包纯只读分析，不安装任何工具
- 依赖 Gradle 的 dependencies task 和 grep/find 命令
- 架构层次定义参考 AGENTS.md 中的四层架构描述
