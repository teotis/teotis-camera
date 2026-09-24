package com.opencamera.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LauncherIconContractTest {
    @Test
    fun `manifest uses project launcher icons`() {
        val manifest = parseXml(projectFile("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0)
        val attributes = application.attributes

        assertEquals("@mipmap/ic_launcher", attributes.getNamedItem("android:icon").nodeValue)
        assertEquals("@mipmap/ic_launcher_round", attributes.getNamedItem("android:roundIcon").nodeValue)
    }

    @Test
    fun `adaptive icon uses cobalt background and concept 22 foreground`() {
        val backgroundValues = projectFile("src/main/res/values/launcher_icon.xml").readText()
        val adaptiveIcon = projectFile("src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val themedIcon = projectFile("src/main/res/mipmap-anydpi-v33/ic_launcher.xml").readText()

        assertTrue(backgroundValues.contains("#1257D6"))
        assertTrue(adaptiveIcon.contains("@color/ic_launcher_background"))
        assertTrue(adaptiveIcon.contains("@drawable/ic_launcher_foreground"))
        assertTrue(themedIcon.contains("@drawable/ic_launcher_monochrome"))
    }

    @Test
    fun `foreground keeps transparent adaptive safe-zone padding`() {
        val header = pngHeader(projectFile("src/main/res/drawable-nodpi/ic_launcher_foreground.png"))

        assertEquals(1024, header.width)
        assertEquals(1024, header.height)
        assertEquals(6, header.colorType, "foreground must retain an alpha channel")
    }

    @Test
    fun `legacy density fallbacks are present`() {
        val expectedSizes = mapOf(
            "mipmap-mdpi" to 48,
            "mipmap-hdpi" to 72,
            "mipmap-xhdpi" to 96,
            "mipmap-xxhdpi" to 144,
            "mipmap-xxxhdpi" to 192,
        )

        expectedSizes.forEach { (density, expectedSize) ->
            listOf("ic_launcher.png", "ic_launcher_round.png").forEach { name ->
                val header = pngHeader(projectFile("src/main/res/$density/$name"))
                assertEquals(expectedSize, header.width)
                assertEquals(expectedSize, header.height)
            }
        }
    }

    private fun pngHeader(file: File): PngHeader {
        val bytes = file.readBytes()
        assertTrue(bytes.size >= 26, "${file.path} must contain a PNG header")
        assertTrue(
            bytes.copyOfRange(1, 4).contentEquals(byteArrayOf(80, 78, 71)),
            "${file.path} must be a PNG",
        )
        assertEquals("IHDR", bytes.copyOfRange(12, 16).decodeToString())
        return PngHeader(
            width = pngInt(bytes, 16),
            height = pngInt(bytes, 20),
            colorType = bytes[25].toInt() and 0xff,
        )
    }

    private fun pngInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun parseXml(file: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun projectFile(relativePath: String): File {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::isFile)
            ?: error("Missing project file: $relativePath")
    }

    private data class PngHeader(
        val width: Int,
        val height: Int,
        val colorType: Int,
    )
}
