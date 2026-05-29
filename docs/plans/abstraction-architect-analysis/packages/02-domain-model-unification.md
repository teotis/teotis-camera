# Package 02 - Domain Model Unification

## Task

检测 OpenCamera 中重复的领域表示、可合并的类型、缺失的不变量约束。找出同一概念在不同模块中有不同表示的情况。

## Analysis Focus

1. **重复领域表示**
   - 同一概念（如 zoom level、capture request、preview state）在不同模块中有不同定义？
   - 数据类/密封类是否表达了相同语义但结构不同？

2. **缺失不变量约束**
   - 哪些类型应该有不变量但没有（如 zoom range 应该有 bounds check）？
   - 哪些状态组合是非法的但没有类型级保证？

3. **可合并的类型**
   - 哪些接口/抽象类表达了相似职责？
   - 哪些 data class 可以统一为一个通用表示？

## Allowed Paths

- Read: all `*.kt` files under `app/`, `core/`, `feature/`
- Write: `docs/plans/abstraction-architect-analysis/status/02-domain-model-unification.md`

## Forbidden Paths

- Any source code modification

## Verification Commands

```bash
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 重复表示清单：每个包含涉及的类型、文件路径、语义描述
- 缺失不变量清单：每个包含类型、应有约束、当前状态
- 可合并类型建议：每个包含涉及的类型、合并理由
- 至少 3 组具体的重复表示（如果存在）

## Acceptance Criteria

- [ ] 扫描了所有模块的数据类、密封类、接口定义
- [ ] 比较了跨模块的类型名称和结构相似性
- [ ] 检查了类型中的约束逻辑（init 块、require、check）
- [ ] 输出格式化的发现清单到 status 文件
- [ ] 无源码修改
