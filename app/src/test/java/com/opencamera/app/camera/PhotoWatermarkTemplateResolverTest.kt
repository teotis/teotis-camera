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
    fun `structured watermarkModeName produces device-model dot mode-name title`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "classic-overlay",
            watermarkText = "PHOTO Auto",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkModeName" to "Scenery",
                    "watermarkProfileName" to "Handheld"
                )
            ),
            preservedExif = mapOf(
                ExifInterface.TAG_MODEL to "vivo X300 Ultra"
            )
        )

        assertEquals("vivo X300 Ultra · Scenery Handheld", resolved.title)
    }

    @Test
    fun `device model priority prefers custom tag over exif tag`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "classic-overlay",
            watermarkText = "PHOTO Auto",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkModel" to "Custom Model",
                    "watermarkModeName" to "Photo"
                )
            ),
            preservedExif = mapOf(
                ExifInterface.TAG_MODEL to "EXIF Model"
            )
        )

        assertEquals("Custom Model · Photo", resolved.title)
    }

    @Test
    fun `night mode structured tags output Scenery not Night`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "retro-frame",
            watermarkText = "Scenery Handheld",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkModeName" to "Scenery",
                    "watermarkProfileName" to "Handheld",
                    "watermarkCameraParams" to "1/50s • f/1.8 • ISO 320"
                )
            ),
            preservedExif = emptyMap()
        )

        assertEquals("OpenCamera · Scenery Handheld", resolved.title)
        assertTrue(resolved.supportingLines.any { it.contains("Handheld") })
        assertTrue(resolved.supportingLines.any { it.contains("1/50s") })
    }

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
        assertTrue(resolved.supportingLines.any { it.contains("2026-04-11 21:00") })
        assertTrue(resolved.supportingLines.any { it.contains("ISO 320") })
    }

    @Test
    fun `pure text resolver keeps overlay template and no frame expansion`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "pure-text",
            watermarkText = "OpenCamera",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkPosition" to "top-right",
                    "watermarkTextScale" to "1.2",
                    "watermarkTextOpacity" to "0.8"
                )
            ),
            preservedExif = emptyMap()
        )

        assertEquals("pure-text", resolved.templateId)
        assertFalse(resolved.usesExpandedFrame)
        assertEquals(WatermarkTextPlacement.TOP_RIGHT, resolved.placement)
        assertEquals(1.2f, resolved.textScale)
        assertEquals(0.8f, resolved.textOpacity)
    }

    @Test
    fun `blur four border resolver clamps unsupported solid background to light blur`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "blur-four-border",
            watermarkText = "OpenCamera",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkFrameBackground" to "white",
                    "watermarkPosition" to "bottom-center"
                )
            ),
            preservedExif = emptyMap()
        )

        assertEquals("blur-four-border", resolved.templateId)
        assertTrue(resolved.usesExpandedFrame)
        assertEquals(WatermarkFrameBackground.SOURCE_LIGHT_BLUR, resolved.frameBackground)
        assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, resolved.placement)
    }

    @Test
    fun `professional bottom bar resolves model with mode and profile in title`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "professional-bottom-bar",
            watermarkText = "PHOTO Auto",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkModel" to "X300 Ultra",
                    "watermarkModeName" to "Scenery",
                    "watermarkProfileName" to "Handheld",
                    "watermarkDatetime" to "2026-04-11 20:16",
                    "watermarkCameraParams" to "1/50s • f/1.8 • ISO 320"
                )
            ),
            preservedExif = emptyMap()
        )

        assertEquals("professional-bottom-bar", resolved.templateId)
        assertEquals("X300 Ultra · Scenery Handheld", resolved.title)
        assertTrue(resolved.usesExpandedFrame)
        assertEquals(WatermarkFrameBackground.DARK, resolved.frameBackground)
        assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, resolved.placement)
        assertTrue(resolved.supportingLines.any { it.contains("2026-04-11 20:16") })
        assertTrue(resolved.supportingLines.any { it.contains("ISO 320") })
    }

    @Test
    fun `professional bottom bar degrades gracefully with missing mode tags`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "professional-bottom-bar",
            watermarkText = "OpenCamera",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkDatetime" to "2026-05-25 10:00"
                )
            ),
            preservedExif = mapOf(
                ExifInterface.TAG_MODEL to "OpenCamera DevKit",
                ExifInterface.TAG_EXPOSURE_TIME to "1/250",
                ExifInterface.TAG_F_NUMBER to "2.8",
                ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "100"
            )
        )

        assertEquals("professional-bottom-bar", resolved.templateId)
        assertEquals("OpenCamera", resolved.title)
        assertTrue(resolved.supportingLines.any { it.contains("2026-05-25 10:00") })
        assertTrue(resolved.supportingLines.any { it.contains("ISO 100") })
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
