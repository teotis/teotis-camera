# 04 Public History Remediation Plan — 公开仓历史身份清理方案

## 一、当前暴露摘要

### 1.1 仓库基本信息

| 项目 | 值 |
|------|-----|
| 远端 URL | `git@github.com:teotis/teotis-camera.git` |
| 当前分支 | `scrub/brand-reference-content-scrub` |
| 总 commit 数 | 6（含最新 test fixture 清理） |
| 暴露身份 | `<REDACTED_USER> <<REDACTED_EMAIL>>` |

### 1.2 暴露详情

全部 6 个 commit 的 **author** 和 **committer** 字段均包含真实身份：

| Commit | Subject | Author | Date |
|--------|---------|--------|------|
| `b203091` | chore: 替换测试 fixture 中 vivo X300 Ultra 为中性设备名 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `a8e440d` | docs: switch public license to GPLv3 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `0acb879` | docs: 更新 README 展示实现亮点 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `7939324` | feat: 同步当前公开版更新 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `bb56b95` | fix: 修复导出脚本保留 Git 元数据 | <REDACTED_USER> | <REDACTED_EMAIL> |
| `c8adf1e` | feat: teotis-camera 初始公开版 | <REDACTED_USER> | <REDACTED_EMAIL> |

**泄露内容**：
- 个人姓名 `<REDACTED_USER>`
- 公司邮箱 `<REDACTED_EMAIL>`（暴露雇主 厂商 身份）
- GitHub 用户页面可关联到真实自然人

---

## 二、修复方案（二选一）

### 方案 A（推荐）：git filter-repo 改写历史 + force-with-lease

**原理**：用 `git filter-repo` 重写全部 commit 的 author/committer 为中性公开身份，然后 force-push 覆盖远端。

**优势**：保留完整 commit 历史和内容，变更最小化。

**劣势**：需要团队成员重新克隆或 reset；历史被重写，旧 fork/缓存可能保留原始身份。

#### 2.1 准备步骤

```bash
# 1. 安装 git-filter-repo（如未安装）
pip3 install git-filter-repo

# 2. 备份当前公开仓
cp -r /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera \
      /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera.bak.$(date +%Y%m%d)

# 3. 创建 identity replacement 脚本
cat > /tmp/replace-identity.py << 'PYEOF'
#!/usr/bin/env python3
"""Replace author/committer identity in all commits."""
import subprocess, os, sys

AUTHOR_NAME = "Teotis"
AUTHOR_EMAIL = "noreply@teotis.dev"

env = os.environ.copy()
env["FILTER_BRANCH_SQUELCH_WARNING"] = "1"

# Use git filter-branch as fallback if filter-repo unavailable
cmd = [
    "git", "filter-branch", "-f", "--env-filter",
    f'''
    export GIT_AUTHOR_NAME="{AUTHOR_NAME}"
    export GIT_AUTHOR_EMAIL="{AUTHOR_EMAIL}"
    export GIT_COMMITTER_NAME="{AUTHOR_NAME}"
    export GIT_COMMITTER_EMAIL="{AUTHOR_EMAIL}"
    ''',
    "--tag-name-filter", "cat",
    "--", "--all"
]

repo_path = sys.argv[1]
result = subprocess.run(cmd, cwd=repo_path, env=env, capture_output=True, text=True)
print(result.stdout)
if result.returncode != 0:
    print(f"STDERR: {result.stderr}", file=sys.stderr)
    sys.exit(result.returncode)
PYEOF
```

#### 2.2 Dry-run（安全验证）

```bash
# 在临时目录克隆并执行 dry-run
DRYRUN_DIR="/private/tmp/teotis-camera-history-safety-dryrun-$(date +%Y%m%d%H%M%S)"
mkdir -p "$DRYRUN_DIR"
git clone /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera "$DRYRUN_DIR/repo"
cd "$DRYRUN_DIR/repo"

# 执行 filter-repo 改写
git filter-repo --force \
  --replace-text <(echo "<REDACTED_EMAIL>==>noreply@teotis.dev") \
  --commit-callback '
    import subprocess
    subprocess.run(["git", "commit", "--amend",
      "--author=Teotis <noreply@teotis.dev>",
      "--no-edit"], check=True)
  '

# 验证：应无真实身份残留
git log --all --format='%an <%ae> | %cn <%ce> | %s'
```

**dry-run 路径**：`/private/tmp/teotis-camera-history-safety-dryrun-*`

#### 2.3 正式执行（需明确授权）

```bash
# ⚠️ 以下命令需要用户明确授权后执行

cd /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera

# 1. 执行改写
git filter-repo --force \
  --replace-text <(echo "<REDACTED_EMAIL>==>noreply@teotis.dev") \
  --commit-callback '
    import subprocess
    subprocess.run(["git", "commit", "--amend",
      "--author=Teotis <noreply@teotis.dev>",
      "--no-edit"], check=True)
  '

# 2. 验证本地
git log --all --format='%an <%ae> | %cn <%ce> | %s'
# 预期：全部显示 Teotis <noreply@teotis.dev>

# 3. Force-push（需要团队协调）
git push --force-with-lease origin main
```

#### 2.4 回滚方案

```bash
# 如果改写出问题，从备份恢复
rm -rf /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera
cp -r /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera.bak.YYYYMMDD \
      /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera
cd /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera
git push --force-with-lease origin main
```

---

### 方案 B：归档重建（Clean Reset）

**原理**：保留旧仓库作为归档，新建一个干净的公开仓重新推送。

**优势**：彻底干净，无历史改写风险；旧仓可设为 private 或 archive。

**劣势**：丢失 commit 历史；GitHub star/fork 需要迁移；需要重新推送所有内容。

#### 2.5 归档重建步骤

```bash
# 1. 归档旧仓库
# 在 GitHub 上将 teotis/teotis-camera 设为 Archive（Settings → Danger Zone → Archive this repository）

# 2. 新建干净仓库
# 在 GitHub 创建新 repo（如 teotis/opencamera）

# 3. 从当前内容重新初始化
cd /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera
git checkout main  # 确保在 main 分支

# 创建全新仓库（无历史）
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"
git init
git config user.name "Teotis"
git config user.email "noreply@teotis.dev"

# 复制内容
cp -r /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/* .
cp /Volumes/Extreme_SSD/project/open_camera/public/teotis-camera/.gitignore .

# 初始提交
git add -A
git commit -m "feat: teotis-camera initial public release"
git remote add origin git@github.com:teotis/opencamera.git
git push -u origin main
```

---

## 三、推荐方案

**推荐方案 A（filter-repo 改写）**，理由：

1. 保留完整 commit 历史，便于追溯
2. 变更范围最小（仅改写 author/committer 字段）
3. `git filter-repo` 是官方推荐工具，比 `filter-branch` 安全
4. 回滚方案清晰（从备份恢复）

**前置条件**：
- 安装 `git-filter-repo`（`pip3 install git-filter-repo`）
- 用户明确授权执行 force-push

---

## 四、远程验证计划

改写完成后，执行以下验证：

```bash
# 1. 新鲜克隆
CLONE_DIR="/private/tmp/teotis-camera-verify-$(date +%Y%m%d%H%M%S)"
git clone git@github.com:teotis/teotis-camera.git "$CLONE_DIR"
cd "$CLONE_DIR"

# 2. 身份扫描
git log --all --format='%H %an <%ae> %cn <%ce> | %s'
# 预期：全部显示 Teotis <noreply@teotis.dev>

# 3. 搜索残留
git log --all --format='%H %ae %ce %s' | grep -i 'xiaomi\|<REDACTED_USER>'
# 预期：无输出

# 4. GitHub Web 验证
# 打开 https://github.com/teotis/teotis-camera/commits/main
# 检查所有 commit 的作者是否显示为 Teotis

# 5. 检查 tag（如有）
git tag -l
git for-each-ref --format='%(refname:short) %(objecttype) %(*authorname) %(*authoremail)' refs/tags/
```

---

## 五、导出脚本同步修复

在改写历史的同时，必须修复 `scripts/export_clean_repo.sh`，防止未来导出再次泄露身份：

```bash
# 在 export_clean_repo.sh 的 git init 之后添加：
git config user.name "Teotis"
git config user.email "noreply@teotis.dev"
```

此修复已在 01-public-exposure-inventory 中识别，需在正式发布前完成。

---

## 六、风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 旧 fork/缓存保留原始身份 | 中 | 无法完全控制，需接受；GitHub 缓存会在几天内更新 |
| 团队成员需重新克隆 | 低 | 提前通知；`git fetch && git reset --hard origin/main` |
| GitHub Web 界面缓存 | 低 | 等待 24-48 小时自动刷新 |
| filter-repo 执行失败 | 低 | 从备份恢复（方案 2.4） |
| 其他包（05）执行验证失败 | 中 | 确保 04 完成后再执行 05 |

---

## 七、执行清单

- [ ] 安装 git-filter-repo（如未安装）
- [ ] 备份当前公开仓
- [ ] 执行 dry-run（临时目录）
- [ ] 用户确认 dry-run 结果
- [ ] 正式执行 filter-repo 改写
- [ ] 修复 export_clean_repo.sh 身份配置
- [ ] Force-push 到远端
- [ ] 新鲜克隆验证
- [ ] GitHub Web 界面验证
- [ ] 更新 04 状态为 completed
