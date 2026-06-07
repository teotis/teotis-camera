package com.opencamera.core.device

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiFrameTemporaryOutputTrackerTest {
    @Test
    fun `cleanup deletes registered temporary outputs and preserves registration order`() {
        val tempDir = createTempDirectory(prefix = "multi-frame-temp-").toFile()
        val frameA = File(tempDir, "frame_a.jpg").apply { writeText("a") }
        val frameB = File(tempDir, "frame_b.jpg").apply { writeText("bb") }

        try {
            val tracker = MultiFrameTemporaryOutputTracker()

            tracker.register(frameA)
            tracker.register(frameB)

            assertEquals(
                listOf(frameA.absolutePath, frameB.absolutePath),
                tracker.outputPaths()
            )

            tracker.cleanup()

            assertFalse(frameA.exists())
            assertFalse(frameB.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup ignores missing or null temporary outputs`() {
        val tempDir = createTempDirectory(prefix = "multi-frame-temp-missing-").toFile()
        val missing = File(tempDir, "missing.jpg")

        try {
            val tracker = MultiFrameTemporaryOutputTracker()

            tracker.register(null)
            tracker.register(missing)
            tracker.cleanup()

            assertTrue(missing.exists().not())
            assertEquals(emptyList(), tracker.outputPaths())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
