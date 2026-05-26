# Codex Initialization

This repository is an Android/Kotlin camera project named `OpenCamera`. Treat it as a Claude Code project that has been initialized for Codex work.

## Local Command Rule

- Always run shell commands through `rtk`, per `/Users/dingren/.codex/RTK.md`.
- Examples:
  - `rtk rg --files`
  - `rtk ./gradlew --no-daemon :app:assembleDebug`
  - `rtk ./scripts/verify_stage_7_observability.sh`
- If `rtk` hides too much output for diagnosis, use `rtk proxy <command>` for the raw command.

## Project Shape

- Build system: Gradle Kotlin DSL.
- Root project name: `OpenCamera`.
- Android app module: `:app`.
- Core modules:
  - `:core:session` owns the session kernel, state/effect handling, recovery, diagnostics, and trace.
  - `:core:device` owns device-facing contracts, shot translation, zoom/video/manual request modeling, and device runtime issue types.
  - `:core:media` owns capture/media pipeline contracts.
  - `:core:mode` owns mode catalog and plugin contracts.
  - `:core:settings` owns persisted settings and feature catalog contracts.
- Feature modules:
  - `:feature:mode-photo`
  - `:feature:mode-video`
  - `:feature:mode-night`
  - `:feature:mode-portrait`
  - `:feature:mode-document`
  - `:feature:mode-humanistic`
  - `:feature:mode-pro`
- App composition and Android/CameraX integration live mainly in `app/src/main/java/com/opencamera/app` and `app/src/main/java/com/opencamera/app/camera`.
- Build output is redirected to `~/.codex-build/OpenCamera` by the root `build.gradle.kts`.

## Architecture Contract

The high-level architecture is "four main layers plus cross-cutting governance":

- `Mode Plugin`
- `Session Kernel`
- `Device Adapter`
- `Media Pipeline`
- Cross-cutting stability, recovery, observability, diagnostics, and automation.

Important boundaries:

- UI renders state and dispatches intents only. It must not directly drive camera runtime behavior.
- `Session Kernel` is the only runtime owner for session state, preview/capture/recording state, recovery decisions, and state transitions.
- Mode plugins describe requested behavior and policies. They must not call CameraX/Camera2/HAL directly.
- Device adapters translate abstract session/device requests into platform implementation details.
- Runtime state and persisted settings must stay separate.
- Do not create a second hidden session kernel in coordinators, managers, bridges, UI, or adapters.
- Any hardware-dependent capability must have explicit `supported`, `unsupported`, or `degraded` semantics that can be tested.

## Current Stage

- Current milestone: Stage `7`, stability governance and automation hardening.
- Current documented progress: about `80%`.
- Stage `6` and `6B-*` feature work is considered complete/frozen unless the user explicitly reopens it.
- Stage `7` already has owners for diagnostics, runtime issue forwarding, recovery failure handling, zoom, thermal issue forwarding, background recovery, performance budget, provider invalidation, and preview startup stall watchdog.
- Remaining high-value gaps are mostly platform/real-device dependent, especially true provider death/restart signals, long-running real-device recovery behavior, and device-specific performance thresholds.
- Do not enter a new stage without explicit user approval. One approval only applies to one stage transition.

## Required Working Loop

When the user asks to continue or advance work without naming a narrow target:

1. Read `codex/plan.md`, `codex/prompt.md`, and `codex/documentation.md`.
2. Compare those docs with current code and verification results.
3. Identify the current stage, biggest verified gap, and the smallest valuable closed loop.
4. Implement only that stage-local closed loop.
5. Run focused verification first, then the necessary stage verification.
6. Fix failures before stacking new changes.
7. Update `codex/documentation.md` after a meaningful verified loop or before stopping.
8. Reassess whether another high-value closed loop remains before ending.

Stop when:

- Stage completion requires user approval to cross into the next stage.
- Further progress needs external information, device access, permissions, or user decision.
- Remaining work is low value or would add more complexity than benefit.
- Verification cannot be made meaningful in the local environment.

## Verification

Use targeted Gradle tests for the touched modules, then the relevant stage script.

Current Stage 7 verification entry:

### Build Isolation（强制）

所有 Gradle 命令必须明确选择构建隔离策略：

- **主 workspace（本仓库根目录）**：直接使用 `rtk ./gradlew ...` 即可，构建输出在 `~/.codex-build/OpenCamera/`。
- **worktree 或外部 agent 执行**：必须使用 `rtk ./scripts/run_isolated_gradle.sh <gradle-args>` 以隔离构建根目录。wrapper 会根据 `git rev-parse --show-toplevel` 的 hash 自动派生独立输出路径 `~/.codex-build/OpenCamera-<hash>/`。
- **旧 stage 验证脚本**：`verify_stage_6b0/6b1/6b2/6b3` 已支持 `OPENCAMERA_BUILD_ROOT > CODEX_BUILD_ROOT > 默认根`，外部 agent 单独调用这些脚本时必须传入隔离根。
- 不得依赖"从主 root 执行"作为唯一的隔离手段。并行 worktree 同时编译时，共享构建输出会导致 class 文件/ jar 元数据污染。
- 遇到 `CODEX_BUILD_ROOT` 下出现缺失 Kotlin 类、部分 jar、metadata checksum/read 失败、或来自另一 workspace 的 source path 时，先用隔离构建根重新运行最小失败命令，再判定为产品回归。

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

That script currently covers:

- `DefaultCameraSessionTest`
- `SessionDiagnosticsTest`
- `AndroidThermalRuntimeIssueMonitorTest`
- `CameraXCaptureAdapterCapabilityDetectionTest`
- `CameraXCaptureAdapterRuntimeIssueTest`
- `PreviewStartupRuntimeIssueMonitorTest`
- `SessionUiRenderModelTest`
- `CameraSessionCoordinatorTest`
- `:app:assembleDebug`

Useful focused commands:

```bash
# 聚焦模块测试（主 workspace）
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest

# 聚焦模块测试（worktree / 隔离构建）
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest

# 组装验证
rtk ./gradlew --no-daemon :app:assembleDebug
```

If Gradle shows transient Kotlin/build-directory errors in `~/.codex-build/OpenCamera`, rerun the smallest relevant verification serially before declaring a product regression.

## Documentation Rules

- `codex/documentation.md` is the living status document.
- Preserve its main structure:
  - Current status
  - Current stage judgment
  - Current verification baseline
  - Current residual risks
  - Next-step suggestions
  - Recent effective loops
  - Historical archive
- Update it after meaningful verified changes.
- Agent handoff and orchestration documents must live under `docs/plans/`. Do not create or revive `codex/agent_plans/`; link new package indexes from `docs/plans/INDEX.md`.
- Do not rewrite `codex/plan.md`, `codex/prompt.md`, or `codex/implement.md` core rules unless the user explicitly asks. Suggestions about those files should be recorded separately.
- Some historical docs contain old absolute paths. Treat the current workspace `/Volumes/Extreme_SSD/project/codex_camera` as authoritative.

## Edit Constraints

- Keep changes scoped to the current closed loop.
- Do not do cosmetic whole-repo formatting.
- Do not move ownership across layers casually.
- Do not add broad abstractions unless they remove real duplication or match the existing architecture.
- Prefer existing contract/test patterns.
- Add or update tests for behavior changes, especially session/device/app coordinator behavior.
- This directory may not be a Git repository. Do not rely on Git status as the sole source of truth for user changes.

- 默认构建输出在 `~/.codex-build/OpenCamera/`（由 `build.gradle.kts` 控制）。可通过 Gradle property `opencamera.buildRoot` 或环境变量 `OPENCAMERA_BUILD_ROOT` / `CODEX_BUILD_ROOT` 覆盖。
- `.codex-build/` 中的构建产物不纳入项目交付

## Security

- 本仓库是无 API key 的 Android 客户端项目，不涉及外部 API 调用。
- 构建和测试在本地环境运行，不触网。

## Agent-specific adapters

- Claude Code should read `CLAUDE.md`, which points back to this file.
- Codex app should use this `AGENTS.md` as the shared project instruction file.
