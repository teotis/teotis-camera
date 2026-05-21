package com.opencamera.core.media

data class MediaProcessorAvailability(
    val filterRenderAvailable: Boolean = true,
    val watermarkRenderAvailable: Boolean = true,
    val multiFrameMergeAvailable: Boolean = true,
    val portraitRenderAvailable: Boolean = true,
    val documentProcessorAvailable: Boolean = true,
    val temporalMediaAssemblerAvailable: Boolean = true
) {
    companion object {
        val ALL_AVAILABLE = MediaProcessorAvailability()
        val NONE_AVAILABLE = MediaProcessorAvailability(
            filterRenderAvailable = false,
            watermarkRenderAvailable = false,
            multiFrameMergeAvailable = false,
            portraitRenderAvailable = false,
            documentProcessorAvailable = false,
            temporalMediaAssemblerAvailable = false
        )
    }
}
