# 纯净版仓库导出方案

## 目标

从 codex_camera 项目创建一个"纯净版"副本，用于推送到开源 GitHub 仓库。排除所有设计信息、Agent 信息、注释信息、方案信息等功能无关内容，且不包含 xiaomi/miui 等品牌敏感命名。

## 目标目录

`/Volumes/Extreme_SSD/project/opencamera-clean/`

## 排除清单

### 目录级排除

| 目录 | 原因 | 大小 |
|------|------|------|
| `codex/` | Agent 计划、设计文档、审计报告 | 177MB |
| `docs/` | 设计规范、UI 候选方案 | 40MB |
| `specs/` | 设计规范文档 | 9MB |
| `scripts/` | 阶段验证脚本 | 11MB |
| `.codegraph/` | 代码图索引 | - |
| `.gradle/` | Gradle 缓存 | - |
| `.idea/` | IDE 配置 | - |
| `.tmp/` | 临时文件 | - |
| `.git/` | 原始 git 历史 | - |

### 文件级排除

| 文件 | 原因 |
|------|------|
| `AGENTS.md` | Agent 配置信息 |
| `V2-Readiness-Release-Gate-Report.md` | 发布门禁报告 |
| `local.properties` | 本地 SDK 路径（用户特定） |
| `._*` | macOS 资源叉文件 |

## 保留内容

- `app/` — 应用模块（含 src/test）
- `core/` — 核心模块（含 src/test）
- `feature/` — 功能模块（含 src/test）
- `gradle/` — Gradle wrapper
- `gradle.properties` — 构建属性
- `gradlew` / `gradlew.bat` — Gradle wrapper 脚本
- `build.gradle.kts` — 根构建文件（修改版）
- `settings.gradle.kts` — 设置文件
- `.gitignore` — Git 忽略规则

## 配置修改

### build.gradle.kts

移除 `sharedBuildRoot` 自定义构建输出路径，改为 Gradle 默认的 `build/` 目录：

```kotlin
// 删除以下内容：
val sharedBuildRoot = file("${System.getProperty("user.home")}/.codex-build/OpenCamera")

allprojects {
    val projectBuildPath = if (path == ":") {
        "root"
    } else {
        path.removePrefix(":").replace(':', '/')
    }
    layout.buildDirectory.set(sharedBuildRoot.resolve(projectBuildPath))
}
```

## 实现方式

Shell 脚本 `scripts/export_clean_repo.sh`，使用 rsync 过滤复制 + sed 修改配置 + git init。

## 品牌安全检查

源码扫描结果：Kotlin/XML 源文件中未发现 xiaomi、miui 等品牌敏感命名。包名为 `com.opencamera`，项目名为 `OpenCamera`，均无品牌风险。
