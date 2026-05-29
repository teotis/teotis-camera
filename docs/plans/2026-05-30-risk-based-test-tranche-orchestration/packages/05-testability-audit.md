# Package 05 - High-Risk Class Testability Audit

## Package ID
`05-testability-audit`

## Goal
扫描高风险 Android/CameraX/UI 类，输出 testability 审计报告。**不写测试、不修改源码**，仅产出结构化分析文档。

## Target Classes (Audit Only)

### Android Framework Dependent
1. **GestureRouter** (`app/.../gesture/GestureRouter.kt`) - 依赖 Context, GestureDetector, ScaleGestureDetector, MotionEvent
2. **CameraOrientationMonitor** (`app/.../CameraOrientationMonitor.kt`) - 依赖 Context, OrientationEventListener
3. **OrientationContentRotator** (`app/.../OrientationContentRotator.kt`) - 依赖 View, ObjectAnimator
4. **VideoFrameExtractor** (`app/.../VideoFrameExtractor.kt`) - 依赖 Context, MediaMetadataRetriever, Bitmap
5. **GalleryLauncher** (`app/.../GalleryLauncher.kt`) - 依赖 AppCompatActivity, FileProvider, Intent
6. **SharedPreferencesPersistedSettingsStore** (`app/.../SharedPreferencesPersistedSettingsStore.kt`) - 依赖 Context, SharedPreferences

### CameraX Integration
7. **CameraXCaptureAdapter** (`app/.../camera/CameraXCaptureAdapter.kt`) - 深度 CameraX 依赖
8. **CameraDeviceAdapter** (`app/.../camera/device/CameraDeviceAdapter.kt`) - 相机设备适配器
9. **CameraXLivePreviewFrameSource** (`app/.../camera/live/CameraXLivePreviewFrameSource.kt`) - 实时预览

### Activity/UI
10. **MainActivity** (`app/.../MainActivity.kt`) - Activity 生命周期
11. **MainActivityViews** / **MainActivityRenderer** - View 层
12. **CockpitSurfaceRenderer** / **PreviewOverlayView** - SurfaceView/自定义 View

## Audit Deliverables

对每个类产出：
1. **当前 testability 评级**: A (easy) / B (moderate) / C (hard) / D (not feasible)
2. **阻塞因素**: 具体依赖哪些不可 mock 的框架类
3. **可测试的子集**: 如果有纯逻辑方法可以提取测试
4. **重构建议**: 如何通过 DI/接口提取/策略模式提升可测试性
5. **ROI 评估**: 重构成本 vs 测试收益

## Output Format
在 scratch 目录下产出 `audit-report.md`，结构化 Markdown 表格。

## Allowed Paths
- `scratch/` (写入审计报告)
- 所有 target class 文件 (只读)

## Dependencies
none

## Verification Commands
```bash
# 验证报告文件存在且非空
test -s "docs/plans/2026-05-30-risk-based-test-tranche-orchestration/scratch/05-testability-audit/audit-report.md"
```

## Acceptance Criteria
- [ ] audit-report.md 产出，覆盖所有 12 个 target 类
- [ ] 每个类有明确的 testability 评级和理由
- [ ] 没有修改任何源码文件

## Branch/Worktree Policy
- Branch: `agent/test-tranche/05-testability-audit`
- Worktree: `.claude/worktrees/test-tranche/05-testability-audit`
