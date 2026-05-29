# Package 01 - Architecture Boundary Integrity

## Task

检测 OpenCamera 四层架构（Mode Plugin → Session Kernel → Device Adapter → Media Pipeline）的边界违规。找出所有违反架构契约的依赖方向和隐藏耦合。

## Analysis Focus

1. **层间依赖方向违规**
   - Mode Plugin 是否直接调用了 CameraX/Camera2/HAL？
   - UI 层是否绕过 Session Kernel 直接驱动 camera runtime？
   - Device Adapter 是否向上依赖 Session 或 Mode？

2. **Session Kernel 边界**
   - 是否存在"第二个隐藏 Session Kernel"在 coordinator/manager/bridge/UI/adapter 中？
   - Session 状态是否在多处被修改（应只有 Session Kernel 拥有）？

3. **模块可见性**
   - core 模块的 internal 类是否被 app 或 feature 模块访问？
   - 模块间依赖是否通过公开契约（接口/抽象类）而非实现类？

## Allowed Paths

- Read: all `*.kt` files under `app/`, `core/`, `feature/`
- Write: `docs/plans/abstraction-architect-analysis/status/01-architecture-boundary-integrity.md`

## Forbidden Paths

- Any source code modification

## Verification Commands

```bash
# 无源码修改验证
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 违规清单：每个违规包含文件路径、行号、违规类型、违反的架构规则
- 按严重程度分类：critical / warning / info
- 至少 5 个具体示例（如果存在）
- 无违规时明确声明

## Acceptance Criteria

- [ ] 扫描了 `app/`, `core/`, `feature/` 下所有 Kotlin 源文件
- [ ] 检查了 import 语句中的跨层依赖
- [ ] 检查了类实例化和方法调用中的边界违规
- [ ] 输出格式化的发现清单到 status 文件
- [ ] 无源码修改
