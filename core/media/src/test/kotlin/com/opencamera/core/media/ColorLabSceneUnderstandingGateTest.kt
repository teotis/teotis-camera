package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorLabSceneUnderstandingGateTest {
    @Test
    fun `gate passes only when positive and safety thresholds both pass`() {
        val eligible = List(90) { sample("eligible-$it", eligible = true, applied = true) } +
            List(10) { sample("missed-$it", eligible = true, applied = false) }
        val ineligible = List(99) { sample("safe-negative-$it", eligible = false, applied = false) } +
            listOf(sample("false-apply", eligible = false, applied = true))

        val result = ColorLabSceneUnderstandingGate.evaluate(eligible + ineligible)

        assertTrue(result.positiveGatePassed)
        assertTrue(result.safetyGatePassed)
        assertTrue(result.readyForDefaultStage3)
    }

    @Test
    fun `gate fails when eligible samples are below ninety percent`() {
        val samples = List(89) { sample("eligible-hit-$it", eligible = true, applied = true) } +
            List(11) { sample("eligible-miss-$it", eligible = true, applied = false) } +
            List(100) { sample("safe-negative-$it", eligible = false, applied = false) }

        val result = ColorLabSceneUnderstandingGate.evaluate(samples)

        assertFalse(result.positiveGatePassed)
        assertTrue(result.safetyGatePassed)
        assertFalse(result.readyForDefaultStage3)
    }

    @Test
    fun `gate fails when wrong application exceeds one percent even if positives pass`() {
        val samples = List(100) { sample("eligible-hit-$it", eligible = true, applied = true) } +
            List(98) { sample("safe-negative-$it", eligible = false, applied = false) } +
            List(2) { sample("false-apply-$it", eligible = false, applied = true) }

        val result = ColorLabSceneUnderstandingGate.evaluate(samples)

        assertTrue(result.positiveGatePassed)
        assertFalse(result.safetyGatePassed)
        assertFalse(result.readyForDefaultStage3)
    }

    @Test
    fun `neutral or skin damage blocks safety even without false application`() {
        val samples = List(100) { sample("eligible-hit-$it", eligible = true, applied = true) } +
            List(99) { sample("safe-negative-$it", eligible = false, applied = false) } +
            List(3) { sample("neutral-damage-$it", eligible = false, applied = false, neutralDamaged = true) } +
            List(3) { sample("skin-damage-$it", eligible = false, applied = false, skinDamaged = true) }

        val result = ColorLabSceneUnderstandingGate.evaluate(samples)

        assertFalse(result.safetyGatePassed)
        assertFalse(result.readyForDefaultStage3)
        assertTrue(result.neutralDamageRate > ColorLabSceneUnderstandingGate.MAX_DAMAGE_RATE)
        assertTrue(result.skinDamageRate > ColorLabSceneUnderstandingGate.MAX_DAMAGE_RATE)
    }

    private fun sample(
        id: String,
        eligible: Boolean,
        applied: Boolean,
        neutralDamaged: Boolean = false,
        skinDamaged: Boolean = false
    ): ColorLabSceneUnderstandingSample {
        return ColorLabSceneUnderstandingSample(
            sampleId = id,
            eligibleForRegionAwareColor = eligible,
            regionAwareColorApplied = applied,
            neutralColorDamaged = neutralDamaged,
            skinColorDamaged = skinDamaged
        )
    }
}
