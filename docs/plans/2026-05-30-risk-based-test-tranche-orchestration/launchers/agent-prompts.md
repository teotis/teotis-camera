# Agent Prompts

## Package: 01-settings-codecs-tests - Settings Codecs & Defaults Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/packages/01-settings-codecs-tests.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/01-settings-codecs-tests.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 01-settings-codecs-tests`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh

你被授权为 `core/settings` 模块中 5 个未测试的纯逻辑类补全单元测试。

### 目标类
1. `FilterProfileShareCodec` + `ImportedFilterProfilesSerializer` + `ManualCaptureDraftSerializer` (在 `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsShareCodecs.kt`)
2. `SettingsMetadataCodecs` 中的扩展函数 (在 `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt`)
3. `SettingsDefaults` 中的配置数据 (在 `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`)

### 测试要求
- 在 `core/settings/src/test/kotlin/com/opencamera/core/settings/` 下创建测试文件
- 每个测试类头部用注释说明：覆盖了哪些行为、哪些行为暂时不适合单测
- 优先测试 round-trip 一致性、边界值、错误路径
- 只测试 public API 和 observable behavior，不 mock 私有实现
- 所有测试必须在 `rtk ./gradlew --no-daemon :core:settings:test` 中通过

### 验证
```bash
rtk ./gradlew --no-daemon :core:settings:test
```

### 完成后
- commit 测试文件
- 更新 coordinator status 为 completed
- 运行 mark-state 命令
- 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh mark-state 01-settings-codecs-tests completed --commit <commit-sha> --verification ":core:settings:test: PASSED"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh advance --from 01-settings-codecs-tests
```

---

## Package: 02-device-media-pure-tests - Device & Media Pure Logic Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/packages/02-device-media-pure-tests.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/02-device-media-pure-tests.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 02-device-media-pure-tests`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh

你被授权为 `core/device` 和 `core/media` 模块中未测试的纯逻辑类补全单元测试。

### 目标类
1. `MultiFrameCaptureExecutionPlanner` (在 `core/device/src/main/kotlin/com/opencamera/core/device/MultiFrameCaptureExecutionPlanner.kt`)
2. `ReversibleWatermarkArchiveManifest` + `sha256Hex` (在 `core/media/src/main/kotlin/com/opencamera/core/media/ReversibleWatermarkArchive.kt`)
3. `MediaProcessorAvailability` (在 `core/media/src/main/kotlin/com/opencamera/core/media/MediaProcessorAvailability.kt`)

### 测试要求
- `MultiFrameCaptureExecutionPlanner` 测试文件放在 `core/device/src/test/kotlin/com/opencamera/core/device/`
- `ReversibleWatermarkArchive` 和 `MediaProcessorAvailability` 测试文件放在 `core/media/src/test/kotlin/com/opencamera/core/media/`
- 每个测试类头部用注释说明覆盖行为和不适合单测的行为
- 重点测试 JSON parser 的边界情况（特殊字符、空对象、缺少字段、类型错误）
- 只测试 public API 和 observable behavior

### 验证
```bash
rtk ./gradlew --no-daemon :core:device:test :core:media:test
```

### 完成后
- commit 测试文件
- 更新 coordinator status 为 completed
- 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh mark-state 02-device-media-pure-tests completed --commit <commit-sha> --verification ":core:device:test :core:media:test: PASSED"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh advance --from 02-device-media-pure-tests
```

---

## Package: 03-app-logic-tests - App Layer Pure Logic Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/packages/03-app-logic-tests.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/03-app-logic-tests.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 03-app-logic-tests`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh

你被授权为 `app` 模块中 3 个可纯逻辑测试的未覆盖类补全单元测试。

### 目标类
1. `smartFilterResolutionOptions` (在 `app/src/main/java/com/opencamera/app/camera/ResolutionFilterUtils.kt`) - internal pure function
2. `galleryOpenTargetFor` (在 `app/src/main/java/com/opencamera/app/GalleryOpenTarget.kt`) - internal pure function
3. `DeviceCapabilitiesEffectQuery` (在 `app/src/main/java/com/opencamera/app/DeviceEffectCapabilityQuery.kt`)

### 测试要求
- 测试文件放在 `app/src/test/java/com/opencamera/app/` 下
- `ResolutionFilterUtils` 的函数是 `internal`，同模块 test 可以直接访问
- `GalleryOpenTarget` 测试需要构造 `ThumbnailSource`、`SavedMediaType` 等纯数据类作为输入
- `DeviceCapabilitiesEffectQuery` 测试需要用 fake `DeviceCapabilities` 对象
- 每个测试类头部用注释说明覆盖行为和不适合单测的行为
- 只测试 public/internal API 的 observable behavior

### 验证
```bash
rtk ./gradlew --no-daemon :app:testDebugUnitTest --tests "com.opencamera.app.ResolutionFilterUtilsTest" --tests "com.opencamera.app.GalleryOpenTargetLogicTest" --tests "com.opencamera.app.DeviceCapabilitiesEffectQueryTest"
```

### 完成后
- commit 测试文件
- 更新 coordinator status 为 completed
- 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh mark-state 03-app-logic-tests completed --commit <commit-sha> --verification ":app:testDebugUnitTest (3 test classes): PASSED"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh advance --from 03-app-logic-tests
```

---

## Package: 04-app-mixed-tests - App Layer Mixed Tests

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/packages/04-app-mixed-tests.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/04-app-mixed-tests.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 04-app-mixed-tests`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh

你被授权为 `app` 模块中有计算属性的数据类补全测试。

### 目标类
1. `SessionUiRenderContracts` (在 `app/src/main/java/com/opencamera/app/SessionUiRenderContracts.kt`) - 包含 `SettingsControlRenderModel.isInteractive`、`buttonLabel` 等 computed properties
2. `SettingsTab` (在 `app/src/main/java/com/opencamera/app/SettingsTab.kt`) - 简单枚举

### 测试要求
- 测试文件放在 `app/src/test/java/com/opencamera/app/` 下
- 利用 `unitTests.isReturnDefaultValues = true` 处理 Android 框架默认值
- 重点测试 `isInteractive` 的各种条件组合
- 重点测试 `buttonLabel` 的格式化逻辑
- `SettingsTab` 可合并到同一测试文件
- 每个测试类头部用注释说明覆盖行为和不适合单测的行为

### 验证
```bash
rtk ./gradlew --no-daemon :app:testDebugUnitTest --tests "com.opencamera.app.SessionUiRenderContractsTest"
```

### 完成后
- commit 测试文件
- 更新 coordinator status 为 completed
- 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh mark-state 04-app-mixed-tests completed --commit <commit-sha> --verification ":app:testDebugUnitTest (SessionUiRenderContractsTest): PASSED"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh advance --from 04-app-mixed-tests
```

---

## Package: 05-testability-audit - High-Risk Class Testability Audit

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/packages/05-testability-audit.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/05-testability-audit.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 05-testability-audit`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh

你被授权扫描高风险 Android/CameraX/UI 类，输出 testability 审计报告。**不写测试、不修改源码**，仅产出结构化分析文档。

### 审计目标（12 个类）
1. `GestureRouter` - 依赖 Context, GestureDetector, ScaleGestureDetector
2. `CameraOrientationMonitor` - 依赖 Context, OrientationEventListener
3. `OrientationContentRotator` - 依赖 View, ObjectAnimator
4. `VideoFrameExtractor` - 依赖 Context, MediaMetadataRetriever, Bitmap
5. `GalleryLauncher` - 依赖 AppCompatActivity, FileProvider
6. `SharedPreferencesPersistedSettingsStore` - 依赖 Context, SharedPreferences
7. `CameraXCaptureAdapter` - 深度 CameraX 依赖
8. `CameraDeviceAdapter` - 相机设备适配器
9. `CameraXLivePreviewFrameSource` - 实时预览
10. `MainActivity` - Activity 生命周期
11. `MainActivityViews` / `MainActivityRenderer` - View 层
12. `CockpitSurfaceRenderer` / `PreviewOverlayView` - SurfaceView/自定义 View

### 审计产出
对每个类产出：
- **testability 评级**: A (easy) / B (moderate) / C (hard) / D (not feasible)
- **阻塞因素**: 具体依赖
- **可测试子集**: 如果有纯逻辑方法可以提取
- **重构建议**: DI/接口提取/策略模式
- **ROI 评估**: 重构成本 vs 测试收益

### 输出
在 scratch 目录下产出 `audit-report.md`。

### 验证
```bash
test -s "$(bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 05-testability-audit)/audit-report.md"
```

### 完成后
- 更新 coordinator status 为 completed
- 调用 advance

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh mark-state 05-testability-audit completed --verification "audit-report.md exists and non-empty"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh advance --from 05-testability-audit
```

---

## Package: 99-finalize - Integration & Merge

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh

你是 finalize agent。负责合并所有测试包到集成分支，运行全量测试验证，合并回 mainline。

### 步骤
1. 读取 INDEX、graph、所有 package docs、status files 和 state.tsv。
2. 运行 `bash launchers/orchestrate.sh verify-finalize`。
3. 验证每个功能包的 acceptance criteria、changed files、branch/commit。
4. 创建或更新集成分支 `agent/test-tranche/integration`。
5. 按顺序合并：01 → 02 → 03 → 04 → 05。
6. 运行集成验证：
   ```bash
   rtk ./gradlew --no-daemon :core:settings:test :core:device:test :core:media:test :app:testDebugUnitTest
   ```
7. 合并集成分支回 mainline。
8. 产出 FINAL_REPORT.md。
9. 清理 worktree/branch。

### 验证
```bash
rtk ./gradlew --no-daemon :core:settings:test :core:device:test :core:media:test :app:testDebugUnitTest
```

### 完成后
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "integration tests: PASSED"
```

---
