# OpenCamera 2.0 Readiness 主控审查结论

> 日期：2026-05-22  
> 主控任务：高难 10% 与多模态限定审查  
> 输入：UI / Interaction / Feature / IO / Stability / Release Gate 六份审计报告  
> 结论性质：本轮文本证据主控仲裁；多模态截图、录屏、保存媒体尚未到位

## Decision

- Verdict: **NO GO for overall 2.0 entry**
- Evidence completeness: **Partial**
- 原因：A-E 审计报告已存在且能支撑文本侧审查，但最新 APK 截图、横屏录屏、拍后缩略图录屏、保存 JPEG/视频输出样本、真机 provider/thermal/recovery 日志仍缺失。
- 说明：根目录 `V2-Readiness-Release-Gate-Report.md` 生成时认为 A-E 全部缺失；当前 A-E 报告已经存在，因此该 F 报告的 `INSUFFICIENT EVIDENCE` 输入状态已过期。本主控结论以当前 A-E 报告为准。

本轮不是“架构不可用”的失败，而是“2.0 整体准入尚未过门”。核心拍照、录像、模式切换、恢复链路在文本和测试证据上基本连通；阻断来自假入口/占位能力、后处理失败可观测性、UI 命名/i18n 与缺少多模态实证。

## Readiness Matrix

| Dimension | Result | P0/P1 主控归因 | Key Evidence | Missing Evidence |
| --- | --- | --- | --- | --- |
| UI design logic | Risk | UI 报告标 3 个 P0、4 个 P1；主控归一为 release blocker，非核心链路 P0 | 顶部栏/底部 cockpit/单一路由基本成立；硬编码文案、`ColorLab` 按钮打开 `LensLab` 内部路由、侧栏遗留设置资源 | 真实窄屏截图、横屏截图、面板遮挡截图 |
| Interaction smooth | Risk | 0 Fail；2 Risk：面板 trace 缺失、永久拒绝权限后无设置引导 | 7 条路径中 5 Pass、2 Risk；模式切换/变焦/拍照/录像主路径闭合 | 横屏误触录屏、面板开合录屏、权限永久拒绝真机流程 |
| Feature availability | Fail | 2 Fail：RAW 假入口、多帧合并占位；3 Risk：Live 静态降级、连续变焦未实现、部分降级未告知 | 32 Pass / 3 Risk / 2 Fail；大部分功能有 owner 与测试 | 用户可见降级提示截图，RAW/Live/Night 真机产物 |
| IO chain | Fail | 1 P0：后处理/sidecar 失败可能静默或阻断；3 P1：后处理失败仍报 saved、transaction 永远 success、orphaned failure 静默 | 四条金路径文本链路完整；Photo/Video/Mode switch/Recovery capture 均可追踪 | 保存 JPEG/视频样本、缩略图 3 秒录屏、Live sidecar 真机日志 |
| Stability/observability | Pass with external risks | 0 P0/P1；P2：humanistic 测试旧期望、perf budget 未真机校准、recovery retry 无退避 | Stage 7 核心 owner 通过；runtime issue/recovery/watchdog/thermal/diagnostics 证据完整 | provider death 注入、长稳循环、thermal chamber、多设备 perf 基线 |

## Top Blocking Risks

| Priority | Title | Domain | Evidence | 主控判断 | Required Closure |
| --- | --- | --- | --- | --- | --- |
| P0 | 后处理/sidecar 失败的用户可见性不足 | IO | `IO-Chain-Audit.md` 报告 `emitShotCompleted()` 可能吞掉 postprocess failure，transaction status 可能永远 success | 结果可信度问题，阻断 2.0 整体准入 | 后处理失败必须进入 `ShotFailed`、degraded result 或用户可见 warning；补单测和真机保存验证 |
| P0 | RAW 是假入口 | Feature | `Feature-Availability-Audit.md` 报告 RAW toggle 只进 metadata，无 DNG 输出 | 假入口，直接违反“可触及功能有效可用” | 实现真实 RAW/DNG，或 UI 明确标注 unsupported / saved-only 并从 2.0 主功能降级 |
| P0 | 夜景多帧合并是占位符 | Feature | 多帧 pipeline 仅记录输入并删除临时文件，无真实融合 | 假能力，影响 Night/Scenery 产品可信度 | 实现真实合并，或产品侧明确 degraded/placeholder，不把多帧效果作为 2.0 承诺 |
| P1 | Live Photo 始终静态降级且提示不足 | Feature/IO | adapter hardcodes `METADATA_ONLY`，`frameCount=0` | 可作为 degraded 功能存在，但必须告知 | UI 和 metadata 呈现 `still-only fallback`，补真机 sidecar 验证 |
| P1 | UI 命名和路由语义漂移 | UI | `buttonColorLabEntry` 打开内部 `LensLab`；`FilterLab/LensLab` 对应 `风格/色彩实验室` 映射隐式 | 容易导致后续 agent 接错入口 | 统一为 `StyleLab / ColorLab` 或写显式映射测试 |
| P1 | 权限永久拒绝后缺设置引导 | Interaction | `shouldShowRequestPermissionRationale` 两个分支逻辑相同 | 不是主链路断裂，但真实用户会卡住 | 增加永久拒绝检测与系统设置入口 |

## Four Golden Paths

### Photo Capture

文本链路完整：shutter -> session intent -> shot plan -> CameraX capture -> media postprocess -> thumbnail feedback。  
主控判定：**Risk/Fail boundary**。主路径可走通，但后处理失败、sidecar 失败、transaction success 语义不足会让“保存成功”与“真实结果可信”脱节。2.0 不能只证明能拍到文件，还要证明后处理结果、缩略图和用户反馈一致。

### Video Record

文本链路完整：start -> requesting -> recording -> stop -> stopping -> completed -> saved feedback。  
主控判定：**Risk**。录像状态机和 watchdog 证据较好；仍缺视频 sidecar/subtitle scoped storage 真机验证和输出文件可打开证据。

### Mode Switch + Capture

文本链路完整：mode tap -> exact mode selected -> controller switch -> capture policy -> output。  
主控判定：**Risk**。模式切换交互链路通过，但 Feature 报告指出 RAW、多帧、Live 等模式内能力存在“入口/声明强于真实执行”的问题。模式可切，不等于模式能力达到 2.0。

### Permission Recovery + Capture

文本链路完整：permission/host recovery -> preview bind -> first frame/stall -> capture。  
主控判定：**Pass with UX risk**。Stage 7 证据支持恢复主链；权限永久拒绝后缺设置引导，真机 provider death/long-run recovery 仍需外部验证。

## Visual And Output QA Status

本轮不能完成多模态最终仲裁，因为缺少以下材料：

- 最新 APK 版本或构建 commit。
- Photo/Video/Pro/Portrait 竖屏首屏截图。
- 横屏截图或录屏。
- 风格、快捷、Dev、设置、色彩实验室面板截图。
- 拍照后 3 秒缩略图录屏。
- 水印、滤镜/色彩实验室、画幅裁切保存 JPEG 样本。
- 录像开始/停止录屏和输出文件可打开证据。
- provider death、thermal、长稳恢复真机日志。

因此，UI 美观性、横屏真实手感、缩略图跳变、成片视觉一致性只能标 `Pending multimodal review`，不能判 `GO`。

## 本轮综合判断

### 已经比较稳的部分

- 四层主链路仍然清晰：Mode Plugin -> Session Kernel -> Device Adapter -> Media Pipeline。
- 拍照、录像、模式切换、恢复后拍摄的文本链路基本闭合。
- Stage 7 稳定性治理证据最好：runtime issue、recovery、防递归、preview stall、thermal、diagnostics 都有 owner 和测试。
- 色彩实验室、风格、滤镜等多数 2.0 上层能力已有纯 Kotlin 内核和测试，不再只是 UI 想法。

### 阻止整体进入 2.0 的部分

- 结果可信度不足：后处理失败、sidecar 失败、transaction success 与用户反馈之间还可能脱节。
- 假入口/占位能力未完全降级：RAW、多帧夜景、Live motion 是最典型的 2.0 风险。
- UI 仍有命名/i18n/路由漂移：不一定阻断主链路，但会阻断“设计逻辑自洽”的 2.0 标准。
- 多模态证据缺失：没有截图/录屏/保存媒体，不能最终判断 UI 和成片是否达到用户标准。

## Recommended Verdict

**本轮审查结论：NO GO for overall 2.0 entry.**

建议表述为：

> OpenCamera 已具备 2.0 审查的基础架构和大部分文本链路证据，但尚未达到“整体进入 2.0 标准”。当前应进入 P0/P1 收敛期，而不是宣布 2.0 准入。

## 2-Week Closure Plan

### Week 1：清零文本侧 P0

1. 处理 IO P0：后处理/sidecar 失败必须进入可观测结果，不允许静默吞没。
2. 处理 RAW：实现真实 RAW 或明确降级为 unsupported/saved-only，UI 不得像已生效功能。
3. 处理多帧夜景：实现真实合并或产品降级，不把 placeholder 当 2.0 能力。
4. 修复 humanistic mode 两个旧期望/回归测试，确保 Stage 7 脚本完整通过。
5. 统一 `StyleLab / ColorLab` 内部路由命名或补显式映射测试。

### Week 2：补齐真机和多模态证据

1. 收集最新 APK 的竖屏/横屏/面板截图。
2. 录制拍照后缩略图 3 秒变化。
3. 导出水印、滤镜/色彩实验室、画幅裁切保存 JPEG。
4. 验证录像输出和 sidecar/subtitle 写入。
5. 做权限永久拒绝、前后台恢复、provider stall 的真机复核。
6. 由主控重跑 Readiness Matrix，重新判定 `CONDITIONAL GO` 或 `GO`。
