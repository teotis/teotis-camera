# Package 03 - Session Kernel Invariants

## Task

深度分析 `:core:session` 模块的状态机完整性、恢复缺口和隐式状态。这是项目的核心运行时，需要确保所有状态转换都有明确的不变量保证。

## Analysis Focus

1. **状态机完整性**
   - Session 状态的所有合法转换路径是否都有定义？
   - 是否存在未处理的状态转换（如从 ERROR 直接到 CAPTURING）？
   - 状态转换是否有 guard 条件？

2. **恢复机制**
   - 异常状态恢复路径是否覆盖所有故障模式？
   - 恢复后是否保证回到一致状态？
   - 是否有死锁/活锁风险？

3. **隐式状态**
   - 是否有状态没有通过状态机管理（如成员变量直接控制行为）？
   - 回调/监听器中的状态是否与主状态机同步？

## Allowed Paths

- Read: all `*.kt` files under `core/session/`, `app/src/main/java/com/opencamera/app/camera/`
- Write: `docs/plans/abstraction-architect-analysis/status/03-session-kernel-invariants.md`

## Forbidden Paths

- Any source code modification

## Verification Commands

```bash
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 状态转换矩阵：列出所有状态和转换条件
- 缺失转换清单：每个包含源状态、目标状态、触发条件
- 恢复缺口清单：每个包含故障场景、当前恢复行为、预期行为
- 隐式状态清单：每个包含变量位置、控制的行为、与主状态机的关系

## Acceptance Criteria

- [ ] 深入分析了 `core/session/` 中的状态定义和转换逻辑
- [ ] 检查了 `app/camera/` 中的 coordinator 对 session 状态的使用
- [ ] 识别了所有状态枚举/密封类及其转换
- [ ] 检查了错误处理和恢复路径
- [ ] 输出格式化的发现清单到 status 文件
- [ ] 无源码修改
