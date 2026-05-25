package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget

internal interface MaskAwarePortraitRenderEditor : PortraitRenderEditor {
    suspend fun applyWithMask(
        target: ProcessorTarget,
        bitmap: Bitmap,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>>
}
