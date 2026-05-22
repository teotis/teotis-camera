# 并行任务 D：输入输出链路通畅性审查（非多模态）

## 1. 目标

验证从输入意图到媒体输出的链路完整、可追踪、无静默失败。

## 2. 审查范围

- Input：UI intents / actions
- Kernel：session state/effect
- Device：request translation / capability handling
- Media：capture output / thumbnail / metadata
- Feedback：UI result presentation

## 3. 必测链路

1. Photo capture E2E
2. Video record E2E
3. Mode switch + capture E2E
4. Recovery 后再次 capture E2E

## 4. 产出

- `IO-Chain-Audit.md`
- 链路时序图（文本即可）
- 断链风险清单（含日志/测试证据定位）

## 5. 验证建议

- 先跑最小相关测试，再跑 stage 7 脚本
- 对不可本地复现项标注“需真机验证”

