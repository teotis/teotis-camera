# Package 03 - App Layer Pure Logic Tests

## Package ID
`03-app-logic-tests`

## Goal
为 `app` 模块中可纯逻辑测试的未覆盖类补全单元测试。

## Target Classes

### 1. ResolutionFilterUtils (`app/.../camera/ResolutionFilterUtils.kt`)
- **Risk**: LOW - pure function `smartFilterResolutionOptions()`，internal 但同模块 test 可访问
- **Testable behaviors**:
  - 空输入返回空列表
  - ≤3 个选项原样返回（去重后）
  - >3 个选项返回 highest/medium/lowest 三档
  - 去重逻辑（相同 width+height 只保留一个）
  - 结果按像素数降序排列
  - medium 选择最接近中间值的选项
  - 单元素输入返回单元素列表
- **Not suitable for unit test**: 无

### 2. GalleryOpenTarget (`app/.../GalleryOpenTarget.kt`)
- **Risk**: LOW - pure function `galleryOpenTargetFor()`
- **Testable behaviors**:
  - SavedMedia + content:// renderUri → CONTENT_URI 类型
  - SavedMedia + file:// renderUri → ABSOLUTE_FILE 类型
  - SavedMedia + 绝对路径 outputPath → ABSOLUTE_FILE 类型
  - SavedMedia + VIDEO → mimeType "video/*"
  - SavedMedia + PHOTO → mimeType "image/*"
  - PreviewSnapshot → mimeType "image/*"，kind ABSOLUTE_FILE
  - null source → null
  - 空 renderUri 回退到 outputPath
  - 非 content:// 且非 file:// 且非绝对路径 → null
- **Not suitable for unit test**: 无（需要构造 ThumbnailSource/SavedMediaType 数据，但都是纯数据类）

### 3. DeviceCapabilitiesEffectQuery (`app/.../DeviceEffectCapabilityQuery.kt`)
- **Risk**: LOW - 简单适配器
- **Testable behaviors**:
  - supportsPortraitDepth() 委托到 capabilities.supportsPortraitDepthEffect
  - supportsDocumentGeometry() 委托到 capabilities.supportsDocumentScanEnhancement
  - supportsManualControls() 委托到 capabilities.supportsAppliedManualControls
  - asEffectCapabilityQuery() 扩展函数返回正确类型
- **Not suitable for unit test**: 无

## Allowed Paths
- `app/src/test/java/com/opencamera/app/`
- `app/src/main/java/com/opencamera/app/camera/ResolutionFilterUtils.kt` (read only)
- `app/src/main/java/com/opencamera/app/GalleryOpenTarget.kt` (read only)
- `app/src/main/java/com/opencamera/app/DeviceEffectCapabilityQuery.kt` (read only)

## Dependencies
none

## Verification Commands
```bash
rtk ./gradlew --no-daemon :app:testDebugUnitTest --tests "com.opencamera.app.ResolutionFilterUtilsTest" --tests "com.opencamera.app.GalleryOpenTargetLogicTest" --tests "com.opencamera.app.DeviceCapabilitiesEffectQueryTest"
```

## Acceptance Criteria
- [ ] ResolutionFilterUtilsTest.kt 创建并全部通过
- [ ] GalleryOpenTargetLogicTest.kt 创建并全部通过
- [ ] DeviceCapabilitiesEffectQueryTest.kt 创建并全部通过
- [ ] 所有测试在 `:app:testDebugUnitTest` 中稳定通过
- [ ] 每个测试类头部注释说明覆盖行为和不适合单测的行为

## Branch/Worktree Policy
- Branch: `agent/test-tranche/03-app-logic-tests`
- Worktree: `.claude/worktrees/test-tranche/03-app-logic-tests`
