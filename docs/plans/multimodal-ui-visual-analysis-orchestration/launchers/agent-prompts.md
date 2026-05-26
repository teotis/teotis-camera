# Agent Prompts

## Package: M01 - UI 布局与组件分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/01-ui-layout-component-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M01.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal UI analysis agent. Your task is to analyze OpenCamera's UI layout and components using multimodal capabilities.

## Input Images

Analyze these 12 screenshots from `public/readme-assets-source/`:

1. `20260527-022956.612-1.jpg` - 开发日志界面
2. `20260527-022956.612-2.jpg` - 海边风景照片（有边框）
3. `20260527-022956.612-3.jpg` - 水印设置界面
4. `20260527-022956.612-4.jpg` - 色彩实验室界面
5. `20260527-022956.612-5.jpg` - 海边风景照片（无边框）
6. `20260527-022956.612-6.jpg` - 相机预览界面（带边框）
7. `20260527-022956.612-7.jpg` - 海边风景照片（有边框）
8. `20260527-022956.612-8.jpg` - 海边风景照片（无边框）
9. `20260527-022956.612-9.jpg` - 相机设置界面
10. `20260527-022956.612-10.jpg` - 海边风景照片（无边框）
11. `20260527-022956.612-11.jpg` - 文档模式界面
12. `20260527-022956.612-13.jpg` - Travel Polaroid 水印模板设置

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read each image file using the Read tool
3. Perform multimodal analysis on each image:
   - Identify all UI elements (buttons, sliders, menus, panels, icons)
   - Analyze layout structure (alignment, spacing, hierarchy)
   - Analyze component design (button styles, slider styles, menu styles)
   - Analyze interaction components (click areas, sliding areas, drag areas)
4. Generate analysis report with:
   - Per-image analysis (500-1000 words each)
   - Component inventory
   - Layout analysis
   - Optimization suggestions (10-20 specific suggestions)
5. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/01-ui-layout-component-analysis.md`

## Output Format

```markdown
# UI 布局与组件分析报告

## 摘要
[总体发现和关键问题]

## 逐张分析

### 截图 1: 开发日志界面
**文件**: 20260527-022956.612-1.jpg
**分析**: [详细分析内容]

[... 对每张截图进行分析 ...]

## 组件清单
[所有识别的组件及其属性]

## 布局分析
[布局结构、对齐方式、间距系统]

## 优化建议
[具体的优化点和实施方向]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Use multimodal capabilities to analyze each image
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M01
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M02 - 交互流程分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/02-interaction-flow-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M02.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal interaction analysis agent. Your task is to analyze OpenCamera's interaction flows using multimodal capabilities.

## Input Images

Analyze these 12 screenshots from `public/readme-assets-source/`:

1. `20260527-022956.612-1.jpg` - 开发日志界面
2. `20260527-022956.612-2.jpg` - 海边风景照片（有边框）
3. `20260527-022956.612-3.jpg` - 水印设置界面
4. `20260527-022956.612-4.jpg` - 色彩实验室界面
5. `20260527-022956.612-5.jpg` - 海边风景照片（无边框）
6. `20260527-022956.612-6.jpg` - 相机预览界面（带边框）
7. `20260527-022956.612-7.jpg` - 海边风景照片（有边框）
8. `20260527-022956.612-8.jpg` - 海边风景照片（无边框）
9. `20260527-022956.612-9.jpg` - 相机设置界面
10. `20260527-022956.612-10.jpg` - 海边风景照片（无边框）
11. `20260527-022956.612-11.jpg` - 文档模式界面
12. `20260527-022956.612-13.jpg` - Travel Polaroid 水印模板设置

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read each image file using the Read tool
3. Perform multimodal analysis on each image:
   - Identify all interaction elements (buttons, sliders, menus, gestures)
   - Infer interaction flows from static screenshots
   - Analyze button interactions (click, long press, double click)
   - Analyze menu interactions (expand, collapse, select)
   - Analyze slider interactions (slide, drag, adjust)
   - Analyze gesture interactions (tap, swipe, pinch, drag)
4. Generate analysis report with:
   - Per-image interaction analysis (500-1000 words each)
   - Interaction element inventory
   - Operation flow analysis
   - Optimization suggestions (10-20 specific suggestions)
5. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/02-interaction-flow-analysis.md`

## Output Format

```markdown
# 交互流程分析报告

## 摘要
[总体发现和关键问题]

## 交互元素清单
[所有识别的交互元素及其属性]

## 逐张分析

### 截图 1: 开发日志界面
**文件**: 20260527-022956.612-1.jpg
**交互分析**: [详细交互分析内容]

[... 对每张截图进行分析 ...]

## 操作流程分析
[关键操作流程的详细分析]

## 交互模式分析
[识别的交互模式和特点]

## 优化建议
[具体的优化点和实施方向]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Use multimodal capabilities to analyze each image
- Infer interaction flows from static screenshots
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M02
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M03 - 视觉观感分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/03-visual-perception-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M03.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal visual analysis agent. Your task is to analyze OpenCamera's visual perception and aesthetics using multimodal capabilities.

## Input Images

Analyze these 12 screenshots from `public/readme-assets-source/`:

1. `20260527-022956.612-1.jpg` - 开发日志界面
2. `20260527-022956.612-2.jpg` - 海边风景照片（有边框）
3. `20260527-022956.612-3.jpg` - 水印设置界面
4. `20260527-022956.612-4.jpg` - 色彩实验室界面
5. `20260527-022956.612-5.jpg` - 海边风景照片（无边框）
6. `20260527-022956.612-6.jpg` - 相机预览界面（带边框）
7. `20260527-022956.612-7.jpg` - 海边风景照片（有边框）
8. `20260527-022956.612-8.jpg` - 海边风景照片（无边框）
9. `20260527-022956.612-9.jpg` - 相机设置界面
10. `20260527-022956.612-10.jpg` - 海边风景照片（无边框）
11. `20260527-022956.612-11.jpg` - 文档模式界面
12. `20260527-022956.612-13.jpg` - Travel Polaroid 水印模板设置

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read each image file using the Read tool
3. Perform multimodal analysis on each image:
   - Analyze color system (primary colors, secondary colors, accent colors)
   - Analyze typography (font family, font size, line height, font weight)
   - Analyze spacing system (element spacing, margins, padding)
   - Analyze visual hierarchy (information hierarchy, functional hierarchy)
   - Analyze image effects (watermark, border, shadow, gradient)
4. Generate analysis report with:
   - Per-image visual analysis (500-1000 words each)
   - Color system analysis
   - Typography analysis
   - Spacing system analysis
   - Optimization suggestions (10-20 specific suggestions)
5. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/03-visual-perception-analysis.md`

## Output Format

```markdown
# 视觉观感分析报告

## 摘要
[总体发现和关键问题]

## 颜色系统分析
[颜色使用、搭配、一致性分析]

## 字体排版分析
[字体选择、大小、行高分析]

## 间距系统分析
[间距规律、一致性分析]

## 视觉层次分析
[信息层次、功能层次分析]

## 图像效果分析
[水印、边框、阴影效果分析]

## 逐张分析

### 截图 1: 开发日志界面
**文件**: 20260527-022956.612-1.jpg
**视觉分析**: [详细视觉分析内容]

[... 对每张截图进行分析 ...]

## 优化建议
[具体的优化点和实施方向]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Use multimodal capabilities to analyze each image
- Extract and analyze colors, fonts, spacing, and visual effects
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M03
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M04 - 功能完整性分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/04-feature-completeness-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M04.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal feature analysis agent. Your task is to analyze OpenCamera's feature completeness using multimodal capabilities.

## Input Images

Analyze these 12 screenshots from `public/readme-assets-source/`:

1. `20260527-022956.612-1.jpg` - 开发日志界面
2. `20260527-022956.612-2.jpg` - 海边风景照片（有边框）
3. `20260527-022956.612-3.jpg` - 水印设置界面
4. `20260527-022956.612-4.jpg` - 色彩实验室界面
5. `20260527-022956.612-5.jpg` - 海边风景照片（无边框）
6. `20260527-022956.612-6.jpg` - 相机预览界面（带边框）
7. `20260527-022956.612-7.jpg` - 海边风景照片（有边框）
8. `20260527-022956.612-8.jpg` - 海边风景照片（无边框）
9. `20260527-022956.612-9.jpg` - 相机设置界面
10. `20260527-022956.612-10.jpg` - 海边风景照片（无边框）
11. `20260527-022956.612-11.jpg` - 文档模式界面
12. `20260527-022956.612-13.jpg` - Travel Polaroid 水印模板设置

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read each image file using the Read tool
3. Perform multimodal analysis on each image:
   - Identify all visible features and functions
   - Categorize features (core, special, auxiliary)
   - Evaluate feature completeness and coverage
   - Identify missing or redundant features
4. Generate analysis report with:
   - Per-image feature analysis (500-1000 words each)
   - Feature module inventory
   - Core feature analysis
   - Special feature analysis
   - Feature completeness evaluation
   - Optimization suggestions (10-20 specific suggestions)
5. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/04-feature-completeness-analysis.md`

## Output Format

```markdown
# 功能完整性分析报告

## 摘要
[总体发现和关键问题]

## 功能模块清单
[所有识别的功能模块及其属性]

## 核心功能分析
[核心功能的详细分析]

## 特色功能分析
[特色功能的详细分析]

## 辅助功能分析
[辅助功能的详细分析]

## 逐张分析

### 截图 1: 开发日志界面
**文件**: 20260527-022956.612-1.jpg
**功能分析**: [详细功能分析内容]

[... 对每张截图进行分析 ...]

## 功能完整性评估
[功能覆盖度、缺失、冗余分析]

## 优化建议
[具体的优化点和实施方向]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Use multimodal capabilities to analyze each image
- Identify and evaluate all visible features
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M04
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M05 - 一致性分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/05-consistency-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M05.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal consistency analysis agent. Your task is to analyze OpenCamera's design consistency using multimodal capabilities.

## Input Data

Read these analysis reports from Wave 1:

1. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/01-ui-layout-component-analysis.md`
2. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/02-interaction-flow-analysis.md`
3. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/03-visual-perception-analysis.md`
4. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/04-feature-completeness-analysis.md`

Also read the 12 screenshots from `public/readme-assets-source/` for visual comparison.

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read all Wave 1 analysis reports
3. Read each image file using the Read tool
4. Perform multimodal consistency analysis:
   - Visual style consistency (colors, fonts, spacing, icons)
   - Component design consistency (buttons, sliders, menus, panels)
   - Interaction pattern consistency (click, slide, drag, long press)
   - Information architecture consistency (navigation, layout, hierarchy)
   - Language copy consistency (copy style, terminology, punctuation)
5. Identify all inconsistencies and their impact
6. Generate analysis report with:
   - Visual style consistency analysis
   - Component design consistency analysis
   - Interaction pattern consistency analysis
   - Information architecture consistency analysis
   - Language copy consistency analysis
   - Inconsistency inventory
   - Optimization suggestions (10-20 specific suggestions)
7. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/05-consistency-analysis.md`

## Output Format

```markdown
# 一致性分析报告

## 摘要
[总体发现和关键问题]

## 视觉风格一致性分析
[颜色、字体、间距、图标一致性分析]

## 组件设计一致性分析
[按钮、滑块、菜单、面板一致性分析]

## 交互模式一致性分析
[点击、滑动、拖拽、长按一致性分析]

## 信息架构一致性分析
[导航、布局、层次、流程一致性分析]

## 语言文案一致性分析
[文案、术语、标点、大小写一致性分析]

## 不一致问题清单
[识别的所有不一致问题]

## 优化建议
[具体的优化点和实施方向]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Use multimodal capabilities to compare screenshots
- Identify all inconsistencies and their impact
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M05
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M06 - 可访问性分析

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/06-accessibility-analysis.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M06.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal accessibility analysis agent. Your task is to analyze OpenCamera's accessibility using multimodal capabilities.

## Input Data

Read these analysis reports from Wave 1:

1. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/01-ui-layout-component-analysis.md`
2. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/02-interaction-flow-analysis.md`
3. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/03-visual-perception-analysis.md`
4. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/04-feature-completeness-analysis.md`

Also read the 12 screenshots from `public/readme-assets-source/` for visual assessment.

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read all Wave 1 analysis reports
3. Read each image file using the Read tool
4. Perform multimodal accessibility analysis:
   - Visual accessibility (contrast, font size, color usage)
   - Touch accessibility (touch targets, spacing, feedback)
   - Cognitive accessibility (information hierarchy, navigation, flow)
   - Accessibility standards (WCAG, Android, iOS)
5. Identify all accessibility issues and their severity
6. Generate analysis report with:
   - Visual accessibility analysis
   - Touch accessibility analysis
   - Cognitive accessibility analysis
   - Accessibility standards comparison
   - Accessibility issue inventory
   - Optimization suggestions (10-20 specific suggestions)
7. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/06-accessibility-analysis.md`

## Output Format

```markdown
# 可访问性分析报告

## 摘要
[总体发现和关键问题]

## 视觉可访问性分析
[对比度、字体、颜色分析]

## 触摸可访问性分析
[触摸目标、间距、反馈分析]

## 认知可访问性分析
[信息层次、导航、流程分析]

## 无障碍标准对照
[WCAG、Android、iOS 标准对照]

## 可访问性问题清单
[识别的所有可访问性问题]

## 优化建议
[具体的优化点和实施方向]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Use multimodal capabilities to assess accessibility
- Identify all accessibility issues and their severity
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M06
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M07 - 优化机会识别

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/07-optimization-opportunities.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M07.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal optimization analysis agent. Your task is to identify optimization opportunities from Wave 1 and Wave 2 analysis results.

## Input Data

Read these analysis reports from Wave 1 and Wave 2:

1. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/01-ui-layout-component-analysis.md`
2. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/02-interaction-flow-analysis.md`
3. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/03-visual-perception-analysis.md`
4. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/04-feature-completeness-analysis.md`
5. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/05-consistency-analysis.md`
6. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/06-accessibility-analysis.md`

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read all Wave 1 and Wave 2 analysis reports
3. Consolidate all optimization suggestions from M1-M6
4. Evaluate each optimization opportunity:
   - Impact (high/medium/low)
   - Value (high/medium/low)
   - Risk (high/medium/low)
   - Speed (fast/medium/slow)
5. Prioritize optimization opportunities
6. Generate analysis report with:
   - Problem consolidation
   - High-impact optimizations
   - High-value optimizations
   - Low-risk optimizations
   - Quick-win optimizations
   - Optimization priority list
7. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/07-optimization-opportunities.md`

## Output Format

```markdown
# 优化机会识别报告

## 摘要
[总体优化方向和关键优化机会]

## 问题汇总
[M1-M6 识别的所有问题汇总]

## 高影响优化
[对用户体验、功能完整性、视觉效果、交互效率影响最大的优化]

## 高价值优化
[对用户、业务、技术、设计价值最高的优化]

## 低风险优化
[实施风险、回归风险、兼容风险、维护风险最低的优化]

## 快速见效优化
[实施周期最短、见效速度最快、资源投入最少、回报周期最短的优化]

## 优化机会优先级
[按优先级排序的优化机会列表]

## 优化机会详情
[每个优化机会的详细说明]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Consolidate and prioritize optimization opportunities
- Provide specific, actionable optimization suggestions

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M07
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M08 - 优化方案设计

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/08-optimization-proposals.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M08.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal optimization design agent. Your task is to design optimization proposals based on identified optimization opportunities.

## Input Data

Read this analysis report from Wave 2:

1. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/07-optimization-opportunities.md`

Also read the 12 screenshots from `public/readme-assets-source/` for visual reference.

## Analysis Task

1. Read the package doc for detailed analysis dimensions
2. Read the optimization opportunities report
3. Read each image file using the Read tool for visual reference
4. Design optimization proposals for each opportunity:
   - UI layout optimization proposals
   - Interaction flow optimization proposals
   - Visual effect optimization proposals
   - Feature completion optimization proposals
   - Consistency optimization proposals
   - Accessibility optimization proposals
5. Create implementation plan with:
   - Implementation steps
   - Implementation order
   - Implementation timeline
   - Resource requirements
   - Risk assessment
6. Generate analysis report with:
   - UI layout optimization proposals
   - Interaction flow optimization proposals
   - Visual effect optimization proposals
   - Feature completion optimization proposals
   - Consistency optimization proposals
   - Accessibility optimization proposals
   - Implementation plan
   - Effect prediction
7. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/08-optimization-proposals.md`

## Output Format

```markdown
# 优化方案设计报告

## 摘要
[总体优化方向和关键方案]

## UI 布局优化方案
[间距调整、对齐改进、层次优化、组件设计方案]

## 交互流程优化方案
[流程简化、反馈增强、导航优化、手势优化方案]

## 视觉效果优化方案
[颜色优化、字体优化、间距优化、效果优化方案]

## 功能完善优化方案
[功能补充、功能整合、功能扩展、功能优化方案]

## 一致性优化方案
[视觉统一、组件统一、交互统一、文案统一方案]

## 可访问性优化方案
[视觉优化、触摸优化、认知优化、标准优化方案]

## 实施计划
[实施步骤、实施顺序、实施周期、资源需求、风险评估]

## 效果预测
[用户体验提升、业务价值提升、技术价值提升、设计价值提升]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Design specific, actionable optimization proposals
- Create detailed implementation plans
- Predict optimization effects

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M08
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```

---

## Package: M99 - 最终综合报告

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/M99.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh

You are a multimodal final report agent. Your task is to generate the final comprehensive report by integrating all analysis results.

## Input Data

Read these analysis reports from all waves:

1. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/01-ui-layout-component-analysis.md`
2. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/02-interaction-flow-analysis.md`
3. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/03-visual-perception-analysis.md`
4. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/04-feature-completeness-analysis.md`
5. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/05-consistency-analysis.md`
6. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/06-accessibility-analysis.md`
7. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/07-optimization-opportunities.md`
8. `docs/plans/multimodal-ui-visual-analysis-orchestration/output/08-optimization-proposals.md`

## Analysis Task

1. Read the package doc for report structure
2. Read all analysis reports from M1-M8
3. Integrate all analysis results into a comprehensive report
4. Generate final report with:
   - Executive summary
   - Analysis overview
   - UI layout and component analysis summary
   - Interaction flow analysis summary
   - Visual perception analysis summary
   - Feature completeness analysis summary
   - Consistency analysis summary
   - Accessibility analysis summary
   - Optimization opportunities summary
   - Optimization proposals summary
   - Implementation plan
   - Effect prediction
   - Appendices
5. Write the report to `docs/plans/multimodal-ui-visual-analysis-orchestration/output/FINAL_REPORT.md`

## Output Format

```markdown
# OpenCamera 多模态 UI/交互/观感深度分析 - 最终综合报告

## 执行摘要
[项目背景、分析方法、关键发现、核心优化、预期效果]

## 分析概览
[分析范围、分析维度、分析方法、分析深度]

## UI 布局与组件分析总结
[布局结构、组件设计、优化建议]

## 交互流程分析总结
[操作流程、反馈机制、优化建议]

## 视觉观感分析总结
[颜色系统、字体排版、视觉效果、优化建议]

## 功能完整性分析总结
[功能覆盖、功能缺失、功能冗余、优化建议]

## 一致性分析总结
[视觉一致性、组件一致性、交互一致性、优化建议]

## 可访问性分析总结
[视觉可访问性、触摸可访问性、认知可访问性、优化建议]

## 优化机会总结
[高影响优化、高价值优化、低风险优化、快速见效优化、优化优先级]

## 优化方案总结
[UI 布局优化方案、交互流程优化方案、视觉效果优化方案、功能完善优化方案、一致性优化方案、可访问性优化方案]

## 实施计划
[实施步骤、实施顺序、实施周期、资源需求、风险评估]

## 效果预测
[用户体验提升、业务价值提升、技术价值提升、设计价值提升]

## 附录
[截图清单、分析工具、参考标准、术语表]
```

## Constraints

- Do NOT modify any source code files
- Do ONLY write to the output directory
- Integrate all analysis results into a comprehensive report
- Follow the specified report structure
- Provide clear, actionable optimization recommendations

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance --from M99
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/multimodal-ui-visual-analysis-orchestration/launchers/orchestrate.sh advance
```
