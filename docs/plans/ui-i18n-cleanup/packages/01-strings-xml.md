# Package 01-strings-xml - strings.xml 资源英文清理

## Objective
清理 `values/strings.xml` 中残留的英文文本，确保所有用户可见字符串资源均为中文。

## Allowed Paths
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml` (如需同步英文资源键)

## Forbidden Paths
- 所有其他文件

## Tasks

### 1. 修复 format_color_tone
- **文件**: `app/src/main/res/values/strings.xml` 第 285 行
- **当前**: `<string name="format_color_tone">Color: %.2f, Tone: %.2f</string>`
- **修改为**: `<string name="format_color_tone">颜色: %.2f, 色调: %.2f</string>`

### 2. 全面扫描
- 扫描 `values/strings.xml` 中所有 `<string>` 标签的值
- 确认除技术缩写 (RAW, ISO, EV, N/A, WB, f/%s, %sx, %sK, %sms, %.1fD, Motion Photo, MP4, JPEG, CCD, B&W) 外无英文残留
- 技术缩写和国际通用术语可保留英文

## Verification Commands
```bash
# 确认 format_color_tone 已翻译
grep 'format_color_tone' app/src/main/res/values/strings.xml
# 输出应包含中文"颜色"而非英文"Color"

# 确认无其他纯英文 string value（排除技术缩写和格式字符串）
grep -P '>[A-Z][a-z]+ [A-Za-z]+<' app/src/main/res/values/strings.xml | grep -v 'Motion Photo\|MP4\|JPEG\|B&W\|CCD'
```

## Acceptance Criteria
- `format_color_tone` 已翻译为中文
- 无其他纯英文句子或短语残留在 strings.xml 中
- 技术缩写 (RAW/ISO/EV/WB/N/A) 保留不翻译

## Expected Evidence
- git diff 显示 format_color_tone 的修改
- grep 扫描结果确认无其他英文残留

## Branch/Worktree Policy
- Branch: `agent/ui-i18n-cleanup/01-strings-xml`
- Worktree: `.claude/worktrees/01-strings-xml`

## Unlock Condition
- None (wave 1, no dependencies)
