# Package 06: Performance Optimization Analysis

## Package ID
`06-performance-optimization-analysis`

## Objective

对 OpenCamera 项目的性能进行全面分析。识别性能瓶颈、优化机会和改进建议，提升应用的响应速度和资源效率。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/06-performance-optimization-analysis.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- `03-module-dependency-analysis` must be completed first

## Acceptance Criteria

1. 产出详细的性能优化分析报告到 status 文件
2. 识别 Top 10 性能瓶颈
3. 识别至少 5 个优化机会
4. 提供具体的优化建议
5. 包含性能基准数据

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/06-performance-optimization-analysis.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/06-performance-optimization-analysis.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 性能瓶颈清单
- 优化机会排序
- 优化建议
- 性能基准数据
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/06-performance-optimization-analysis`
- Worktree: `.worktrees/06-performance-optimization-analysis`
- Base: `main`

## Unlock Condition

- `03-module-dependency-analysis` must be completed

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

### Tertiary: 源码搜索与静态阅读
- 性能关键路径分析
- 调用链追踪

## Key Files to Analyze

### Performance Critical Paths
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` - 相机适配器
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` - 后处理
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` - 水印处理
- `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt` - 人像处理

### Session Performance
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`

### UI Performance
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
