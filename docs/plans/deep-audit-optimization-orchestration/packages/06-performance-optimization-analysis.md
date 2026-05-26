# Package 06: Performance Optimization Analysis - 性能优化分析

## Package ID
`06-performance-optimization-analysis`

## Objective

对 OpenCamera 项目的性能进行全面分析。识别性能瓶颈、优化机会和改进建议，提升应用的响应速度和资源效率。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/06-performance-optimization-analysis.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files)

## Forbidden Paths

- All source code files
- Build files
- Configuration files

## Dependencies

- `03-module-dependency-analysis` must be completed first

## Analysis Scope

### 1. 启动性能分析
- 冷启动时间
- 热启动时间
- 启动阻塞操作
- 启动优化机会

### 2. 运行时性能分析
- 帧率稳定性
- 内存使用模式
- CPU 使用模式
- 电池消耗

### 3. 相机性能分析
- 预览帧率
- 拍照延迟
- 录像性能
- 对焦速度

### 4. I/O 性能分析
- 文件读写性能
- 数据库操作
- 网络请求
- 缓存策略

## Analysis Tools

### Primary: 自定义性能分析
- 代码热点分析
- 资源使用分析
- 瓶颈识别

### Secondary: Android Profiler 分析
- CPU Profiler
- Memory Profiler
- Network Profiler
- Energy Profiler

### Tertiary: CodeGraph
- 性能关键路径分析
- 调用链追踪

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/06-performance-optimization-analysis.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/06-performance-optimization-analysis.md
```

## Acceptance Criteria

1. 产出详细的性能优化分析报告
2. 识别 Top 10 性能瓶颈
3. 识别至少 5 个优化机会
4. 提供具体的优化建议
5. 包含性能基准数据

## Evidence Requirements

- 性能瓶颈清单
- 优化机会排序
- 优化建议
- 性能基准数据
- 验证命令执行结果

## Branch Policy

- Branch: `agent/deep-audit/06-performance-optimization-analysis`
- Worktree: `.worktrees/06-performance-optimization-analysis`
- Base: `main`

## Unlock Condition

- `03-module-dependency-analysis` must be completed

## Expected Duration

- 分析时间：45-90 分钟
- 报告生成：30-45 分钟

## Notes

- 关注相机预览和拍照的性能
- 分析大文件（如 CameraXCaptureAdapter.kt）的性能影响
- 识别可优化的算法和数据结构
- 不要修改任何源代码
