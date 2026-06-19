package com.opencamera.app.camera

import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget

internal interface MaskAwarePortraitRenderEditor : PortraitRenderEditor {
    suspend fun applyWithMask(
        target: ProcessorTarget,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>>
}
