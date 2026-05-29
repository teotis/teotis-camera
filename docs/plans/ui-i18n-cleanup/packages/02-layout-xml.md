# Package 02-layout-xml - 布局 XML 硬编码英文清理

## Objective
清理布局 XML 文件中硬编码的英文 `android:text` 属性，改为引用字符串资源。

## Allowed Paths
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml` (如需添加新字符串资源)

## Forbidden Paths
- 所有其他文件（特别是 worktrees/ 目录下的副本）

## Tasks

### 1. 修复 activity_main.xml 中的 "Depth"
- **文件**: `app/src/main/res/layout/activity_main.xml` 第 852 行
- **当前**: `android:text="Depth"`
- **方案 A** (推荐): 改为 `android:text="@string/label_depth"` 并在 strings.xml 中添加 `<string name="label_depth">景深</string>`
- **方案 B**: 如果已有其他地方通过代码设置此文本，可移除 `android:text` 属性

### 2. 全面扫描其他布局文件
```bash
# 扫描主模块所有布局文件中的硬编码英文
grep -rn 'android:text="[A-Za-z]' app/src/main/res/layout/
# 扫描其他模块
grep -rn 'android:text="[A-Za-z]' feature/*/src/main/res/layout/ core/*/src/main/res/layout/
```

## Verification Commands
```bash
# 确认无硬编码英文
grep -rn 'android:text="[A-Za-z]' app/src/main/res/layout/
# 应无输出

# 确认新添加的字符串资源存在
grep 'label_depth' app/src/main/res/values/strings.xml
```

## Acceptance Criteria
- activity_main.xml 中不再有硬编码英文 `android:text`
- 如添加新字符串资源，strings.xml 和 values-en/strings.xml 均需同步
- 所有布局文件中无其他硬编码英文文本

## Expected Evidence
- git diff 显示布局文件和 strings.xml 的修改
- grep 确认无残留硬编码英文

## Branch/Worktree Policy
- Branch: `agent/ui-i18n-cleanup/02-layout-xml`
- Worktree: `.claude/worktrees/02-layout-xml`

## Unlock Condition
- None (wave 1, no dependencies)
