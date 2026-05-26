# A8: Architecture Boundary Violations - 架构边界违规检测

## Objective

检测 OpenCamera 项目中的架构边界违规。使用 abstraction-architect 和自定义扫描工具，识别层次违规、职责混乱、依赖方向错误等问题。

## Analysis Scope

### 1. 层次违规检测
- UI 层直接访问设备层
- Mode Plugin 直接调用 CameraX/Camera2
- Session Kernel 外部的状态修改
- Media Pipeline 的职责越界

### 2. 职责混乱检测
- God Class 识别
- 职责重叠检测
- 职责缺失识别
- 职责分配不当

### 3. 依赖方向违规
- 稳定依赖原则违反
- 抽象依赖具体
- 高层依赖低层
- 循环依赖

### 4. 接口隔离违反
- 胖接口识别
- 接口污染
- 接口隔离原则违反
- 依赖倒置原则违反

## Analysis Tools

### Primary: abstraction-architect
- 架构边界分析
- 违规模式识别
- 修复建议

### Secondary: CodeGraph
- 依赖方向分析
- 调用链追踪
- 影响范围分析

### Tertiary: 自定义扫描
- 架构规则检查
- 边界违规检测
- 模式匹配

## Key Files to Analyze

### Architecture Boundaries
- `core/session/` vs `app/` - Session 和 App 边界
- `core/device/` vs `app/camera/` - Device 和 Camera 边界
- `core/mode/` vs `feature/` - Mode 和 Feature 边界
- `core/media/` vs `app/camera/` - Media 和 Camera 边界

### Cross-Cutting Concerns
- `core/settings/` - 设置
- `core/capability/` - 能力
- `core/effect/` - 效果

### Integration Points
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

## Analysis Output

产出文件：`output/08-architecture-boundary-violations.md`

### 报告结构
1. **执行摘要**：关键发现和建议
2. **层次违规**：检测到的层次违规
3. **职责混乱**：职责分配问题
4. **依赖方向**：依赖方向违规
5. **接口隔离**：接口隔离问题
6. **修复建议**：具体修复建议

## Analysis Guidelines

- **只读不写**：分析源代码，但不修改任何文件
- **数据驱动**：基于实际代码数据进行分析
- **具体示例**：提供具体的代码示例和位置
- **可操作性**：提供具体、可执行的建议

## Expected Findings

1. 是否存在跨层依赖
2. 职责分配是否清晰
3. 依赖方向是否正确
4. 接口隔离是否恰当
5. 架构边界是否稳定
6. 如何修复违规问题
