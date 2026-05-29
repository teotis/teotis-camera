# Package: 02-translation-audit-completeness

## Mission

创建翻译完整性审计工具，允许开发者快速确认任意语言设定下所有 UI 文字内容是否完整、恰当；同时补齐当前英文翻译的缺失项。

## Allowed Paths

- `app/src/main/res/values/strings.xml` (只读，用于比较)
- `app/src/main/res/values-en/strings.xml`
- `scripts/i18n_audit.py` (新建)
- `scripts/README.md` (可选，如新建应简短)
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt` (只读，用于交叉验证)

## Forbidden Paths

- All Kotlin source directories outside the read-only files listed above
- All feature module directories
- `INDEX.md`
- Other package status files
- `res/values/strings.xml` (不可修改)

## Acceptance Criteria

### A1. 翻译审计脚本

创建 `scripts/i18n_audit.py`，功能：

- [ ] 扫描 `app/src/main/res/` 下所有 `values*` 目录中的 `strings.xml`
- [ ] 解析每个 strings.xml 中的所有 `<string name="...">` 条目
- [ ] 以默认语言（`values/strings.xml`）为基准，报告每个其他语言文件的：
  - 缺失的 key（在基准中存在但在目标语言中不存在）
  - 多余的 key（在目标语言中存在但在基准中不存在）
  - 字符串值为空或仅为占位符的 key
  - 统计摘要：每种语言的总 key 数、覆盖率百分比
- [ ] 支持 `--format json` 输出 JSON 格式（便于 CI 集成）
- [ ] 支持 `--check <locale>` 仅检查指定语言
- [ ] 退出码：0 = 所有语言完整，1 = 存在缺失 key

脚本使用 Python 3 标准库（`xml.etree.ElementTree`），无外部依赖。

### A2. 补齐英文缺失字符串

在 `values-en/strings.xml` 中补齐以下缺失的字符串（与 `values/strings.xml` 对比确认）：

当前已知缺失（约13条，位于 `values/strings.xml` 末尾的文档批处理相关字符串）：
- `document_batch_organizer_title`
- `document_batch_page_count`
- `document_batch_remove`
- `document_batch_move_up`
- `document_batch_move_down`
- `document_batch_crop_applied`
- `document_batch_crop_skipped`
- `document_batch_crop_failed`

以及可能在处理状态区域缺失的 key。

- [ ] 运行审计脚本确认 `values-en/strings.xml` 覆盖率达到 100%（与 `values/strings.xml` 比较）
- [ ] 所有新增的英文翻译准确、自然
- [ ] `watermark_template_pure_text` 和 `watermark_template_blur_four_border` 的 fallback 值也加入英文 strings.xml（当前 `AppTextResolver` 中硬编码了中文 fallback）

### A3. 审计脚本集成到验证脚本

- [ ] 脚本可通过 `rtk ./scripts/i18n_audit.py` 或 `python3 scripts/i18n_audit.py` 运行
- [ ] 输出对开发者友好，清晰标注缺失项和所在行号

## Verification Commands

```bash
# 1. 运行审计脚本
python3 scripts/i18n_audit.py

# 2. 确认英文覆盖率 100%
python3 scripts/i18n_audit.py --check en

# 3. JSON 输出模式
python3 scripts/i18n_audit.py --format json

# 4. 编译验证
./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- `scripts/i18n_audit.py` 文件内容
- 审计脚本运行输出，显示中英双语 100% 覆盖
- Git diff 展示 `values-en/strings.xml` 新增的英文翻译
- 构建成功输出

## Notes

1. 审计脚本应能正确处理 Android strings.xml 的各种格式：`<string name="x">value</string>`、`<string name="x"><b>bold</b></string>`、`<string name="x">"%d items"</string>` 等。

2. 不需要处理 `string-array`、`plurals` 等高级资源类型（当前项目未使用），但脚本架构可预留扩展点。

3. 翻译质量（准确性、自然度）由人工 review，脚本只做完整性检查。
