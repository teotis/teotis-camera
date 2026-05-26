# 2026-05-23 Effect Device Dependency Inversion Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are written for text-only agents and do not require screenshot or video analysis.

## Goal

Remove the non-natural `core:effect -> core:device` dependency while preserving the current Stage 7 architecture contract:

- Effect code decides whether an `EffectSpec` entry is supported, degraded, or unsupported.
- Device code owns concrete `DeviceCapabilities`.
- App/session composition wires the two through explicit query adapters.
- No UI, coordinator, or mode plugin should become a second capability owner.

## Verification Result

The external review finding is accepted.

Current direct evidence:

- `core/effect/build.gradle.kts` declares `implementation(project(":core:device"))`.
- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt` imports `DeviceCapabilities` and passes it into `EffectCapabilityResolver`.
- `core/effect/src/main/kotlin/com/opencamera/core/effect/CapabilityGraphResolver.kt` also imports `DeviceCapabilities` plus capability graph contract types from `core.device`.
- `app/src/main/java/com/opencamera/app/AppContainer.kt` constructs both resolvers directly from `cameraAdapter.capabilities`.
- `rtk ./gradlew --no-daemon :core:effect:dependencies --configuration compileClasspath` shows `:core:effect` has `:core:device` on its compile classpath, and `:core:device` brings `:core:media`.

The issue is therefore broader than a single constructor type: cutting only `EffectCapabilityResolver(DeviceCapabilities)` is not enough, because `CapabilityGraphResolver` would keep `core:effect` tied to `core:device`.

## Recommended Work Packages

1. [Effect Capability Query Contract](./2026-05-23-effect-capability-query-contract.md)
   - Smallest behavior-preserving inversion.
   - Adds an effect-owned query interface and a device-owned adapter.
   - Removes the direct `DeviceCapabilities` import from `EffectCapabilityResolver`.

2. [Capability Graph Ownership Cleanup](./2026-05-23-capability-graph-ownership-cleanup.md)
   - Required to actually remove `implementation(project(":core:device"))` from `core:effect`.
   - Moves generic capability graph contracts/resolver out of `core:effect` and out of `core:device` ownership.
   - Introduces device query adapters instead of passing `DeviceCapabilities` into the graph resolver.

3. [Dependency Gate And Verification](./2026-05-23-effect-device-inversion-verification.md)
   - Runs after packages 1 and 2.
   - Defines the dependency assertions, focused tests, and Stage 7 verification required before claiming success.

## Recommended Sequence

Execute package 1 first. It is low risk and gives a clean API for the concrete effect resolver.

Execute package 2 second. It is the true dependency cleanup and will touch more imports in `core:mode`, `core:session`, tests, and app composition.

Execute package 3 last. Do not declare the architectural issue resolved until the Gradle dependency output proves `:core:effect` no longer has `:core:device` on its compile classpath.

## Current Topology

```text
core:effect
  -> core:device
       -> core:media
            -> core:settings

core:effect also directly depends on core:media and core:settings.
```

Problem shape:

- `EffectCapabilityResolver` only needs semantic questions such as portrait depth and document geometry support.
- `CapabilityGraphResolver` is a cross-cutting capability resolver but currently lives inside `core:effect` while importing device-owned graph contracts.
- This makes effect tests and evolution depend on the device module even when testing pure effect behavior.

## Target Topology

```text
core:effect
  -> core:media
  -> core:settings

core:device
  -> core:effect       # only for implementing EffectCapabilityQuery
  -> core:capability   # only if package 2 introduces the capability module
  -> core:media
  -> core:settings

core:capability
  -> core:effect
  -> core:media
```

If the team decides not to add `core:capability`, package 2 may instead place graph query contracts in an existing non-device owner. That fallback must still satisfy the hard acceptance rule: `core:effect` must not depend on `core:device`.

## Conflict Warnings

- Do not leave `CapabilityGraphResolver` in `core:effect` while removing only the `EffectCapabilityResolver` constructor dependency. That would produce a cosmetic inversion, not a real dependency cleanup.
- Do not move concrete `DeviceCapabilities` into `core:effect`.
- Do not make `core:effect` depend on Android, CameraX, Camera2, app, or coordinator classes.
- Do not change effect degradation behavior in this task. Portrait depth fallback and document basic mode fallback should remain behaviorally identical.
- Do not cross into a new product stage. This is Stage 7 architecture hardening, not a feature expansion.

## Global Acceptance

- `core/effect/build.gradle.kts` has no `implementation(project(":core:device"))`.
- `rtk ./gradlew --no-daemon :core:effect:dependencies --configuration compileClasspath` output does not include `project :core:device`.
- `rtk rg "com.opencamera.core.device" core/effect/src/main core/effect/src/test` returns no matches.
- Existing effect resolver tests still cover supported, degraded, and mixed specs.
- Capability graph tests still cover still capture, video, preview snapshots, night multi-frame, manual controls, raw output, portrait segmentation, document geometry, filter render, watermark render, save transaction, and thumbnail result.
- `rtk ./scripts/verify_stage_7_observability.sh` remains green.
