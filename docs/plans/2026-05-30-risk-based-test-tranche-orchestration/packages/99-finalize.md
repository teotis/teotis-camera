# Package 99 - Finalize

## Package ID
`99-finalize`

## Goal
合并所有测试包到集成分支，运行全量测试验证，合并回 mainline。

## Steps

1. 读取 INDEX、graph、所有 package docs、status files 和 state.tsv。
2. 运行 `bash launchers/orchestrate.sh verify-finalize` 验证所有功能包。
3. 验证每个功能包：
   - acceptance criteria 已满足
   - changed files 在 allowed paths 内
   - branch、worktree、base commit、commit hash 已记录
   - verification commands 通过
4. 创建或更新集成分支 `agent/test-tranche/integration`。
5. 按顺序合并功能包分支：01 → 02 → 03 → 04 → 05。
6. 运行集成验证：
   ```bash
   rtk ./gradlew --no-daemon :core:settings:test :core:device:test :core:media:test :app:testDebugUnitTest
   ```
7. 合并集成分支回 mainline。
8. 产出 FINAL_REPORT.md 和 status/99-finalize.md。
9. 清理所有记录的 worktree/branch。

## Allowed Paths
- 所有功能包的 allowed paths（只读检查）
- 集成分支的 test 文件
- status/ 目录
- FINAL_REPORT.md

## Dependencies
- 01-settings-codecs-tests (status)
- 02-device-media-pure-tests (status)
- 03-app-logic-tests (status)
- 04-app-mixed-tests (status)
- 05-testability-audit (status)

## Verification Commands
```bash
rtk ./gradlew --no-daemon :core:settings:test :core:device:test :core:media:test :app:testDebugUnitTest
```

## Acceptance Criteria
- [ ] 所有功能包已完成
- [ ] 集成分支包含所有测试变更
- [ ] 全量测试通过
- [ ] 合并回 mainline 成功
- [ ] FINAL_REPORT.md 产出

## Merge Strategy
- 合并顺序: 01 → 02 → 03 → 04 → 05
- 冲突处理: 99-finalize 负责解决
- mainline 合并: 本地非 force merge
