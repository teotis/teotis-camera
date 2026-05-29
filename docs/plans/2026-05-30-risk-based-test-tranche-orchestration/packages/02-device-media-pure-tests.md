# Package 02 - Device & Media Pure Logic Tests

## Package ID
`02-device-media-pure-tests`

## Goal
为 `core/device` 和 `core/media` 模块中未测试的纯逻辑类补全单元测试。

## Target Classes

### 1. MultiFrameCaptureExecutionPlanner (`core/device/.../MultiFrameCaptureExecutionPlanner.kt`)
- **Risk**: LOW - 纯逻辑，无 Android 依赖
- **Testable behaviors**:
  - plan() 对单帧请求生成 1 步计划
  - plan() 对多帧请求生成正确步数
  - 最后一帧标记为 FINAL_OUTPUT，其余为 TEMPORARY
  - frameCount 被 coerceAtLeast(1)
  - interFrameDelayMillis 被 coerceAtLeast(0)
  - totalFrameCount / temporaryFrameCount / finalFrameIndex 计算正确
  - 非 MULTI_FRAME_CAPTURE 请求抛出 IllegalArgumentException
- **Not suitable for unit test**: 无

### 2. ReversibleWatermarkArchiveManifest (`core/media/.../ReversibleWatermarkArchive.kt`)
- **Risk**: LOW - 纯 JSON 序列化/反序列化（手写 parser，不依赖 Gson/Moshi）
- **Testable behaviors**:
  - toJson() 生成合法 JSON 字符串
  - fromJson() 解析合法 JSON 还原为 Manifest
  - fromJson() 对缺少必需字段抛出 IllegalArgumentException
  - fromJson() 对非法 schema/version/container 抛出异常
  - fromJson() 对负 payloadLength 抛出异常
  - toJson → fromJson round-trip 一致性
  - 特殊字符转义（引号、反斜杠、换行等）
  - parseJsonToMap() 内部 JSON 解析器的边界情况
- **Not suitable for unit test**: 无

### 3. sha256Hex (`core/media/.../ReversibleWatermarkArchive.kt`)
- **Risk**: LOW - 纯函数
- **Testable behaviors**:
  - 空字节数组的 sha256
  - 已知输入的 sha256 与预期值匹配
  - 输出长度固定为 64 字符十六进制
- **Not suitable for unit test**: 无

### 4. MediaProcessorAvailability (`core/media/.../MediaProcessorAvailability.kt`)
- **Risk**: LOW - 纯数据类
- **Testable behaviors**:
  - ALL_AVAILABLE 所有字段为 true
  - NONE_AVAILABLE 所有字段为 false
  - 自定义构造各字段正确
- **Not suitable for unit test**: 无

## Allowed Paths
- `core/device/src/test/kotlin/com/opencamera/core/device/`
- `core/media/src/test/kotlin/com/opencamera/core/media/`
- `core/device/src/main/kotlin/com/opencamera/core/device/MultiFrameCaptureExecutionPlanner.kt` (read only)
- `core/media/src/main/kotlin/com/opencamera/core/media/ReversibleWatermarkArchive.kt` (read only)
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaProcessorAvailability.kt` (read only)

## Dependencies
none

## Verification Commands
```bash
rtk ./gradlew --no-daemon :core:device:test :core:media:test
```

## Acceptance Criteria
- [ ] MultiFrameCaptureExecutionPlannerTest.kt 创建并全部通过
- [ ] ReversibleWatermarkArchiveTest.kt 创建并全部通过
- [ ] MediaProcessorAvailabilityTest.kt 创建并全部通过
- [ ] 所有测试在对应模块 test task 中稳定通过
- [ ] 每个测试类头部注释说明覆盖行为和不适合单测的行为

## Branch/Worktree Policy
- Branch: `agent/test-tranche/02-device-media-pure-tests`
- Worktree: `.claude/worktrees/test-tranche/02-device-media-pure-tests`
