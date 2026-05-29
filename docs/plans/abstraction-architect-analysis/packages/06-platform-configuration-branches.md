# Package 06 - Platform Configuration Branches

## Task

检测设备特定代码路径、平台分支模式和配置驱动的行为差异。这些分支是否可以统一为能力查询？

## Analysis Focus

1. **设备特定分支**
   - 是否有基于设备型号/品牌的硬编码分支？
   - 设备能力查询是否统一？

2. **平台版本分支**
   - Android API level 分支是否合理？
   - 是否有可以统一的版本适配？

3. **配置驱动行为**
   - 设置项如何影响运行时行为？
   - 配置组合是否有爆炸风险？

4. **能力图治理**
   - `:core:capability` 的能力定义是否覆盖所有设备差异？
   - 能力查询是否有 fallback 策略？

## Dependencies

- 01-architecture-boundary-integrity (需要了解边界违规)
- 02-domain-model-unification (需要了解能力表示)
- 03-session-kernel-invariants (需要了解设备适配)
- 04-cross-cutting-concerns (需要了解配置管理)

## Allowed Paths

- Read: all `*.kt` files under `app/`, `core/`, `feature/`
- Write: `docs/plans/abstraction-architect-analysis/status/06-platform-configuration-branches.md`

## Forbidden Paths

- Any source code modification

## Verification Commands

```bash
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 设备分支清单：每个包含位置、分支条件、建议统一方式
- 能力图覆盖分析：哪些设备差异已被能力图覆盖，哪些没有
- 配置组合风险：哪些设置组合可能导致意外行为

## Acceptance Criteria

- [ ] 扫描了设备特定的条件分支
- [ ] 检查了能力图的覆盖范围
- [ ] 分析了配置驱动的行为差异
- [ ] 输出格式化的发现清单到 status 文件
- [ ] 无源码修改
