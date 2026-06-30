package com.opencamera.app

import com.opencamera.core.media.MediaPostProcessor
import com.opencamera.core.media.ShotResult
import kotlin.test.Test
import kotlin.test.assertTrue

class ProductionPostProcessorOrderTest {
    @Test
    fun `content understanding runs before portrait and style consumers`() {
        val processors = createProductionMediaPostProcessors(
            focusStackFusionProcessor = MarkerPostProcessor("focus-stack"),
            multiFrameFusionProcessor = MarkerPostProcessor("fusion"),
            documentAutoCropPostProcessor = MarkerPostProcessor("document"),
            photoFrameRatioPostProcessor = MarkerPostProcessor("frame"),
            contentUnderstandingPostProcessor = MarkerPostProcessor("content"),
            checkInContentDecisionPostProcessor = MarkerPostProcessor("checkin-content"),
            portraitRenderPostProcessor = MarkerPostProcessor("portrait"),
            photoAlgorithmWatermarkPostProcessor = MarkerPostProcessor("algorithm-watermark"),
            photoSelfieMirrorPostProcessor = MarkerPostProcessor("mirror"),
            pipelineMetadataPostProcessor = MarkerPostProcessor("metadata")
        )

        val names = processors.map { (it as MarkerPostProcessor).name }

        assertTrue(names.indexOf("focus-stack") < names.indexOf("fusion"))
        assertTrue(names.indexOf("content") < names.indexOf("checkin-content"))
        assertTrue(names.indexOf("checkin-content") < names.indexOf("portrait"))
        assertTrue(names.indexOf("checkin-content") < names.indexOf("algorithm-watermark"))
        assertTrue(names.indexOf("content") < names.indexOf("portrait"))
        assertTrue(names.indexOf("content") < names.indexOf("algorithm-watermark"))
        assertTrue(names.indexOf("frame") < names.indexOf("content"))
        assertTrue(names.indexOf("mirror") > names.indexOf("algorithm-watermark"))
    }

    private data class MarkerPostProcessor(
        val name: String
    ) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult = result
    }
}
