package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameBundleContractsTest {

    // ── PixelReference ───────────────────────────────────────────────

    @Test
    fun `File pixel reference stores path and creates File`() {
        val ref = PixelReference.File("/tmp/frame0.jpg")
        assertEquals("/tmp/frame0.jpg", ref.path)
        assertEquals(java.io.File("/tmp/frame0.jpg"), ref.toFile())
    }

    @Test
    fun `ContentUri pixel reference stores URI string`() {
        val ref = PixelReference.ContentUri("content://media/123")
        assertEquals("content://media/123", ref.uri)
    }

    @Test
    fun `InMemory pixel reference stores bytes and label`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val ref = PixelReference.InMemory(bytes, "preview")
        assertEquals("preview", ref.label)
        assertTrue(bytes.contentEquals(ref.bytes))
    }

    @Test
    fun `InMemory pixel reference equality uses content`() {
        val bytes = byteArrayOf(1, 2, 3)
        val a = PixelReference.InMemory(bytes.copyOf(), "x")
        val b = PixelReference.InMemory(bytes.copyOf(), "x")
        assertEquals(a, b)
    }

    @Test
    fun `InMemory pixel reference inequality on different bytes`() {
        val a = PixelReference.InMemory(byteArrayOf(1, 2), "x")
        val b = PixelReference.InMemory(byteArrayOf(3, 4), "x")
        assertFalse(a.equals(b))
    }

    // ── NoiseModel ───────────────────────────────────────────────────

    @Test
    fun `NoiseModel Unknown is singleton`() {
        assertTrue(NoiseModel.Unknown === NoiseModel.Unknown)
    }

    @Test
    fun `NoiseModel Known stores profile data`() {
        val model = NoiseModel.Known(profileId = "iso-stable", varianceScale = 0.8f)
        assertEquals("iso-stable", model.profileId)
        assertEquals(0.8f, model.varianceScale)
        assertNull(model.notes)
    }

    @Test
    fun `NoiseModel Known with notes`() {
        val model = NoiseModel.Known(profileId = "p1", varianceScale = 1.0f, notes = "test-note")
        assertEquals("test-note", model.notes)
    }

    // ── MotionScore ──────────────────────────────────────────────────

    @Test
    fun `MotionScore Unknown is singleton`() {
        assertTrue(MotionScore.Unknown === MotionScore.Unknown)
    }

    @Test
    fun `MotionScore Known stores score in valid range`() {
        val score = MotionScore.Known(score = 0.42f, source = "gyro")
        assertEquals(0.42f, score.score)
        assertEquals("gyro", score.source)
    }

    @Test
    fun `MotionScore Known rejects score below 0`() {
        try {
            MotionScore.Known(score = -0.1f)
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("[0,1]") == true)
        }
    }

    @Test
    fun `MotionScore Known rejects score above 1`() {
        try {
            MotionScore.Known(score = 1.1f)
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("[0,1]") == true)
        }
    }

    @Test
    fun `MotionScore Known accepts boundary values`() {
        assertEquals(0.0f, MotionScore.Known(score = 0.0f).score)
        assertEquals(1.0f, MotionScore.Known(score = 1.0f).score)
    }

    // ── WhiteBalance ─────────────────────────────────────────────────

    @Test
    fun `WhiteBalance defaults to auto with no values`() {
        val wb = WhiteBalance()
        assertTrue(wb.isAuto)
        assertNull(wb.temperature)
        assertNull(wb.tint)
        assertNull(wb.presetLabel)
    }

    @Test
    fun `WhiteBalance manual preset`() {
        val wb = WhiteBalance(temperature = 5500, tint = 0.1f, isAuto = false, presetLabel = "Daylight")
        assertEquals(5500, wb.temperature)
        assertFalse(wb.isAuto)
        assertEquals("Daylight", wb.presetLabel)
    }

    // ── FrameBundleFrame ─────────────────────────────────────────────

    @Test
    fun `FrameBundleFrame requires frameIndex and pixelReference`() {
        val frame = FrameBundleFrame(
            frameIndex = 0,
            pixelReference = PixelReference.File("/tmp/f0.jpg")
        )
        assertEquals(0, frame.frameIndex)
        assertEquals(FrameRole.FUSION_SUPPLEMENT, frame.frameRole)
        assertEquals(FocusStackFrameRole.NONE, frame.focusStackRole)
        assertEquals("image/jpeg", frame.outputFormat)
        assertFalse(frame.isDegraded)
    }

    @Test
    fun `FrameBundleFrame stores focus stack role and focus distance`() {
        val frame = FrameBundleFrame(
            frameIndex = 1,
            pixelReference = PixelReference.File("/tmp/near.jpg"),
            focusStackRole = FocusStackFrameRole.NEAR,
            focusDistanceDiopters = 3.25f
        )

        assertEquals(FocusStackFrameRole.NEAR, frame.focusStackRole)
        assertEquals(3.25f, frame.focusDistanceDiopters)
    }

    @Test
    fun `guided near far focus stack spec requires near and far roles`() {
        val spec = FocusStackCaptureSpec.guidedNearFar()

        assertEquals(FocusStackCaptureMode.GUIDED_NEAR_FAR, spec.mode)
        assertEquals(listOf(FocusStackFrameRole.NEAR, FocusStackFrameRole.FAR), spec.requiredFrameRoles)
        assertEquals("focus-stack:guided-near-far-v1", spec.algorithmProfile)
        assertTrue(spec.userGuidanceRequired)
    }

    @Test
    fun `automatic near far focus stack spec does not require hidden user guidance`() {
        val spec = FocusStackCaptureSpec.automaticNearFar()

        assertEquals(FocusStackCaptureMode.AUTO_NEAR_FAR, spec.mode)
        assertEquals(listOf(FocusStackFrameRole.NEAR, FocusStackFrameRole.FAR), spec.requiredFrameRoles)
        assertEquals("focus-stack:auto-near-far-v1", spec.algorithmProfile)
        assertFalse(spec.userGuidanceRequired)
    }

    @Test
    fun `FrameBundleFrame stores all optional metadata`() {
        val frame = FrameBundleFrame(
            frameIndex = 2,
            pixelReference = PixelReference.File("/tmp/f2.jpg"),
            frameRole = FrameRole.FUSION_ANCHOR,
            exposureTimeNanos = 33_000_000L,
            isoSensitivity = 800,
            timestampNanos = 1_000_000_000L,
            whiteBalance = WhiteBalance(temperature = 4000, isAuto = false),
            focalLengthMm = 6.86f,
            lensId = "camera-0-wide",
            noiseModel = NoiseModel.Known("stable-iso"),
            motionScore = MotionScore.Known(0.15f),
            outputFormat = "image/yuv"
        )
        assertEquals(33_000_000L, frame.exposureTimeNanos)
        assertEquals(800, frame.isoSensitivity)
        assertEquals("camera-0-wide", frame.lensId)
        assertEquals("image/yuv", frame.outputFormat)
        assertTrue(frame.frameRole == FrameRole.FUSION_ANCHOR)
    }

    @Test
    fun `FrameBundleFrame degraded with reasons`() {
        val frame = FrameBundleFrame(
            frameIndex = 1,
            pixelReference = PixelReference.File("/tmp/f1.jpg"),
            isDegraded = true,
            degradationReasons = listOf("blur-detected", "metadata-missing")
        )
        assertTrue(frame.isDegraded)
        assertEquals(2, frame.degradationReasons.size)
    }

    // ── FrameBundle ──────────────────────────────────────────────────

    @Test
    fun `FrameBundle stores frames and computes frameCount`() {
        val bundle = FrameBundle(
            shotId = "shot-1",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg")),
                FrameBundleFrame(1, PixelReference.File("/f1.jpg")),
                FrameBundleFrame(2, PixelReference.File("/f2.jpg"))
            )
        )
        assertEquals(3, bundle.frameCount)
        assertEquals("shot-1", bundle.shotId)
    }

    @Test
    fun `FrameBundle validFrames excludes degraded`() {
        val bundle = FrameBundle(
            shotId = "shot-2",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg")),
                FrameBundleFrame(1, PixelReference.File("/f1.jpg"), isDegraded = true),
                FrameBundleFrame(2, PixelReference.File("/f2.jpg"))
            )
        )
        assertEquals(2, bundle.validFrames.size)
        assertTrue(bundle.validFrames.all { !it.isDegraded })
    }

    @Test
    fun `FrameBundle anchorFrame returns first FUSION_ANCHOR`() {
        val bundle = FrameBundle(
            shotId = "shot-3",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"), frameRole = FrameRole.FUSION_SUPPLEMENT),
                FrameBundleFrame(1, PixelReference.File("/f1.jpg"), frameRole = FrameRole.FUSION_ANCHOR),
                FrameBundleFrame(2, PixelReference.File("/f2.jpg"), frameRole = FrameRole.FUSION_ANCHOR)
            )
        )
        assertNotNull(bundle.anchorFrame)
        assertEquals(1, bundle.anchorFrame?.frameIndex)
    }

    @Test
    fun `FrameBundle anchorFrame returns null when no anchor`() {
        val bundle = FrameBundle(
            shotId = "shot-4",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"), frameRole = FrameRole.DIAGNOSTIC)
            )
        )
        assertNull(bundle.anchorFrame)
    }

    @Test
    fun `FrameBundle isDegraded when any frame has unknown noise model`() {
        val bundle = FrameBundle(
            shotId = "shot-5",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"), noiseModel = NoiseModel.Unknown)
            )
        )
        assertTrue(bundle.isDegraded)
    }

    @Test
    fun `FrameBundle isDegraded when any frame has unknown motion score`() {
        val bundle = FrameBundle(
            shotId = "shot-6",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Unknown)
            )
        )
        assertTrue(bundle.isDegraded)
    }

    @Test
    fun `FrameBundle isDegraded when any frame isDegraded`() {
        val bundle = FrameBundle(
            shotId = "shot-7",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Known(0.1f),
                    isDegraded = true)
            )
        )
        assertTrue(bundle.isDegraded)
    }

    @Test
    fun `FrameBundle not degraded when all metadata present and no degraded flag`() {
        val bundle = FrameBundle(
            shotId = "shot-8",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Known(0.1f)),
                FrameBundleFrame(1, PixelReference.File("/f1.jpg"),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Known(0.2f))
            )
        )
        assertFalse(bundle.isDegraded)
    }

    // ── Status computation ───────────────────────────────────────────

    @Test
    fun `computeBundleStatus returns ABSENT for null`() {
        assertEquals(FrameBundleStatus.ABSENT, computeBundleStatus(null))
    }

    @Test
    fun `computeBundleStatus returns ABSENT for empty frames`() {
        val bundle = FrameBundle(shotId = "s", frames = emptyList())
        assertEquals(FrameBundleStatus.ABSENT, computeBundleStatus(bundle))
    }

    @Test
    fun `computeBundleStatus returns PRESENT for non-degraded bundle`() {
        val bundle = FrameBundle(
            shotId = "s",
            frames = listOf(FrameBundleFrame(0, PixelReference.File("/f.jpg"),
                noiseModel = NoiseModel.Known("p"),
                motionScore = MotionScore.Known(0.0f)))
        )
        assertEquals(FrameBundleStatus.PRESENT, bundle.status())
    }

    @Test
    fun `computeBundleStatus returns DEGRADED for degraded bundle`() {
        val bundle = FrameBundle(
            shotId = "s",
            frames = listOf(FrameBundleFrame(0, PixelReference.File("/f.jpg")))
        )
        assertEquals(FrameBundleStatus.DEGRADED, bundle.status())
    }

    // ── Sentinel helpers ─────────────────────────────────────────────

    @Test
    fun `unknownNoiseModel returns Unknown`() {
        assertTrue(unknownNoiseModel() is NoiseModel.Unknown)
    }

    @Test
    fun `unknownMotionScore returns Unknown`() {
        assertTrue(unknownMotionScore() is MotionScore.Unknown)
    }

    @Test
    fun `unknownPixelReference returns File with unknown scheme`() {
        val ref = unknownPixelReference()
        assertTrue(ref is PixelReference.File)
        assertTrue((ref as PixelReference.File).path.startsWith("unknown://"))
    }

    // ── ShotResult integration ───────────────────────────────────────

    private fun testShotResult(frameBundle: FrameBundle? = null) = ShotResult(
        shotId = "shot-test",
        mediaType = MediaType.PHOTO,
        outputPath = "/tmp/out.jpg",
        saveRequest = SaveRequest.photoLibrary(),
        thumbnailSource = ThumbnailSource.None,
        metadata = MediaMetadata(),
        frameBundle = frameBundle,
        intermediateOutputPaths = listOf("/tmp/f0.jpg", "/tmp/f1.jpg")
    )

    @Test
    fun `ShotResult without frameBundle has ABSENT status`() {
        val result = testShotResult()
        assertNull(result.frameBundle)
        assertEquals(FrameBundleStatus.ABSENT, result.frameBundleStatus())
        assertFalse(result.hasFrameBundle())
        assertFalse(result.isFusionDegraded())
    }

    @Test
    fun `ShotResult with valid frameBundle has PRESENT status`() {
        val bundle = FrameBundle(
            shotId = "shot-test",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/tmp/f0.jpg"),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Known(0.0f))
            )
        )
        val result = testShotResult(frameBundle = bundle)
        assertTrue(result.hasFrameBundle())
        assertEquals(FrameBundleStatus.PRESENT, result.frameBundleStatus())
        assertFalse(result.isFusionDegraded())
    }

    @Test
    fun `ShotResult with degraded frameBundle has DEGRADED status`() {
        val bundle = FrameBundle(
            shotId = "shot-test",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/tmp/f0.jpg"))
            )
        )
        val result = testShotResult(frameBundle = bundle)
        assertEquals(FrameBundleStatus.DEGRADED, result.frameBundleStatus())
        assertTrue(result.isFusionDegraded())
    }

    @Test
    fun `ShotResult intermediateOutputPaths unchanged with frameBundle`() {
        val bundle = FrameBundle(
            shotId = "shot-test",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/tmp/f0.jpg")),
                FrameBundleFrame(1, PixelReference.File("/tmp/f1.jpg"))
            )
        )
        val result = testShotResult(frameBundle = bundle)
        assertEquals(listOf("/tmp/f0.jpg", "/tmp/f1.jpg"), result.intermediateOutputPaths)
    }

    // ── diagnosticsSummary ───────────────────────────────────────────

    @Test
    fun `diagnosticsSummary includes frame count and degraded count`() {
        val bundle = FrameBundle(
            shotId = "s",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/f0.jpg"), isDegraded = true,
                    noiseModel = NoiseModel.Known("p"), motionScore = MotionScore.Known(0.0f)),
                FrameBundleFrame(1, PixelReference.File("/f1.jpg"),
                    noiseModel = NoiseModel.Known("p"), motionScore = MotionScore.Known(0.0f)),
                FrameBundleFrame(2, PixelReference.File("/f2.jpg"),
                    noiseModel = NoiseModel.Unknown, motionScore = MotionScore.Known(0.0f))
            )
        )
        val summary = bundle.diagnosticsSummary()
        assertTrue(summary.contains("frames=3"))
        assertTrue(summary.contains("degraded=1"))
        assertTrue(summary.contains("unknown-noise=1"))
    }
}
