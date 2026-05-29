# 01 Public Exposure Inventory — 公开仓暴露清单

## 概述

对 `public/teotis-camera` 公开仓进行全面暴露检查，覆盖 Git 历史身份、工作区文件、图片元数据、README/NOTICE/AUTHORS、行业引用等维度。

**检查时间**：2026-05-28
**公开仓路径**：`public/teotis-camera`
**远端**：`git@github.com:teotis/teotis-camera.git`
**分支**：`main`（仅一个分支）
**总 commit 数**：5（已推送到 GitHub）

---

## 一、已确认泄露（P0）

### 1.1 Git 历史全部 commit 暴露真实身份 — P0

**严重性**：P0 — 已推送到 GitHub，远端完全可见

**影响范围**：全部 5 个 commit，包括 author 和 committer 字段

| Commit Hash | Subject | Author | Email |
|-------------|---------|--------|-------|
| `c8adf1e` | feat: teotis-camera 初始公开版 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `bb56b95` | fix: 修复导出脚本保留 Git 元数据 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `7939324` | feat: 同步当前公开版更新 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `0acb879` | docs: 更新 README 展示实现亮点 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `a8e440d` | docs: switch public license to GPLv3 | <REDACTED_USER> | <REDACTED_EMAIL> |

**证据**：
```
git -C public/teotis-camera log --all --format='%H %an <%ae> | %cn <%ce> | %s'
```
所有 5 个 commit 的 author 和 committer 均为 `<REDACTED_USER> <<REDACTED_EMAIL>>`。

**根本原因**：公开仓 `git init` 后未配置本地 `user.name` / `user.email`，导致继承全局 `~/.gitconfig` 中的 `user.name=<REDACTED_USER>` / `user.email=<REDACTED_EMAIL>`。

**泄露内容**：
- 个人姓名 `<REDACTED_USER>`
- 公司邮箱 `<REDACTED_EMAIL>`（暴露雇主 厂商 身份）
- 个人/公司身份可通过 GitHub 用户页面、Git 历史关联到真实自然人

**修复方案**：
1. 用 `git filter-repo` 改写公开仓全部 commit，将 author/committer 替换为公开安全身份（如 `Teotis <noreply@teotis.dev>` 或 GitHub noreply 地址）
2. `git push --force` 覆盖远端历史
3. 修复后重新克隆验证 GitHub 可见内容

**风险**：历史改写后旧版本缓存和 fork 可能保留原始身份，需通知相关方。

### 1.2 导出脚本未设置公开仓 Git 身份 — P0

**严重性**：P0 — 流程缺陷，每次导出都会产生身份泄露

**位置**：`scripts/export_clean_repo.sh`

脚本执行了 `git init` 但**没有**在 init 后设置 `user.name` / `user.email`，导致每个后续 `git commit` 都继承全局配置中的真实身份。`scripts/PUBLIC_VERSION_RULES.md` 中明确要求使用公开安全身份，但脚本未执行。

**修复方案**：在 `export_clean_repo.sh` 的 `git init` 之后添加：
```bash
git config user.name "Teotis"
git config user.email "noreply@teotis.dev"
```

---

## 二、需人工判定（P1）

### 2.1 测试 fixture 中 vivo X300 Ultra 设备引用 — P1

**严重性**：P1 — 公开源码中出现行业设备型号

**位置**：`app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`

**出现次数**：3 处（行 25, 29, 78）

```kotlin
ExifInterface.TAG_MODEL to "vivo X300 Ultra"
assertEquals("vivo X300 Ultra · Scenery Handheld", resolved.title)
ExifInterface.TAG_MODEL to "vivo X300 Ultra"
```

**分析**：
- 这是水印模板解析器的单元测试，使用 `vivo X300 Ultra` 作为测试设备型号
- 功能上：测试水印标题能正确解析 EXIF 中的设备型号——属于中性功能测试
- 风险上：公开仓中出现行业设备型号可能被解读为"参考行业"或"从行业逆向"
- 规则判定：`PUBLIC_VERSION_RULES.md` 明确指出测试 fixture 中的具体设备型号应优先替换成中性样例

**修复建议**：替换为中性设备名，如 `"Demo Device Pro"` 或 `"Test Camera X1"`，同时更新对应的 `assertEquals` 预期值。

---

## 三、已排除（Allowed）

### 3.1 图片 EXIF 中的 Vivo 设备软件标识

**严重性**：Allowed

**位置**：`docs/assets/` 下 6 张 JPEG 图片

**发现**：
```
preview-main.jpg:     software=Android PD2509_A_16.0.22.21.W10
quick-controls.jpg:   software=Android PD2509_A_16.0.22.21.W10
color-lab.jpg:        software=Android PD2509_A_16.0.22.21.W10
watermark-lab.jpg:    software=Android PD2509_A_16.0.22.21.W10
document-batch.jpg:   software=Android PD2509_A_16.0.22.21.W10
watermark-output.jpg: (无 EXIF software 标记)
```

**分析**：`PD2509` 是 Vivo 设备固件标识符，这些截图来自 Vivo 真机。EXIF 中仅包含 `software` 字段，**无**位置、时间戳、GPS、相机序列号等敏感信息。

**允许理由**：
- 属于应用展示截图的正常设备信息，不暗示"学习"或"参考"
- 用户在公开平台上看到截图时不会产生"这是从行业复制"的联想
- `PUBLIC_VERSION_RULES.md` 允许"用户可理解的中性使用"

**建议**：可保留，但如需极致安全，建议在上传前 strip EXIF。

### 3.2 README 中的架构描述

**严重性**：Allowed

README.md 和 README_EN.md 中的架构描述均为通用技术文档（状态驱动 UI、模式插件、色彩管线等），不包含行业比较或"学习参考"措辞。内容干净。

### 3.3 NOTICE / AUTHORS

**严重性**：Allowed

- `NOTICE`：版权声明为 `Copyright (C) 2026 Teotis`，不包含个人姓名
- `AUTHORS`：仅列出 `Teotis`，不包含个人身份

### 3.4 远端配置

**严重性**：Allowed

公开仓 `.git/config` 中 remote origin 指向 `git@github.com:teotis/teotis-camera.git`，无个人路径或凭证泄露。

### 3.5 工作区隐藏文件

**严重性**：Allowed

`._*` macOS 资源 fork 文件和 `.gradle/` 目录仅存在于本地工作区，已被 `.gitignore` 排除，**未被 git 追踪**，不会出现在公开推送中。

### 3.6 commit message 内容

**严重性**：Allowed

所有 5 个 commit message 均为中性描述（`feat:`, `fix:`, `docs:`, `chore:`），不包含行业引用、个人身份或敏感信息。

---

## 四、检查方法与工具

| 检查项 | 工具/命令 | 结果 |
|--------|-----------|------|
| Git 身份 | `git log --all --format='%H %an <%ae> %cn <%ce>'` | 5 commit 全部暴露 |
| Git 配置 | `git config --list --show-origin` | 全局配置暴露真实身份 |
| Git 分支 | `git branch -a` | 仅 main 分支 |
| 工作区身份 | `grep -rn '<REDACTED_USER>\|<REDACTED_USER>@\|/Users/\|丁仁'` | 无泄露（文件已清理） |
| 行业引用 | `grep -rn 'Apple\|参考设备\|vivo\|厂商\|厂商系统\|参考相机应用\|品牌联名\|品牌联名'` | 3 处（test fixture） |
| 宽范围行业 | `grep -rn 'xiaomi\|miui\|oppo\|huawei\|samsung\|pixel'` | 0 处 |
| 图片元数据 | `file *.jpg` + `strings` | EXIF software 字段 |
| 敏感文件 | `find *.env *.key *.pem *.keystore` | 无 |
| 隐藏文件 | `find .*` + `git ls-files` | ._* 和 .gradle 未追踪 |
| 删除文件历史 | `git log --diff-filter=D` | 无已删除文件 |
| README | 人工审查 | 无行业痕迹 |
| NOTICE/AUTHORS | 人工审查 | 使用 "Teotis"，无个人身份 |

---

## 五、严重性汇总

| 严重性 | 数量 | 说明 |
|--------|------|------|
| **P0** | 2 | Git 历史全部 commit 暴露真实身份 + 导出脚本未设置公开身份 |
| **P1** | 1 | 测试 fixture 中 vivo X300 Ultra 设备引用 |
| **P2** | 0 | — |
| **Allowed** | 5 | 图片 EXIF、README、NOTICE/AUTHORS、远端配置、隐藏文件 |

---

## 六、修复建议（按 package 分配）

### Package 03: Git History Sanitization（高优先级）

| # | 问题 | 修复 | 依赖 |
|---|------|------|------|
| 1 | 全部 5 个 commit author/committer 暴露 | 用 `git filter-repo` 改写 author/committer 为 `Teotis <noreply@teotis.dev>` | 需用户批准 force push |
| 2 | `~/.gitconfig` 全局配置暴露 | 不影响公开仓（只需确保公开仓有本地 config 覆盖全局） | — |

### Package 04: Export Pipeline Hardening（高优先级）

| # | 问题 | 修复 | 依赖 |
|---|------|------|------|
| 1 | `export_clean_repo.sh` 未设置公开仓 Git 身份 | 在 `git init` 后添加 `git config user.name/email` | — |
| 2 | 导出脚本未 strip 图片 EXIF | 添加 `exiftool -all=` 到导出流程（可选） | — |

### Package 05: Test Fixture Cleanup（低优先级）

| # | 问题 | 修复 | 依赖 |
|---|------|------|------|
| 1 | `PhotoWatermarkTemplateResolverTest.kt` 中 `vivo X300 Ultra` | 替换为 `Demo Device Pro` | — |

---

## 七、结论

**当前公开仓存在 P0 级身份泄露**：所有 5 个 commit 的 author 和 committer 均为 `<REDACTED_USER> <<REDACTED_EMAIL>>`，已推送到 GitHub 远端。任何人都可通过 `git log` 查看开发者真实姓名和公司邮箱。

工作区文件已清理干净（无个人路径、无行业注释），但**Git 历史是公开面的一部分**，仅清理工作区不够。

**必须先修复 P0 后才能进行任何公开推送。**
