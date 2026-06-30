package com.opencamera.app

import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Scrim visibility tests for [MainActivityRenderer.renderPanelVisibility].
 *
 * Covers ISSUE-002: `panelDismissScrim` must be visible under
 * `CockpitPanelRoute.StyleLab` so that an outside tap (preview blank area)
 * dismisses the style card rail. `StyleStrip` must remain excluded so the
 * check-in standalone style surface keeps its independent dismiss behavior.
 *
 * The scrim click listener in [MainActivityActionBinder.bindPanelActions]
 * delegates to `CockpitPanelCommand.DismissAll`; the route transition
 * `StyleLab -> None` on `DismissAll` is covered by
 * `CockpitPanelRouterTest.`DismissAll from StyleLab closes to None and resets filter state``.
 */
@RunWith(RobolectricTestRunner::class)
class PanelDismissScrimVisibilityTest {

    @Test
    fun `scrim is visible under StyleLab`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)

        assertTrue(
            "panelDismissScrim must be visible under StyleLab so outside tap dismisses the card rail (ISSUE-002)",
            views.panelDismissScrim.isVisible
        )
    }

    @Test
    fun `scrim is hidden under StyleStrip to preserve standalone dismiss behavior`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleStrip)

        assertFalse(
            "panelDismissScrim must remain hidden under StyleStrip (preserved behavior)",
            views.panelDismissScrim.isVisible
        )
    }

    @Test
    fun `scrim is hidden under None`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.None)

        assertFalse(views.panelDismissScrim.isVisible)
    }

    @Test
    fun `scrim visibility toggles with StyleLab open and dismiss`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)
        assertTrue(views.panelDismissScrim.isVisible)

        // Simulate the scrim click path: DismissAll -> None, then re-render.
        renderer.renderPanelVisibility(CockpitPanelRoute.None)
        assertFalse(
            "scrim must hide once the route leaves StyleLab",
            views.panelDismissScrim.isVisible
        )
        assertFalse(
            "rail must hide once the route leaves StyleLab",
            views.bottomCockpit.stylePresetCardRail.isVisible
        )
    }

    @Test
    fun `scrim is visible under ColorLab to preserve existing outside-dismiss behavior`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.ColorLab)

        assertTrue(views.panelDismissScrim.isVisible)
    }

    private fun createViews(): MainActivityViews {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        return MainActivityViews(
            preview = PreviewViews(
                previewView = allocateInstance(PreviewView::class.java),
                overlayView = allocateInstance(PreviewOverlayView::class.java),
                thumbnail = allocateInstance(ImageView::class.java),
                captureOutput = TextView(context)
            ),
            topBar = TopBarViews(
                titleText = TextView(context),
                permissionStatus = TextView(context),
                colorLabEntry = Button(context),
                settingsEntry = Button(context),
                filterEntry = Button(context)
            ),
            quickPanel = QuickPanelViews(
                panel = NestedScrollView(context),
                content = LinearLayout(context),
                grid = Button(context),
                resolution = Button(context),
                brightnessSlider = allocateInstance(SeekBar::class.java),
                brightnessValueText = TextView(context),
                frameRatio = Button(context),
                watermark = Button(context),
                livePhoto = Button(context),
                timer = Button(context),
                launcher = Button(context),
                resetDefaults = Button(context)
            ),
            floatingUtility = FloatingUtilityViews(
                quickLauncher = Button(context),
                lowLightNightPrompt = Button(context)
            ),
            documentBatchRail = DocumentBatchRailViews(
                rail = LinearLayout(context),
                chip = TextView(context),
                thumbnail = allocateInstance(ImageView::class.java),
                itemScroll = NestedScrollView(context),
                itemList = LinearLayout(context),
                moveUpButton = Button(context),
                moveDownButton = Button(context),
                overviewButton = Button(context)
            ),
            documentBatchOrganizer = DocumentBatchOrganizerViews(
                panel = LinearLayout(context),
                scroll = NestedScrollView(context),
                title = TextView(context),
                count = TextView(context),
                itemList = LinearLayout(context),
                close = Button(context),
                emptyHint = TextView(context),
                footer = LinearLayout(context),
                continueShooting = Button(context),
                exportButton = Button(context),
                status = TextView(context)
            ),
            filterStrip = FilterStripViews(
                scroll = HorizontalScrollView(context),
                chips = LinearLayout(context)
            ),
            runtimeProControls = RuntimeProControlsViews(
                scroll = HorizontalScrollView(context),
                chips = LinearLayout(context)
            ),
            settingsPanel = SettingsPanelViews(
                panel = NestedScrollView(context),
                close = Button(context),
                back = Button(context),
                rootContent = LinearLayout(context),
                portraitLabContent = LinearLayout(context),
                watermarkSelectorContent = LinearLayout(context),
                watermarkDetailContent = LinearLayout(context),
                headline = TextView(context),
                supportingText = TextView(context),
                heroSummary = TextView(context),
                commonSummary = TextView(context),
                photoSummary = TextView(context),
                videoSummary = TextView(context),
                editingHint = TextView(context),
                tabCommon = Button(context),
                tabPhoto = Button(context),
                tabVideo = Button(context),
                commonSection = LinearLayout(context),
                photoSection = LinearLayout(context),
                videoSection = LinearLayout(context),
                shutterSound = LinearLayout(context),
                selfieMirror = LinearLayout(context),
                appLanguage = LinearLayout(context),
                photoFilter = LinearLayout(context),
                photoPortraitLab = LinearLayout(context),
                photoWatermark = LinearLayout(context),
                photoLive = LinearLayout(context),
                photoLiveSaveFormat = LinearLayout(context),
                photoTimer = LinearLayout(context),
                videoResolution = LinearLayout(context),
                videoFrameRate = LinearLayout(context),
                videoDynamicFps = LinearLayout(context),
                videoAudio = LinearLayout(context),
                videoFilter = LinearLayout(context),
                portraitHeadline = TextView(context),
                portraitSupportingText = TextView(context),
                portraitHeroSummary = TextView(context),
                portraitEditingHint = TextView(context),
                portraitProfile = Button(context),
                portraitBeautyPreset = Button(context),
                portraitBeautyStrength = Button(context),
                portraitBokehEffect = Button(context),
                portraitDepthStrengthSeekBar = allocateInstance(SeekBar::class.java),
                portraitDepthStrengthValue = TextView(context),
                portraitFooter = TextView(context),
                watermarkSelectorHeadline = TextView(context),
                watermarkSelectorSupportingText = TextView(context),
                watermarkSelectorHeroSummary = TextView(context),
                watermarkSelectorList = LinearLayout(context),
                watermarkSelectorEditingHint = TextView(context),
                watermarkSelectorFooter = TextView(context),
                watermarkDetailHeadline = TextView(context),
                watermarkDetailSupportingText = TextView(context),
                watermarkDetailHeroSummary = TextView(context),
                watermarkDetailEditingHint = TextView(context),
                watermarkPlacement = Button(context),
                watermarkTextScale = Button(context),
                watermarkTextOpacity = Button(context),
                watermarkFrameBackground = Button(context),
                watermarkDetailFooter = TextView(context),
                resetDefaults = Button(context)
            ),
            filterLab = FilterLabViews(
                panel = NestedScrollView(context),
                headline = TextView(context),
                supportingText = TextView(context),
                heroSummary = TextView(context),
                currentSummary = TextView(context),
                sectionFiltersTitle = TextView(context),
                selectionCard = LinearLayout(context),
                selectionList = LinearLayout(context),
                editingHint = TextView(context),
                footer = TextView(context),
                photoTab = Button(context),
                humanisticTab = Button(context),
                portraitTab = Button(context),
                videoTab = Button(context),
                saveCustom = Button(context),
                sectionPaletteTitle = TextView(context),
                adjustmentPanel = LinearLayout(context),
                modeToggle = Button(context),
                paletteSummary = TextView(context),
                paletteHint = TextView(context),
                paletteSurface = allocateInstance(FilterPaletteView::class.java),
                advancedTitle = TextView(context),
                advancedControls = LinearLayout(context),
                advancedExposure = Button(context),
                advancedSoftGlow = Button(context),
                advancedHalo = Button(context),
                advancedGrain = Button(context),
                advancedSharpness = Button(context),
                advancedVignette = Button(context),
                advancedHighlights = Button(context),
                advancedShadows = Button(context),
                advancedWarmBoost = Button(context),
                advancedCoolBoost = Button(context),
                advancedTemperatureShift = Button(context),
                advancedTintShift = Button(context),
                resetDefaults = Button(context)
            ),
            devConsole = DevConsoleViews(
                entry = Button(context),
                panel = allocateInstance(MaterialCardView::class.java),
                scroll = NestedScrollView(context),
                tabKey = Button(context),
                tabCore = Button(context),
                tabError = Button(context),
                tabAll = Button(context),
                title = TextView(context),
                summary = TextView(context),
                eventsRecycler = allocateInstance(RecyclerView::class.java),
                storageInfo = TextView(context),
                export = Button(context),
                vendorProbe = Button(context),
                close = Button(context),
                scrollTop = Button(context),
                scrollBottom = Button(context)
            ),
            modeTrack = ModeTrackViews(
                scroll = HorizontalScrollView(context),
                photo = Button(context),
                checkIn = Button(context),
                video = Button(context),
                document = Button(context),
                humanistic = Button(context),
                modeAction = Button(context)
            ),
            bottomCockpit = BottomCockpitViews(
                shutter = Button(context),
                lensFacing = Button(context),
                focalLengthSlider = allocateInstance(FocalLengthSliderView::class.java),
                recordingIndicator = TextView(context),
                stylePresetCardRail = StylePresetCardRailView(context)
            ),
            panelDismissScrim = View(context)
        )
    }

    private fun createRenderer(views: MainActivityViews): MainActivityRenderer {
        return MainActivityRenderer(
            views = views,
            cockpit = allocateInstance(CockpitSurfaceRenderer::class.java),
            settings = allocateInstance(SettingsPanelRenderer::class.java),
            filterLab = allocateInstance(FilterLabPanelRenderer::class.java),
            devConsole = allocateInstance(DevConsoleRenderer::class.java)
        )
    }

    companion object {
        private val unsafe: Any by lazy {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val field = unsafeClass.getDeclaredField("theUnsafe")
            field.isAccessible = true
            field.get(null)
        }

        private fun <T> allocateInstance(type: Class<T>): T {
            val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
            @Suppress("UNCHECKED_CAST")
            return allocateInstance.invoke(unsafe, type) as T
        }
    }
}
