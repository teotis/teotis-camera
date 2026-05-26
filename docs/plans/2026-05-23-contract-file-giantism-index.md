# 2026-05-23 Contract File Giantism Governance Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are text-only and do not require screenshots, videos, or visual judgment.

## Goal

Reduce the two oversized core contract files into smaller, naturally owned files without changing package names, public symbol names, runtime behavior, or the Stage 7 verification contract.

## Verification Verdict

The external review is substantially correct, with two important corrections.

Accepted evidence:

- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt` is 1382 lines.
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt` is 1133 lines.
- No source file references `SettingsContractsKt` or `MediaPipelineContractsKt`, so same-package top-level moves should not hit direct file-class references.
- `SettingsContracts.kt` currently mixes:
  - value enums at lines `3-176`, `267-300`, `326-389`, and `399-497`;
  - data models and capability constraints at lines `178-606`;
  - `FeatureCatalog` operations and actions at lines `606-723`;
  - filter share codec, imported-profile serializer, metadata helpers, and manual draft serializer at lines `728-931`;
  - persisted settings actions and reducer at lines `933-1112`;
  - built-in filter profiles and watermark templates at lines `1113-1382`.
- `MediaPipelineContracts.kt` currently mixes:
  - shot/media primitives and result models at lines `7-376`;
  - concrete postprocessors and `ShotExecutor` at lines `378-589`;
  - ShotGraph 2.0 node models at lines `596-681`;
  - algorithm processor contracts at lines `685-725`;
  - frame stream contracts at lines `730-850`;
  - processor bridge and graph builder logic at lines `853-1070`;
  - media save transaction result logic at lines `1078-1133`.

Corrections to the external proposal:

- `PersistedSettingsSerializer.kt` already exists as a separate 208-line file. The remaining Settings problem is not "all serialization lives in the contract file"; it is that share codecs, imported-profile serialization, manual draft serialization, default catalog data, and reducers still live beside type definitions.
- The split should not move symbols to new packages or modules in the first pass. The low-risk path is same package, same public names, and no product behavior changes. Wider API cleanup can follow only after the mechanical split compiles and Stage 7 verification remains green.

## Grothendieck Judgment

This is a valid Ruthless Generalization target because the current files contain multiple abstractions that only share a package, not a reason to change together.

The natural owners are:

- value vocabulary: enums and small value types;
- data contracts: persisted settings, feature catalog, shot plans, media results, graph nodes;
- state evolution: actions and reducers;
- transport/serialization: share codecs, metadata codecs, draft serializers, persisted map serializers;
- default catalog data: built-in profiles and templates;
- execution helpers: shot executor and concrete postprocessors;
- graph/transaction derivation: pure transformations from existing models.

The intended benefit is not product functionality. It is maintenance locality: future edits to watermark defaults, filter sharing, frame stream contracts, or media save transactions should not require opening and recompiling one large mixed abstraction.

## Work Packages

1. [Settings Contract Split](./2026-05-23-settings-contract-split-plan.md)
   - Lowest product risk.
   - Keeps `com.opencamera.core.settings` and all public symbol names stable.
   - Moves enums, data models, actions/reducers, codecs, operations, and defaults into focused files.

2. [Media Pipeline Contract Split](./2026-05-23-media-pipeline-contract-split-plan.md)
   - Slightly broader blast radius because `core:device`, `core:session`, features, and app postprocessors all import media symbols.
   - Keeps `com.opencamera.core.media` and all public symbol names stable.
   - Separates shot lifecycle, postprocessors, ShotGraph, algorithm contracts, frame stream, graph planning, and save transaction contracts.

## Recommended Execution Order

Run the Settings split first. It has cleaner boundaries, an existing serializer split, and a smaller verification surface.

After the Settings split is green, the Media split can be done independently. If separate agents work in parallel, use one integrator to handle final verification and avoid broad formatting conflicts in Gradle/Kotlin generated imports.

## Global Invariants

- Do not change package declarations.
- Do not rename public classes, objects, functions, enum entries, storage keys, profile IDs, metadata keys, or template IDs.
- Do not move ownership across architecture layers.
- Do not move media or settings contracts into `app`.
- Do not introduce new abstractions beyond file-level ownership and tiny delegating wrappers needed for source compatibility.
- Do not change Stage 7 behavior or add feature work.
- Keep all shell commands prefixed with `rtk`.

## Global Verification

After Settings split:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionUiRenderModelTest
```

After Media split:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Before declaring the whole governance pass complete:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Completion Criteria

- `SettingsContracts.kt` is deleted or reduced to an empty package placeholder.
- `MediaPipelineContracts.kt` is deleted or reduced to an empty package placeholder.
- No new file grows beyond roughly 450 lines in the first split.
- All moved symbols remain in their original package and compile for existing Kotlin source imports.
- The verification commands above pass.
- `codex/documentation.md` records the split, verification result, and any residual risk.

