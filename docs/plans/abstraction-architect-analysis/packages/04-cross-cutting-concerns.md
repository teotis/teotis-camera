# Package 04 - Cross-Cutting Concerns

## Task

检测横切关注点（日志、配置、可观测性、诊断、恢复）的散落耦合模式。这些关注点应该有集中的治理点，而不是分散在各处。

## Analysis Focus

1. **日志/诊断散落**
   - 日志调用是否遵循统一模式？
   - 诊断信息是否有集中收集点？
   - 错误信息格式是否一致？

2. **配置管理**
   - 设置/配置是否有多处读取点导致不一致风险？
   - Feature flags / capability 查询是否统一？

3. **可观测性**
   - Trace/metrics 是否有统一的注入点？
   - 性能监控是否分散在各模块？

4. **恢复/重试模式**
   - 错误恢复逻辑是否重复出现在多处？
   - 重试策略是否统一？

## Allowed Paths

- Read: all `*.kt` files under `app/`, `core/`, `feature/`
- Write: `docs/plans/abstraction-architect-analysis/status/04-cross-cutting-concerns.md`

## Forbidden Paths

- Any source code modification

## Verification Commands

```bash
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 散落模式清单：每个包含模式类型、涉及文件列表、当前实现方式
- 集中化建议：每个包含建议的集中点、受益模块、迁移难度
- 至少 3 类横切关注点的详细分析

## Acceptance Criteria

- [ ] 扫描了所有模块的日志/诊断模式
- [ ] 检查了配置/设置的读取模式
- [ ] 识别了恢复/重试的重复逻辑
- [ ] 输出格式化的发现清单到 status 文件
- [ ] 无源码修改
