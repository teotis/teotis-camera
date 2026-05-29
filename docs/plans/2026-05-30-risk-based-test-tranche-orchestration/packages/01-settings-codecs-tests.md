# Package 01 - Settings Codecs & Defaults Tests

## Package ID
`01-settings-codecs-tests`

## Goal
为 `core/settings` 模块中 3 个未测试的纯逻辑类补全单元测试。

## Target Classes

### 1. FilterProfileShareCodec (`core/settings/.../SettingsShareCodecs.kt`)
- **Risk**: LOW - 纯文本序列化/反序列化，无 Android 依赖
- **Testable behaviors**:
  - export() 生成正确格式的序列化字符串
  - import() 解析合法字符串还原为 FilterProfile
  - import() 对非法头部抛出 IllegalArgumentException
  - import() 对畸形行抛出 IllegalArgumentException
  - export → import round-trip 保持数据一致
  - 各字段默认值处理（contrast=1f, saturation=1f 等）
- **Not suitable for unit test**: 无

### 2. ImportedFilterProfilesSerializer (`core/settings/.../SettingsShareCodecs.kt`)
- **Risk**: LOW - 纯文本编解码
- **Testable behaviors**:
  - serialize() 正确拼接多个 profile
  - deserialize() 正确拆分并还原
  - 空列表序列化/反序列化
  - builtIn profile 被过滤
  - round-trip 一致性
- **Not suitable for unit test**: 无

### 3. ManualCaptureDraftSerializer (`core/settings/.../SettingsShareCodecs.kt`)
- **Risk**: LOW - 纯文本编解码
- **Testable behaviors**:
  - serialize() 生成正确 key=value 格式
  - deserialize() 解析各参数（auto/null 处理）
  - null 输入 deserialize 返回默认值
  - round-trip 一致性
- **Not suitable for unit test**: 无

### 4. SettingsMetadataCodecs (`core/settings/.../SettingsMetadataCodecs.kt`)
- **Risk**: LOW - 纯数据转换函数
- **Testable behaviors**:
  - ManualCaptureParams.compactSummary() 格式
  - ManualCaptureParams.toMetadataTags() 各字段
  - FilterRenderSpec.toMetadataTags() / parseFilterRenderSpec() round-trip
  - PerceptualColorRecipe.toMetadataTags() / parsePerceptualColorRecipe() round-trip
  - parseFilterRenderSpec() 对缺失版本号返回 null
  - parsePerceptualColorRecipe() 对缺失 toneLift 返回 NEUTRAL
- **Not suitable for unit test**: 无

### 5. SettingsDefaults (`core/settings/.../SettingsDefaults.kt`)
- **Risk**: LOW - 纯配置数据
- **Testable behaviors**:
  - defaultFilterRenderSpecOrNull() 对已知 id 返回非 null
  - defaultFilterRenderSpecOrNull() 对未知 id 返回 null
  - DEFAULT_FILTER_PROFILES 列表非空
  - DEFAULT_FILTER_PROFILES 各项 id 唯一
  - DEFAULT_WATERMARK_TEMPLATES 各项 id 唯一
  - DEFAULT_WATERMARK_TEMPLATES 各项 tokenKeys 非空
- **Not suitable for unit test**: 无

## Allowed Paths
- `core/settings/src/test/kotlin/com/opencamera/core/settings/`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsShareCodecs.kt` (read only)
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsMetadataCodecs.kt` (read only)
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt` (read only)

## Dependencies
none

## Verification Commands
```bash
rtk ./gradlew --no-daemon :core:settings:test
```

## Acceptance Criteria
- [ ] SettingsShareCodecsTest.kt 创建并全部通过
- [ ] SettingsMetadataCodecsTest.kt 创建并全部通过
- [ ] SettingsDefaultsTest.kt 创建并全部通过
- [ ] 所有测试在 `:core:settings:test` 中稳定通过
- [ ] 每个测试类头部注释说明覆盖行为和不适合单测的行为

## Branch/Worktree Policy
- Branch: `agent/test-tranche/01-settings-codecs-tests`
- Worktree: `.claude/worktrees/test-tranche/01-settings-codecs-tests`
