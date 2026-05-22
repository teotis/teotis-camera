# OpenCamera 2.0 Readiness High-Cost Decision Items

> 用途：等待用户决策，不交给外部 agent 立即实现。  
> 原因：这些问题需要真实算法、平台能力、真机矩阵或产品取舍；贸然开工容易引入大复杂度，却不能保证 2.0 准入收益。

## 1. 决策总览

| Item | 成本 | 当前状态 | 推荐决策 |
| --- | --- | --- | --- |
| 真实 RAW / DNG 输出 | 高 | 仅 metadata flag，无 DNG | 暂不实现；先显式降级为 unsupported/saved-only |
| 真实夜景多帧合并 | 高 | placeholder 删除临时帧，无真实融合 | 暂不实现；先显式降级为单帧增强 |
| Live Photo motion | 高 | `STILL_ONLY_FALLBACK`，无 motion frames | 暂不实现完整 Live；先明确 still-only fallback |
| 视频帧级水印 | 高 | SRT sidecar，不烧录画面 | 保持 sidecar；是否烧录待产品决策 |
| provider death / camera service restart 真机专项 | 高 | 仓内有上层 guardrail，无真机强信号 | 等有设备和脚本环境后专项执行 |
| thermal chamber / 长稳 / 多设备 perf 矩阵 | 高 | unit 通过，缺设备数据 | 等测试资源明确后专项执行 |
| 真实成片视觉 QA | 高/多模态 | 缺截图/保存图/录屏 | 等最新 APK 和素材到位后执行 |

## 2. 真实 RAW / DNG 输出

### 当前状态

- UI 有 RAW toggle。
- `rawEnabled` 可进入 metadata。
- `ManualControlCapabilityMatrix.raw` 当前类似 `SAVED_ONLY`。
- 没有配置 Camera2 RAW stream、`ImageReader`、DNG writer，也没有 DNG 文件保存链路。

### 为什么是高成本

- 需要 Camera2 interop 或更底层 Camera2 capture session 支持。
- 需要设备 capability 探测：`REQUEST_AVAILABLE_CAPABILITIES_RAW`、sensor active array、black level、color calibration 等。
- 需要 DNG metadata 完整性，否则生成的 DNG 可能不可用或质量不可信。
- 会冲击当前 CameraX ImageCapture 主链路和 Media Pipeline。

### 决策选项

1. **推荐：2.0 不承诺 RAW 输出**
   - UI 标注 `暂不支持 RAW 输出` 或 `仅记录设置`。
   - 优点：最快清掉假入口 P0。
   - 缺点：专业模式完整度降低。

2. **专项实现 RAW MVP**
   - 只在支持 RAW 的设备上生成 DNG。
   - 需要新设备适配和真机验证。
   - 风险：高，可能拖慢 2.0 准入。

3. **隐藏 RAW 入口**
   - 直到真实实现前不展示。
   - 优点：最诚实。
   - 缺点：用户感知功能减少。

## 3. 真实夜景多帧合并

### 当前状态

- Night/Scenery 可声明多帧策略。
- `MultiFrameMergePlaceholderPostProcessor` 只记录输入、删除临时文件，不做对齐/融合。

### 为什么是高成本

- 真实多帧需要图像对齐、曝光融合、降噪、鬼影处理、内存/CPU 预算。
- 需要设备曝光/ISO/时间戳一致性控制。
- 需要大量保存图对比和真机夜景样张验证。

### 决策选项

1. **推荐：2.0 先降级为单帧夜景增强**
   - 显示 `单帧增强` 或 `多帧合成暂未启用`。
   - 不承诺多帧降噪/HDR。

2. **实现轻量多帧融合 MVP**
   - 低分辨率对齐 + 简单平均/加权融合。
   - 风险：画质可能不如单帧，反而损害产品观感。

3. **等待算法专项**
   - 后续用独立阶段做图像算法，不塞进 2.0 准入。

## 4. Live Photo Motion

### 当前状态

- `LiveTemporalAssemblyPlanner` 有完整规划语义。
- `CameraXCaptureAdapter.captureLivePhoto` 当前 hardcode `METADATA_ONLY`。
- 输出是静态图 + sidecar，`frameCount=0`。

### 为什么是高成本

- 需要预览帧环形缓冲。
- 需要前后时窗同步 still capture。
- 需要 motion segment 编码/mux、失败清理、缩略图选择。
- 需要处理 scoped storage、容器兼容、播放器/回看入口。

### 决策选项

1. **推荐：2.0 先保留 still-only fallback**
   - UI 明确显示 `实况运动暂不可用`。
   - sidecar 记录 fallback reason。

2. **实现 app-level Live MVP**
   - 使用短视频 + still + metadata bundle。
   - 风险：存储、性能、同步、回看复杂度高。

3. **隐藏 Live 入口**
   - 直到 motion 可用再开放。

## 5. 视频帧级水印

### 当前状态

- 当前支持 SRT sidecar 或 metadata 形式。
- 不做视频画面内水印烧录。

### 为什么是高成本

- 需要 MediaCodec/GL/FFmpeg 等帧级处理。
- 会显著增加录制后处理耗时、发热、失败率。
- 需要处理音视频同步和中断恢复。

### 决策选项

1. **推荐：2.0 保持 sidecar，并明确不是画面烧录**
2. **后续专项做短视频后处理烧录**
3. **仅对低分辨率/短视频支持烧录**

## 6. Provider Death / Camera Service Restart 真机专项

### 当前状态

- 仓内已有 provider/fatal issue invalidation、preview stall watchdog、recovery trace。
- 缺平台级 provider death 真信号和真机注入。

### 为什么是高成本

- 需要特定设备、ADB 注入、camera provider 进程识别。
- 不同 Android 版本和厂商行为差异大。
- 自动化稳定性很难只靠 unit tests 证明。

### 决策选项

1. **推荐：2.0 前只保留上层 guardrail，标外部风险**
2. **建立真机稳定性专项**
   - 30min+ recovery loop。
   - provider crash/restart injection。
   - 记录 recovery success rate。

## 7. Thermal / Long-Run / Perf Matrix

### 当前状态

- thermal runtime issue、perf budget、diagnostics 均有 owner。
- 阈值仍是默认估算。

### 为什么是高成本

- 需要多设备、长时间运行、热环境或压力工具。
- 需要建立 P50/P90/P99 数据基线。
- 涉及产品阈值取舍：快、稳、画质、发热之间不可兼得。

### 决策选项

1. **推荐：2.0 先使用默认 budget + 明确 residual risk**
2. **建立设备矩阵**
   - 至少 3 台设备。
   - cold start / resume / recovery / capture / mode switch 指标。
3. **仅选择目标设备做准入**
   - 缩小 2.0 支持范围。

## 8. 多模态视觉与成片 QA

### 当前状态

缺少：

- 最新 APK 版本或 commit。
- 竖屏/横屏截图。
- 面板截图。
- 拍照后缩略图录屏。
- 水印、滤镜、色彩实验室、画幅裁切保存 JPEG。
- 录像输出文件和录屏。

### 为什么是高成本/多模态限定

- 需要真实设备和视觉判断。
- 不能由纯文本 agent 从代码推断“好看/无跳变/成片一致”。

### 决策选项

1. **推荐：低/中成本修复后再跑一次多模态 QA**
2. **立即收集素材，先做视觉阻断判断**
3. **推迟到下一轮 APK**

## 9. 推荐总决策

为了尽快推进 2.0 准入，建议：

1. 立即执行低成本与中成本文档。
2. 高成本项先不实现真实能力，统一改为显式 unsupported/degraded。
3. 清掉假入口和静默失败后，再收集 APK 多模态材料。
4. 下一轮主控审查目标从 `NO GO` 推进到 `CONDITIONAL GO`。

## 10. 等待用户决策的问题

请用户明确：

1. RAW：隐藏、标注不支持，还是投入真实 DNG？
2. 夜景多帧：标注单帧增强，还是投入真实融合算法？
3. Live Photo：标注 still-only，隐藏入口，还是投入 motion MVP？
4. 视频水印：继续 sidecar，还是做画面烧录？
5. 2.0 准入是否需要真机 provider death / thermal / 长稳矩阵，还是先以仓内 guardrail 通过？
