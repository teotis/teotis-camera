# Package 99 - Finalize

## Task

验证所有分析包完成，整合结果，将最终 HTML 报告复制到计划根目录。

## Steps

1. Read INDEX.md, graph, all package docs, all status files, and `state.tsv`.
2. Run `bash launchers/orchestrate.sh verify-finalize`.
3. Verify every functional package:
   - Status file exists and contains findings
   - No source code was modified
   - Evidence pack complete
4. Copy `status/report.html` to `docs/plans/abstraction-architect-analysis/report.html`.
5. Write `FINAL_REPORT.md` summarizing:
   - Total findings by severity
   - Top 5 priority items
   - Architecture health score
   - Next steps
6. Write `status/99-finalize.md`.

## Merge Strategy

- No code branches to merge (analysis only)
- Final HTML report is the primary deliverable
- Copy report.html to plan root for easy access

## Verification

```bash
# 验证最终报告存在
test -s docs/plans/abstraction-architect-analysis/report.html && echo "OK" || echo "MISSING"
# 验证无源码修改
rtk git diff --name-only | grep -v 'docs/plans/abstraction-architect-analysis' && echo "VIOLATION" || echo "CLEAN"
```
