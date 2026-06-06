package com.opencamera.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Document
import org.w3c.dom.Element

class MainSurfaceOpacityContractTest {
    private val colors = parseColors(resourceFile("values/colors.xml"))
    private val theme = parseXml(resourceFile("values/themes.xml"))
    private val layout = parseXml(resourceFile("layout/activity_main.xml"))

    @Test
    fun `main activity has opaque fallback surfaces behind translucent chrome`() {
        val root = layout.documentElement
        val openCameraTheme = theme.elementByName("style", "Theme.OpenCamera")

        assertEquals("@color/oc_root_background", root.androidAttr("background"))
        assertEquals("#FF000000", colors.getValue("oc_root_background"))
        assertEquals("@color/oc_root_background", openCameraTheme.itemByName("android:windowBackground").textContent.trim())
    }

    private fun parseColors(file: File): Map<String, String> {
        val nodes = parseXml(file).getElementsByTagName("color")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent.trim())
            }
        }
    }

    private fun Document.elementByName(tagName: String, name: String): Element {
        val nodes = getElementsByTagName(tagName)
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            if (element.getAttribute("name") == name) return element
        }
        error("Missing <$tagName name=\"$name\">")
    }

    private fun Element.itemByName(name: String): Element {
        val nodes = getElementsByTagName("item")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            if (element.getAttribute("name") == name) return element
        }
        error("Missing <item name=\"$name\">")
    }

    private fun Element.androidAttr(name: String): String =
        getAttribute("android:$name").takeIf(String::isNotBlank)
            ?: error("Missing android:$name on ${tagName}")

    private fun parseXml(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun resourceFile(relativePath: String): File {
        return listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath")
        ).firstOrNull(File::isFile) ?: error("Missing resource file $relativePath")
    }
}
