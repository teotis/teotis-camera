# Package 99-finalize - 集成验证与合并

## Objective
验证所有功能包的修改，合并到集成分支，最终合并回主线。

## Allowed Paths
- 所有本次编排涉及的路径（只读检查）
- `docs/plans/ui-i18n-cleanup/FINAL_REPORT.md`
- `docs/plans/ui-i18n-cleanup/status/99-finalize.md`

## Tasks

### 1. 验证所有功能包
- 读取 INDEX.md、graph、所有 package docs、所有 status 文件和 state.tsv
- 运行 `bash launchers/orchestrate.sh verify-finalize`
- 验证每个功能包：
  - acceptance criteria 已满足
  - changed files 在 allowed paths 内
  - branch、worktree、base commit、commit hash 已记录
  - verification commands 通过

### 2. 集成合并
- 创建或更新集成分支 `agent/ui-i18n-cleanup/integration`
- 按顺序合并：01-strings-xml → 02-layout-xml → 03-kotlin-hardcoded
- 遇到冲突时停止并记录

### 3. 集成验证
```bash
# 编译验证
./gradlew assembleDebug

# 最终英文残留扫描
grep -rn 'android:text="[A-Za-z]' app/src/main/res/layout/
grep -rn 'Toast\.makeText.*"[A-Za-z]' app/src/main/java/ --include="*.kt"
grep -rn '\.text = "[A-Z][a-z]' app/src/main/java/ --include="*.kt"
grep 'format_color_tone' app/src/main/res/values/strings.xml
```

### 4. 合并回主线
- 将集成分支合并回 main
- 仅在验证通过后执行

### 5. 清理
- 删除编排创建的本地 worktrees/branches
- 写 FINAL_REPORT.md

## Verification Commands
```bash
./gradlew assembleDebug
grep -rn 'android:text="[A-Za-z]' app/src/main/res/layout/
grep -rn 'Toast\.makeText.*"[A-Za-z]' app/src/main/java/ --include="*.kt"
```

## Acceptance Criteria
- 所有功能包状态为 completed
- 集成分支编译通过
- 无硬编码英文 UI 字符串残留
- 集成分支已合并回主线

## Branch/Worktree Policy
- Branch: `agent/ui-i18n-cleanup/99-finalize`
- Worktree: `.claude/worktrees/99-finalize`

## Unlock Condition
- All functional packages (01-strings-xml, 02-layout-xml, 03-kotlin-hardcoded) completed
