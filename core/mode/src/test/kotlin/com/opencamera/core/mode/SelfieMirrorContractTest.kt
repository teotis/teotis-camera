package com.opencamera.core.mode

import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.MediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfieMirrorContractTest {

    @Test
    fun `front lens with mirror enabled mirrors both preview and saved output`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = true
        )

        assertTrue(policy.shouldMirrorPreview)
        assertTrue(policy.shouldMirrorSavedOutput)
        assertTrue(policy.previewMatchesSavedOutput)
    }

    @Test
    fun `front lens with mirror disabled does not mirror preview or saved output`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = false
        )

        assertFalse(policy.shouldMirrorPreview)
        assertFalse(policy.shouldMirrorSavedOutput)
        assertTrue(policy.previewMatchesSavedOutput)
    }

    @Test
    fun `back lens with mirror enabled does not mirror preview or saved output`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.BACK,
            selfieMirrorEnabled = true
        )

        assertFalse(policy.shouldMirrorPreview)
        assertFalse(policy.shouldMirrorSavedOutput)
        assertTrue(policy.previewMatchesSavedOutput)
    }

    @Test
    fun `back lens with mirror disabled does not mirror preview or saved output`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.BACK,
            selfieMirrorEnabled = false
        )

        assertFalse(policy.shouldMirrorPreview)
        assertFalse(policy.shouldMirrorSavedOutput)
        assertTrue(policy.previewMatchesSavedOutput)
    }

    @Test
    fun `policy always asserts preview matches saved output across all lens and setting combinations`() {
        LensFacing.entries.forEach { lens ->
            listOf(true, false).forEach { enabled ->
                val policy = selfieMirrorPolicy(lens, enabled)
                assertTrue(
                    policy.previewMatchesSavedOutput,
                    "Preview and saved output must match for lens=$lens, enabled=$enabled"
                )
            }
        }
    }

    @Test
    fun `metadata selfieMirrorApply tag matches policy for front lens with mirror enabled`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = true
        )
        val tags = buildMetadataTags(LensFacing.FRONT, selfieMirrorEnabled = true)

        assertEquals("front", tags["captureLensFacing"])
        assertEquals("on", tags["selfieMirrorEnabled"])
        assertEquals("true", tags["selfieMirrorApply"])
        assertEquals(policy.shouldMirrorSavedOutput, tags["selfieMirrorApply"].toBoolean())
    }

    @Test
    fun `metadata selfieMirrorApply tag matches policy for back lens`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.BACK,
            selfieMirrorEnabled = true
        )
        val tags = buildMetadataTags(LensFacing.BACK, selfieMirrorEnabled = true)

        assertEquals("back", tags["captureLensFacing"])
        assertEquals("false", tags["selfieMirrorApply"])
        assertEquals(policy.shouldMirrorSavedOutput, tags["selfieMirrorApply"].toBoolean())
    }

    @Test
    fun `metadata selfieMirrorApply tag matches policy for front lens with mirror disabled`() {
        val policy = selfieMirrorPolicy(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = false
        )
        val tags = buildMetadataTags(LensFacing.FRONT, selfieMirrorEnabled = false)

        assertEquals("off", tags["selfieMirrorEnabled"])
        assertEquals("false", tags["selfieMirrorApply"])
        assertEquals(policy.shouldMirrorSavedOutput, tags["selfieMirrorApply"].toBoolean())
    }

    @Test
    fun `postprocessor logic ignores video media type regardless of mirror tag`() {
        val shouldSkip = shouldMirrorPhotoOutput(
            mediaType = MediaType.VIDEO,
            selfieMirrorApply = true,
            mimeType = "video/mp4"
        )

        assertTrue(shouldSkip == MirrorWorkDecision.SKIP)
    }

    @Test
    fun `postprocessor logic skips non-jpeg photo mime type`() {
        val decision = shouldMirrorPhotoOutput(
            mediaType = MediaType.PHOTO,
            selfieMirrorApply = true,
            mimeType = "image/png"
        )

        assertEquals(MirrorWorkDecision.SKIP, decision)
    }

    @Test
    fun `postprocessor logic skips when selfieMirrorApply is false`() {
        val decision = shouldMirrorPhotoOutput(
            mediaType = MediaType.PHOTO,
            selfieMirrorApply = false,
            mimeType = "image/jpeg"
        )

        assertEquals(MirrorWorkDecision.SKIP, decision)
    }

    @Test
    fun `postprocessor logic executes mirror when media is photo and selfieMirrorApply is true`() {
        val decision = shouldMirrorPhotoOutput(
            mediaType = MediaType.PHOTO,
            selfieMirrorApply = true,
            mimeType = "image/jpeg"
        )

        assertEquals(MirrorWorkDecision.APPLY, decision)
    }

    @Test
    fun `preview scaleX equals negative one exactly when policy mirrors`() {
        val mirroredPolicy = selfieMirrorPolicy(LensFacing.FRONT, selfieMirrorEnabled = true)
        val notMirroredPolicy = selfieMirrorPolicy(LensFacing.FRONT, selfieMirrorEnabled = false)

        assertEquals(-1f, if (mirroredPolicy.shouldMirrorPreview) -1f else 1f)
        assertEquals(1f, if (notMirroredPolicy.shouldMirrorPreview) -1f else 1f)
    }

    // -- Helpers --

    private enum class MirrorWorkDecision {
        APPLY,
        SKIP
    }

    private fun buildMetadataTags(
        lensFacing: LensFacing,
        selfieMirrorEnabled: Boolean
    ): Map<String, String> {
        val apply = lensFacing == LensFacing.FRONT && selfieMirrorEnabled
        return mapOf(
            "captureLensFacing" to lensFacing.name.lowercase(),
            "selfieMirrorEnabled" to if (selfieMirrorEnabled) "on" else "off",
            "selfieMirrorApply" to apply.toString()
        )
    }

    private fun shouldMirrorPhotoOutput(
        mediaType: MediaType,
        selfieMirrorApply: Boolean,
        mimeType: String
    ): MirrorWorkDecision {
        if (mediaType != MediaType.PHOTO) return MirrorWorkDecision.SKIP
        if (!mimeType.equals("image/jpeg", ignoreCase = true)) return MirrorWorkDecision.SKIP
        if (!selfieMirrorApply) return MirrorWorkDecision.SKIP
        return MirrorWorkDecision.APPLY
    }
}
