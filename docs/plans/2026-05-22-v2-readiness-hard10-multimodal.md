# OpenCamera 2.0 准入：高难 10% 与多模态限定任务

> 该文档由“多模态/高阶主控 agent”独占执行；非多模态 agent 不承接。当前阶段先完成判据和证据拼接方案；真实截图、录屏、保存媒体文件到位后再执行视觉/成片仲裁。

## 1. 任务边界

本任务只做最难且跨域耦合最强的部分：

1. 统一 2.0 判据：设计语义、交互语法、状态机、产物链路。
2. 多模态视觉审查：界面逻辑一致性、状态可感知性、横竖屏真实观感、保存图可信度。
3. 端到端证据拼接：输入动作 -> session/device/media -> 输出可验证。
4. 决策级结论：`GO / CONDITIONAL GO / NO GO`。

非目标：

- 不代替 A-F 非多模态 agent 做普通静态检查。
- 不在无截图/录屏/保存图时假装完成视觉判断。
- 不新增 stage，不批准 2.0 实现阶段启动。
- 不修改生产代码；本任务是审查和分发设计。

## 2. 主控 Readiness Matrix

主控最终输出必须至少包含以下矩阵。非多模态 agent 的发现都要汇入对应行。

| 维度 | 主控判断问题 | 必要证据 | 多模态限定项 | 判定 |
| --- | --- | --- | --- | --- |
| UI 逻辑 | 第一屏是否是稳定 cockpit，而不是面板堆叠 | `v2_ui`、XML、render model、A 组表 | 截图检查遮挡、层级、对比、横屏旋转 | Pass/Risk/Fail |
| 交互 | 点击、返回、外点关闭、横屏命中是否可预期 | B 组时序、gesture tests | 录屏看误触、迟滞、面板遮挡 | Pass/Risk/Fail |
| 功能 | 可见入口是否有真实 owner 和降级语义 | C 组功能矩阵、contracts/tests | 视觉确认状态是否被用户理解 | Pass/Risk/Fail |
| IO | 拍照/录像/缩略图/媒体打开是否通畅 | D 组链路表、media tests/logs | 保存 JPEG/视频/缩略图对比 | Pass/Risk/Fail |
| 稳定 | 权限、前后台、首帧、thermal、provider stall 是否可恢复/可诊断 | E 组脚本和 diagnostics | 真机录屏或日志复核恢复体验 | Pass/Risk/Fail |

## 3. 多模态视觉审查

### 3.1 输入材料

需要向用户或实现 agent 收集：

- 最新 APK 版本号或构建 commit。
- 竖屏第一屏截图：Photo/Video/Pro/Portrait 至少各一张。
- 横屏截图或录屏：横屏后控件是否只旋转文字/图标，预览框是否横屏化。
- 面板截图：风格、快捷、Dev、设置、色彩实验室。
- 拍照后 3 秒录屏：缩略图从按下快门到最终保存的变化。
- 保存 JPEG 原图和后处理图：含水印、滤镜/色彩实验室、画幅裁切各一组。
- 录像开始/停止录屏和输出文件可打开证据。

### 3.2 视觉判定标准

| 项 | Pass | Risk/Fail |
| --- | --- | --- |
| 顶部栏 | 左侧应用名，中部靠右色彩实验室，最右设置；不拼接“点号 模式名” | 色彩实验室跑到侧栏；设置入口层级混乱 |
| 侧栏 | 仅风格、快捷、Dev；按钮短、可读、可点 | 同时塞设置/色彩实验室；二级面板遮挡主操作 |
| 底部 cockpit | 缩略图、快门、镜头/变焦平衡；快门有明确按钮感 | 快门像普通图形，缩略图/文字挤压快门 |
| 模式栏 | 文字清晰，命中区域稳定，选中态不靠长文案解释 | 误触频繁，窄屏省略影响理解 |
| 横屏 | 布局语义不变，按钮/文字按用户要求旋转，预览成像区域对齐 | 控件横飞、预览偏移、tap/grid/frame 不同源 |
| 色彩实验室 | 二维调色板为核心，横轴色彩、纵轴影调，预览/成片语义一致 | 退回专业参数列表，或只改 UI 不进成片 |

## 4. IO 链路端到端归因

主控必须把 D 组文本链路和多模态材料拼成四条金路径。

### 4.1 标准拍照

证据链：

```text
Shutter tap
-> SessionIntent / app action
-> DefaultCameraSession state/effect
-> Device shot request / CameraXCaptureAdapter
-> Media pipeline / postprocessor
-> ThumbnailRenderCommand
-> saved media visible/openable
```

Fail 条件：

- 文件保存失败或不可打开。
- 缩略图先显示错误版本再跳变，且无明确 pending 策略。
- 水印/滤镜/色彩实验室 metadata 到了但成片没有效果。

### 4.2 录像开始/停止

证据链：

```text
Record tap
-> recording state visible
-> device recording starts
-> stop tap
-> media output saved
-> UI saved/thumbnail feedback
```

Fail 条件：

- 开始/停止无明确状态。
- 输出路径或 sidecar 写入失败。
- 录像期间切模式/切镜头导致隐式状态错乱。

### 4.3 模式切换后拍照

证据链：

```text
Mode tap
-> exact mode selected
-> mode declaration / capture policy updates
-> shot metadata reflects new mode
-> output result matches mode policy or explicit degraded state
```

Fail 条件：

- 点击某模式选到相邻模式。
- 模式 UI 变了但 capture policy 未变。
- unsupported/degraded 没有用户可见提示。

### 4.4 权限恢复后再次拍照

证据链：

```text
Permission denied / detached
-> user grants permission or returns foreground
-> recovery bind requested
-> first frame arrives or preview stall reported
-> shutter enabled only when safe
-> saved output works
```

Fail 条件：

- 预览恢复但快门不可用且无说明。
- recovery 失败没有 diagnostics。
- 首帧卡死无 watchdog 或用户反馈。

## 5. 高难归因规则

### 5.1 文档、代码、截图冲突时

优先级：

1. 用户可见行为和真实输出。
2. 运行时日志、测试、diagnostics。
3. 代码契约和状态机。
4. 设计文档。

设计文档只能定义目标，不能覆盖真实行为。

### 5.2 架构正确但体验断裂时

按用户体验判风险。例如：session 正确保存文件，但缩略图回退导致用户以为保存失败，应至少 P1/P2，而不是“架构通过”。

### 5.3 体验正常但链路不可证明时

按证据不足处理，不判 `GO`。示例：截图看起来拍照成功，但没有媒体文件、metadata、日志或测试支撑，只能判 `Risk`。

## 6. 难点与破解策略

| 难点 | 现象 | 破解策略 |
| --- | --- | --- |
| 跨文档版本漂移 | 设计文档、代码实现、测试断言不在同一语义版本 | 以用户可见行为为第一真值，要求每个结论标证据日期/文件 |
| 视觉问题难量化 | UI 讨论容易主观化 | 拆成可读、可点、可理解、无遮挡、状态显隐、输出一致 |
| 真机问题难本地复现 | provider death、长稳、thermal 依赖设备 | 标注 `需真机确认`，不混为已通过 |
| 多 agent 并发冲突 | 多人同时改 `MainActivity.kt`/XML | 本批只审查，后续实现指定单一集成 owner |
| 厂商对标误导 | 想学 vivo/Apple 但无私有能力 | 学产品语义和降级表达，不承诺私有算法质量 |

## 7. 输出模板

```markdown
# OpenCamera 2.0 Readiness 主控结论

## Decision

- Verdict: GO / CONDITIONAL GO / NO GO
- Date:
- APK / commit:
- Evidence completeness: Complete / Partial / Insufficient

## Readiness Matrix

| Dimension | Result | P0 | P1 | Key Evidence | Missing Evidence |
| --- | --- | --- | --- | --- | --- |

## Top P0/P1 Risks

| Priority | Title | Evidence | Owner | Fix direction | Retest |
| --- | --- | --- | --- | --- | --- |

## Four Golden Paths

### Photo Capture
### Video Record
### Mode Switch + Capture
### Permission Recovery + Capture

## Visual And Output QA

## 2-Week Closure Plan
```

## 8. 当前主控初步结论

在没有最新 APK 视觉材料、保存媒体文件和真机日志前，主控不能宣称项目已整体进入 `GO`。当前最稳妥的设计执行方案是：

1. 先分发 A-E 完成非多模态采证。
2. 主控同步建立矩阵并等待截图/录屏/保存图。
3. F 组汇总门禁报告后，由主控做最终仲裁。

预设结论倾向：`CONDITIONAL GO` 审查流程启动；最终是否升级为 `GO` 取决于 D 组 IO 金路径和多模态保存图/缩略图复核。
