package com.opencamera.core.session

import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.settings.PerceptualColorRecipe
import com.opencamera.core.settings.toMetadataTags
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureFeedbackPolicyTest {

    private fun shotRequest(
        customTags: Map<String, String> = emptyMap(),
        postProcessSpec: PostProcessSpec = PostProcessSpec(),
        algorithmProfile: String? = null
    ) = ShotRequest(
            shotId = "shot-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(
                metadata = com.opencamera.core.media.MediaMetadata(
                    customTags = customTags,
                    algorithmProfile = algorithmProfile
                )
            ),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = postProcessSpec,
            captureProfile = CaptureProfile()
        )

    // ── Default (allow) ───────────────────────────────────────────────

    @Test
    fun `plain still capture allows preview bitmap`() {
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(shotRequest())
        )
    }

    // ── Frame ratio suppression ───────────────────────────────────────

    @Test
    fun `non-4-3 frame ratio suppresses feedback`() {
        val request = shotRequest(customTags = mapOf("frameRatio" to "16:9"))
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `4-3 frame ratio allows feedback`() {
        val request = shotRequest(customTags = mapOf("frameRatio" to "4:3"))
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `1-1 frame ratio suppresses feedback`() {
        val request = shotRequest(customTags = mapOf("frameRatio" to "1:1"))
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    // ── Selfie mirror suppression ─────────────────────────────────────

    @Test
    fun `selfie mirror suppresses feedback`() {
        val request = shotRequest(customTags = mapOf("selfieMirrorApply" to "true"))
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `selfie mirror off allows feedback`() {
        val request = shotRequest(customTags = mapOf("selfieMirrorApply" to "false"))
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    // ── Watermark template suppression ────────────────────────────────

    @Test
    fun `classic-overlay watermark template allows feedback`() {
        val request = shotRequest(customTags = mapOf("watermarkTemplate" to "classic-overlay"))
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `non-classic-overlay watermark template suppresses feedback`() {
        val request = shotRequest(customTags = mapOf("watermarkTemplate" to "modern-frame"))
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `null watermark template does not suppress`() {
        val request = shotRequest() // no watermarkTemplate tag
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    // ── Filter profile suppression ────────────────────────────────────

    @Test
    fun `filter with non-null render spec suppresses feedback`() {
        // filterSpec.brightnessShift=1 triggers a non-null parse result
        val request = shotRequest(customTags = mapOf(
            "filterSpec.version" to "1",
            "filterSpec.brightnessShift" to "1"
        ))
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `photo-original low risk profile with default spec allows feedback`() {
        // LOW_RISK_PREVIEW_FILTER_PROFILES includes "photo-original"
        // Without any filterSpec custom tags, fromMetadataTags returns null
        // which means requiresTrustedSavedMedia(null) -> false
        val request = shotRequest(
            customTags = mapOf("filterProfile" to "photo-original"),
            algorithmProfile = "photo-original"
        )
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `photo-vivid low risk profile with default spec allows feedback`() {
        val request = shotRequest(
            customTags = mapOf("filterProfile" to "photo-vivid"),
            algorithmProfile = "photo-vivid"
        )
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `non-low-risk filter profile with render spec suppresses feedback`() {
        val request = shotRequest(
            customTags = mapOf(
                "filterProfile" to "portrait-classic",
                "filterSpec.version" to "1",
                "filterSpec.brightnessShift" to "1"
            ),
            algorithmProfile = "portrait-classic"
        )
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    // ── Combined conditions ────────────────────────────────────────────

    @Test
    fun `multiple suppress conditions still produce SUPPRESS`() {
        val request = shotRequest(
            customTags = mapOf(
                "selfieMirrorApply" to "true",
                "frameRatio" to "16:9",
                "watermarkTemplate" to "modern-frame"
            ),
            postProcessSpec = PostProcessSpec(watermarkText = "test")
        )
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `classic-overlay and 4-3 ratio and no selfie all at once allows feedback`() {
        val request = shotRequest(
            customTags = mapOf(
                "selfieMirrorApply" to "false",
                "frameRatio" to "4:3",
                "watermarkTemplate" to "classic-overlay"
            )
        )
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }

    // ── Perceptual recipe suppression ──────────────────────────────────

    @Test
    fun `non-neutral perceptual recipe suppresses feedback`() {
        val recipe = PerceptualColorRecipe(
            toneLift = 0.4f,
            chromaBoost = 0.2f,
            warmthBias = 0.3f,
            neutralProtection = 0.75f,
            skinProtection = 0.70f
        )
        val request = shotRequest(customTags = recipe.toMetadataTags())
        assertEquals(
            CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA,
            captureFeedbackPolicyFor(request)
        )
    }

    @Test
    fun `neutral perceptual recipe allows feedback`() {
        val request = shotRequest(customTags = PerceptualColorRecipe.NEUTRAL.toMetadataTags())
        assertEquals(
            CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP,
            captureFeedbackPolicyFor(request)
        )
    }
}
