package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.ProcessorEditorResult

internal interface MaskAwarePhotoAlgorithmEditor : PhotoAlgorithmEditor {
    suspend fun applyWithMask(
        bitmap: Bitmap,
        spec: PhotoAlgorithmSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>>
}
