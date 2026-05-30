# OpenCamera 代码质量扫描报告

**扫描日期**: 2026-05-30
**项目**: OpenCamera
**分支**: main
**扫描模式**: 只读，不自动修复

---

## 扫描概览

| 维度 | 状态 | 说明 |
|------|------|------|
| 01-静态分析 | ⚠️ 未完成 | agent 会话异常退出，未产出报告 |
| 02-架构合规 | ✅ 完成 | 模块依赖方向正确，无越层调用 |
| 03-测试覆盖 | ✅ 完成 | 1135 个测试，91.2% 通过率 |

---

## 1. 架构合规（✅ 通过）

- 模块依赖方向符合四层架构，无越层调用
- Feature 模块间无互相依赖，Core 不依赖 App
- 15 个文件超过 500 行，15 个类超过 300 行
- 9 个类圈复杂度超过 100（最高 CC=508 CameraXCaptureAdapter）

### 高风险项

| 文件 | CC | 行数 |
|------|-----|------|
| CameraXCaptureAdapter.kt | 508 | 3371 |
| SessionUiRenderModel.kt | 473 | 2325 |
| DefaultCameraSession.kt | 250 | 1821 |
| PhotoWatermarkPostProcessor.kt | 192 | 1295 |
| MainActivity.kt | 151 | 910 |

---

## 2. 测试覆盖（91.2% 通过率）

| 指标 | 值 |
|------|-----|
| 总测试数 | 1135 |
| 通过 | 1035 |
| 失败 | 100 |
| 通过率 | 91.2% |
| 全部通过的模块 | 8/16 (50%) |
| 完全无测试的模块 | 1 (feature:mode-fullclear) |

### 失败根因

| 根因 | 失败数 | 占比 |
|------|--------|------|
| 国际化字符串不匹配（i18n） | 63 | 63% |
| 逻辑断言失败 | 36 | 36% |
| 状态枚举不匹配 | 1 | 1% |

---

## 3. 风险汇总

1. CameraXCaptureAdapter CC=508，职责过重需拆分
2. SessionUiRenderModel CC=473，渲染逻辑过于集中
3. 100 个测试失败，63% 是 i18n 字符串不匹配
4. app 模块 Kotlin 编译器内部错误
5. core:settings 测试覆盖率仅 47%
6. feature:mode-fullclear 完全无测试

---

*详细交互式报告: [quality-scan-report.html](code-quality-scan-report.html)*
