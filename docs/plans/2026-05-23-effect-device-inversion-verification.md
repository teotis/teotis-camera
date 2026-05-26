# Effect Device Inversion Verification Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:verification-before-completion` before claiming this work is complete. Use `rtk` for every shell command. This plan is text-only and does not require device screenshots or video.

**Goal:** Prove that `core:effect` no longer depends on `core:device`, and that resolver behavior did not regress.

---

## Required Checks

Run these after the implementation packages land.

### 1. Source Import Gate

```bash
rtk rg "com\\.opencamera\\.core\\.device" core/effect/src/main core/effect/src/test
```

Expected:

- No matches.

### 2. Build File Gate

```bash
rtk rg "core:device" core/effect/build.gradle.kts
```

Expected:

- No matches.

### 3. Gradle Dependency Gate

```bash
rtk ./gradlew --no-daemon :core:effect:dependencies --configuration compileClasspath
```

Expected:

- Output for `:core:effect` does not include `project :core:device`.
- Output may include `project :core:media` and `project :core:settings`.

If `project :core:device` appears anywhere in the `compileClasspath` tree, the inversion is incomplete.

### 4. Focused Tests

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:capability:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

If the implementation chooses not to introduce `:core:capability`, replace `:core:capability:test` with the module that owns the moved capability graph resolver. The dependency gate still applies unchanged.

### 5. Stage 7 Gate

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Expected:

- All Stage 7 observability tests and `:app:assembleDebug` pass.

If Gradle reports transient Kotlin/build directory errors under `~/.codex-build/OpenCamera`, rerun the smallest failed verification serially before declaring a product regression.

## Behavior Regression Checklist

Confirm tests still prove:

- Empty `EffectSpec` resolves to empty report.
- Filter, watermark, frame, and selfie mirror effects remain supported.
- Portrait effect without depth support degrades to `renderPath = "focus"`.
- Document effect without document geometry support degrades by setting `autoCrop = false` and `contrastProfile = null`.
- Filter capture render is unsupported when the processor is unavailable.
- Filter preview render becomes `PREVIEW_ONLY` when capture render is unavailable.
- Night multi-frame degrades to `single-frame` when device or processor support is missing.
- Manual controls support, saved-only, and unsupported states map to the same graph results as before.
- RAW output support and saved-only states map to the same graph results as before.
- Live photo temporal requirements remain unsupported when temporal assembly is unavailable.

## Documentation Update

After verification passes, update `codex/documentation.md` with:

- The exact dependency inversion completed.
- The final owner of `CapabilityGraphResolver`.
- The verification commands that passed.
- Any residual risk, especially if `core:mode` still exposes `DeviceCapabilities` for mode support checks.

## Completion Criteria

This work is complete only when all are true:

- `core:effect` has no source import or Gradle dependency on `core:device`.
- App composition still creates both effect and capability graph resolvers through explicit query adapters.
- Focused tests pass.
- Stage 7 verification passes.
- `codex/documentation.md` records the architectural cleanup.

Do not claim completion after only adding `EffectCapabilityQuery`; that is a partial inversion.
