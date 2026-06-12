package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.StillCaptureQualityPreference
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AbstractStillCaptureModeTest {

    private fun createTestMode(
        onCapabilitiesChanged: ((DeviceCapabilities) -> Unit)? = null,
        exitDetailText: String? = null
    ): TestStillCaptureMode {
        return TestStillCaptureMode(
            context = ModeContext(),
            onCapabilitiesChangedHook = onCapabilitiesChanged,
            exitDetailText = exitDetailText
        )
    }

    // ── Capability hook ordering ────────────────────────────────────

    @Test
    fun `onDeviceCapabilitiesChanged calls capability hook before snapshot rebuild`() = runBlocking {
        var hookCalled = false
        var snapshotAfterHook: String? = null
        lateinit var capturedMode: TestStillCaptureMode

        val mode = createTestMode(
            onCapabilitiesChanged = {
                hookCalled = true
                snapshotAfterHook = capturedMode.snapshot.value.state.headline
            }
        )
        capturedMode = mode

        mode.onEnter()
        hookCalled = false
        mode.onDeviceCapabilitiesChanged(DeviceCapabilities.DEFAULT)

        assertTrue(hookCalled, "onModeCapabilitiesChanged should have been called")
        assertEquals("photo mode active", snapshotAfterHook)
        assertEquals("photo mode active", mode.snapshot.value.state.headline)
    }

    @Test
    fun `default capability hook does not throw`() = runBlocking {
        val mode = createTestMode()
        mode.onEnter()
        mode.onDeviceCapabilitiesChanged(DeviceCapabilities.DEFAULT)
        assertEquals("photo mode active", mode.snapshot.value.state.headline)
    }

    // ── Exit detail ─────────────────────────────────────────────────

    @Test
    fun `default exitDetail returns null`() {
        val mode = createTestMode()
        assertNull(mode.testExitDetail())
    }

    @Test
    fun `overridden exitDetail returns custom text`() {
        val mode = createTestMode(exitDetailText = "Switch back to continue.")
        assertEquals("Switch back to continue.", mode.testExitDetail())
    }

    @Test
    fun `onExit uses exitDetail as snapshot detail`() = runBlocking {
        val mode = createTestMode(exitDetailText = "Goodbye!")
        mode.onEnter()
        mode.onExit()
        assertEquals("Goodbye!", mode.snapshot.value.state.detail)
    }

    @Test
    fun `onExit with null exitDetail uses default detail`() = runBlocking {
        val mode = createTestMode(exitDetailText = null)
        mode.onEnter()
        mode.onExit()
        assertEquals("Test photo mode", mode.snapshot.value.state.detail)
    }

    // ── Watermark tokens ────────────────────────────────────────────

    @Test
    fun `watermarkTokens produces model datetime cameraParams keys`() {
        val mode = createTestMode()
        val tokens = mode.testWatermarkTokens()

        assertEquals("OpenCamera", tokens["watermarkModel"])
        assertTrue(tokens.containsKey("watermarkDatetime"))
        assertTrue(tokens.containsKey("watermarkCameraParams"))
        assertEquals(3, tokens.size)
    }

    @Test
    fun `watermarkTokens with custom cameraParams uses provided value`() {
        val mode = createTestMode()
        val tokens = mode.testWatermarkTokens(cameraParams = "8MP • 16:9 • Flash Auto")

        assertEquals("8MP • 16:9 • Flash Auto", tokens["watermarkCameraParams"])
    }

    @Test
    fun `watermarkTokens datetime format matches expected pattern`() {
        val mode = createTestMode()
        val tokens = mode.testWatermarkTokens()
        val datetime = tokens["watermarkDatetime"]!!

        assertTrue(
            datetime.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")),
            "Expected yyyy-MM-dd HH:mm format but got: $datetime"
        )
    }

    // ── captureMetadataTags ─────────────────────────────────────────

    @Test
    fun `captureMetadataTags with default includes capture aid tags`() = runBlocking {
        val mode = createTestMode()
        mode.onEnter()
        val tags = mode.testCaptureMetadataTags(
            modeTags = mapOf("mode" to "photo")
        )

        assertTrue(tags.containsKey("captureLensFacing"), "Should include capture aid tags")
        assertEquals("photo", tags["mode"])
    }

    @Test
    fun `captureMetadataTags with includeCaptureAidTags false omits capture aid`() = runBlocking {
        val mode = createTestMode()
        mode.onEnter()
        val tags = mode.testCaptureMetadataTags(
            modeTags = mapOf("mode" to "video"),
            includeCaptureAidTags = false
        )

        assertFalse(tags.containsKey("captureLensFacing"), "Should NOT include capture aid tags")
        assertEquals("video", tags["mode"])
    }

    @Test
    fun `captureMetadataTags merges effectSpec tags when provided`() = runBlocking {
        val mode = createTestMode()
        mode.onEnter()
        val tags = mode.testCaptureMetadataTags(
            effectSpec = EffectSpec.EMPTY,
            modeTags = mapOf("mode" to "photo")
        )

        assertEquals("photo", tags["mode"])
    }

    @Test
    fun `captureMetadataTags overrideTags take precedence`() = runBlocking {
        val mode = createTestMode()
        mode.onEnter()
        val tags = mode.testCaptureMetadataTags(
            modeTags = mapOf("mode" to "photo"),
            overrideTags = mapOf("mode" to "photo-v2")
        )

        assertEquals("photo-v2", tags["mode"])
    }

    @Test
    fun `captureMetadataTags collision between mode and effect raises MetadataCollision`() = runBlocking {
        val mode = createTestMode()
        mode.onEnter()
        try {
            mode.testCaptureMetadataTags(
                effectSpec = EffectSpec(listOf(
                    com.opencamera.core.effect.PortraitEffect(
                        profileId = "luminous", renderPath = "depth",
                        beautyPreset = "clear", beautyStrength = "balanced",
                        bokehEffect = "smooth"
                    )
                )),
                modeTags = mapOf("mode" to "check-in")
            )
            throw AssertionError("Expected MetadataCollision to be thrown")
        } catch (e: MetadataCollision) {
            assertEquals("mode", e.key)
        }
    }

    // ── Concrete test subclass ──────────────────────────────────────

    private class TestStillCaptureMode(
        context: ModeContext,
        private val onCapabilitiesChangedHook: ((DeviceCapabilities) -> Unit)? = null,
        private val exitDetailText: String? = null
    ) : AbstractStillCaptureMode(context) {

        override val id: ModeId = ModeId.PHOTO
        override fun modeEventPrefix() = "photo"
        override fun initialHeadline() = "Photo pipeline ready"
        override fun buildEffectSpec() = EffectSpec.EMPTY
        override fun buildSnapshot(headline: String, detail: String?) = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "Photo", shutterLabel = "Capture"),
            state = ModeState(headline = headline, detail = detail ?: buildDefaultDetail())
        )
        override fun buildDefaultDetail() = "Test photo mode"
        override fun buildCaptureStrategy(
            effectSpec: EffectSpec,
            countdownSeconds: Int
        ): ModeSignal.SubmitCapture = ModeSignal.SubmitCapture(
            CaptureStrategy.SingleFrame(
                saveRequest = com.opencamera.core.media.SaveRequest.photoLibrary(
                    metadata = com.opencamera.core.media.MediaMetadata()
                ),
                postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
                captureProfile = CaptureProfile(
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
                    stillCaptureResolutionPreset = com.opencamera.core.media.StillCaptureResolutionPreset.LARGE_12MP
                )
            ),
            countdownSeconds = countdownSeconds
        )

        override suspend fun onModeCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) {
            onCapabilitiesChangedHook?.invoke(deviceCapabilities)
        }

        override fun exitDetail(): String? = exitDetailText

        // Public delegates for test access to protected methods
        fun testExitDetail(): String? = exitDetail()
        fun testWatermarkTokens(cameraParams: String = watermarkCameraParams()) =
            watermarkTokens(cameraParams)
        fun testCaptureMetadataTags(
            effectSpec: EffectSpec? = null,
            modeTags: Map<String, String>,
            overrideTags: Map<String, String> = emptyMap(),
            includeCaptureAidTags: Boolean = true
        ) = captureMetadataTags(effectSpec, modeTags, overrideTags, includeCaptureAidTags)
    }
}
