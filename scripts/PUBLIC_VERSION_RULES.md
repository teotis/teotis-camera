# Teotis Camera 公开版维护规则

## 概述

公开版仓库 `teotis-camera` 位于 `public/teotis-camera/`，连接到远端 `git@github.com:teotis/teotis-camera.git`。

主项目（私有仓库）不包含 `public/` 目录（已在 `.gitignore` 中排除）。

## 当前安全状态

公开版发布现在按“先核查、再同步、最后推送”的安全发布流程处理。发现以下任一情况时必须暂停推送，先完成核查和修复：

- Git author/committer、reflog、remote-visible history、README、NOTICE、AUTHORS、图片元数据或构建文件中出现个人真实姓名、个人邮箱、公司邮箱、公司名、设备账号、本机路径或其他可识别身份信息。
- 公开说明、截图、测试 fixture、提交信息或代码注释中出现未清理的竞品学习/参考痕迹，例如 Apple、vivo、Xiaomi、MIUI、MiuiCamera、具体真机型号或供应商相机特性描述。
- 导出脚本行为与本文件不一致，例如导出到非 `public/teotis-camera/` 目录、重新初始化公开仓 Git 历史、没有保留远端配置、或没有运行公开安全检查。
- 需要改写已推送历史。历史改写和强制推送必须先拿到用户明确批准，不能由 agent 自动执行。

当前已知高风险项：`public/teotis-camera` 的既有 commit author/email 可能暴露个人或公司身份。即使工作区文件已经清理，GitHub 仍会显示历史作者；必须单独通过历史清理方案处理。

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
4. 清理硬编码的用户路径和身份信息（例如本机用户路径、真实姓名、个人/公司邮箱）
5. 生成简化版 `build.gradle.kts`
6. 更新 `settings.gradle.kts` 中的项目名称（`OpenCamera` → `TeotisCamera`）
7. 创建公开版 `.gitignore`
8. 运行公开版安全检查，检查不通过时停止提交/推送

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

提交前必须先确认公开仓 local Git identity 使用公开安全身份，不得继承本机全局身份：

```bash
git config user.name
git config user.email
git log --format='%h %an <%ae> | %cn <%ce>' -n 5
```

允许使用项目/组织身份或 GitHub noreply 身份；禁止使用个人真实姓名、公司邮箱或本机用户名。

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

## 公开安全边界

### 身份信息

必须排除或改写：

- 真实姓名、昵称与真实身份可关联的用户名
- 个人邮箱、公司邮箱、企业域名邮箱
- 本机路径，例如 `/Users/<name>/`、`/Volumes/<private-disk>/`
- Git commit author/committer、reflog、tags、notes、branch names 中的个人或公司信息
- 图片 EXIF、XMP、IPTC、ICC profile 注释、文件系统扩展属性中的个人或设备信息

Git 历史属于公开面的一部分。只检查工作区文件不够，必须检查 `git log --all`、tracked files、hidden Git metadata 和已推送远端状态。

### 第三方/竞品参考信息

公开仓不得留下“学习参考某厂/某系统相机/某真机”的内部过程描述。以下词汇在公开仓中默认视为需要人工判定的高风险信号：

- Apple、iPhone、iOS Camera
- vivo、X 系列真机型号
- Xiaomi、小米、MIUI、MiuiCamera
- Leica、Hasselblad 等品牌联名相机表述
- 竞品、参考、学习、借鉴、复刻、对标、系统相机

允许保留的情况必须满足两点：它是平台 API、兼容性测试或用户可理解的中性示例；同时不暗示复制、复刻、内部学习资料或特定供应商实现。测试 fixture 中的具体设备型号应优先替换成中性样例，例如 `Demo Device Pro`。

### 公开资产

公开 README 可包含 `docs/assets/` 下经筛选的展示图，但这些图片必须满足：

- 无 EXIF/XMP/IPTC 个人、设备、位置或软件流水线信息
- 不展示私人空间、设备序列号、账号、水印里的真实身份
- 文件名、alt 文本和 README 文案不包含竞品学习痕迹
- 来源、授权和可公开分发状态明确

除公开展示资产外，私有 `docs/`、规划文档、agent 记录、设计参考和内部截图仍必须排除。

## 排除内容清单

### 必须排除（敏感信息）

| 类型 | 文件/目录 | 原因 |
|------|-----------|------|
| 本地配置 | `local.properties` | 包含本地 SDK 路径和用户名 |
| Agent 指令 | `AGENTS.md`, `CLAUDE.md`, `GEMINI.md` | 包含内部工作流和私有路径 |
| Agent 工作区 | `codex/` | 包含 agent 计划、报告、内部文档 |
| 设计文档 | 私有 `docs/`, `specs/` | 包含内部设计稿和参考资料；公开 README 需要的展示资产只能以白名单方式进入 `public/teotis-camera/docs/assets/` |
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

### 路径和身份清理

以下模式必须替换或阻断：

| 类型 | 示例 | 处理方式 |
|------|------|----------|
| 本机路径 | `/Users/<name>/`, `/Volumes/<private-disk>/` | 替换为 `<HOME>` / `<WORKSPACE>` 或删除 |
| 个人/公司邮箱 | `*@xiaomi.com`, 个人邮箱 | 替换为公开安全邮箱或删除 |
| 个人身份 | 真实姓名、本机用户名 | 替换为 `Teotis` / 项目身份或删除 |
| 竞品学习痕迹 | Apple/vivo/Xiaomi/MIUI/MiuiCamera 等内部参考描述 | 删除或改为中性产品能力描述 |

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
- [ ] Git author/committer、reflog 和最近提交不暴露个人或公司身份
- [ ] 无 API 密钥或凭证
- [ ] 无内部文档或设计稿
- [ ] 无 agent 交互记录
- [ ] 无未清理的竞品学习/参考痕迹
- [ ] 展示图片无 EXIF/XMP/IPTC/扩展属性泄露
- [ ] README 内容准确且专业

### 自动化检查命令

```bash
# 检查公开仓 Git 身份
git -C public/teotis-camera log --all --format='%H %an <%ae> %cn <%ce>' | \
  grep -Ei 'dingren|xiaomi|/Users|丁仁' && echo "identity leak"

# 检查公开工作区敏感词
grep -RInE 'dingren|/Users/|xiaomi|丁仁|Apple|iPhone|vivo|Xiaomi|MIUI|MiuiCamera|竞品|参考|学习|复刻|对标' public/teotis-camera \
  --exclude-dir=.git

# 检查是否包含敏感文件
find public/teotis-camera -name "*.env" -o -name "*.key" -o -name "*.pem"

# 检查文件数量
find public/teotis-camera -type f | wc -l
```

这些命令只是最低线。完整核查必须同时覆盖 tracked files、untracked files、Git history、image metadata、README/NOTICE/AUTHORS、license attribution、export script behavior 和 GitHub 远端显示效果。

## 故障处理

### 如果误提交了敏感信息

1. 立即暂停公开仓推送和自动同步
2. 记录泄露类型、文件/commit、远端可见范围和是否涉及凭证或雇主身份
3. 从当前工作区删除或替换敏感内容
4. 设计历史清理方案；优先使用 `git filter-repo`，但强制推送前必须获得用户明确批准
5. 历史清理后重新克隆远端验证 GitHub 可见内容
6. 如果暴露的是凭证，立即轮换凭证；如果暴露的是个人或公司身份，按用户决策决定是否重建公开仓或改写历史

### 如果导出脚本失败

1. 检查源目录权限
2. 确认磁盘空间充足
3. 手动执行 rsync 命令排查问题
4. 检查 sed 命令兼容性（macOS vs Linux）

## 相关文件

- `scripts/export_clean_repo.sh` - 导出脚本
- `.gitignore` - 主项目忽略规则（包含 `public/`）
- `AGENTS.md` - 主项目 agent 指令（不包含在公开版中）
- `docs/plans/public-release-safety-audit-orchestration/` - 公开仓全面核查与整改编排包
