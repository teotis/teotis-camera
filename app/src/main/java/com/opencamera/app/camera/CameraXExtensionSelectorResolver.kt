package com.opencamera.app.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import com.opencamera.core.device.CameraExtensionAvailability
import com.opencamera.core.device.CameraExtensionMode
import com.opencamera.core.device.CameraExtensionResolution
import com.opencamera.core.device.LensFacing
import java.util.concurrent.TimeUnit

/**
 * Production [ExtensionSelectorResolver] that wraps the real CameraX [ExtensionsManager].
 */
internal class CameraXExtensionSelectorResolver(
    private val manager: ExtensionsManager
) : ExtensionSelectorResolver {

    override fun resolve(
        desiredMode: CameraExtensionMode,
        lensFacing: LensFacing
    ): ExtensionSelectorResult {
        if (desiredMode == CameraExtensionMode.NONE) {
            return ExtensionSelectorResult.NotRequested
        }
        val cameraSelector = lensFacing.toCameraSelector()
        val extensionMode = desiredMode.toExtensionMode()
        return runCatching {
            if (!manager.isExtensionAvailable(cameraSelector, extensionMode)) {
                return ExtensionSelectorResult.Fallback(
                    CameraExtensionResolution(
                        requestedMode = desiredMode,
                        availability = CameraExtensionAvailability.UNSUPPORTED,
                        reason = "Extension mode ${desiredMode.tagValue} not available for ${lensFacing.name}"
                    )
                )
            }
            val extensionSelector = manager.getExtensionEnabledCameraSelector(
                cameraSelector,
                extensionMode
            )
            ExtensionSelectorResult.Resolved(
                selector = extensionSelector,
                resolution = CameraExtensionResolution(
                    requestedMode = desiredMode,
                    availability = CameraExtensionAvailability.AVAILABLE,
                    reason = "Extension ${desiredMode.tagValue} selected for ${lensFacing.name}"
                )
            )
        }.getOrElse { error ->
            val availability = when {
                error is SecurityException -> CameraExtensionAvailability.MANAGER_UNAVAILABLE
                error.message?.contains("selector", ignoreCase = true) == true ->
                    CameraExtensionAvailability.SELECTOR_ERROR
                else -> CameraExtensionAvailability.QUERY_ERROR
            }
            ExtensionSelectorResult.Fallback(
                CameraExtensionResolution(
                    requestedMode = desiredMode,
                    availability = availability,
                    reason = error.message ?: "Extension resolution failed",
                    diagnostics = mapOf("exception" to error::class.java.simpleName)
                )
            )
        }
    }
}

private fun LensFacing.toCameraSelector(): CameraSelector {
    return when (this) {
        LensFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
        LensFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
    }
}

private fun CameraExtensionMode.toExtensionMode(): Int {
    return when (this) {
        CameraExtensionMode.NONE -> ExtensionMode.AUTO
        CameraExtensionMode.NIGHT -> ExtensionMode.NIGHT
        CameraExtensionMode.HDR -> ExtensionMode.HDR
        CameraExtensionMode.AUTO -> ExtensionMode.AUTO
        CameraExtensionMode.BOKEH -> ExtensionMode.BOKEH
        CameraExtensionMode.FACE_RETOUCH -> ExtensionMode.FACE_RETOUCH
    }
}

/**
 * Lazy [ExtensionSelectorResolver] that defers [ExtensionsManager] initialization
 * until the first [resolve] call. Returns [CameraExtensionAvailability.MANAGER_UNAVAILABLE]
 * until the underlying manager is ready, without blocking app startup.
 *
 * The initialization completes synchronously on first use (blocking only the calling thread
 * for the duration of the `ExtensionsManager` async bind, which is typically fast after the
 * first camera session has started).
 */
internal class LazyCameraXExtensionSelectorResolver(
    private val context: Context
) : ExtensionSelectorResolver {

    @Volatile
    private var delegate: CameraXExtensionSelectorResolver? = null

    override fun resolve(
        desiredMode: CameraExtensionMode,
        lensFacing: LensFacing
    ): ExtensionSelectorResult {
        val resolver = delegate ?: initializeDelegate()
        return if (resolver != null) {
            resolver.resolve(desiredMode, lensFacing)
        } else {
            ExtensionSelectorResult.Fallback(
                CameraExtensionResolution(
                    requestedMode = desiredMode,
                    availability = CameraExtensionAvailability.MANAGER_UNAVAILABLE,
                    reason = "ExtensionsManager not yet available"
                )
            )
        }
    }

    private fun initializeDelegate(): CameraXExtensionSelectorResolver? {
        if (delegate != null) return delegate
        return synchronized(this) {
            if (delegate != null) return@synchronized delegate
            try {
                val provider = ProcessCameraProvider.getInstance(context)
                    .get(5, TimeUnit.SECONDS)
                val manager = ExtensionsManager.getInstanceAsync(context, provider)
                    .get(5, TimeUnit.SECONDS)
                CameraXExtensionSelectorResolver(manager).also { delegate = it }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
