package com.opencamera.core.mode

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.FrameRatio
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameRatioDelegateTest {

    @Test
    fun `initial frame ratio is 4_3`() {
        val delegate = FrameRatioDelegate(
            context = ModeContext(),
            modeEventPrefix = "photo",
            effectSpecProvider = { EffectSpec.EMPTY }
        )

        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())
    }

    @Test
    fun `cycle advances and emits event snapshot and effect`() = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        var headline = ""
        val delegate = FrameRatioDelegate(
            context = ModeContext(
                eventSink = { event -> events += event },
                onEffectSpecChanged = { effectSpec -> effects += effectSpec }
            ),
            modeEventPrefix = "photo",
            effectSpecProvider = { EffectSpec.EMPTY }
        )

        val signal = delegate.cycleFrameRatio(
            snapshotHeadline = "Frame ratio updated",
            updateSnapshot = { nextHeadline -> headline = nextHeadline }
        )

        assertEquals(FrameRatio.RATIO_16_9, delegate.currentFrameRatio())
        assertEquals(listOf("photo.frame-ratio.selected.16x9"), events)
        assertEquals(1, effects.size)
        assertEquals("Frame ratio updated", headline)
        assertEquals(ModeSignal.ShowHint("Frame: 16:9"), signal)
    }

    @Test
    fun `select valid ratio updates current ratio`() = runBlocking {
        val events = mutableListOf<String>()
        val delegate = FrameRatioDelegate(
            context = ModeContext(eventSink = { event -> events += event }),
            modeEventPrefix = "night",
            effectSpecProvider = { EffectSpec.EMPTY }
        )

        val signal = delegate.selectFrameRatio(
            ratio = FrameRatio.RATIO_1_1,
            updateSnapshot = {}
        )

        assertEquals(FrameRatio.RATIO_1_1, delegate.currentFrameRatio())
        assertEquals(listOf("night.frame-ratio.selected.1x1"), events)
        assertEquals(ModeSignal.ShowHint("画幅：1:1"), signal)
    }

    @Test
    fun `select unsupported ratio does not emit side effects`() = runBlocking {
        val events = mutableListOf<String>()
        var effectCount = 0
        val delegate = FrameRatioDelegate(
            context = ModeContext(
                eventSink = { event -> events += event },
                onEffectSpecChanged = { effectCount += 1 }
            ),
            modeEventPrefix = "document",
            effectSpecProvider = { EffectSpec.EMPTY },
            supportedRatios = listOf(FrameRatio.RATIO_4_3)
        )

        val signal = delegate.selectFrameRatio(
            ratio = FrameRatio.RATIO_16_9,
            updateSnapshot = {}
        )

        assertEquals(FrameRatio.RATIO_4_3, delegate.currentFrameRatio())
        assertTrue(events.isEmpty())
        assertEquals(0, effectCount)
        assertEquals(ModeSignal.ShowHint("当前模式不支持 16:9 画幅"), signal)
    }
}
