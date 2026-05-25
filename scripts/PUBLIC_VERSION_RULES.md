# Teotis Camera 公开版维护规则

## 概述

公开版仓库 `teotis-camera` 位于 `public/teotis-camera/`，连接到远端 `git@github.com:teotis/teotis-camera.git`。

主项目（私有仓库）不包含 `public/` 目录（已在 `.gitignore` 中排除）。

## 导出流程

### 自动导出

运行导出脚本：

```bash
./scripts/export_clean_repo.sh
```

脚本会自动：
1. 同步源码到 `public/teotis-camera/` 目录（**保留 `.git` 目录**）
2. 使用 `rsync --delete` 删除目标中多余的文件
3. 排除所有敏感和非源码内容
4. 清理硬编码的用户路径（`/Users/dingren/` → `<HOME>`）
5. 生成简化版 `build.gradle.kts`
6. 更新 `settings.gradle.kts` 中的项目名称（`OpenCamera` → `TeotisCamera`）
7. 创建公开版 `.gitignore`

**重要**：脚本不会删除 `.git` 目录，因此公开仓的 Git 历史和远端配置会保留。

### 同步推送

导出后，进入 public 目录提交并推送：

```bash
cd public/teotis-camera
git add -A
git status  # 检查变更
git commit -m "feat: 同步公开版更新"
git push
```

### 首次设置（仅需一次）

如果 `public/teotis-camera/.git` 不存在，需要初始化：

```bash
cd public/teotis-camera
git init
git remote add origin git@github.com:teotis/teotis-camera.git
git add -A
git commit -m "feat: teotis-camera 初始公开版"
git push -u origin main
```

## 排除内容清单

### 必须排除（敏感信息）

| 类型 | 文件/目录 | 原因 |
|------|-----------|------|
| 本地配置 | `local.properties` | 包含本地 SDK 路径和用户名 |
| Agent 指令 | `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` | 包含内部工作流和私有路径 |
| Agent 工作区 | `codex/` | 包含 agent 计划、报告、内部文档 |
| 设计文档 | `docs/`, `specs/` | 包含内部设计稿和参考资料 |
| 脚本 | `scripts/` | 包含内部验证和导出脚本 |
| 分析报告 | `*.html` (根目录) | 包含架构分析报告 |
| 发布报告 | `V2-Readiness-Release-Gate-Report.md` | 包含内部发布评估 |

### 必须排除（开发工具）

| 类型 | 文件/目录 | 原因 |
|------|-----------|------|
| IDE 配置 | `.idea/` | IDE 特定配置 |
| 代码图谱 | `.codegraph/` | CodeGraph 索引数据库 |
| 临时文件 | `.tmp/` | 临时分析文件 |
| Claude 工作区 | `.claude/` | Claude Code worktrees |
| Git worktrees | `.worktrees/` | Git worktrees |
| 构建缓存 | `.gradle/` | Gradle 构建缓存 |

### 路径清理

以下模式会被替换：

| 原始路径 | 替换为 |
|----------|--------|
| `/Users/dingren/` | `<HOME>` |

### 项目名称映射

| 文件 | 原始值 | 公开版值 |
|------|--------|----------|
| `settings.gradle.kts` | `rootProject.name = "OpenCamera"` | `rootProject.name = "TeotisCamera"` |

### 公开版专用文件

以下文件不存在于源目录，是公开版独有的，不会被 rsync --delete 删除：

- `README.md` - 中文文档
- `README_EN.md` - 英文文档
- `LICENSE` - MIT 许可证

如需修改这些文件，请直接编辑 `public/teotis-camera/` 中的版本。

## README 维护

公开版包含两个 README 文件：

- `README.md` - 中文版本
- `README_EN.md` - 英文版本

更新 README 时，需要同步更新两个版本，保持内容一致。

### README 内容要求

1. **项目简介**：简要说明项目定位和核心特性
2. **架构设计**：四层架构图和设计原则
3. **功能列表**：结构化的模块和功能清单
4. **技术栈**：主要依赖和版本信息
5. **项目结构**：目录树说明
6. **构建指南**：环境要求和构建步骤

## 版本同步策略

### 何时同步

- 完成重要功能或架构变更后
- 修复重要 bug 后
- 发布新版本前
- 定期同步（建议每月一次）

### 同步检查清单

1. 运行导出脚本
2. 检查导出结果，确保无敏感信息泄露
3. 更新 README（如有功能变更）
4. 提交并推送到公开仓库
5. 在 GitHub 上验证公开版内容

## 安全检查

### 推送前必须检查

- [ ] 无硬编码的用户路径
- [ ] 无 API 密钥或凭证
- [ ] 无内部文档或设计稿
- [ ] 无 agent 交互记录
- [ ] README 内容准确且专业

### 自动化检查命令

```bash
# 检查是否包含敏感路径
grep -r "/Users/dingren" public/teotis-camera/

# 检查是否包含敏感文件
find public/teotis-camera -name "*.env" -o -name "*.key" -o -name "*.pem"

# 检查文件数量
find public/teotis-camera -type f | wc -l
```

## 故障处理

### 如果误提交了敏感信息

1. 立即从公开仓库删除敏感内容
2. 使用 `git filter-branch` 或 `git filter-repo` 清理历史
3. 强制推送到远端
4. 检查是否需要撤销已暴露的凭证

### 如果导出脚本失败

1. 检查源目录权限
2. 确认磁盘空间充足
3. 手动执行 rsync 命令排查问题
4. 检查 sed 命令兼容性（macOS vs Linux）

## 相关文件

- `scripts/export_clean_repo.sh` - 导出脚本
- `.gitignore` - 主项目忽略规则（包含 `public/`）
- `AGENTS.md` - 主项目 agent 指令（不包含在公开版中）
