# Package 04 - App Layer Mixed Tests (Computed Props + Config)

## Package ID
`04-app-mixed-tests`

## Goal
为 `app` 模块中有计算属性但依赖少量 Android 类型的数据类补全测试。需要利用 `unitTests.isReturnDefaultValues = true` 处理框架默认值。

## Target Classes

### 1. SessionUiRenderContracts (`app/.../SessionUiRenderContracts.kt`)
- **Risk**: MEDIUM - 数据类包含 computed properties (`isInteractive`, `buttonLabel`)，依赖 `PersistedSettingsAction` / `FeatureCatalogAction`（均为纯接口/数据类）
- **Testable behaviors**:
  - SettingsControlRenderModel.isInteractive 当 enabled=true + SUPPORTED + nextAction!=null 时为 true
  - SettingsControlRenderModel.isInteractive 当 enabled=false 时为 false
  - SettingsControlRenderModel.isInteractive 当 UNSUPPORTED 时为 false
  - SettingsControlRenderModel.isInteractive 当 nextAction=null 时为 false
  - SettingsControlRenderModel.buttonLabel 格式包含 label/value/availability
  - SettingsControlRenderModel.buttonLabel 当有 supportLabel 时包含 "•"
  - FeatureCatalogControlRenderModel.isInteractive 当 nextAction!=null 时为 true
  - FeatureCatalogControlRenderModel.buttonLabel 格式
  - SettingsControlAvailability 枚举值
- **Not suitable for unit test**: 无

### 2. SettingsTab (`app/.../SettingsTab.kt`)
- **Risk**: LOW - 简单枚举
- **Testable behaviors**:
  - 枚举值 COMMON/PHOTO/VIDEO 存在
  - values() 包含所有预期值
- **Not suitable for unit test**: 无（过于简单，可合并到其他测试类）

## Allowed Paths
- `app/src/test/java/com/opencamera/app/`
- `app/src/main/java/com/opencamera/app/SessionUiRenderContracts.kt` (read only)
- `app/src/main/java/com/opencamera/app/SettingsTab.kt` (read only)

## Dependencies
none

## Verification Commands
```bash
rtk ./gradlew --no-daemon :app:testDebugUnitTest --tests "com.opencamera.app.SessionUiRenderContractsTest"
```

## Acceptance Criteria
- [ ] SessionUiRenderContractsTest.kt 创建并全部通过
- [ ] 所有测试在 `:app:testDebugUnitTest` 中稳定通过
- [ ] 测试类头部注释说明覆盖行为和不适合单测的行为

## Branch/Worktree Policy
- Branch: `agent/test-tranche/04-app-mixed-tests`
- Worktree: `.claude/worktrees/test-tranche/04-app-mixed-tests`
