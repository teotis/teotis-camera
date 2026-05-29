# Package: 99-finalize — 验证、合并与清理

## Goal

验证 01-dev-log-tag-system 包的所有验收标准，将变更合并到 main 分支，生成最终报告。

## Allowed Paths

```
docs/plans/real-device-dev-log-tag-system-orchestration/**
```

Forbidden: everything outside the plan directory.

## Steps

### 1. 读取状态

```bash
bash launchers/orchestrate.sh status
bash launchers/orchestrate.sh verify-finalize
```

### 2. 验证包证据

- [ ] `01-dev-log-tag-system` 状态为 `completed`
- [ ] 分支存在，commit hash 已记录
- [ ] 改动文件均在允许路径内
- [ ] 验证命令结果已记录且通过

### 3. 运行最终验证

```bash
# 在包分支上运行测试
./gradlew :app:testDebugUnitTest --tests "com.opencamera.app.DevLogRenderModelTest"
./gradlew :core:session:test
./gradlew :app:compileDebugKotlin
```

### 4. 创建集成分支并合并

```bash
git checkout main
git checkout -b agent/dev-log-tag-system/integration
git merge agent/dev-log-tag-system/01-dev-log-tag-system
```

### 5. 集成验证

```bash
./gradlew :app:testDebugUnitTest
./gradlew :core:session:test
```

### 6. 合并到 mainline

```bash
git checkout main
git merge agent/dev-log-tag-system/integration
```

### 7. 写报告

- [ ] 写 `FINAL_REPORT.md`
- [ ] 写 `status/99-finalize.md`

### 8. 清理

仅在一切成功后：
- 删除 `agent/dev-log-tag-system/01-dev-log-tag-system` 本地分支
- 删除 `agent/dev-log-tag-system/integration` 本地分支
- 删除对应 worktree

### 依赖

- 01-dev-log-tag-system 必须 `completed`

### 外部 QA

真机验证为 external-assist：安装 APK 后验证 ColorLab 页面滑动行为和 LINK Tab 日志内容是人工步骤，不阻塞合并。
