# Package: 99-finalize — i18n Multi-Language System Integration

## Mission

验证三个功能包的完整性，合并到集成分支，运行集成验证，最终合入 mainline。

## Allowed Paths (all authorized for 99-finalize)

- Full repository (merge operations only)
- `docs/plans/i18n-multi-language-system/FINAL_REPORT.md`
- `docs/plans/i18n-multi-language-system/status/99-finalize.md`

## Acceptance Criteria

### F1. 包完整性验证

对每个功能包验证：
- [ ] 状态为 `completed`
- [ ] 分支存在且包含预期 commit
- [ ] 变更文件均在 allowed paths 内
- [ ] acceptance criteria 均已满足
- [ ] 验证命令均已通过

### F2. 集成合并

- [ ] 创建或更新集成分支 `feat/i18n-multi-language`
- [ ] 按 Merge Strategy 顺序合并：01 → 02 → 03
- [ ] 无合并冲突（若有，记录并 block）
- [ ] 集成验证通过

### F3. 集成验证

```bash
# 1. settings 模块测试
./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test

# 2. app 模块测试
./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest

# 3. 完整组装验证
./gradlew --no-daemon :app:assembleDebug

# 4. 翻译审计
python3 scripts/i18n_audit.py
```

### F4. Mainline 合并

- [ ] 集成分支合并到 mainline（local non-force merge）
- [ ] 合并后 mainline 编译通过
- [ ] 合并后测试通过

### F5. 清理

- [ ] 删除所有由本编排创建的本地包分支/worktree
- [ ] 最终状态写入 `FINAL_REPORT.md`

## External Gates

以下外部验证不在自动化范围内：
- **real-device-language-switch-qa**: 在真机上安装 APK、切换语言、验证所有面板文字正确渲染。属于 release confidence 级别，不阻塞代码合并。

## Failure Rules

- 任何失败设置 `99-finalize` 为 `blocked`
- 记录失败阶段、命令、冲突文件
- 保留分支和工作树
- 不同状态/workspace 不一致时在分离 worktree 中验证

## Success Rules

- 标记 `99-finalize` 为 `finalized`
- 记录集成分支、mainline merge commit、验证摘要、清理结果
- 重复运行 finalize 必须幂等，报告 `already finalized`
