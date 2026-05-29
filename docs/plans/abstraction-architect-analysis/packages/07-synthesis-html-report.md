# Package 07 - Synthesis HTML Report

## Task

整合所有分析包的发现，生成一个交互式 HTML 综合报告。报告应包含架构健康度评分、问题优先级矩阵和可操作的优化路线图。

## Dependencies

- 01-architecture-boundary-integrity (completed)
- 02-domain-model-unification (completed)
- 03-session-kernel-invariants (completed)
- 04-cross-cutting-concerns (completed)
- 05-conversion-glue-detection (completed)
- 06-platform-configuration-branches (completed)

## Allowed Paths

- Read: all status files under `docs/plans/abstraction-architect-analysis/status/`
- Write: `docs/plans/abstraction-architect-analysis/status/07-synthesis-html-report.md`
- Write: `docs/plans/abstraction-architect-analysis/status/report.html`

## Forbidden Paths

- Any source code modification

## Report Structure

```html
<!-- 报告应包含以下章节 -->
1. 执行摘要 - 架构健康度评分 (0-100)
2. 架构边界分析 - 01 包发现的可视化
3. 领域模型分析 - 02 包发现的类型关系图
4. Session Kernel 分析 - 03 包发现的状态机图
5. 横切关注点分析 - 04 包发现的耦合热力图
6. 转换胶水分析 - 05 包发现的数据流图
7. 平台配置分析 - 06 包发现的分支模式
8. 优先级矩阵 - 影响 vs 工作量的散点图
9. 优化路线图 - 分阶段的改进建议
10. 附录 - 完整发现清单
```

## Verification Commands

```bash
# 验证 HTML 文件存在且非空
test -s docs/plans/abstraction-architect-analysis/status/report.html && echo "OK" || echo "MISSING"
# 验证无源码修改
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```

## Expected Evidence

- 自包含 HTML 文件，包含内联 CSS 和 JavaScript
- 深色/浅色主题支持
- 可交互的图表和表格
- 响应式布局

## Acceptance Criteria

- [ ] 读取了所有 6 个分析包的 status 文件
- [ ] 整合了所有发现到统一报告
- [ ] HTML 文件可直接在浏览器中打开
- [ ] 包含交互式图表和过滤功能
- [ ] 无源码修改
