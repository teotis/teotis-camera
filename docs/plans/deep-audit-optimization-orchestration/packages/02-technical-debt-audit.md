# Package 02: Technical Debt Audit

## Package ID
`02-technical-debt-audit`

## Objective

使用 renewal-architect skill 对 OpenCamera 项目进行全面的技术债务审计。识别技术债务、评估现代化机会、制定治理策略。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/02-technical-debt-audit.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

None

## Acceptance Criteria

1. 产出详细的技术债务审计报告到 status 文件
2. 对技术债务进行分类和优先级排序
3. 识别至少 10 个具体的技术债务项
4. 提供现代化路线图
5. 包含 ROI 估算

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/02-technical-debt-audit.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/02-technical-debt-audit.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 技术债务清单
- 优先级排序矩阵
- 现代化路线图
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/02-technical-debt-audit`
- Worktree: `.worktrees/02-technical-debt-audit`
- Base: `main`

## Unlock Condition

- 无依赖，可立即开始

## Analysis Scope

### 1. 技术债务分类
- 代码债务（重复代码、复杂代码、坏味道）
- 架构债务（层次违规、紧耦合、职责不清）
- 测试债务（测试缺失、测试脆弱、覆盖不足）
- 文档债务（文档缺失、文档过时、文档不一致）
- 依赖债务（过时依赖、安全漏洞、许可证问题）

### 2. 现代化机会评估
- Kotlin 语言特性利用程度
- Android Jetpack 组件采用情况
- CameraX API 使用深度
- 协程和 Flow 的使用模式
- 依赖注入框架应用

### 3. 治理策略制定
- 债务优先级排序
- 偿还策略（增量 vs 重构）
- 风险评估
- 资源估算

## Analysis Tools

### Primary: renewal-architect
- 遗留系统现代化分析
- 技术债务治理框架
- ROI 驱动的优化建议
- 安全的现代化策略

### Secondary: 静态分析工具
- 代码复杂度分析
- 重复代码检测
- 依赖分析
