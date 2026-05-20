### 第三版：

- 第 1 阶段：新 `SessionKernel` 落地，打通拍照/录像两个主模式。

  - 目标：建立 `dispatch + state + effect` 主链路，UI 只消费状态、发送意图。

  - 关键产物：`CameraSession`、模式插件契约、真实预览/拍照/录像验证链路、最小 trace。
- 第 2 阶段：预览与会话稳定化。
  - 目标：把预览从“能显示”提升为“稳定、可恢复、可作为后续模式基础”。
  - 关键产物：首帧管理、前后台恢复、surface 丢失处理、权限中断处理、预览快照缩略图、预览性能指标。
- 第 3 阶段：设备层基础抽象成型。
  - 目标：让模式不再依赖具体 CameraX/Camera2 实现细节，建立未来承载 `MiuiCamera` 能力的设备边界。
  - 说明：这一阶段开始，`CameraX` 可以继续作为验证实现，但不能再作为唯一长期抽象。
- 第 4 阶段：统一拍摄与媒体管线。
  - 目标：整合单帧、多帧、录像抓拍、缩略图、保存任务、后处理入口，形成统一 `Shot / Media Pipeline`。
  - 关键产物：`ShotExecutor`、`SaveRequest`、`ThumbnailSource`、媒体保存任务、EXIF/水印挂点、算法/后处理入口。
  - 说明：这一阶段结束后，复杂模式应只声明自己需要的拍摄策略，不再自己拼拍照细节。
- 第 5 阶段：迁移代表性复杂模式。
  - 目标：迁入足够代表 `MiuiCamera` 复杂度的模式，验证新架构是否真的能承载复杂能力。
  - 范围：夜景、人像、专业、文档。
  - 验证点：多帧、手动参数、特殊 UI、特殊保存链路、能力降级、恢复重建。
- 第 6 阶段：高复杂 feature 扩展。
  - 目标：在前面内核、设备、拍摄、模式边界稳定后，再接 水印、Live、闪光灯控制、像素控制、画幅、滤镜等 feature。
  - 原则：只能通过 `mode plugin + session service + device adapter + media pipeline` 接入，禁止绕回旧式全局耦合。
  - 当前基线：第 6 阶段第一轮 feature 扩展已经完成并归档；`PHOTO / PRO / NIGHT / VIDEO`、静态图 style / watermark / frame ratio、document / portrait 后处理、video quality / watermark sidecar、动态能力刷新、still quality / resolution / native output size 等已经形成闭环。
  - 补充范围：用户新增的一组 vivo x300 ultra 参考型产品需求，统一收纳为第 6 阶段追加 feature 包；实现时仍按“先契约和设置，后单点真实输出；先静态图片闭环，后实时预览 / 录像 / Live / RAW”的顺序推进。

  #### 第 6 阶段追加 feature 包：可行性和实施顺序

  - `6B-0 设置、持久化和能力目录底座`
    - 目标：先建立 `PersistedSettings / FeatureSettings / capability catalog / UiSpec renderer` 的小边界，不把新功能状态散进 `MainActivity` 或各 mode controller。
    - 内容：独立设置入口；通用 / 拍照 / 录像三类设置页的数据模型；水印、构图线、快门声音、自拍镜像、默认录像规格、默认滤镜 / 个性滤镜、默认 Live、倒计时、麦克风模式等持久化项。
    - 可行性：可行，是后续多数需求的前置项。
    - 验证：settings reducer / serializer / session settings snapshot 单测；UI 只消费 render model。

  - `6B-1 模式命名和模式目录扩展`
    - 目标：在不破坏已归档 `NIGHT` 主链路的前提下，把产品入口整理成普通拍照、录像、人像、风光、人文、专业 / pro overlay 等清晰入口。
    - 内容：新增 `HUMANISTIC` mode plugin；将夜景产品化升级为“风光与夜景 / 风光模式”的显示层和 profile 集合；先把 `PHOTO / PORTRAIT / SCENERY / HUMANISTIC` 的 mode id、展示名、默认 style、可用子功能声明清楚。
    - 可行性：可行；若改底层 enum / id，必须一次性更新 registry、session 状态、测试和 UI 文案。
    - 顺序：先做显示名 / profile 兼容，再决定是否把内部 `NIGHT` id 迁成 `SCENERY`，避免一次改名扩大风险。

  - `6B-2 个性滤镜和模式滤镜体系`
    - 目标：把“滤镜”从若干 hard-coded `algorithmProfile` 升级为可枚举、可调节、可持久化、可分享的 `FilterProfile`。
    - 内容：拍照四款滤镜：鲜明、原色、追光、浓郁；人文五款滤镜：原色、鲜明、人文街头、人文肖像、人文生活；人像七款滤镜：蓝调、复古、ccd、鲜明、原色、追光、浓郁。
    - 内容：滤镜图标调节入口；普通调色板横划调颜色、纵划调影调；进阶项曝光、柔光、光晕、颗粒、锐度、暗角、高光、阴影、暖色增强、冷色增强、色温偏移、色调偏移；一键保存为个性滤镜；导出 / 导入标准分享文件；下次默认调用。
    - 可行性：分层可行；第一步做设置、metadata、静态图片后处理和分享文件；实时预览近似 LUT / shader、真实厂商色彩科学和高质量胶片模拟不作为第一闭环。
    - 验证：profile 解析 / 兼容 / 导出导入单测；每个官方 profile 至少有 metadata 和静态图 postprocess 命中回归。

  - `6B-3 水印 V2 和边框成片`
    - 目标：把当前简单文字水印升级为模板化、可配置、可定位、支持边框和参数的媒体后处理。
    - 内容：图中水印；图下方边框水印；旅拍打卡拍立得样式，默认文案“去有天空的地方”；复古水印款式，下画框较宽、其余三边较窄；文字水印支持画面位置、字体大小、字体透明度。
    - 内容：支持机型名、日期时间、地理位置、拍照参数；边框背景支持暗 / 白 / 原图高斯模糊 / 原图浅色多巴胺模糊 / 原图鲜明 1 模糊；Live 照片需要定义动态段里的水印表现。
    - 可行性：静态图片完全可行；视频 / Live 画面内烧录成本更高，先做静态图模板和 sidecar / metadata，再推进视频帧级渲染。
    - 验证：watermark template resolver 纯测；位图后处理 golden / 像素级 smoke；EXIF / 地理位置缺失时的降级测试。

  - `6B-4 构图线、倒计时和轻量拍摄辅助`
    - 目标：把模式常用子功能做成 session 可见的拍摄前配置，而不是 UI 本地按钮。
    - 内容：预览构图线；拍照 / 人像 / 风光 / 人文等模式倒计时；通用设置中的构图线、快门声音、自拍镜像。
    - 可行性：可行；构图线是 preview overlay，倒计时是 session capture gate，自拍镜像需要在预览和成片保存两侧明确语义。
    - 验证：倒计时取消 / 切模式 / 前后台中断回归；overlay 设置 render model 单测。

  - `6B-5 人像模式升级`
    - 目标：在已有人像轻量渲染之上，先把产品 profile 和输出 metadata 闭环，再逐步替换为更强算法。
    - 内容：原生人像，美颜真实、风格原色；透亮人像，美颜透亮、风格鲜明；光斑效果选择；美颜强度 / 方案选择。
    - 可行性：profile / metadata / 静态图轻量后处理可行；真实美颜、主体分割、深度估计、光斑形状重建属于高风险算法项，必须 capability-gated，可先做可替换后处理接口和保守效果。
    - 验证：portrait profile -> capture metadata -> portrait processor spec 的端到端单测。

  - `6B-6 录像规格、低光 fps 和收音模式`
    - 目标：把录像从粗粒度 `FHD / HD / SD` 提升为能力门控的 `VideoSpec(resolution, fps, dynamicPolicy, audioProfile)`。
    - 内容：默认录像规格设置，初始默认 `4k / 25fps`；1080p 支持 25 / 30 / 60 / 100 / 120fps；4k 支持 25 / 30 / 60 / 100 / 120fps；8k 支持 25 / 30 / 60fps；自动低光 fps，暗光下采用 24fps 录制；麦克风收音模式：标准、演唱会。
    - 可行性：契约 / UI / 能力降级可行；实际支持完全依赖设备、CameraX / Camera2、编码器和音频链路。100 / 120fps、8k、低光自动切 24fps、演唱会收音必须先暴露 unsupported / degraded 语义。
    - 验证：video capability matrix 解析 / 选择 / 降级测试；adapter 层质量选择测试；低光策略先用注入的 brightness / scene signal 做纯测。

  - `6B-7 Live 照片`
    - 目标：先定义 Live 作为“still + short motion + metadata + thumbnail”的媒体组合，禁止把它伪装成普通 JPEG flag。
    - 内容：拍照、人像、风光模式支持开启 Live；水印需要支持 Live 动态；保存结果需要有可定位的 still、motion、sidecar / container 信息。
    - 可行性：架构上可行，但需要预览环形缓冲、短视频录制 / mux、拍照同步、媒体保存组合、缩略图选择和失败清理；应在录像规格模型稳定后推进。
    - 验证：Live capture plan / media bundle / failure cleanup 单测；真机 instrumentation 后续进入第 7 阶段加固。

  - `6B-8 风光 / 人像 / 人文的 Pro 版界面和 RAW / 手动参数`
    - 目标：把“pro 按钮”定义为模式内的专业控制 overlay / variant，而不是复制多个互相分叉的 pro 模式。
    - 内容：风光、人像、人文支持点击 pro 按钮进入对应 pro 版界面；支持 RAW；支持 ISO、s、ev、af、a、wb 等手动参数。
    - 可行性：UI / mode policy / metadata 可行；RAW、光圈 `a`、手动 AF / AE / WB 的真实执行依赖 Camera2 能力和设备镜头，必须走 device adapter / request translator。
    - 验证：manual parameter model / request translation 单测；unsupported 控件禁用和降级提示回归。

  - `6B-9 验收和阶段边界`
    - 每个 feature 必须至少经过 `mode declaration -> session state/effect -> device graph/shot plan/media metadata -> adapter/postprocessor -> render model/test` 中真正相关的链路，不接受只改文案。
    - 每个硬件相关 feature 都必须有 supported / unsupported / degraded 的可测试语义。
    - 实时预览效果、真实低光判断、真实人像分割、8k / high-fps 录制、RAW、Live 容器兼容性可以在第 6 阶段先完成契约和最小闭环；大规模真机矩阵、provider death、热恢复、长稳、性能和 instrumentation 仍属于第 7 阶段。
- 第 7 阶段：稳定性治理与自动化补强。
  - 目标：把“能跑”升级成“可长期维护、可回归验证、可定位问题”。
  - 关键产物：冷启动、首帧、切模式、切镜头、切变焦、权限中断、provider death、thermal、后台恢复、保存失败、恢复失败等自动化测试；统一 `DebugDump / RecoveryTrace / PerfSnapshot`。
