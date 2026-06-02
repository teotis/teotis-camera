package com.opencamera.core.mode

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio

class FrameRatioDelegate(
    private val context: ModeContext,
    private val modeEventPrefix: String,
    private val effectSpecProvider: () -> EffectSpec,
    private val supportedRatios: List<FrameRatio> = DEFAULT_FRAME_RATIOS
) {
    init {
        require(supportedRatios.isNotEmpty()) {
            "FrameRatioDelegate requires at least one supported ratio"
        }
    }

    private var frameRatioIndex = 0

    fun currentFrameRatio(): FrameRatio = supportedRatios[frameRatioIndex]

    suspend fun cycleFrameRatio(
        snapshotHeadline: String,
        updateSnapshot: (headline: String) -> Unit
    ): ModeSignal {
        frameRatioIndex = (frameRatioIndex + 1) % supportedRatios.size
        val frameRatio = currentFrameRatio()
        return publishSelection(
            frameRatio = frameRatio,
            snapshotHeadline = snapshotHeadline,
            hintMessage = "Frame: ${frameRatio.label}",
            updateSnapshot = updateSnapshot
        )
    }

    suspend fun selectFrameRatio(
        ratio: FrameRatio,
        snapshotHeadline: String = "Frame ratio updated",
        updateSnapshot: (headline: String) -> Unit
    ): ModeSignal {
        val nextIndex = supportedRatios.indexOf(ratio)
        if (nextIndex < 0) {
            return ModeSignal.ShowHint("Current mode does not support ${ratio.label} frame ratio")
        }
        frameRatioIndex = nextIndex
        return publishSelection(
            frameRatio = ratio,
            snapshotHeadline = snapshotHeadline,
            hintMessage = "Frame: ${ratio.label}",
            updateSnapshot = updateSnapshot
        )
    }

    private suspend fun publishSelection(
        frameRatio: FrameRatio,
        snapshotHeadline: String,
        hintMessage: String,
        updateSnapshot: (headline: String) -> Unit
    ): ModeSignal {
        context.eventSink(
            "${modeEventPrefix}.frame-ratio.selected.${frameRatio.eventTag()}"
        )
        updateSnapshot(snapshotHeadline)
        context.onEffectSpecChanged(effectSpecProvider())
        return ModeSignal.ShowHint(hintMessage)
    }

    companion object {
        val DEFAULT_FRAME_RATIOS: List<FrameRatio> = listOf(
            FrameRatio.RATIO_4_3,
            FrameRatio.RATIO_16_9,
            FrameRatio.RATIO_1_1
        )
    }
}
