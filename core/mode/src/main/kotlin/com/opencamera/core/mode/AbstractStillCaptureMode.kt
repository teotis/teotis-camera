package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.WatermarkTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Abstract base for static still-capture modes (Photo, Portrait, Night, Humanistic).
 *
 * Subclasses provide mode-specific behavior via:
 * - [buildEffectSpec] — construct the preview/post-process EffectSpec
 * - [buildCaptureStrategy] — assemble the CaptureStrategy for a shutter press
 * - [buildSnapshot] — produce the ModeSnapshot (UI strings are mode-specific)
 * - [buildDefaultDetail] — default detail text for the snapshot
 *
 * Common behavior is handled by this base:
 * - FrameRatioDelegate management
 * - Watermark template / datetime / camera params utilities
 * - Countdown / live-photo / runtime-state proxies
 * - Session event reduction via [reduceStillShotSessionEvent]
 * - Default lifecycle hook implementations (onEnter, onExit, onDeviceCapabilitiesChanged, etc.)
 */
abstract class AbstractStillCaptureMode(
    protected val context: ModeContext
) : ModeController {

    protected val frameRatioDelegate = FrameRatioDelegate(
        context = context,
        modeEventPrefix = modeEventPrefix(),
        effectSpecProvider = { buildEffectSpec() }
    )

    private val _snapshot by lazy { MutableStateFlow(buildSnapshot(headline = initialHeadline())) }

    override val snapshot: StateFlow<ModeSnapshot> by lazy { _snapshot.asStateFlow() }

    // ── Subclass contract ──────────────────────────────────────────────

    abstract override val id: ModeId

    /** Unique event prefix for FrameRatioDelegate, e.g. "photo", "portrait". */
    protected abstract fun modeEventPrefix(): String

    /** Headline shown when the mode is first created (before onEnter). */
    protected abstract fun initialHeadline(): String

    /** Build the preview/post-process EffectSpec for the current mode state. */
    protected abstract fun buildEffectSpec(): EffectSpec

    /**
     * Build the CaptureStrategy to submit when the shutter is pressed.
     * @param effectSpec the current EffectSpec
     * @param countdownSeconds countdown duration in seconds (0 = no countdown)
     */
    protected abstract fun buildCaptureStrategy(
        effectSpec: EffectSpec,
        countdownSeconds: Int
    ): ModeSignal.SubmitCapture

    /**
     * Produce a ModeSnapshot for the given [headline] and optional [detail].
     * Subclasses control UI labels (title, shutterLabel, action labels) and state flags.
     */
    protected abstract fun buildSnapshot(
        headline: String,
        detail: String? = null
    ): ModeSnapshot

    /** Default detail text for the snapshot (called when detail is null). */
    protected abstract fun buildDefaultDetail(): String

    // ── Optional overrides ─────────────────────────────────────────────

    /** Available flash modes. Override to enable flash cycling via secondary action. */
    protected open fun availableFlashModes(): List<FlashMode> = listOf(FlashMode.OFF)

    /** Current flash mode index. Override with a backing field if flash is supported. */
    protected open var flashModeIndex: Int = 0

    /** Optional ProVariantState. Non-null enables pro action button. */
    protected open val proVariantState: ProVariantState? get() = null

    /** Headline when onDeviceCapabilitiesChanged fires. Override for mode-specific text. */
    protected open fun capabilitiesChangedHeadline(): String = "${id.name.lowercase()} mode active"

    /** Headline for onEnter. */
    protected open fun enterHeadline(): String = "${id.name.lowercase()} mode active"

    /** Headline for onExit. */
    protected open fun exitHeadline(): String = "${id.name.lowercase()} mode inactive"

    /** Headline for stillCaptureResolutionChanged. */
    protected open fun resolutionChangedHeadline(): String = "${id.name.lowercase()} resolution updated"

    /** Headline for stillCaptureQualityChanged. */
    protected open fun qualityChangedHeadline(): String = "${id.name.lowercase()} quality updated"

    /** Text block for session event reduction. */
    protected open fun sessionEventText(): StillShotSessionEventText = StillShotSessionEventText(
        shotStartedHeadline = "${id.name.lowercase()} capture in progress",
        shotCompletedHeadline = "${id.name.lowercase()} saved",
        shotFailedHeadline = "${id.name.lowercase()} capture failed"
    )

    /** Override to add mode-specific enter behavior (before base updates snapshot). */
    protected open suspend fun onModeEnter() {}

    /** Override to add mode-specific exit behavior (before base updates snapshot). */
    protected open suspend fun onModeExit() {}

    // ── ModeController lifecycle ───────────────────────────────────────

    override fun deviceGraph(): DeviceGraphSpec = stillCaptureDeviceGraph(runtimeState())

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
        updateSnapshot(headline = capabilitiesChangedHeadline())
    }

    override suspend fun onLensFacingChanged(
        lensFacing: com.opencamera.core.device.LensFacing
    ) = Unit

    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) {
        updateSnapshot(headline = resolutionChangedHeadline())
    }

    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) {
        updateSnapshot(headline = qualityChangedHeadline())
    }

    override suspend fun onEnter() {
        context.eventSink("${modeEventPrefix()}.enter")
        onModeEnter()
        updateSnapshot(headline = enterHeadline())
        context.onEffectSpecChanged(buildEffectSpec())
    }

    override suspend fun onExit() {
        context.eventSink("${modeEventPrefix()}.exit")
        onModeExit()
        updateSnapshot(headline = exitHeadline())
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> handleShutterPressed()
            ModeIntent.SecondaryActionPressed -> handleSecondaryAction()
            ModeIntent.TertiaryActionPressed -> handleTertiaryAction()
            is ModeIntent.FrameRatioSelected -> handleFrameRatioSelected(intent.ratio)
            ModeIntent.ProActionPressed -> handleProAction()
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        reduceStillShotSessionEvent(
            event = event,
            text = sessionEventText(),
            updateSnapshot = { headline, detail ->
                updateSnapshot(headline = headline, detail = detail)
            }
        )
    }

    // ── Default intent handlers ────────────────────────────────────────

    protected open suspend fun handleShutterPressed(): ModeSignal {
        val effectSpec = buildEffectSpec()
        val signal = buildCaptureStrategy(
            effectSpec = effectSpec,
            countdownSeconds = countdownDuration().seconds
        )
        updateSnapshot(
            headline = if (countdownDuration() == CountdownDuration.OFF) {
                "${id.name.lowercase()} capture requested"
            } else {
                "${id.name.lowercase()} countdown started"
            }
        )
        return signal
    }

    protected open suspend fun handleSecondaryAction(): ModeSignal {
        val modes = availableFlashModes()
        if (modes.size <= 1) return ModeSignal.None
        flashModeIndex = (flashModeIndex + 1) % modes.size
        context.eventSink("${modeEventPrefix()}.flash.selected.${currentFlashMode().name.lowercase()}")
        updateSnapshot(headline = "Flash mode updated")
        context.onEffectSpecChanged(buildEffectSpec())
        return ModeSignal.ShowHint("Flash: ${currentFlashMode().label}")
    }

    protected open suspend fun handleTertiaryAction(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = "Frame ratio updated",
            updateSnapshot = { headline -> updateSnapshot(headline = headline) }
        )

    protected open suspend fun handleFrameRatioSelected(ratio: FrameRatio): ModeSignal =
        frameRatioDelegate.selectFrameRatio(
            ratio = ratio,
            updateSnapshot = { headline -> updateSnapshot(headline = headline) }
        )

    protected open suspend fun handleProAction(): ModeSignal = ModeSignal.None

    // ── Shared utilities ───────────────────────────────────────────────

    protected fun currentFlashMode(): FlashMode = availableFlashModes()[flashModeIndex]

    protected fun currentFrameRatio(): FrameRatio = frameRatioDelegate.currentFrameRatio()

    protected fun countdownDuration(): CountdownDuration =
        context.settingsSnapshot.persisted.photo.countdownDuration

    protected fun livePhotoEnabled(): Boolean =
        context.settingsSnapshot.persisted.photo.livePhotoEnabledByDefault

    protected fun runtimeState(): ModeRuntimeState = context.runtimeState()

    protected fun selectedWatermarkTemplate(): WatermarkTemplate {
        val persistedId = context.settingsSnapshot.persisted.photo.defaultWatermarkTemplateId
        return context.settingsSnapshot.catalog.watermarkTemplates.firstOrNull { it.id == persistedId }
            ?: WatermarkTemplate(id = persistedId, label = persistedId)
    }

    protected fun watermarkDateTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))

    protected fun watermarkCameraParams(vararg extraParts: String): String = buildString {
        append(runtimeState().stillCaptureResolutionPreset.label)
        append(" • ")
        append(currentFrameRatio().label)
        extraParts.forEach { part ->
            append(" • ")
            append(part)
        }
    }

    protected fun toCaptureSpec(
        saveFormat: com.opencamera.core.settings.LiveSaveFormat
    ): LivePhotoCaptureSpec {
        val draft = context.settingsSnapshot.catalog.liveMediaBundleDraft
        return LivePhotoCaptureSpec(
            motionDurationMillis = draft.motionDurationMillis,
            motionMimeType = draft.motionContainer,
            sidecarMimeType = draft.sidecarMimeType,
            saveFormat = saveFormat
        )
    }

    protected fun onOffLabel(enabled: Boolean): String = if (enabled) "on" else "off"

    protected suspend fun cycleFrameRatio(): ModeSignal =
        frameRatioDelegate.cycleFrameRatio(
            snapshotHeadline = "Frame ratio updated",
            updateSnapshot = { headline -> updateSnapshot(headline = headline) }
        )

    protected suspend fun selectFrameRatio(ratio: FrameRatio): ModeSignal =
        frameRatioDelegate.selectFrameRatio(
            ratio = ratio,
            updateSnapshot = { headline -> updateSnapshot(headline = headline) }
        )

    // ── Internal helpers ───────────────────────────────────────────────

    protected fun updateSnapshot(headline: String, detail: String? = null) {
        _snapshot.value = buildSnapshot(
            headline = headline,
            detail = detail ?: buildDefaultDetail()
        )
    }
}
