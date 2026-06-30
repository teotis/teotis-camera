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
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.toStylePresetPreview
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Route → unique-visible-surface invariant tests for [MainActivityRenderer].
 *
 * Covers ISSUE-001 / EV-010: `CockpitPanelRoute.StyleLab` must surface through
 * `stylePresetCardRail` only, never through the deprecated `filterLab.panel`.
 * Scenarios: mode switch, rapid repeat clicks, restore-then-reopen.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityRendererRouteInvariantTest {

    @Test
    fun `StyleLab route makes stylePresetCardRail visible and filterLab panel invisible`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)

        assertTrue(
            "stylePresetCardRail must be visible under StyleLab",
            views.bottomCockpit.stylePresetCardRail.isVisible
        )
        assertFalse(
            "filterLab.panel must NOT be visible under StyleLab (ISSUE-001 / EV-010)",
            views.filterLab.panel.isVisible
        )
    }

    @Test
    fun `rapid repeat StyleLab toggles keep filterLab panel invisible`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)
        renderer.renderPanelVisibility(CockpitPanelRoute.None)
        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)
        renderer.renderPanelVisibility(CockpitPanelRoute.None)
        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)

        assertTrue(views.bottomCockpit.stylePresetCardRail.isVisible)
        assertFalse(views.filterLab.panel.isVisible)
    }

    @Test
    fun `mode switch while StyleLab open keeps rail visible and filterLab panel invisible`() {
        val views = createViews()
        val renderer = createRenderer(views)

        // Style entry visible (e.g. PHOTO mode) → open StyleLab.
        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab, isStyleEntryVisible = true)
        assertTrue(views.bottomCockpit.stylePresetCardRail.isVisible)
        assertFalse(views.filterLab.panel.isVisible)

        // Mode switch updates isStyleEntryVisible but not the route. The rail
        // visibility depends only on route, so it must remain visible; the
        // filterLab.panel must remain invisible.
        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab, isStyleEntryVisible = false)
        assertTrue(
            "rail visibility is route-driven and must survive mode switch",
            views.bottomCockpit.stylePresetCardRail.isVisible
        )
        assertFalse(views.filterLab.panel.isVisible)
    }

    @Test
    fun `restore from dismissed state then reopen StyleLab keeps filterLab panel invisible`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)
        renderer.renderPanelVisibility(CockpitPanelRoute.None)
        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)

        assertTrue(views.bottomCockpit.stylePresetCardRail.isVisible)
        assertFalse(views.filterLab.panel.isVisible)
    }

    @Test
    fun `ColorLab route still maps filterLab panel as active surface`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.ColorLab)

        assertTrue(views.filterLab.panel.isVisible)
        assertFalse(views.bottomCockpit.stylePresetCardRail.isVisible)
    }

    @Test
    fun `CheckInStylePanel route still maps filterLab panel as active surface`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.CheckInStylePanel)

        assertTrue(views.filterLab.panel.isVisible)
        assertFalse(views.bottomCockpit.stylePresetCardRail.isVisible)
    }

    @Test
    fun `None route hides both stylePresetCardRail and filterLab panel`() {
        val views = createViews()
        val renderer = createRenderer(views)

        renderer.renderPanelVisibility(CockpitPanelRoute.None)

        assertFalse(views.bottomCockpit.stylePresetCardRail.isVisible)
        assertFalse(views.filterLab.panel.isVisible)
    }

    @Test
    fun `eager style model render cannot reveal style rail outside StyleLab route`() {
        val views = createViews()
        val renderer = createRenderer(views)
        val filterRenderer = FilterLabPanelRenderer(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            views = views.filterLab,
            cardRail = views.bottomCockpit.stylePresetCardRail
        )

        renderer.renderPanelVisibility(CockpitPanelRoute.None)
        filterRenderer.renderPage(stylePageWithCardRail())

        assertFalse(
            "stylePresetCardRail visibility must remain route-owned; eager style refreshes from other switches must not flash it",
            views.bottomCockpit.stylePresetCardRail.isVisible
        )
    }

    @Test
    fun `eager style model render preserves visible rail under StyleLab route`() {
        val views = createViews()
        val renderer = createRenderer(views)
        val filterRenderer = FilterLabPanelRenderer(
            context = org.robolectric.RuntimeEnvironment.getApplication(),
            views = views.filterLab,
            cardRail = views.bottomCockpit.stylePresetCardRail
        )

        renderer.renderPanelVisibility(CockpitPanelRoute.StyleLab)
        filterRenderer.renderPage(stylePageWithCardRail())

        assertTrue(views.bottomCockpit.stylePresetCardRail.isVisible)
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

    private fun stylePageWithCardRail(): FilterLabPageRenderModel {
        val spec = FilterRenderSpec(contrast = 1.1f, saturation = 1.08f)
        val tab = FilterLabTabRenderModel(FilterLabFamily.PHOTO, "Photo", isSelected = true)
        return FilterLabPageRenderModel(
            headline = "Style",
            supportingText = "Choose a style",
            heroSummary = "",
            currentFilterSummary = "Portrait Retro",
            rosterText = "",
            editingEnabled = true,
            editingHint = "",
            showAdjustmentPanel = false,
            photoTab = tab,
            humanisticTab = FilterLabTabRenderModel(FilterLabFamily.HUMANISTIC, "Humanistic", isSelected = false),
            portraitTab = FilterLabTabRenderModel(FilterLabFamily.PORTRAIT, "Portrait", isSelected = false),
            videoTab = FilterLabTabRenderModel(FilterLabFamily.VIDEO, "Video", isSelected = false),
            documentTab = FilterLabTabRenderModel(FilterLabFamily.DOCUMENT, "Document", isSelected = false),
            filterItems = emptyList(),
            adjustControl = FilterLabAdjustRenderModel(
                buttonLabel = "Adjust",
                family = FilterLabFamily.PHOTO,
                sourceProfileId = "photo-retro",
                isEnabled = true,
                willCreateCustomCopy = false
            ),
            adjustmentPanel = FilterAdjustmentPanelRenderModel(
                isVisible = false,
                mode = FilterAdjustmentMode.LIGHT,
                selectedProfileId = "photo-retro",
                selectedProfileLabel = "Portrait Retro",
                renderSpec = spec,
                modeToggleLabel = "Advanced",
                lightPalette = FilterLightPaletteRenderModel("", ""),
                advancedControls = FilterAdvancedControl.entries.map { control ->
                    FilterAdvancedControlRenderModel(control, control.name)
                }
            ),
            cycleControl = SettingsControlRenderModel("Style", "Portrait Retro"),
            saveCustomControl = FilterLabSaveCustomRenderModel(
                buttonLabel = "Save",
                family = FilterLabFamily.PHOTO,
                sourceProfileId = "photo-retro",
                isEnabled = true
            ),
            footer = "",
            stylePresetCardRail = StylePresetRailRenderModel(
                title = "Photo Styles",
                activeFamily = FilterLabFamily.PHOTO,
                cards = listOf(
                    StylePresetCardRenderModel(
                        profileId = "photo-retro",
                        title = "Retro",
                        family = FilterLabFamily.PHOTO,
                        preview = spec.toStylePresetPreview(),
                        isSelected = true,
                        isEnabled = true,
                        applyAction = null,
                        moodLabel = "Warm",
                        spec = spec
                    )
                ),
                isEnabled = true,
                supportingText = "Tap to apply"
            )
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
