# 并行任务 F：2.0 准入门禁汇总报告

> 非多模态任务。汇总 A-E 组结论，形成可决策门禁报告草案，供多模态/高难主控最终仲裁。

## 1. 目标

把 A-E 的审查输出收束成一个门禁报告，回答：

- 项目是否可整体进入 2.0 标准？
- 如果不能，阻断项是什么？
- 如果可以 conditional go，2 周内必须清零什么？
- 哪些事项必须等待多模态或真机材料？

F 组不重新做 A-E 的详细审查；只做证据归并、风险去重、结论草案。

## 2. 输入

必须收集：

- `UI-Static-Audit.md`
- `Interaction-Flow-Audit.md`
- `Feature-Availability-Audit.md`
- `IO-Chain-Audit.md`
- `Stability-Observability-Audit.md`
- `codex/agent_plans/2026-05-22-v2-readiness-master-plan.md`
- `codex/agent_plans/2026-05-22-v2-readiness-hard10-multimodal.md`
- 当前 `codex/documentation.md`
- Stage 7 验证命令输出摘要

若 A-E 任一缺失，报告必须标注 `Evidence incomplete`，不能给最终 `GO`。

## 3. 输出结构

生成 `V2-Readiness-Release-Gate-Report.md`：

```markdown
# OpenCamera 2.0 Readiness Release Gate Report

## Executive Summary

- Recommendation: GO / CONDITIONAL GO / NO GO / INSUFFICIENT EVIDENCE
- Evidence completeness:
- P0 count:
- P1 count:
- Main blockers:

## Gate Checklist

| Gate | Result | Evidence | Missing evidence |
| --- | --- | --- | --- |
| UI design logic coherent | | | |
| Interaction smooth | | | |
| Reachable features effective | | | |
| IO chain clear | | | |
| Stability/observability supports 2.0 | | | |

## P0/P1 Risk Ledger

| Priority | Title | Domain | Evidence | Owner | Fix direction | Retest |
| --- | --- | --- | --- | --- | --- | --- |

## Dedupe Notes

## External Dependencies

## Recommended Verdict

## 1-Week Closure Plan

## 2-Week Closure Plan

## Handoff To Multimodal主控
```

## 4. 去重规则

同一根因只保留一个主风险，其他表现挂为 symptoms。

示例：

- `缩略图无水印跳变`、`滤镜未进成片`、`保存后缩略图回退` 可能同属媒体结果/反馈链路，应由 D 组确认是否同根。
- `色彩实验室入口在侧栏`、`侧栏混乱`、`设置位置不对` 可能同属 IA 问题，应由 A 组归并。
- `横屏交互不好`、`预览偏下`、`网格不准`、`画幅长边错误` 可能同属 preview active rect 几何问题，应由 B/D 与多模态主控共同确认。

## 5. 判定规则

| Recommendation | 条件 |
| --- | --- |
| `GO` | A-E 完整，P0=0，主链路 P1<=2，多模态/真机缺口不影响核心交付 |
| `CONDITIONAL GO` | P0=0，P1 可在 2 周内清零，有明确 owner 和复测命令 |
| `NO GO` | 任一 P0，或拍照/录像/保存/权限恢复金路径不可证明通畅 |
| `INSUFFICIENT EVIDENCE` | A-E 缺失关键报告，或无最新 APK/日志/媒体材料无法判断 |

## 6. 门禁 checklist

| Gate | 必须回答 |
| --- | --- |
| UI design logic coherent | 当前 UI 是否符合顶部/侧栏/cockpit/面板 2.0 口径？ |
| Interaction smooth | exact tap、面板开合、权限恢复、横屏命中是否可预期？ |
| Reachable features effective | 可见入口是否真正影响 session/device/media 或明确 degraded？ |
| IO chain clear | 拍照、录像、模式切换拍摄、恢复后拍摄是否能从输入走到输出？ |
| Stability supports 2.0 | Stage 7 owner 是否通过脚本且剩余风险已外部依赖化？ |
| Multimodal pending | 哪些结论必须等截图、录屏、保存图？ |

## 7. 质量要求

- 结论必须可追溯到 A-E 或主控文档的证据。
- 不接受“感觉可用”描述。
- 对外部依赖项（真机、平台、权限、截图/录屏/保存图）单列。
- 不掩盖 Evidence incomplete；没有证据时写 `Insufficient evidence`。
- 不替主控做最终视觉/成片仲裁。

## 8. 建议复测命令汇总

F 组可把 A-E 的命令汇总成一个最小门禁复测清单：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.CockpitPanelRouteTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./scripts/verify_stage_7_observability.sh
```

如果任一命令因历史旧期望失败，必须引用 A-E 对应说明，不得简单删掉。
