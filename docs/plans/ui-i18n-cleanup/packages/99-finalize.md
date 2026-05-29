# Package 99-finalize - 集成验证与合并

## Objective
验证所有功能包的修改，合并到集成分支，验证通过后合并回主线。

## Prerequisites
- 所有功能 package（01, 02, 03）状态为 `completed`
- Wave final 启动条件: 所有功能 package 完成

## Authorization
- Inspect all package docs, status files, state, branches, commits, diffs
- Create/update integration branch `agent/ui-i18n-cleanup/integration`
- Merge package branches in order: 01-strings-xml → 02-feature-mode-i18n → 03-app-core-i18n
- Run integration verification
- Merge to mainline after verification passes
- Write FINAL_REPORT.md and status/99-finalize.md
- Delete local package branches/worktrees after success

## Steps

### Step 1: Pre-merge Verification
```bash
bash launchers/orchestrate.sh verify-finalize
```
确认：
- 所有功能 package 状态为 `completed`
- 每个 package 有 commit hash
- 每个 package 分支存在且 commit hash 有效
- 每个 package 有 verification results

### Step 2: Create Integration Branch
```bash
git checkout main
git checkout -b agent/ui-i18n-cleanup/integration
```

### Step 3: Merge Package Branches
按顺序合并:
```bash
# Order matters: 01 first (adds string resources), then 02 and 03 (reference them)
git merge agent/ui-i18n-cleanup/01-strings-xml --no-ff -m "merge: 01-strings-xml — add Chinese string resources"
git merge agent/ui-i18n-cleanup/02-feature-mode-i18n --no-ff -m "merge: 02-feature-mode-i18n — fix 8 feature mode plugins"
git merge agent/ui-i18n-cleanup/03-app-core-i18n --no-ff -m "merge: 03-app-core-i18n — fix app + core module hardcoded English"
```

每个 merge 如果出现冲突:
- 停止并记录冲突文件
- 在 `status/99-finalize.md` 中记录
- 用 `mark-state 99-finalize blocked` 更新状态
- 不解锁后续步骤

### Step 4: Integration Verification
```bash
# 1. 最终检查 values/strings.xml 中无英文
grep -n '>[A-Z][a-z]' app/src/main/res/values/strings.xml | grep -v 'app_name' | grep -v '%' || echo "PASS"

# 2. 检查 feature mode 插件中无硬编码英文 UI
grep -rn '"[A-Z][a-z].*[a-z]{2,}' feature/mode-*/src/main/kotlin --include="*.kt" \
  | grep -v '//' | grep -v 'import' | grep -v 'package' | grep -v 'Log\.' \
  | grep -v 'TAG =' | grep -v 'require\|check\|error\|assert' | grep -v '/test/' \
  || echo "PASS"

# 3. 检查 app + core 模块中无硬编码英文 UI
grep -rn '"[A-Z][a-z].*[a-z]{2,}' app/src/main/java core/device/src/main core/media/src/main --include="*.kt" \
  | grep -v '//' | grep -v 'import' | grep -v 'package' | grep -v 'Log\.' \
  | grep -v 'TAG =' | grep -v 'require\|check\|error\|assert\|TODO' \
  | grep -v 'BuildConfig' | grep -v '\.put(' | grep -v '\.add(' \
  || echo "PASS"

# 4. 检查 strings.xml 两个 locale 文件 key 一致
diff <(grep -oP 'name="\K[^"]+' app/src/main/res/values/strings.xml | sort) \
     <(grep -oP 'name="\K[^"]+' app/src/main/res/values-en/strings.xml | sort) \
     || echo "WARNING: key mismatch (check if intentional)"

# 5. Gradle 编译检查（如果有本地 Android SDK）
# ./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

### Step 5: External QA Gate (if applicable)
根据 INDEX.md 的 Capability Preflight:
- `real-device-visual-qa` 是 external-assist，不阻塞实现
- 如果 user 要求在合并前进行 visual QA，在此处停止并请求用户确认

### Step 6: Merge to Mainline
```bash
git checkout main
git merge agent/ui-i18n-cleanup/integration --no-ff -m "merge: UI i18n cleanup — translate all user-facing English to Chinese"
```

### Step 7: Cleanup
仅在所有步骤成功后:
```bash
# Delete local package branches
git branch -d agent/ui-i18n-cleanup/01-strings-xml
git branch -d agent/ui-i18n-cleanup/02-feature-mode-i18n
git branch -d agent/ui-i18n-cleanup/03-app-core-i18n
git branch -d agent/ui-i18n-cleanup/integration

# Remove local worktrees
git worktree remove .claude/worktrees/01-strings-xml
git worktree remove .claude/worktrees/02-feature-mode-i18n
git worktree remove .claude/worktrees/03-app-core-i18n
git worktree remove .claude/worktrees/99-finalize
```

### Step 8: Write Reports
- `FINAL_REPORT.md`: 完整报告，包含改动统计、通过的验证、已知限制
- `status/99-finalize.md`: 详细完成状态

## Failure Rules
- 任何失败将 99-finalize 标记为 `blocked`
- 记录失败阶段、命令、分支、冲突文件、日志摘要、恢复建议
- 使用 `mark-state 99-finalize blocked --error ... --failed-command ... --conflict-files ... --log-summary ... --recovery-hint ...`
- 保留所有分支/worktree
- 永远不 force-push 或 hard reset

## Success Rules
- 标记 99-finalize 为 `finalized`
- 记录 integration branch, mainline merge commit, verification summary, cleanup results
- 重新运行 finalize 时幂等处理，报告 `already finalized`
