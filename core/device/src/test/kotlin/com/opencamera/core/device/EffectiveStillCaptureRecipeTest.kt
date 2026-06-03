package com.opencamera.core.device

import com.opencamera.core.capability.CapabilitySupport
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectiveStillCaptureRecipeTest {

    @Test
    fun `standard resolution shows simple MP label`() {
        val graph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(3264, 2448, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals("12MP", recipe.quickLabel)
        assertEquals("12MP", recipe.pixelLabel)
        assertEquals(StillCaptureResolutionSource.STANDARD, recipe.resolutionSource)
        assertEquals(CapabilitySupport.SUPPORTED, recipe.bindability)
        assertFalse(recipe.previewResolutionMismatch)
    }

    @Test
    fun `maximum resolution shows Max annotation`() {
        val graph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION)
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION),
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals("48MP (Max)", recipe.quickLabel)
        assertEquals("48MP", recipe.pixelLabel)
        assertEquals(StillCaptureResolutionSource.MAXIMUM_RESOLUTION, recipe.resolutionSource)
        assertTrue(recipe.previewResolutionMismatch)
    }

    @Test
    fun `high resolution shows High annotation`() {
        val graph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(6000, 4000, StillCaptureResolutionSource.HIGH_RESOLUTION)
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(6000, 4000, StillCaptureResolutionSource.HIGH_RESOLUTION),
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals("24MP (High)", recipe.quickLabel)
        assertEquals("24MP", recipe.pixelLabel)
        assertEquals(StillCaptureResolutionSource.HIGH_RESOLUTION, recipe.resolutionSource)
        assertTrue(recipe.previewResolutionMismatch)
    }

    @Test
    fun `empty available sizes returns preset fallback`() {
        val graph = DeviceGraphSpec.stillCapture()
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = emptyList()
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(null, recipe.resolvedOutputSize)
        assertEquals("12MP", recipe.pixelLabel)
        assertEquals("12MP", recipe.quickLabel)
        assertEquals(CapabilitySupport.UNSUPPORTED, recipe.bindability)
    }

    @Test
    fun `LARGE_12MP resolves to maximum available size`() {
        val graph = DeviceGraphSpec.stillCapture()
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION),
                StillCaptureOutputSize(3264, 2448, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(8000, recipe.resolvedOutputSize?.width)
        assertEquals(6000, recipe.resolvedOutputSize?.height)
        assertEquals("48MP (Max)", recipe.quickLabel)
    }

    @Test
    fun `MEDIUM_8MP selects largest not exceeding desired pixels`() {
        val graph = DeviceGraphSpec.stillCapture(
            resolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(3264, 2448, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(1600, 1200, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(3264, recipe.resolvedOutputSize?.width)
        assertEquals(2448, recipe.resolvedOutputSize?.height)
    }

    @Test
    fun `explicit output size takes priority over preset fallback`() {
        val graph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(3264, 2448, StillCaptureResolutionSource.STANDARD)
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION),
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(3264, 2448, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(3264, recipe.resolvedOutputSize?.width)
        assertEquals(2448, recipe.resolvedOutputSize?.height)
        assertEquals(StillCaptureResolutionSource.STANDARD, recipe.resolutionSource)
    }

    @Test
    fun `metadataCustomTags includes all required keys`() {
        val graph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)
        val tags = recipe.metadataCustomTags

        assertEquals("quality", tags["stillQuality"])
        assertEquals("12mp", tags["stillResolution"])
        assertEquals("standard", tags["stillResolutionSource"])
        assertEquals("4000x3000", tags["stillOutputSize"])
        assertEquals(null, tags["stillBindability"]) // SUPPORTED 不需要 bindability tag
    }

    @Test
    fun `metadataCustomTags includes bindability for unsupported`() {
        val graph = DeviceGraphSpec.stillCapture()
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = emptyList()
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)
        val tags = recipe.metadataCustomTags

        assertEquals("unsupported", tags["stillBindability"])
        assertEquals("No still capture sizes available", tags["stillBindabilityReason"])
    }

    @Test
    fun `enrichDeviceGraph sets resolved output size`() {
        val graph = DeviceGraphSpec.stillCapture()
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION),
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)
        val enriched = recipe.enrichDeviceGraph(graph)

        assertEquals(8000, enriched.stillCapture.outputSize?.width)
        assertEquals(6000, enriched.stillCapture.outputSize?.height)
    }

    @Test
    fun `enrichDeviceGraph is idempotent`() {
        val graph = DeviceGraphSpec.stillCapture()
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)
        val first = recipe.enrichDeviceGraph(graph)
        val second = recipe.enrichDeviceGraph(first)

        assertEquals(first.stillCapture.outputSize, second.stillCapture.outputSize)
    }

    @Test
    fun `default quality preference is QUALITY`() {
        val graph = DeviceGraphSpec.stillCapture()
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(StillCaptureQualityPreference.QUALITY, recipe.qualityPreference)
        assertEquals("quality", recipe.metadataCustomTags["stillQuality"])
    }

    @Test
    fun `diagnostics includes source bindability and output`() {
        val graph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION)
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(8000, 6000, StillCaptureResolutionSource.MAXIMUM_RESOLUTION),
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)
        val diag = recipe.diagnostics()

        assertTrue(diag.any { it == "still:resolution-source=maximum-resolution" })
        assertTrue(diag.any { it == "still:bindability=supported" })
        assertTrue(diag.any { it == "still:output=8000x6000" })
        assertTrue(diag.any { it == "still:preview-fidelity=degraded" })
    }

    @Test
    fun `SMALL_2MP selects largest not exceeding desired`() {
        val graph = DeviceGraphSpec.stillCapture(
            resolutionPreset = StillCaptureResolutionPreset.SMALL_2MP
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(3264, 2448, StillCaptureResolutionSource.STANDARD),
                StillCaptureOutputSize(1600, 1200, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(1600, recipe.resolvedOutputSize?.width)
        assertEquals(1200, recipe.resolvedOutputSize?.height)
        assertEquals("2MP", recipe.quickLabel)
    }

    @Test
    fun `LATENCY quality preference reflects in metadata`() {
        val graph = DeviceGraphSpec.stillCapture(
            qualityPreference = StillCaptureQualityPreference.LATENCY
        )
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(4000, 3000, StillCaptureResolutionSource.STANDARD)
            )
        )

        val recipe = EffectiveStillCaptureRecipe.build(graph, capabilities)

        assertEquals(StillCaptureQualityPreference.LATENCY, recipe.qualityPreference)
        assertEquals("latency", recipe.metadataCustomTags["stillQuality"])
    }
}
