# Package: 03-i18n-developer-workflow

## Mission

构建新语言支持的低成本开发工作流：新语言 bootstrap 脚本、开发者文档、以及高级 i18n 特性评估（RTL、复数、热切换增强等）。

## Allowed Paths

- `scripts/i18n_bootstrap.sh` (新建)
- `scripts/i18n_audit.py` (只读，可能需要引用)
- `docs/plans/i18n-multi-language-system/` (只能写 status 和 scratch)
- `app/src/main/res/` (只能通过脚本间接影响，不直接修改)

## Forbidden Paths

- All Kotlin source directories
- All feature module directories
- `INDEX.md`
- Other package status files

## Acceptance Criteria

### A1. 新语言 bootstrap 脚本

创建 `scripts/i18n_bootstrap.sh`：

- [ ] 接受两个参数：`<locale_code>` (如 `ja`, `ko`, `fr`) 和 `<language_name>` (如 "日本語", "한국어", "Français")
- [ ] 自动创建 `app/src/main/res/values-<locale_code>/strings.xml`
- [ ] 以 `values-en/strings.xml` 为模板，保留所有 key，将 value 替换为占位符格式 `[<language_name>] <english_value>`（便于翻译者识别）
- [ ] 输出后续手动步骤清单：
  1. 在 `AppLanguage.kt` 枚举中添加新语言条目
  2. 在 `AppTextResolver.languageDisplayName()` 中添加新语言的显示名称
  3. 运行 `python3 scripts/i18n_audit.py` 确认覆盖率
  4. 逐条翻译占位符为实际目标语言文本
  5. 构建验证：`./gradlew :app:assembleDebug`
- [ ] 脚本本身幂等——重复运行不会覆盖已有的翻译内容（检查目标文件是否已存在）

### A2. 开发者文档

在 `docs/plans/i18n-multi-language-system/` 下输出一份工作流文档（作为 scratch 产物或直接写到 plan 目录下的 `I18N_WORKFLOW.md`）：

- [ ] 覆盖以下主题：
  - 项目 i18n 架构概述（`AppTextResolver`、`AppLanguage`、`PersistedSettingsSerializer`、`AppCompatDelegate`）
  - 如何添加新语言（分步指南，引用 bootstrap 脚本和审计脚本）
  - 字符串资源的组织约定（`values/` = 默认/中文，`values-<locale>/` = 其他语言）
  - 如何在代码中使用 `AppTextResolver` 获取本地化字符串（而非直接 `context.getString()`）
  - 注意事项：fallback 值的使用、格式化字符串的占位符一致性、不要在 `AppTextResolver` 中硬编码中文

### A3. 高级 i18n 特性评估

输出一份评估（作为 scratch 产物），覆盖：

- [ ] **RTL 支持**：检查当前 `AndroidManifest.xml` 已声明 `android:supportsRtl="true"`，评估添加阿拉伯语等 RTL 语言需要的额外工作量（主要是布局适配，当前仅一个 `activity_main.xml` 布局文件，大部分 UI 为编程式构建，适配难度较低）
- [ ] **热切换增强**：评估当前 `AppCompatDelegate.setApplicationLocales()` 的热切换覆盖范围；说明哪些场景下需要 Activity 重建才能完全生效（若有）；给出是否需要显示"重启生效"提示的建议
- [ ] **复数支持**：评估当前 `strings.xml` 中是否有需要复数形式的字符串（如 "1 page" vs "N pages"）；若有，建议使用 Android `plurals` 资源
- [ ] **第三方库/系统 UI 本地化**：CameraX 等系统 API 的 UI 文字（如权限对话框）由系统处理，无需应用层干预——在文档中注明

## Verification Commands

```bash
# 1. Bootstrap 脚本语法和帮助
bash scripts/i18n_bootstrap.sh --help

# 2. 测试 bootstrap（dry-run 或临时目录）
bash scripts/i18n_bootstrap.sh xx "Test" --dry-run

# 3. 编译验证
./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- `scripts/i18n_bootstrap.sh` 文件内容
- `docs/plans/i18n-multi-language-system/I18N_WORKFLOW.md` 文档内容
- 高级特性评估报告（scratch 产物）
- Bootstrap 脚本 dry-run 输出
- 构建成功输出

## Notes

1. Bootstrap 脚本以 `values-en/strings.xml` 而非 `values/strings.xml`（中文）为模板，因为英文作为国际通用语更适合作为翻译中间语言。

2. 不要在 03 包中实际添加新语言（那是后续独立任务），只需确保基础设施和文档就位。

3. 文档应简洁实用，避免过度工程化的流程描述。
