package com.opencamera.app.camera

import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoWatermarkTemplateResolverTest {
    @Test
    fun `travel polaroid resolver uses default slogan and degrades without location`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "travel-polaroid",
            watermarkText = "PHOTO Auto",
            metadata = MediaMetadata(),
            preservedExif = mapOf(
                ExifInterface.TAG_MODEL to "vivo X300 Ultra",
                ExifInterface.TAG_DATETIME_ORIGINAL to "2026:04:11 20:16:00"
            )
        )

        assertEquals("travel-polaroid", resolved.templateId)
        assertEquals("去有天空的地方", resolved.title)
        assertEquals(WatermarkFrameBackground.WHITE, resolved.frameBackground)
        assertTrue(resolved.usesExpandedFrame)
        assertTrue(resolved.supportingLines.any { it.contains("vivo X300 Ultra") })
        assertTrue(resolved.supportingLines.any { it.contains("2026-04-11 20-16-00") })
        assertFalse(resolved.supportingLines.any { it.contains(",") })
    }

    @Test
    fun `retro frame resolver prefers explicit metadata tokens and background override`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "retro-frame",
            watermarkText = "Night Street",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkModel" to "X300U",
                    "watermarkDatetime" to "2026-04-11 21:00",
                    "watermarkCameraParams" to "1/50s • f/1.8 • ISO 320",
                    "watermarkFrameBackground" to "source-light-blur",
                    "watermarkPosition" to "bottom-right",
                    "watermarkTextScale" to "1.2",
                    "watermarkTextOpacity" to "0.8"
                )
            ),
            preservedExif = emptyMap()
        )

        assertEquals("retro-frame", resolved.templateId)
        assertEquals("Night Street", resolved.title)
        assertEquals(WatermarkFrameBackground.SOURCE_LIGHT_BLUR, resolved.frameBackground)
        assertEquals(WatermarkTextPlacement.BOTTOM_RIGHT, resolved.placement)
        assertEquals(1.2f, resolved.textScale)
        assertEquals(0.8f, resolved.textOpacity)
        assertTrue(resolved.supportingLines.any { it.contains("X300U") })
        assertTrue(resolved.supportingLines.any { it.contains("ISO 320") })
    }

    @Test
    fun `unknown template falls back to classic overlay and formats gps camera params`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "unsupported-template",
            watermarkText = "PHOTO On",
            metadata = MediaMetadata(),
            preservedExif = mapOf(
                ExifInterface.TAG_MODEL to "OpenCamera DevKit",
                ExifInterface.TAG_DATETIME to "2026:04:11 09:30:10",
                ExifInterface.TAG_GPS_LATITUDE to "31/1,13/1,3000/100",
                ExifInterface.TAG_GPS_LATITUDE_REF to "N",
                ExifInterface.TAG_GPS_LONGITUDE to "121/1,28/1,4500/100",
                ExifInterface.TAG_GPS_LONGITUDE_REF to "E",
                ExifInterface.TAG_EXPOSURE_TIME to "1/120",
                ExifInterface.TAG_F_NUMBER to "1.75",
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "64"
            )
        )

        assertEquals("classic-overlay", resolved.templateId)
        assertEquals("template-fallback", resolved.warning)
        assertEquals(WatermarkFrameBackground.DARK, resolved.frameBackground)
        assertTrue(resolved.supportingLines.any { it.contains("31.2250, 121.4792") })
        assertTrue(resolved.supportingLines.any { it.contains("ISO 64") })
    }
}
