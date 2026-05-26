# Session Split 03: Preview Recovery Processor Extraction

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this plan. Use `rtk` for every command. Execute after session split packages 01 and 02.

## Goal

Move preview host, preview bind/recovery, preview runtime issue, preview snapshot, capture feedback snapshot, and tap focus/AE metering behavior out of `DefaultCameraSession.kt` into one focused internal processor.

## Stage 7 Importance

This is the highest-value behavior extraction because current Stage 7 progress is centered on:

- recovery failure guardrail;
- runtime issue forwarding;
- background preview host recovery;
- provider/fatal/recoverable issue semantics;
- preview startup stall;
- first-frame metrics;
- tap focus/AE metering.

The extraction must preserve those semantics exactly.

## Files

Create:

- `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessorTest.kt` if useful for new unit coverage.

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Behavior To Move

Move these current `DefaultCameraSession.kt` handlers into the new processor:

- `handlePreviewHostAttached`
- `handlePreviewHostDetached`
- `requestPendingPreviewHostRecovery`
- `handlePreviewBindingStarted`
- `handlePreviewFirstFrameAvailable`
- `handlePreviewSnapshotUpdated`
- `handleCaptureFeedbackSnapshotUpdated`
- `handlePreviewSurfaceLost`
- `handlePreviewError`
- `handlePreviewRuntimeIssue`
- `handlePreviewStopped`
- `handlePreviewTapToFocus`
- `handlePreviewMeteringCompleted`
- `requestPreviewBinding`
- `requestPreviewUnbind`
- `shouldAttemptPreviewErrorRecovery`

Move these mutable fields out of `DefaultCameraSession.kt` into the processor:

- `pendingPreviewHostRecoveryReason`
- `meteringCounter`

Do not move capture countdown ownership in this package. Preview failures may cancel a countdown through a callback provided by the session runtime.

## Processor Shape

Create:

```kotlin
package com.opencamera.core.session

import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringRequest
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.device.displayReason
import com.opencamera.core.device.normalizedZoomRatioValue
import com.opencamera.core.device.recoveryReason
import com.opencamera.core.media.CaptureFeedbackPreview
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.outputPathOrNull
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class PreviewRecoverySessionProcessor(
    private val state: MutableStateFlow<SessionState>,
    private val effects: MutableSharedFlow<SessionEffect>,
    private val trace: SessionTrace,
    private val updateState: SessionStateUpdater,
    private val countdownInProgress: () -> Boolean,
    private val cancelPendingCountdown: (String) -> Unit
) {
    private var pendingPreviewHostRecoveryReason: String? = null
    private var meteringCounter: Int = 0

    suspend fun process(intent: SessionIntent) {
        when (intent) {
            SessionIntent.PreviewHostAttached -> handlePreviewHostAttached()
            is SessionIntent.PreviewHostDetached -> handlePreviewHostDetached(intent.reason)
            is SessionIntent.PreviewBindingStarted -> handlePreviewBindingStarted(intent.reason, intent.isRecovery)
            is SessionIntent.PreviewFirstFrameAvailable -> handlePreviewFirstFrameAvailable(intent.firstFrameLatencyMillis)
            is SessionIntent.PreviewSnapshotUpdated -> handlePreviewSnapshotUpdated(intent.source, intent.generation)
            is SessionIntent.CaptureFeedbackSnapshotUpdated -> handleCaptureFeedbackSnapshotUpdated(intent.shotId, intent.outputPath)
            is SessionIntent.PreviewSurfaceLost -> handlePreviewSurfaceLost(intent.reason)
            is SessionIntent.PreviewError -> handlePreviewError(intent.reason)
            is SessionIntent.PreviewRuntimeIssue -> handlePreviewRuntimeIssue(intent.issue)
            is SessionIntent.PreviewStopped -> handlePreviewStopped(intent.reason)
            is SessionIntent.PreviewTapToFocus -> handlePreviewTapToFocus(intent.normalizedX, intent.normalizedY)
            is SessionIntent.PreviewMeteringCompleted -> handlePreviewMeteringCompleted(intent.result)
            else -> error("Unexpected preview intent: $intent")
        }
    }
}
```

The exact import list will differ after package 01. Keep the class `internal`.

## Shared State Updater

To avoid copying the huge `updateState` signature into every processor, create a type alias or small functional interface in `DefaultCameraSession.kt` or a new internal file:

```kotlin
internal fun interface SessionStateUpdater {
    fun update(block: SessionStateUpdate.() -> Unit)
}
```

If that is too large for this package, use the existing `updateState` function reference temporarily by passing a small wrapper with named methods needed by preview:

```kotlin
internal interface PreviewSessionMutations {
    fun updatePreviewBlocked(reason: String)
    fun updatePreviewStarting(reason: String, isRecovery: Boolean)
    fun updatePreviewActive(firstFrameLatencyMillis: Long)
    fun updatePreviewError(reason: String, action: String)
    fun updatePreviewStopped(reason: String)
    fun updatePreviewThumbnail(source: ThumbnailSource, generation: Int)
    fun updateCaptureFeedback(shotId: String, outputPath: String)
    fun updatePreviewMeteringRequested(requestId: String, point: PreviewMeteringPoint)
    fun updatePreviewMeteringCompleted(result: PreviewMeteringResult)
}
```

Prefer the smallest wrapper that avoids making the new processor depend on all unrelated session fields.

## DefaultCameraSession Wiring

After package 02, `processPreviewRecoveryIntent(intent)` should become:

```kotlin
private suspend fun processPreviewRecoveryIntent(intent: SessionIntent) {
    previewRecoveryProcessor.process(intent)
}
```

Initialize the processor after `_state` and `_effects` exist:

```kotlin
private val previewRecoveryProcessor = PreviewRecoverySessionProcessor(
    state = _state,
    effects = _effects,
    trace = trace,
    updateState = previewSessionMutations,
    countdownInProgress = ::countdownInProgress,
    cancelPendingCountdown = ::cancelPendingCountdown
)
```

`previewSessionMutations` should be the small wrapper created in this package. It should call the existing `DefaultCameraSession.updateState` function and preserve the current field defaults.

If constructor ordering makes this awkward, initialize it with `by lazy` after `_state` and `_effects`.

## Semantics To Preserve

- `PreviewHostDetached` cancels active countdown.
- Running session + host detach stores a pending recovery reason.
- Reattach requests recovery bind when lifecycle is running, host exists, permission exists, and recording is not active.
- `PreviewBindingStarted` increments `bindCount` and increments `recoveryCount` only for recovery binds.
- First frame updates last/best/worst first-frame latency and sets preview active.
- Preview snapshot must not overwrite a saved-media thumbnail.
- Capture feedback snapshot is ignored for unknown shot id or no active shot.
- Capture feedback snapshot is suppressed when `captureFeedbackPolicyFor(activeShot)` requires saved media.
- Surface loss during recording becomes a preview error without recovery bind.
- Plain preview error can request recovery only when lifecycle running, camera permission granted, host attached, not recording, and no active shot.
- Recoverable runtime issue during active recovery records `preview.recovery.failed` and does not recursively request another recovery.
- Tap focus only emits `ApplyPreviewMetering` when preview is active, permission is granted, and host is attached.
- Stale metering completions must not overwrite newer request feedback.

## Tests To Keep Green

Run at least these `DefaultCameraSessionTest` cases after moving behavior:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.preview error requests recovery bind when preview host is still attached" --tests "com.opencamera.core.session.DefaultCameraSessionTest.recoverable runtime issue requests recovery bind when preview host is attached" --tests "com.opencamera.core.session.DefaultCameraSessionTest.runtime issue during active recovery becomes recovery failed without requeueing recovery"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.preview host reattach after detach requests recovery bind" --tests "com.opencamera.core.session.DefaultCameraSessionTest.camera permission grant resumes pending host recovery after foreground attach"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.active preview tap emits ApplyPreviewMetering effect" --tests "com.opencamera.core.session.DefaultCameraSessionTest.stale metering completion does not overwrite newer request"
```

Then run the full focused session class:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Acceptance Criteria

- Preview/recovery-related behavior lives in `PreviewRecoverySessionProcessor.kt`.
- `DefaultCameraSession.kt` no longer contains the moved preview handler bodies.
- `DefaultCameraSession.kt` still owns the public `CameraSession` API, channel, state flow, and effect flow.
- No preview processor state is exposed publicly.
- No CameraX code is introduced in `core:session`.
- Existing preview/recovery/tap focus trace labels stay stable.
- Stage 7 verification still passes:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not move capture countdown implementation except for calling `cancelPendingCountdown`.
- Do not move shot planning or recording watchdog.
- Do not change `CameraSessionCoordinator`.
- Do not change `RuntimeIssueMonitor`.
- Do not add retries beyond the existing recovery behavior.
