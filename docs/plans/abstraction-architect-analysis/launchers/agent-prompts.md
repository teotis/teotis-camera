# Agent Prompts

## Package: 01-architecture-boundary-integrity - Architecture Boundary Integrity

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/01-architecture-boundary-integrity.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/01-architecture-boundary-integrity.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 01-architecture-boundary-integrity`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。只读取源代码，分析架构边界违规，将发现写入 status 文件。

使用 CodeGraph 工具（codegraph_context, codegraph_search, codegraph_callees, codegraph_callers, codegraph_impact）来分析代码结构和依赖关系。

分析维度：
1. 检查所有 `import` 语句中的跨层依赖方向
2. 检查类实例化中的边界违规
3. 检查方法调用链中的隐式耦合
4. 特别关注 `app/` → `core/` → `feature/` 的依赖方向

在 status 文件中按严重程度分类输出发现：critical / warning / info。

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 01-architecture-boundary-integrity completed --verification "analysis complete: N findings"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 01-architecture-boundary-integrity
```

---

## Package: 02-domain-model-unification - Domain Model Unification

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/02-domain-model-unification.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/02-domain-model-unification.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 02-domain-model-unification`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。只读取源代码，分析领域模型统一性，将发现写入 status 文件。

使用 CodeGraph 工具来搜索和比较跨模块的类型定义。

分析维度：
1. 搜索同名或相似的 data class / sealed class / interface
2. 检查类型中的不变量约束（init 块、require、check）
3. 比较跨模块的 zoom / capture / preview / state 相关类型
4. 识别可以合并的重复表示

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 02-domain-model-unification completed --verification "analysis complete: N findings"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 02-domain-model-unification
```

---

## Package: 03-session-kernel-invariants - Session Kernel Invariants

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/03-session-kernel-invariants.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/03-session-kernel-invariants.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 03-session-kernel-invariants`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。只读取源代码，分析 Session Kernel 的不变量完整性，将发现写入 status 文件。

使用 CodeGraph 工具来分析 session 相关的状态定义和转换。

分析维度：
1. 识别所有状态枚举/密封类
2. 追踪状态转换路径和 guard 条件
3. 检查恢复机制的覆盖范围
4. 识别隐式状态（成员变量控制行为但不在状态机中）

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 03-session-kernel-invariants completed --verification "analysis complete: N findings"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 03-session-kernel-invariants
```

---

## Package: 04-cross-cutting-concerns - Cross-Cutting Concerns

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/04-cross-cutting-concerns.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/04-cross-cutting-concerns.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 04-cross-cutting-concerns`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。只读取源代码，分析横切关注点的散落耦合，将发现写入 status 文件。

使用 CodeGraph 工具来搜索日志、配置、诊断相关的调用模式。

分析维度：
1. 搜索日志调用模式（Log.d, Log.w, Log.e, println 等）
2. 搜索配置/设置读取模式
3. 搜索恢复/重试逻辑
4. 搜索可观测性相关代码（trace, metrics, diagnostics）

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 04-cross-cutting-concerns completed --verification "analysis complete: N findings"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 04-cross-cutting-concerns
```

---

## Package: 05-conversion-glue-detection - Conversion Glue Detection

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/05-conversion-glue-detection.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/05-conversion-glue-detection.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 05-conversion-glue-detection`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。只读取源代码，分析转换胶水和不必要的映射层，将发现写入 status 文件。

依赖：先读取 01-04 包的 status 文件了解已发现的边界违规和重复表示。

使用 CodeGraph 工具来追踪数据流和调用链。

分析维度：
1. 搜索 Adapter/Wrapper/Converter 类
2. 追踪关键数据流（capture request, preview state, zoom）的转换链
3. 分析回调/监听器的传递路径
4. 识别不必要的类型转换

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 05-conversion-glue-detection completed --verification "analysis complete: N findings"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 05-conversion-glue-detection
```

---

## Package: 06-platform-configuration-branches - Platform Configuration Branches

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/06-platform-configuration-branches.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/06-platform-configuration-branches.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 06-platform-configuration-branches`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。只读取源代码，分析平台配置分支模式，将发现写入 status 文件。

依赖：先读取 01-04 包的 status 文件了解已发现的架构问题。

使用 CodeGraph 工具来搜索设备特定代码和能力查询。

分析维度：
1. 搜索设备特定的条件分支（Build.MODEL, Build.MANUFACTURER 等）
2. 搜索 API level 分支（Build.VERSION.SDK_INT）
3. 分析 `:core:capability` 的覆盖范围
4. 检查配置驱动的行为差异

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 06-platform-configuration-branches completed --verification "analysis complete: N findings"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 06-platform-configuration-branches
```

---

## Package: 07-synthesis-html-report - Synthesis HTML Report

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/07-synthesis-html-report.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/07-synthesis-html-report.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh scratch-path 07-synthesis-html-report`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

你只能编辑 package doc 中指定的 allowed paths。不要编辑 INDEX.md 或其他包的 status 文件。

这是一个纯分析任务。不要修改任何源代码文件。读取所有分析包的 status 文件，整合发现，生成交互式 HTML 综合报告。

步骤：
1. 读取 01-06 包的 status 文件
2. 提取所有发现，按严重程度和类别分类
3. 计算架构健康度评分
4. 生成交互式 HTML 报告，包含：
   - 深色/浅色主题切换
   - 可折叠的章节
   - 发现过滤和排序
   - 优先级矩阵（影响 vs 工作量）
   - 优化路线图
5. 将 HTML 写入 `status/report.html`

参考现有报告的样式：`/Volumes/Extreme_SSD/project/open_camera/docs/plans/structural_abstraction_architect_report.html`

完成分析后，运行 mark-state 并调用 advance：

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 07-synthesis-html-report completed --verification "HTML report generated"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh advance --from 07-synthesis-html-report
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/status/state.tsv
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh

步骤：
1. 读取所有包的 status 文件和 state.tsv
2. 验证所有功能包已完成
3. 验证无源码修改
4. 将 `status/report.html` 复制到 `docs/plans/abstraction-architect-analysis/report.html`
5. 写 `FINAL_REPORT.md`
6. 写 `status/99-finalize.md`

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "all packages complete, report delivered"
```

---
