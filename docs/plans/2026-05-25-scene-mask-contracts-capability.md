# Scene Mask Contracts And Capability

## Goal

Create the pure Kotlin contracts for OpenCamera scene masks so preview-time segmentation and saved-photo segmentation can share one vocabulary without leaking image buffers into `SessionState`.

## Context

- User request: introduce open-source or self-built subject/body recognition to handle preview, postprocess, portrait, foreground/background, and color optimization.
- Verified facts:
  - Current architecture requires UI to dispatch intents and render state only; image/mask frame data must not live in the session kernel.
  - `core:media` already owns `ShotResult`, `MediaPostProcessor`, `PostProcessSpec`, `MediaMetadata`, and pipeline notes.
  - `core:effect` already owns preview render models and effect capability/degradation language.
  - `core:settings` owns persisted settings and `FilterRenderSpec`/Color Lab data.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessorContracts.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
- Non-goals:
  - Do not add ML Kit/MediaPipe dependencies in this package.
  - Do not store mask pixels in `SessionState`, `PersistedSettings`, or mode state.
  - Do not make segmentation required for photo capture.

## Implementation Scope

- Add small contracts under `core/media` or a new `core:scene` only if the project already accepts new core modules. Prefer `core/media` for the first pass to avoid build churn.
- Define scene mask metadata:

```kotlin
enum class SceneMaskRole {
    PERSON_SUBJECT,
    FOREGROUND,
    BACKGROUND,
    DEPTH_APPROXIMATION,
    SEMANTIC_REGION
}

enum class SceneMaskQuality {
    UNAVAILABLE,
    PREVIEW_APPROXIMATE,
    SAVED_PHOTO,
    DEGRADED
}

data class SceneMaskTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val maskWidth: Int,
    val maskHeight: Int,
    val rotationDegrees: Int,
    val mirrorHorizontally: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f
)

data class SceneMaskDescriptor(
    val maskId: String,
    val role: SceneMaskRole,
    val quality: SceneMaskQuality,
    val backendId: String,
    val confidence: Float,
    val transform: SceneMaskTransform,
    val diagnostics: List<String> = emptyList()
)
```

- For pixel payload, use an app-layer interface rather than storing arrays in metadata:

```kotlin
interface SceneMaskPayload {
    val descriptor: SceneMaskDescriptor
    fun alphaAt(maskX: Int, maskY: Int): Float
}
```

- Add metadata tag helpers for `SceneMaskDescriptor` only. Do not serialize pixel data into `ShotResult.metadata.customTags`.
- Add capability contract:

```kotlin
enum class SceneMaskSupport {
    SUPPORTED,
    DEGRADED,
    UNSUPPORTED
}

data class SceneMaskCapability(
    val subjectMask: SceneMaskSupport,
    val savedPhotoMask: SceneMaskSupport,
    val previewMask: SceneMaskSupport,
    val backendId: String,
    val reason: String? = null
)
```

## Steps

1. Inspect existing package boundaries and choose the lowest-churn home for contracts.
2. Add contracts and metadata codecs.
3. Add tests for:
   - descriptor metadata round trip;
   - normalized confidence clamp;
   - transform values preserve crop/rotation;
   - unsupported/degraded support can be represented without a backend.
4. Add pipeline note conventions:
   - `scene-mask:backend=<id>`
   - `scene-mask:preview=approximate|degraded|unsupported`
   - `scene-mask:saved=applied|degraded|unsupported`
   - `scene-mask:reason=<reason>`

## Acceptance Criteria

- Core tests pass without any Android or ML dependency.
- The contracts can describe preview approximate mask and saved-photo mask independently.
- Mask pixels have no route into session state or persisted settings.
- Downstream postprocessors can read descriptor metadata and decide whether to apply subject/background-aware rendering.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
```

## Risks And Notes

- Avoid adding a new core module unless necessary; multiple pending refactor plans already touch core dependencies.
- Keep contracts intentionally small. Full semantic segmentation and depth maps should fit later without forcing the first implementation to solve them.
