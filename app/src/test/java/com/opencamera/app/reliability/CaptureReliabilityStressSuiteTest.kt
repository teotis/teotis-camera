package com.opencamera.app.reliability

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Nightly stress set: randomized deterministic scenarios.
 *
 * Every seed produces a fixed step list via [ReliabilityScriptGenerator], so
 * any failure is replayable from its single seed. The seed range is overridable
 * with `-Dcapture.reliability.stressSeeds=100` and `-Dcapture.reliability.seedOffset=0`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureReliabilityStressSuiteTest {

    @Test
    fun `randomized scenarios preserve all invariants`() = runTest {
        val seedCount = System.getProperty("capture.reliability.stressSeeds")
            ?.toIntOrNull()?.coerceIn(1, 5_000) ?: 60
        val seedOffset = System.getProperty("capture.reliability.seedOffset")
            ?.toLongOrNull() ?: 0L
        val stepCount = System.getProperty("capture.reliability.steps")
            ?.toIntOrNull()?.coerceIn(4, 60) ?: 18

        val failures = mutableListOf<String>()
        for (index in 0 until seedCount) {
            val seed = 500_000L + seedOffset + index
            val steps = ReliabilityScriptGenerator.generate(seed = seed, stepCount = stepCount)
            val runner = ReliabilityScenarioRunner(seed = seed)
            val result = runner.run(testScope = this, steps = steps)
            println("STRESS-PROGRESS seed=$seed passed=${result.passed}")
            if (!result.passed) {
                failures += "seed=$seed\n" + result.summary()
            }
        }

        assertTrue(
            failures.isEmpty(),
            "${failures.size}/$seedCount seeds violated invariants:\n" +
                failures.take(8).joinToString("\n---\n")
        )
    }
}
