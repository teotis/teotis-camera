package com.opencamera.app.camera

import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector

private const val TAG = "PhysicalCameraIdResolver"

/**
 * Resolves a [CameraSelector] that targets a specific physical camera ID
 * from the list of cameras CameraX exposes.
 *
 * Implementations must use public or owned APIs; reflection-based fallbacks
 * must be isolated in a separate implementation and every failure path must
 * be observable via [CameraSelector] fallback behavior.
 */
internal fun interface PhysicalCameraIdResolver {

    /**
     * Returns a [CameraSelector] that selects exactly one camera matching
     * [physicalCameraId]. The caller must catch [IllegalArgumentException] from
     * `bindToLifecycle` if the selector matches no cameras at bind time.
     *
     * Implementations must NOT unbind or tear down any current camera binding;
     * they only produce a selector for the caller to evaluate.
     */
    fun resolveSelector(physicalCameraId: String): CameraSelector
}

/**
 * Primary resolver that uses CameraX's public [CameraSelector.setPhysicalCameraId]
 * API to select a physical camera by ID. This is the recommended approach for
 * CameraX 1.4.x devices that expose physical camera IDs through the public API.
 *
 * On multi-camera devices, `setPhysicalCameraId` tells CameraX to select the
 * physical sub-camera rather than the default logical camera. On single-camera
 * devices, the selector may match the default back camera if the ID is the
 * logical camera ID.
 *
 * If the physical camera ID is not recognized by CameraX, the selector will
 * match zero cameras, causing `bindToLifecycle` to fail with an
 * [IllegalArgumentException]. The caller must catch this and emit an explicit
 * runtime issue.
 */
internal class PublicApiPhysicalIdResolver : PhysicalCameraIdResolver {

    override fun resolveSelector(physicalCameraId: String): CameraSelector {
        return CameraSelector.Builder()
            .setPhysicalCameraId(physicalCameraId)
            .build()
    }
}

/**
 * Reflection-based fallback resolver, isolated from the primary selector path.
 *
 * Every failure is logged and surfaces as a `false` filter result (no match),
 * which causes the caller to emit an explicit
 * [com.opencamera.core.device.DeviceRuntimeIssue] rather than silently binding
 * the wrong lens.
 *
 * This resolver must ONLY be used when [PublicApiPhysicalIdResolver] cannot match
 * the target ID through public APIs, and must be documented as best-effort.
 */
internal class ReflectionPhysicalIdResolver : PhysicalCameraIdResolver {

    override fun resolveSelector(physicalCameraId: String): CameraSelector {
        return CameraSelector.Builder()
            .addCameraFilter { cameras ->
                cameras.filter { cameraInfo ->
                    matchesViaReflection(cameraInfo, physicalCameraId)
                }
            }
            .build()
    }

    private fun matchesViaReflection(cameraInfo: CameraInfo, targetId: String): Boolean {
        return runCatching {
            // Access Camera2CameraInfo internals via reflection to extract camera ID.
            // This is a best-effort fallback for devices where setPhysicalCameraId
            // does not work but the physical camera ID is still accessible through
            // the Camera2 layer.
            val camera2InfoClass = Class.forName("androidx.camera.camera2.interop.Camera2CameraInfo")
            val fromMethod = camera2InfoClass.getMethod("from", CameraInfo::class.java)
            val camera2Info = fromMethod.invoke(null, cameraInfo)
            val getCameraIdMethod = camera2InfoClass.getMethod("getCameraId")
            val id = getCameraIdMethod.invoke(camera2Info) as? String
            id == targetId
        }.onFailure { e ->
            Log.w(TAG, "Reflection fallback failed for physical camera $targetId", e)
        }.getOrDefault(false)
    }
}

/**
 * Composite resolver that uses [PublicApiPhysicalIdResolver] for the primary path.
 *
 * The caller should use the selector returned by [resolveSelector] and catch
 * `IllegalArgumentException` from `bindToLifecycle` to detect a mismatch.
 * If that occurs, the caller may optionally try [resolveSelectorViaReflection]
 * as a best-effort fallback before emitting a runtime issue.
 */
internal class CompositePhysicalCameraIdResolver : PhysicalCameraIdResolver {

    private val publicApiResolver = PublicApiPhysicalIdResolver()
    private val reflectionResolver = ReflectionPhysicalIdResolver()

    override fun resolveSelector(physicalCameraId: String): CameraSelector {
        return publicApiResolver.resolveSelector(physicalCameraId)
    }

    /**
     * Produces a selector using the reflection fallback. Only call this when
     * the primary [resolveSelector] result produced an empty bind or no match
     * was observed. The caller must log the fallback activation.
     */
    fun resolveSelectorViaReflection(physicalCameraId: String): CameraSelector {
        return reflectionResolver.resolveSelector(physicalCameraId)
    }
}
