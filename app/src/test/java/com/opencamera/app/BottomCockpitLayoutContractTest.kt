package com.opencamera.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Document
import org.w3c.dom.Element

class BottomCockpitLayoutContractTest {
    private val layout = parseXml(resourceFile("layout/activity_main.xml"))
    private val dimens = parseDimens(resourceFile("values/dimens.xml"))

    @Test
    fun `preview content uses end aligned aspect fit above bottom cockpit`() {
        val preview = layout.elementByAndroidId("cameraPreview")

        assertEquals("fitEnd", preview.appAttr("scaleType"))
        assertEquals("@id/bottomSheet", preview.appAttr("layout_constraintBottom_toTopOf"))
    }

    @Test
    fun `mode track keeps compact vertical padding`() {
        val modeTrack = layout.elementByAndroidId("modeTrackScroll")

        assertEquals("@dimen/space_2", modeTrack.androidAttr("paddingVertical"))
        assertEquals(2, resolveDimenDp(modeTrack.androidAttr("paddingVertical")))
    }

    @Test
    fun `shutter row is lifted from screen edge without inflating the control stack`() {
        val bottomSheet = layout.elementByAndroidId("bottomSheet")
        val modeTrack = layout.elementByAndroidId("modeTrackScroll")
        val passiveBottomClearance =
            resolveDimenDp(bottomSheet.androidAttr("paddingBottom"))
        val compactVerticalChrome =
                resolveDimenDp(modeTrack.androidAttr("paddingVertical")) * 2 +
                resolveDimenDp("@dimen/cockpit_control_row_margin_top")

        assertEquals(4, passiveBottomClearance)
        assertTrue(compactVerticalChrome <= 6)
    }

    @Test
    fun `bottom control row centers shutter independently from side controls`() {
        val controlRow = layout.elementByAndroidId("bottomControlRow")
        val shutter = layout.elementByAndroidId("buttonShutter")
        val thumbnail = layout.elementByAndroidId("previewThumbnail")
        val lensFacing = layout.elementByAndroidId("buttonLensFacing")

        assertEquals("androidx.constraintlayout.widget.ConstraintLayout", controlRow.tagName)
        assertEquals("parent", shutter.appAttr("layout_constraintStart_toStartOf"))
        assertEquals("parent", shutter.appAttr("layout_constraintEnd_toEndOf"))
        assertEquals("@id/buttonShutter", thumbnail.appAttr("layout_constraintEnd_toStartOf"))
        assertEquals("@id/buttonShutter", lensFacing.appAttr("layout_constraintStart_toEndOf"))
    }

    @Test
    fun `navigation inset padding is capped and preserves xml padding`() {
        assertEquals(
            16,
            BottomCockpitInsetPolicy.resolveBottomPadding(
                layoutPaddingBottomPx = 4,
                navigationBarsBottomPx = 48,
                maxNavigationPaddingPx = 16
            )
        )
        assertEquals(
            4,
            BottomCockpitInsetPolicy.resolveBottomPadding(
                layoutPaddingBottomPx = 4,
                navigationBarsBottomPx = 0,
                maxNavigationPaddingPx = 16
            )
        )
        assertEquals(
            12,
            BottomCockpitInsetPolicy.resolveBottomPadding(
                layoutPaddingBottomPx = 4,
                navigationBarsBottomPx = 12,
                maxNavigationPaddingPx = 16
            )
        )
    }

    private fun resolveDimenDp(reference: String): Int {
        val dimenName = reference.removePrefix("@dimen/")
        return dimens.getValue(dimenName).removeSuffix("dp").toInt()
    }

    private fun Document.elementByAndroidId(id: String): Element {
        val targetValues = setOf("@+id/$id", "@id/$id")
        val nodes = getElementsByTagName("*")
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            if (element.getAttribute("android:id") in targetValues) return element
        }
        error("Missing Android id $id")
    }

    private fun Element.androidAttr(name: String): String =
        getAttribute("android:$name").takeIf(String::isNotBlank)
            ?: error("Missing android:$name on ${tagName}")

    private fun Element.appAttr(name: String): String =
        getAttribute("app:$name").takeIf(String::isNotBlank)
            ?: error("Missing app:$name on ${tagName}")

    private fun parseDimens(file: File): Map<String, String> {
        val nodes = parseXml(file).getElementsByTagName("dimen")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent.trim())
            }
        }
    }

    private fun parseXml(file: File): Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun resourceFile(relativePath: String): File {
        return listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath")
        ).firstOrNull(File::isFile) ?: error("Missing resource file $relativePath")
    }
}
