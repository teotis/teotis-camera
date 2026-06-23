package com.opencamera.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("@id/previewBottomGuide", preview.appAttr("layout_constraintBottom_toBottomOf"))
    }

    @Test
    fun `preview viewport stays stable across bottom cockpit height changes`() {
        val preview = layout.elementByAndroidId("cameraPreview")
        val overlay = layout.elementByAndroidId("previewOverlay")
        val guideline = layout.elementByAndroidId("previewBottomGuide")

        assertEquals("@id/previewBottomGuide", preview.appAttr("layout_constraintBottom_toBottomOf"))
        assertEquals("@id/previewBottomGuide", overlay.appAttr("layout_constraintBottom_toBottomOf"))
        assertEquals("androidx.constraintlayout.widget.Guideline", guideline.tagName)
        val percent = guideline.appAttr("layout_constraintGuide_percent")
        assertTrue(
            percent.toDouble() in 0.72..0.8,
            "Preview bottom guide should leave a compact cockpit without a large dead gap: $percent"
        )
    }

    @Test
    fun `mode track keeps compact vertical padding`() {
        val modeTrack = layout.elementByAndroidId("modeTrackScroll")

        assertEquals("@dimen/space_2", modeTrack.androidAttr("paddingVertical"))
        assertEquals(2, resolveDimenDp(modeTrack.androidAttr("paddingVertical")))
    }

    @Test
    fun `mode track distributes equal width across five modes`() {
        val modeTrackScroll = layout.elementByAndroidId("modeTrackScroll")
        val modeTrackLinearLayout = layout.elementByAndroidId("modeTrack")

        assertEquals("true", modeTrackScroll.androidAttr("fillViewport"))
        assertEquals("match_parent", modeTrackLinearLayout.androidAttr("layout_width"))

        val modeButtonIds = listOf(
            "buttonPhotoMode",
            "buttonCheckInMode",
            "buttonVideoMode",
            "buttonDocumentMode",
            "buttonHumanisticMode"
        )
        for (id in modeButtonIds) {
            val button = layout.elementByAndroidId(id)
            assertEquals("0dp", button.androidAttr("layout_width"),
                "$id must use 0dp layout_width for weight distribution")
            assertEquals("1", button.androidAttr("layout_weight"),
                "$id must use layout_weight=1 for equal distribution")
        }
    }

    @Test
    fun `humanistic mode button is visible by default`() {
        val humanistic = layout.elementByAndroidId("buttonHumanisticMode")
        val visibility = humanistic.androidAttr("visibility")
        assertFalse(
            visibility == "gone",
            "buttonHumanisticMode must not default to visibility=gone"
        )
    }

    @Test
    fun `mode specific controls stay inside bottom cockpit with mode track`() {
        val bottomSheet = layout.elementByAndroidId("bottomSheet")
        val bottomSheetChildren = bottomSheet.childElementsByAndroidId()

        assertTrue("modeTrackScroll" in bottomSheetChildren)
        assertTrue("focalLengthSlider" in bottomSheetChildren)

        val floatingToolLayer = layout.elementByAndroidId("floatingToolLayer")
        val floatingChildren = floatingToolLayer.childElementsByAndroidId()

        assertTrue("filterStripScroll" in floatingChildren)
        assertTrue("runtimeProControlsScroll" in floatingChildren)
        assertTrue("buttonModeAction" in floatingChildren)
        assertFalse("focalLengthSlider" in floatingChildren)
    }

    @Test
    fun `constraint layout references stay within direct sibling scope`() {
        val constraintLayouts =
            layout.getElementsByTagName("androidx.constraintlayout.widget.ConstraintLayout")

        for (layoutIndex in 0 until constraintLayouts.length) {
            val constraintLayout = constraintLayouts.item(layoutIndex) as Element
            val children = constraintLayout.childElements()
            val siblingIds = children.mapNotNull { it.androidIdOrNull() }.toSet()

            children.forEach { child ->
                val attributes = child.attributes
                for (attributeIndex in 0 until attributes.length) {
                    val attribute = attributes.item(attributeIndex)
                    if (!attribute.nodeName.startsWith("app:layout_constraint")) continue
                    if (!attribute.nodeValue.startsWith("@id/")) continue

                    val targetId = attribute.nodeValue.removePrefix("@id/")
                    assertTrue(
                        targetId in siblingIds,
                        "${child.androidIdOrNull() ?: child.tagName} references non-sibling $targetId"
                    )
                }
            }
        }
    }

    @Test
    fun `shutter row is lifted from screen edge without inflating the control stack`() {
        val bottomSheet = layout.elementByAndroidId("bottomSheet")
        val modeTrack = layout.elementByAndroidId("modeTrackScroll")
        val passiveBottomClearance =
                resolveDimenDp(bottomSheet.androidAttr("paddingBottom"))
        val cockpitBottomMargin =
                resolveDimenDp(bottomSheet.androidAttr("layout_marginBottom"))
        val compactVerticalChrome =
                resolveDimenDp(modeTrack.androidAttr("paddingVertical")) * 2 +
                resolveDimenDp("@dimen/cockpit_control_row_margin_top")

        assertEquals(4, passiveBottomClearance)
        assertTrue(cockpitBottomMargin >= 14)
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

    private fun Element.childElementsByAndroidId(): Set<String> = buildSet {
        childElements().mapNotNullTo(this) { it.androidIdOrNull() }
    }

    private fun Element.childElements(): List<Element> = buildList {
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            (nodes.item(index) as? Element)?.let(::add)
        }
    }

    private fun Element.androidIdOrNull(): String? =
        getAttribute("android:id")
            .removePrefix("@+id/")
            .removePrefix("@id/")
            .takeIf(String::isNotBlank)

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

    @Test
    fun `style preset card rail is not child of bottom cockpit to avoid measuring overhead`() {
        val bottomSheet = layout.elementByAndroidId("bottomSheet")
        val bottomSheetChildren = bottomSheet.childElementsByAndroidId()

        assertFalse("stylePresetCardRail" in bottomSheetChildren, "stylePresetCardRail must not be in bottomSheet")
        assertTrue("modeTrackScroll" in bottomSheetChildren, "modeTrackScroll must be in bottomSheet")
    }

    @Test
    fun `style preset card rail is direct child of style rail overlay`() {
        val overlay = layout.elementByAndroidId("styleRailOverlay")
        val overlayChildren = overlay.childElementsByAndroidId()

        assertTrue("stylePresetCardRail" in overlayChildren, "stylePresetCardRail must be in styleRailOverlay")
    }

    @Test
    fun `style rail overlay floats above bottom tool layer`() {
        val overlay = layout.elementByAndroidId("styleRailOverlay")
        assertEquals("@id/floatingToolLayer", overlay.appAttr("layout_constraintBottom_toTopOf"))
        assertEquals("@dimen/style_rail_bottom_gap", overlay.androidAttr("layout_marginBottom"))
    }

    @Test
    fun `style preset card rail starts invisible`() {
        val rail = layout.elementByAndroidId("stylePresetCardRail")
        assertEquals("gone", rail.androidAttr("visibility"))
    }

    @Test
    fun `style preset card rail fills width with wrap height`() {
        val rail = layout.elementByAndroidId("stylePresetCardRail")
        assertEquals("match_parent", rail.androidAttr("layout_width"))
        assertEquals("wrap_content", rail.androidAttr("layout_height"))
        assertEquals("@android:color/transparent", rail.androidAttr("background"))
    }

    @Test
    fun `settings common section does not include unbound empty rows`() {
        val commonSection = layout.elementByAndroidId("settingsCommonSection")
        val commonChildren = commonSection.childElementsByAndroidId()

        assertFalse(
            "buttonGridMode" in commonChildren,
            "buttonGridMode is not rendered by SettingsPanelRenderer and must not reserve blank space"
        )
    }

    @Test
    fun `dev console panel has compact height cap`() {
        val panel = layout.elementByAndroidId("devConsolePanel")

        assertEquals("0dp", panel.androidAttr("layout_height"))
        assertEquals("@dimen/dev_console_panel_min_height", panel.androidAttr("minHeight"))
        assertEquals("@dimen/dev_console_panel_max_height", panel.appAttr("layout_constraintHeight_max"))
    }

    @Test
    fun `dev console log list has stable scrollable height`() {
        val logList = layout.elementByAndroidId("devConsoleContent")

        assertEquals("@dimen/dev_console_log_height", logList.androidAttr("layout_height"))
        assertEquals("true", logList.androidAttr("nestedScrollingEnabled"))
        assertTrue(resolveDimenDp("@dimen/dev_console_log_height") >= 160)
    }

    @Test
    fun `floating tool layer does not participate in bottom cockpit measurement`() {
        val bottomSheet = layout.elementByAndroidId("bottomSheet")
        val bottomSheetChildren = bottomSheet.childElementsByAndroidId()

        assertFalse("floatingToolLayer" in bottomSheetChildren,
            "floatingToolLayer must not be a direct child of bottomSheet")

        // Verify that floating-only views are no longer in bottomSheet.
        assertFalse("filterStripScroll" in bottomSheetChildren)
        assertFalse("runtimeProControlsScroll" in bottomSheetChildren)
        assertFalse("buttonModeAction" in bottomSheetChildren)
        assertFalse("stylePresetCardRail" in bottomSheetChildren)
        assertFalse("recordingIndicator" in bottomSheetChildren)
        assertTrue(
            "focalLengthSlider" in bottomSheetChildren,
            "focalLengthSlider must stay attached to the bottom cockpit instead of floating over preview"
        )

        // Verify they are in floatingToolLayer
        val floatingToolLayer = layout.elementByAndroidId("floatingToolLayer")
        val floatingChildren = floatingToolLayer.childElementsByAndroidId()

        assertTrue("filterStripScroll" in floatingChildren)
        assertTrue("runtimeProControlsScroll" in floatingChildren)
        assertTrue("buttonModeAction" in floatingChildren)
        assertFalse("stylePresetCardRail" in floatingChildren)
        assertFalse("focalLengthSlider" in floatingChildren)
        assertTrue("recordingIndicator" in floatingChildren)
    }

    @Test
    fun `floating tool layer clears bottom cockpit visual band`() {
        val floatingToolLayer = layout.elementByAndroidId("floatingToolLayer")

        assertEquals("@id/bottomSheet", floatingToolLayer.appAttr("layout_constraintBottom_toTopOf"))
        assertEquals(
            "@dimen/floating_tool_bottom_clearance",
            floatingToolLayer.androidAttr("layout_marginBottom")
        )
        assertTrue(
            resolveDimenDp("@dimen/floating_tool_bottom_clearance") >= 48,
            "Mode actions and Pro controls must float above the bottom cockpit band"
        )
    }

}
