package com.opencamera.app.reliability

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Single-seed replay: reproduces exactly one randomized scenario.
 *
 * Usage: `-Dcapture.reliability.replaySeed=500123`. The step list is derived
 * deterministically from the seed via [ReliabilityScriptGenerator], so a stress
 * failure reported with `seed=...` is replayed 1:1 by this test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureReliabilityReplayTest {

    @Test
    fun `replay single seed scenario`() = runTest {
        val seed = System.getProperty("capture.reliability.replaySeed")?.toLongOrNull()
            ?: return@runTest

        val steps = ReliabilityScriptGenerator.generate(seed = seed, stepCount = 18)
        val runner = ReliabilityScenarioRunner(seed = seed)
        val result = runner.run(testScope = this, steps = steps)

        assertTrue(result.passed, result.summary())
    }
}
