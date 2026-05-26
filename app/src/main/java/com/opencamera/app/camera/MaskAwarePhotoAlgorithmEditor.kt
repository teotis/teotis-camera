package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget

internal interface MaskAwarePhotoAlgorithmEditor : PhotoAlgorithmEditor {
    suspend fun applyWithMask(
        target: ProcessorTarget,
        bitmap: Bitmap,
        spec: PhotoAlgorithmSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>>
}
