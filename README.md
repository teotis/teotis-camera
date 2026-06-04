# Open Camera（Teotis Camera）

一个现代化的 Android 相机应用，采用分层架构设计，支持多种拍摄模式和专业级功能。

[English Version](README_EN.md)

## 真机界面

| 取景主界面 | 快捷控制 | 色彩实验室 |
|---|---|---|
| <img src="docs/assets/preview-main.jpg" width="220" alt="Teotis Camera preview UI"> | <img src="docs/assets/quick-controls.jpg" width="220" alt="Quick camera controls"> | <img src="docs/assets/color-lab.jpg" width="220" alt="Color Lab panel"> |

| 文档批处理 | 水印样式页 | 实际成片 |
|---|---|---|
| <img src="docs/assets/document-batch.jpg" width="220" alt="Document batch rail"> | <img src="docs/assets/watermark-lab.jpg" width="220" alt="Watermark template editor"> | <img src="docs/assets/watermark-output.jpg" width="220" alt="Watermarked photo output"> |

## 实现与原理亮点

- **状态驱动的取景 UI**：`SessionUiRenderModel` 将会话状态、设置、能力降级和模式信息汇总为可渲染模型，Activity 只负责渲染与分发用户意图，避免 UI 直接驱动相机运行时。
- **独立的模式插件**：拍照、视频、文档、人像、夜景、人文和专业模式通过 `feature:mode-*` 插件声明拍摄策略、默认设置和能力需求，再由会话内核统一调度。
- **实时色彩管线**：Color Lab 使用 `ColorLabSpec`、`StyleColorPipeline` 和 `PreviewColorTransform` 将色彩选择、风格配置和预览叠加拆开，既能实时预览，也能把配置保存为可复用风格。
- **可组合媒体后处理**：水印、画幅、人像、文档裁切、算法处理等能力以媒体管线后处理器组合，预览提示和最终成片共享同一批设置与效果契约。
- **文档批处理模型**：Document 模式把连续拍摄、裁切状态和缩略图轨道建模为 `DocumentBatch*` 状态，支持多页文档的收集、预览和后续整理。
- **可观测性内建**：开发面板直接读取诊断日志、耗时、最近问题和链路状态，便于在真实设备上定位拍摄、后处理或设置同步问题。

## 架构设计

项目采用 **四层架构 + 横切关注点** 的设计模式：

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (app)                        │
│              渲染状态 & 分发意图                          │
├─────────────────────────────────────────────────────────┤
│                 Mode Plugin Layer                        │
│         feature:mode-photo | mode-video | ...            │
│            描述行为策略，不直接调用平台 API                 │
├─────────────────────────────────────────────────────────┤
│                 Session Kernel Layer                      │
│                   core:session                           │
│     会话状态 | 预览/拍摄/录制 | 恢复决策 | 状态转换         │
├─────────────────────────────────────────────────────────┤
│                Device Adapter Layer                       │
│                   core:device                            │
│        将抽象请求转换为 CameraX/Camera2 平台调用            │
├─────────────────────────────────────────────────────────┤
│                 Media Pipeline Layer                      │
│                   core:media                             │
│          拍摄 | 后处理 | 算法调度 | 媒体保存                │
├─────────────────────────────────────────────────────────┤
│              Cross-cutting Concerns                      │
│    stability | recovery | observability | diagnostics    │
└─────────────────────────────────────────────────────────┘
```

### 核心设计原则

1. **单一职责**：UI 只负责渲染和意图分发，不直接驱动相机运行时行为
2. **状态隔离**：运行时状态与持久化设置严格分离
3. **契约优先**：所有硬件能力必须有明确的 `supported/unsupported/degraded` 语义
4. **可测试性**：每个模块都有完整的单元测试覆盖

## 功能列表

### 拍摄模式

| 模块 | 功能 | 状态 |
|------|------|------|
| `feature:mode-photo` | 标准拍照模式 | ✅ |
| `feature:mode-video` | 视频录制模式 | ✅ |
| `feature:mode-night` | 夜景模式 | ✅ |
| `feature:mode-portrait` | 人像模式 | ✅ |
| `feature:mode-document` | 文档扫描模式 | ✅ |
| `feature:mode-humanistic` | 人文模式 | ✅ |
| `feature:mode-pro` | 专业模式 | ✅ |

### 核心能力

| 模块 | 职责 | 关键特性 |
|------|------|----------|
| `core:session` | 会话内核 | 状态机、恢复机制、诊断追踪 |
| `core:device` | 设备适配 | 变焦、视频、手动参数建模 |
| `core:media` | 媒体管线 | 拍摄图、帧环缓冲、水印归档 |
| `core:mode` | 模式目录 | 插件契约、帧比率、静态捕获图 |
| `core:settings` | 设置持久化 | 功能目录、风格/色彩管线 |
| `core:capability` | 能力图谱 | 契约解析、能力查询 |
| `core:effect` | 效果桥接 | 渲染配方、预览效果适配 |

### 应用层功能

- **手势控制**：点击对焦、双指缩合、滑动调节
- **方向感知**：自适应横竖屏 UI 布局
- **驾驶舱面板**：专业参数调节界面
- **色彩实验室**：实时色彩风格预览
- **水印系统**：可配置的水印后处理管线
- **开发控制台**：运行时诊断和日志导出

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Kotlin | 1.9.24 | 主要开发语言 |
| Android SDK | 35 (min 26) | 目标平台 |
| Gradle | 8.7 | 构建系统 |
| AGP | 8.5.2 | Android 构建插件 |
| CameraX | 1.3.4 | 相机 API 抽象 |
| AndroidX | - | Jetpack 组件库 |

## 项目结构

```
teotis-camera/
├── app/                          # 应用模块
│   └── src/main/java/com/opencamera/app/
│       ├── MainActivity.kt       # 入口 Activity
│       ├── CameraSessionCoordinator.kt  # 会话协调器
│       └── ...                   # UI 和集成代码
├── core/                         # 核心库模块
│   ├── session/                  # 会话内核
│   ├── device/                   # 设备适配
│   ├── media/                    # 媒体管线
│   ├── mode/                     # 模式目录
│   ├── settings/                 # 设置管理
│   ├── capability/               # 能力图谱
│   └── effect/                   # 效果处理
├── feature/                      # 功能模式插件
│   ├── mode-photo/
│   ├── mode-video/
│   ├── mode-night/
│   ├── mode-portrait/
│   ├── mode-document/
│   ├── mode-humanistic/
│   └── mode-pro/
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts           # 模块声明
└── gradle.properties             # Gradle 配置
```

## 构建指南

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 35

### 构建步骤

```bash
# 克隆仓库
git clone git@github.com:teotis/teotis-camera.git
cd teotis-camera

# 构建 Debug APK
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :core:session:test
./gradlew :core:device:test
./gradlew :app:testDebugUnitTest
```

### 安装到设备

```bash
# 安装到连接的设备
./gradlew :app:installDebug
```

## 许可证

本项目代码采用 **GNU General Public License v3.0 or later** 许可证。详见 [LICENSE](LICENSE) 文件。

这意味着任何复制、修改或分发本项目代码的衍生版本，都必须在 GPLv3 或兼容条款下继续开放相应源码，并保留版权、许可证和署名声明。

文档、截图和展示素材默认采用 **Creative Commons Attribution-ShareAlike 4.0 International** 许可证，除非文件中另有说明。

`Teotis Camera` 名称、Logo 和品牌资产不随源码许可证授权商标或品牌使用权，未经明确许可不得用于暗示官方背书或混淆来源。

署名信息见 [NOTICE](NOTICE) 和 [AUTHORS](AUTHORS)。

## 贡献

欢迎提交 Issue 和 Pull Request。请确保：

1. 遵循现有的代码风格
2. 为新功能添加单元测试
3. 更新相关文档
