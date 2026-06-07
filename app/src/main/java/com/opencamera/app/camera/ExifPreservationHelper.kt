package com.opencamera.app.camera

import android.content.ContentResolver
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.ProcessorTarget
import java.io.ByteArrayInputStream
import java.io.File

val EXIF_TAGS_TO_PRESERVE = listOf(
    ExifInterface.TAG_ORIENTATION,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP
)

fun ContentResolver.writeEncodedBytes(target: ProcessorTarget, encodedBytes: ByteArray): Boolean {
    return when (target) {
        is ProcessorTarget.FilePath -> runCatching {
            File(target.path).outputStream().use { it.write(encodedBytes) }
        }.isSuccess
        is ProcessorTarget.ContentUri -> {
            openOutputStream(Uri.parse(target.value), "rwt")?.use {
                it.write(encodedBytes)
            } != null
        }
    }
}

fun readPreservedExif(sourceBytes: ByteArray): Map<String, String> {
    return runCatching {
        ByteArrayInputStream(sourceBytes).use { input ->
            val exif = ExifInterface(input)
            EXIF_TAGS_TO_PRESERVE.mapNotNull { tag ->
                exif.getAttribute(tag)?.let { value -> tag to value }
            }.toMap()
        }
    }.getOrDefault(emptyMap())
}

fun ContentResolver.restorePreservedExif(
    target: ProcessorTarget,
    preservedExif: Map<String, String>
): String? {
    if (preservedExif.isEmpty()) return null
    val restored = runCatching {
        when (target) {
            is ProcessorTarget.FilePath -> {
                ExifInterface(target.path).apply {
                    applyPreservedExif(preservedExif)
                    saveAttributes()
                }
            }
            is ProcessorTarget.ContentUri -> {
                openFileDescriptor(Uri.parse(target.value), "rw")?.use { descriptor ->
                    ExifInterface(descriptor.fileDescriptor).apply {
                        applyPreservedExif(preservedExif)
                        saveAttributes()
                    }
                } ?: error("file-descriptor-unavailable")
            }
        }
    }
    return if (restored.isSuccess) null else "exif-restore-failed"
}

fun ExifInterface.applyPreservedExif(preservedExif: Map<String, String>) {
    preservedExif.forEach { (tag, value) -> setAttribute(tag, value) }
}
