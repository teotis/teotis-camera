package com.opencamera.app

import android.content.Context
import android.view.OrientationEventListener

internal class CameraOrientationMonitor(
    context: Context,
    private val onOrientationChanged: (CameraOrientationRenderModel) -> Unit
) : OrientationEventListener(context) {

    private var lastBucket: CameraPhysicalOrientation? = null

    override fun onOrientationChanged(degrees: Int) {
        val bucket = orientationBucketFromDegrees(degrees) ?: return
        if (bucket == lastBucket) return
        lastBucket = bucket
        onOrientationChanged(orientationRenderModelFromBucket(bucket))
    }

    fun reset() {
        lastBucket = null
    }
}
