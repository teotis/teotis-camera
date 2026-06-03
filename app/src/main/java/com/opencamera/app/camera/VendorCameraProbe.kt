package com.opencamera.app.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VendorCameraProbe {

    fun probe(context: Context): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = cameraManager.cameraIdList
        val sb = StringBuilder()

        sb.appendLine("===== VENDOR CAMERA PROBE =====")
        sb.appendLine("timestamp: ${formatTimestamp(System.currentTimeMillis())}")
        sb.appendLine("camera-count: ${cameraIds.size}")
        sb.appendLine()

        for (cameraId in cameraIds) {
            val chars = cameraManager.getCameraCharacteristics(cameraId)
            sb.appendLine("=== cameraId=$cameraId ===")
            sb.appendLine("  lens-facing: ${lensFacingLabel(chars.get(CameraCharacteristics.LENS_FACING))}")
            sb.appendLine("  supported-hardware-level: ${hardwareLevelLabel(chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")
            sb.appendLine("  logical? ${isLogical(chars)}")

            @Suppress("DEPRECATION")
            val physicalIds = chars.getPhysicalCameraIds()
            if (physicalIds.isNotEmpty()) {
                sb.appendLine("  physical-camera-ids: ${physicalIds.joinToString(", ")}")
            }

            val allKeys = chars.keys
            val standardKeys = mutableListOf<String>()
            val vendorKeys = mutableListOf<String>()

            for (key in allKeys) {
                val keyName = key.name
                val value = chars.get(key)
                val formatted = formatKeyValue(keyName, value)

                if (keyName.startsWith("org.") || keyName.startsWith("com.") ||
                    keyName.contains("vendor") || keyName.contains("Vendor") ||
                    keyName.contains("extension") || keyName.contains("Extension")
                ) {
                    vendorKeys += formatted
                } else {
                    standardKeys += formatted
                }
            }

            if (standardKeys.isNotEmpty()) {
                sb.appendLine("  [standard-keys]")
                standardKeys.forEach { sb.appendLine("    $it") }
            }

            if (vendorKeys.isNotEmpty()) {
                sb.appendLine("  [vendor-keys]")
                vendorKeys.forEach { sb.appendLine("    $it") }
            }

            val captureRequestKeys = chars.availableCaptureRequestKeys
            if (captureRequestKeys.isNotEmpty()) {
                sb.appendLine("  [available-capture-request-keys]")
                captureRequestKeys.forEach { sb.appendLine("    ${it.name}") }
            }

            val availableResultKeys = chars.availableCaptureResultKeys
            if (availableResultKeys.isNotEmpty()) {
                sb.appendLine("  [available-capture-result-keys]")
                availableResultKeys.forEach { sb.appendLine("    ${it.name}") }
            }

            val sessionKeys = chars.availableSessionKeys
            if (sessionKeys.isNotEmpty()) {
                sb.appendLine("  [available-session-keys]")
                sessionKeys.forEach { sb.appendLine("    ${it.name}") }
            }

            sb.appendLine()
        }

        return sb.toString()
    }

    private fun formatKeyValue(name: String, value: Any?): String {
        val valueStr = when (value) {
            null -> "null"
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            is FloatArray -> value.contentToString()
            is DoubleArray -> value.contentToString()
            is ByteArray -> value.contentToString()
            is Array<*> -> value.contentDeepToString()
            is android.util.Size -> "${value.width}x${value.height}"
            is android.util.SizeF -> "${value.width}x${value.height}"
            is android.graphics.Rect -> "Rect(${value.left},${value.top},${value.right},${value.bottom})"
            is android.util.Range<*> -> "[${value.lower}, ${value.upper}]"
            is android.hardware.camera2.params.StreamConfigurationMap -> {
                val jpegSizes = value.getOutputSizes(android.graphics.ImageFormat.JPEG)
                val rawSizes = value.getOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)
                val yuvSizes = value.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                val highResJpeg = value.getHighResolutionOutputSizes(android.graphics.ImageFormat.JPEG)
                val highResRaw = value.getHighResolutionOutputSizes(android.graphics.ImageFormat.RAW_SENSOR)
                val parts = mutableListOf<String>()
                if (jpegSizes.isNotEmpty()) {
                    parts += "JPEG=[${jpegSizes.joinToString { "${it.width}x${it.height}" }}]"
                }
                if (highResJpeg.isNotEmpty()) {
                    parts += "HIGH_RES_JPEG=[${highResJpeg.joinToString { "${it.width}x${it.height}" }}]"
                }
                if (rawSizes.isNotEmpty()) {
                    parts += "RAW=[${rawSizes.joinToString { "${it.width}x${it.height}" }}]"
                }
                if (highResRaw.isNotEmpty()) {
                    parts += "HIGH_RES_RAW=[${highResRaw.joinToString { "${it.width}x${it.height}" }}]"
                }
                if (yuvSizes.isNotEmpty()) {
                    parts += "YUV=[${yuvSizes.joinToString { "${it.width}x${it.height}" }}]"
                }
                "SCM{${parts.joinToString("; ")}}"
            }
            is Boolean -> value.toString()
            is Int -> value.toString()
            is Long -> value.toString()
            is Float -> value.toString()
            is String -> "\"$value\""
            else -> value.toString()
        }
        return "$name = $valueStr"
    }

    private fun lensFacingLabel(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($facing)"
    }

    private fun hardwareLevelLabel(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN($level)"
    }

    private fun isLogical(chars: CameraCharacteristics): Boolean {
        return chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true
    }

    private fun formatTimestamp(millis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(millis))
    }

}
