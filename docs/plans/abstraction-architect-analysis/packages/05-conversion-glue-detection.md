# Package 05 - Conversion Glue Detection

## Task

检测模块间不必要的映射层、接口转换开销和胶水代码。这些通常是架构边界不清晰的症状。

## Analysis Focus

1. **接口映射开销**
   - 哪些地方需要在两个相似接口间做转换？
   - Adapter/Wrapper 模式是否过度使用？

2. **数据转换链**
   - 数据从 UI 到 Device 到 Media 的转换链是否过长？
   - 每层是否都添加了必要的语义，还是只是搬运？

3. **类型擦除/装箱**
   - 是否有类型信息在传递中丢失（如 Any/泛型擦除）？
   - 是否有不必要的装箱/拆箱？

4. **回调/监听器链**
   - 事件传递是否经过过多中间层？
   - 是否有可以扁平化的回调链？

## Dependencies

- 01-architecture-boundary-integrity (需要了解边界违规位置)
- 02-domain-model-unification (需要了解重复表示)
- 03-session-kernel-invariants (需要了解状态转换路径)
- 04-cross-cutting-concerns (需要了解横切耦合点)

## Allowed Paths

- Read: all `*.kt` files under `app/`, `core/`, `feature/`
- Write: `docs/plans/abstraction-architect-analysis/status/05-conversion-glue-detection.md`

## Forbidden Paths

- Any source code modification

## Verification Commands

```bash
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 胶水代码清单：每个包含位置、涉及的类型、转换目的、建议优化
- 转换链分析：关键数据流的完整转换路径图
- 回调链分析：主要事件流的监听器传递路径

## Acceptance Criteria

- [ ] 分析了模块间的 adapter/wrapper 模式
- [ ] 追踪了关键数据流的转换链
- [ ] 检查了回调/监听器的传递路径
- [ ] 输出格式化的发现清单到 status 文件
- [ ] 无源码修改
