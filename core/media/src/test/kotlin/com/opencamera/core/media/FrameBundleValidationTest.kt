package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrameBundleValidationTest {

    // ── Builder ──────────────────────────────────────────────────────

    @Test
    fun `builder creates valid bundle with multiple frames`() {
        val bundle = buildFrameBundle("shot-1") {
            shotNoiseModel(NoiseModel.Known("stable"))
            shotMotionScore(MotionScore.Known(0.1f))
            bundleTimestampNanos(1_000_000L)
            addFrame(FrameBundleFrame(
                frameIndex = 0,
                pixelReference = fileReference("/tmp/f0.jpg"),
                frameRole = FrameRole.FUSION_ANCHOR,
                noiseModel = NoiseModel.Known("iso-200"),
                motionScore = MotionScore.Known(0.05f)
            ))
            addFrame(FrameBundleFrame(
                frameIndex = 1,
                pixelReference = fileReference("/tmp/f1.jpg"),
                noiseModel = NoiseModel.Known("iso-200"),
                motionScore = MotionScore.Known(0.12f)
            ))
        }
        assertEquals("shot-1", bundle.shotId)
        assertEquals(2, bundle.frameCount)
        assertFalse(bundle.isDegraded)
    }

    @Test
    fun `builder sorts frames by frameIndex`() {
        val bundle = buildFrameBundle("shot-2") {
            addFrame(FrameBundleFrame(2, fileReference("/f2.jpg")))
            addFrame(FrameBundleFrame(0, fileReference("/f0.jpg")))
            addFrame(FrameBundleFrame(1, fileReference("/f1.jpg")))
        }
        assertEquals(0, bundle.frames[0].frameIndex)
        assertEquals(1, bundle.frames[1].frameIndex)
        assertEquals(2, bundle.frames[2].frameIndex)
    }

    @Test
    fun `builder rejects empty frames`() {
        try {
            buildFrameBundle("shot-empty") { }
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least one frame") == true)
        }
    }

    @Test
    fun `builder records no-anchor diagnostic when no anchor`() {
        val bundle = buildFrameBundle("shot-3") {
            addFrame(FrameBundleFrame(0, fileReference("/f0.jpg"),
                frameRole = FrameRole.FUSION_SUPPLEMENT))
            addFrame(FrameBundleFrame(1, fileReference("/f1.jpg"),
                frameRole = FrameRole.DIAGNOSTIC))
        }
        assertTrue(bundle.diagnostics.any { it.contains("no-anchor-frame") })
    }

    @Test
    fun `builder records multiple-anchors diagnostic`() {
        val bundle = buildFrameBundle("shot-4") {
            addFrame(FrameBundleFrame(0, fileReference("/f0.jpg"),
                frameRole = FrameRole.FUSION_ANCHOR))
            addFrame(FrameBundleFrame(1, fileReference("/f1.jpg"),
                frameRole = FrameRole.FUSION_ANCHOR))
        }
        assertTrue(bundle.diagnostics.any { it.contains("multiple-anchors=2") })
    }

    @Test
    fun `builder short form creates frame with correct defaults`() {
        val bundle = buildFrameBundle("shot-5") {
            addFrame(
                frameIndex = 0,
                pixelReference = fileReference("/tmp/f0.jpg"),
                exposureTimeNanos = 16_000_000L,
                isoSensitivity = 400
            )
        }
        val frame = bundle.frames[0]
        assertEquals(16_000_000L, frame.exposureTimeNanos)
        assertEquals(400, frame.isoSensitivity)
        assertEquals(FrameRole.FUSION_SUPPLEMENT, frame.frameRole)
        assertEquals("image/jpeg", frame.outputFormat)
    }

    // ── Reference factory helpers ────────────────────────────────────

    @Test
    fun `fileReference creates File pixel reference`() {
        val ref = fileReference("/tmp/test.jpg")
        assertTrue(ref is PixelReference.File)
        assertEquals("/tmp/test.jpg", (ref as PixelReference.File).path)
    }

    @Test
    fun `uriReference creates ContentUri pixel reference`() {
        val ref = uriReference("content://media/42")
        assertTrue(ref is PixelReference.ContentUri)
        assertEquals("content://media/42", (ref as PixelReference.ContentUri).uri)
    }

    @Test
    fun `memoryReference creates InMemory pixel reference`() {
        val ref = memoryReference(byteArrayOf(1, 2, 3), "test")
        assertTrue(ref is PixelReference.InMemory)
        assertEquals("test", (ref as PixelReference.InMemory).label)
    }

    // ── Validation: valid bundle ─────────────────────────────────────

    @Test
    fun `valid bundle passes validation`() {
        val bundle = buildFrameBundle("v-1") {
            addFrame(FrameBundleFrame(0, fileReference("/tmp/f0.jpg"),
                frameRole = FrameRole.FUSION_ANCHOR,
                noiseModel = NoiseModel.Known("stable"),
                motionScore = MotionScore.Known(0.0f)))
            addFrame(FrameBundleFrame(1, fileReference("/tmp/f1.jpg"),
                noiseModel = NoiseModel.Known("stable"),
                motionScore = MotionScore.Known(0.1f)))
        }
        val result = bundle.validate()
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertEquals(FrameBundleStatus.PRESENT, result.status)
    }

    // ── Validation: empty bundle ─────────────────────────────────────

    @Test
    fun `empty bundle validation reports ABSENT`() {
        val bundle = FrameBundle(shotId = "empty", frames = emptyList())
        val result = bundle.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("bundle-empty") })
        assertEquals(FrameBundleStatus.ABSENT, result.status)
    }

    // ── Validation: missing pixel reference ──────────────────────────

    @Test
    fun `bundle with unknown file pixel reference is invalid`() {
        val bundle = FrameBundle(
            shotId = "bad-ref",
            frames = listOf(FrameBundleFrame(0, unknownPixelReference()))
        )
        val result = bundle.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("pixel-reference-missing") })
    }

    @Test
    fun `bundle with blank content URI is invalid`() {
        val bundle = FrameBundle(
            shotId = "blank-uri",
            frames = listOf(FrameBundleFrame(0, PixelReference.ContentUri("")))
        )
        val result = bundle.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("pixel-reference-empty-uri") })
    }

    @Test
    fun `bundle with empty in-memory bytes is invalid`() {
        val bundle = FrameBundle(
            shotId = "empty-mem",
            frames = listOf(FrameBundleFrame(0, PixelReference.InMemory(byteArrayOf())))
        )
        val result = bundle.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("pixel-reference-empty-bytes") })
    }

    // ── Validation: warnings ─────────────────────────────────────────

    @Test
    fun `valid bundle with unknown noise model produces warning`() {
        val bundle = FrameBundle(
            shotId = "warn-1",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/f0.jpg"),
                    motionScore = MotionScore.Known(0.0f))
            )
        )
        val result = bundle.validate()
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("noise-model-unknown") })
    }

    @Test
    fun `valid bundle with unknown motion score produces warning`() {
        val bundle = FrameBundle(
            shotId = "warn-2",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/f0.jpg"),
                    noiseModel = NoiseModel.Known("stable"))
            )
        )
        val result = bundle.validate()
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("motion-score-unknown") })
    }

    @Test
    fun `validation warns for degraded frames`() {
        val bundle = FrameBundle(
            shotId = "warn-3",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/f0.jpg"), isDegraded = true,
                    degradationReasons = listOf("blur"))
            )
        )
        val result = bundle.validate()
        assertTrue(result.warnings.any { it.contains("degraded") && it.contains("blur") })
    }

    @Test
    fun `validation warns when no anchor frame`() {
        val bundle = FrameBundle(
            shotId = "warn-4",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/f0.jpg"),
                    frameRole = FrameRole.FUSION_SUPPLEMENT)
            )
        )
        val result = bundle.validate()
        assertTrue(result.warnings.any { it.contains("no-anchor-frame") })
    }

    @Test
    fun `validation warns on multiple anchor frames`() {
        val bundle = FrameBundle(
            shotId = "warn-5",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/f0.jpg"), frameRole = FrameRole.FUSION_ANCHOR),
                FrameBundleFrame(1, fileReference("/f1.jpg"), frameRole = FrameRole.FUSION_ANCHOR)
            )
        )
        val result = bundle.validate()
        assertTrue(result.warnings.any { it.contains("multiple-anchor-frames=2") })
    }

    // ── Validation summary ───────────────────────────────────────────

    @Test
    fun `validation summary includes valid and status`() {
        val bundle = FrameBundle(
            shotId = "sum",
            frames = listOf(FrameBundleFrame(0, fileReference("/f0.jpg"),
                noiseModel = NoiseModel.Known("p"),
                motionScore = MotionScore.Known(0.0f)))
        )
        val result = bundle.validate()
        val summary = result.summary()
        assertTrue(summary.contains("valid=true"))
        assertTrue(summary.contains("status=PRESENT"))
    }

    // ── IntermediateOutputPaths compatibility ─────────────────────────

    @Test
    fun `toIntermediateOutputPaths returns file paths from bundle`() {
        val bundle = FrameBundle(
            shotId = "iop-1",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/tmp/f0.jpg")),
                FrameBundleFrame(1, PixelReference.ContentUri("content://42")),
                FrameBundleFrame(2, fileReference("/tmp/f2.jpg"))
            )
        )
        val paths = bundle.toIntermediateOutputPaths()
        assertEquals(listOf("/tmp/f0.jpg", "/tmp/f2.jpg"), paths)
    }

    @Test
    fun `toIntermediateOutputPaths returns empty list for no file refs`() {
        val bundle = FrameBundle(
            shotId = "iop-2",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.ContentUri("content://42")),
                FrameBundleFrame(1, PixelReference.InMemory(byteArrayOf(1)))
            )
        )
        assertTrue(bundle.toIntermediateOutputPaths().isEmpty())
    }

    @Test
    fun `isCompatibleWith returns true when all file paths match`() {
        val bundle = FrameBundle(
            shotId = "comp-1",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/tmp/f0.jpg")),
                FrameBundleFrame(1, fileReference("/tmp/f1.jpg"))
            )
        )
        val existingPaths = listOf("/tmp/f0.jpg", "/tmp/f1.jpg", "/tmp/extra.jpg")
        assertTrue(bundle.isCompatibleWith(existingPaths))
    }

    @Test
    fun `isCompatibleWith returns false when bundle has path not in existing list`() {
        val bundle = FrameBundle(
            shotId = "comp-2",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/tmp/f0.jpg")),
                FrameBundleFrame(1, fileReference("/tmp/f-new.jpg"))
            )
        )
        val existingPaths = listOf("/tmp/f0.jpg", "/tmp/f1.jpg")
        assertFalse(bundle.isCompatibleWith(existingPaths))
    }

    @Test
    fun `isCompatibleWith returns true when no file references`() {
        val bundle = FrameBundle(
            shotId = "comp-3",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.ContentUri("content://42"))
            )
        )
        assertTrue(bundle.isCompatibleWith(emptyList()))
    }

    // ── Mixed pixel reference types in same bundle ────────────────────

    @Test
    fun `bundle with mixed pixel reference types validates correctly`() {
        val bundle = FrameBundle(
            shotId = "mixed-1",
            frames = listOf(
                FrameBundleFrame(0, fileReference("/tmp/f0.jpg")),
                FrameBundleFrame(1, PixelReference.ContentUri("content://42")),
                FrameBundleFrame(2, PixelReference.InMemory(byteArrayOf(1, 2, 3)))
            )
        )
        val result = bundle.validate()
        assertTrue(result.isValid)
        assertEquals(3, bundle.frameCount)
    }

    // ── ContentUri and InMemory are accepted (non-Android types) ─────

    @Test
    fun `ContentUri frame validates as valid when URI is non-empty`() {
        val bundle = FrameBundle(
            shotId = "uri-1",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.ContentUri("content://media/123"),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Known(0.0f))
            )
        )
        val result = bundle.validate()
        assertTrue(result.isValid)
    }

    @Test
    fun `InMemory frame validates as valid when bytes are non-empty`() {
        val bundle = FrameBundle(
            shotId = "mem-1",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.InMemory(byteArrayOf(0xFF.toByte(), 0xD8.toByte())),
                    noiseModel = NoiseModel.Known("p"),
                    motionScore = MotionScore.Known(0.0f))
            )
        )
        val result = bundle.validate()
        assertTrue(result.isValid)
    }
}
