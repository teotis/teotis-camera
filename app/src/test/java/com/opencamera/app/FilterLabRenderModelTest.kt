package com.opencamera.app

import androidx.annotation.StringRes
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FilterLabRenderModelTest {


        @Test
        fun `filter lab render model follows active check-in mode and cycles photo defaults`() {
            val model = filterLabPageRenderModel(
                defaultSessionState(
                    activeMode = ModeId.CHECK_IN,
                    availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
                    modeSnapshot = ModeSnapshot(
                        id = ModeId.CHECK_IN,
                        uiSpec = ModeUiSpec(
                            title = "Portrait",
                            shutterLabel = "Capture Portrait"
                        ),
                        state = ModeState(
                            headline = "Portrait mode active",
                            detail = "Ready"
                        )
                    )
                ),
                TestAppTextResolver()
            )

            assertEquals("Style", model.headline)
            assertEquals("", model.heroSummary)
            assertTrue(model.photoTab.isSelected)
            assertFalse(model.portraitTab.isSelected)
            assertEquals(
                PersistedSettingsAction.UpdatePhotoFilter("portrait-ccd"),
                model.cycleControl.nextAction
            )
            assertTrue(model.currentFilterSummary.contains("Current default Portrait Retro"))
            assertTrue(model.saveCustomControl.isEnabled)
            assertEquals("portrait-retro", model.saveCustomControl.sourceProfileId)
            assertTrue(model.footer.contains("Independent Tone Lab prioritized"))
        }



        @Test
        fun `filter lab render model switches to humanistic family and keeps import export deferred`() {
            val model = filterLabPageRenderModel(
                state = defaultSessionState(),
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.HUMANISTIC
            )

            assertTrue(model.humanisticTab.isSelected)
            assertEquals(
                "Next Humanistic look\nStreet\n可用 • 3 looks | import/export deferred",
                model.cycleControl.buttonLabel
            )
            assertEquals(
                PersistedSettingsAction.UpdateHumanisticFilter("humanistic-portrait"),
                model.cycleControl.nextAction
            )
            assertTrue(model.rosterText.contains("• Street"))
            assertTrue(model.saveCustomControl.isEnabled)
            assertTrue(model.supportingText.contains("Import and export stay deferred"))
        }



        @Test
        fun `filter lab render model exposes light adjustment panel for selected custom filter`() {
            val state = defaultSessionState().copy(
                settings = defaultSessionState().settings.copy(
                    persisted = defaultSessionState().settings.persisted.copy(
                        photo = defaultSessionState().settings.persisted.photo.copy(
                            defaultPortraitFilterProfileId = "custom-portrait-original-1"
                        )
                    ),
                    catalog = defaultSessionState().settings.catalog.withImportedFilterProfile(
                        com.opencamera.core.settings.FilterProfile(
                            id = "custom-portrait-original-1",
                            label = "Portrait Original Custom 1",
                            category = com.opencamera.core.settings.FilterProfileCategory.CUSTOM,
                            builtIn = false,
                            renderSpec = com.opencamera.core.settings.FilterRenderSpec(
                                brightnessShift = 8,
                                contrast = 1.06f,
                                saturation = 1.02f,
                                warmthShift = 3,
                                softGlowStrength = 0.1f,
                                grainStrength = 0.1f
                            )
                        )
                    )
                ),
                activeMode = ModeId.CHECK_IN
            )

            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PORTRAIT,
                showAdjustmentPanel = true,
                adjustmentMode = FilterAdjustmentMode.LIGHT
            )

            assertTrue(model.adjustControl.isEnabled)
            assertFalse(model.adjustControl.willCreateCustomCopy)
            assertTrue(model.adjustmentPanel.isVisible)
            assertEquals(FilterAdjustmentMode.LIGHT, model.adjustmentPanel.mode)
            assertEquals("custom-portrait-original-1", model.adjustmentPanel.selectedProfileId)
            assertTrue(model.adjustmentPanel.lightPalette.supportingText.contains("Horizontal swipe"))
            val selectedItem = model.filterItems.first { it.filterProfileId == "custom-portrait-original-1" }
            assertTrue(selectedItem.isSelected)
            assertTrue(selectedItem.supportingText.contains("Selected default"))
            assertNull(selectedItem.adjustButtonLabel)
        }



        @Test
        fun `filter lab render model exposes advanced adjustment controls for selected portrait filter`() {
            val model = filterLabPageRenderModel(
                state = defaultSessionState(activeMode = ModeId.CHECK_IN),
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PORTRAIT,
                showAdjustmentPanel = true,
                adjustmentMode = FilterAdjustmentMode.ADVANCED
            )

            assertTrue(model.adjustmentPanel.isVisible)
            assertEquals(FilterAdjustmentMode.ADVANCED, model.adjustmentPanel.mode)
            assertEquals(12, model.adjustmentPanel.advancedControls.size)
            assertTrue(model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.EXPOSURE })
            assertTrue(model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.HALO })
            assertTrue(model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.WARM_BOOST })
            assertTrue(
                model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.TEMPERATURE_SHIFT }
            )
            assertTrue(
                model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.TINT_SHIFT }
            )
            assertEquals(
                "Temp Shift\nOff\nTap to cycle",
                model.adjustmentPanel.advancedControls.first {
                    it.control == FilterAdvancedControl.TEMPERATURE_SHIFT
                }.buttonLabel
            )
            val selectedItem = model.filterItems.first { it.filterProfileId == "portrait-original" }
            val unselectedItem = model.filterItems.first { it.filterProfileId == "portrait-blue" }
            assertNull(selectedItem.adjustButtonLabel)
            assertEquals(null, unselectedItem.adjustButtonLabel)
        }



        @Test
        fun `advanced control cycling covers halo temperature and tint shifts`() {
            val halo = com.opencamera.core.settings.FilterRenderSpec()
                .nextAdvancedControl(FilterAdvancedControl.HALO)
            val warm = com.opencamera.core.settings.FilterRenderSpec()
                .nextAdvancedControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
            val magenta = com.opencamera.core.settings.FilterRenderSpec()
                .nextAdvancedControl(FilterAdvancedControl.TINT_SHIFT)
            val cool = warm
                .nextAdvancedControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
                .nextAdvancedControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
            val green = magenta
                .nextAdvancedControl(FilterAdvancedControl.TINT_SHIFT)
                .nextAdvancedControl(FilterAdvancedControl.TINT_SHIFT)

            assertEquals(0.1f, halo.haloStrength)
            assertEquals(6, warm.warmthShift)
            assertEquals(6, magenta.tintShift)
            assertEquals(-6, cool.warmthShift)
            assertEquals(-6, green.tintShift)
        }



        @Test
        fun `filter lab adjustment panel is visible by default`() {
            val model = filterLabPageRenderModel(
                state = defaultSessionState(),
                text = TestAppTextResolver()
            )

            assertTrue(model.adjustmentPanel.isVisible)
            assertEquals(FilterAdjustmentMode.LIGHT, model.adjustmentPanel.mode)
        }



        @Test
        fun `built-in filter shows auto-prepare hint on palette`() {
            val model = filterLabPageRenderModel(
                state = defaultSessionState(activeMode = ModeId.CHECK_IN),
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PORTRAIT
            )

            assertTrue(model.adjustmentPanel.isVisible)
            assertTrue(model.adjustmentPanel.needsAutoPrepare)
            assertTrue(model.adjustmentPanel.lightPalette.supportingText.contains("Drag palette to save as custom"))
        }



        @Test
        fun `custom filter does not need auto-prepare`() {
            val state = defaultSessionState().copy(
                settings = defaultSessionState().settings.copy(
                    persisted = defaultSessionState().settings.persisted.copy(
                        photo = defaultSessionState().settings.persisted.photo.copy(
                            defaultPortraitFilterProfileId = "custom-portrait-original-1"
                        )
                    ),
                    catalog = defaultSessionState().settings.catalog.withImportedFilterProfile(
                        com.opencamera.core.settings.FilterProfile(
                            id = "custom-portrait-original-1",
                            label = "Portrait Original Custom 1",
                            category = com.opencamera.core.settings.FilterProfileCategory.CUSTOM,
                            builtIn = false,
                            renderSpec = com.opencamera.core.settings.FilterRenderSpec()
                        )
                    )
                ),
                activeMode = ModeId.CHECK_IN
            )

            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PORTRAIT
            )

            assertTrue(model.adjustmentPanel.isVisible)
            assertFalse(model.adjustmentPanel.needsAutoPrepare)
            assertTrue(model.adjustmentPanel.lightPalette.supportingText.contains("Horizontal swipe"))
        }



        @Test
        fun `advanced mode shows 12 controls`() {
            val model = filterLabPageRenderModel(
                state = defaultSessionState(activeMode = ModeId.CHECK_IN),
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PORTRAIT,
                adjustmentMode = FilterAdjustmentMode.ADVANCED
            )

            assertTrue(model.adjustmentPanel.isVisible)
            assertEquals(FilterAdjustmentMode.ADVANCED, model.adjustmentPanel.mode)
            assertEquals(12, model.adjustmentPanel.advancedControls.size)
        }

        companion object {
            private val strings = SessionUiStrings(
                buttonSwitchToFront = "Switch to Front",
                buttonSwitchToBack = "Switch to Back",
                buttonSingleLens = "Single Lens",
                buttonZoomPrefix = "Zoom",
                buttonZoomUnavailable = "Zoom N/A",
                buttonStillFast = "Still Fast",
                buttonStillMax = "Still Max",
                buttonStillQualityUnavailable = "Still N/A",
                buttonStill12Mp = "Still 12MP",
                buttonStill8Mp = "Still 8MP",
                buttonStill2Mp = "Still 2MP",
                buttonStillResolutionUnavailable = "Size N/A",
                outputErrorPrefix = "Camera issue:",
                outputVideoPrefix = "Last video:",
                outputLivePrefix = "Last Live photo:",
                outputSavedPrefix = "Last photo:",
                outputPreviewPrefix = "Preview thumbnail:",
                outputWaiting = "No photo captured yet."
            )
        }



        @Test
        fun `style lab shows active mode filter items without family tabs or adjustment panel`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )

            assertEquals(StyleAndColorLabRole.STYLE, model.panelRole)
            assertFalse(model.showFamilyTabs)
            assertTrue(model.showFilterItems)
            assertFalse(model.showAdjustmentPanel)
            assertEquals("Style", model.headline)
        }



        @Test
        fun `color lab shows adjustment panel but not filter items and family tabs`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertEquals(StyleAndColorLabRole.COLOR_LAB, model.panelRole)
            assertFalse(model.showFamilyTabs)
            assertFalse(model.showFilterItems)
            assertTrue(model.showAdjustmentPanel)
            assertEquals("Color Lab", model.headline)
        }



        @Test
        fun `filter items do not show raw parameter strings in title`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )

            model.filterItems.forEach { item ->
                assertFalse(item.title.contains("B ") && item.title.contains("C "),
                    "Filter item title should not contain raw parameter strings: ${item.title}")
                assertTrue(item.supportingText.contains("Photo") || item.supportingText.contains("Humanistic"),
                    "Filter item supporting text should contain family label: ${item.supportingText}")
            }
        }



        @Test
        fun `user-facing panels do not leak raw render spec internals`() {
            val state = defaultSessionState()
            val filterModel = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )
            val settingsModel = sessionSettingsPageRenderModel(state, TestAppTextResolver())

            val rawPatterns = listOf("B ", "C ", "S ", "W ", "Mono", "Vig", "Tint ", "Halo ")

            // Filter lab currentFilterSummary must not contain raw params
            rawPatterns.forEach { pattern ->
                assertFalse(filterModel.currentFilterSummary.contains(pattern),
                    "currentFilterSummary should not contain raw pattern '$pattern': ${filterModel.currentFilterSummary}")
            }

            // Filter lab rosterText must not contain raw params
            rawPatterns.forEach { pattern ->
                assertFalse(filterModel.rosterText.contains(pattern),
                    "rosterText should not contain raw pattern '$pattern': ${filterModel.rosterText}")
            }

            // Settings page availability labels must use localized text, not English enum names
            assertFalse(settingsModel.commonSection.gridMode.buttonLabel.contains("Supported"),
                "Settings buttonLabel should not contain English 'Supported'")
            assertFalse(settingsModel.photoSection.livePhoto.buttonLabel.contains("Degraded"),
                "Settings buttonLabel should not contain English 'Degraded'")
        }



        @Test
        fun `selected filter item shows family label in supporting text`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PHOTO,
                panelRole = StyleAndColorLabRole.STYLE
            )

            val selectedItem = assertNotNull(model.filterItems.firstOrNull { it.isSelected })
            assertTrue(selectedItem.supportingText.contains("Photo"),
                "Selected item should contain family label: ${selectedItem.supportingText}")
            assertTrue(selectedItem.supportingText.contains("Selected default"),
                "Selected item should contain selected badge: ${selectedItem.supportingText}")
        }



        @Test
        fun `photo filter family exposes humanistic styles as photo style subitems`() {
            val model = filterLabPageRenderModel(
                state = defaultSessionState(activeMode = ModeId.PHOTO),
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PHOTO
            )

            assertTrue(model.photoTab.isSelected)
            assertTrue(model.rosterText.contains("Street"))
            assertTrue(model.rosterText.contains("Life"))
        }



        @Test
        fun `save as custom button label is localized via text resolver`() {
            val customResolver = object : TestAppTextResolver() {
                internal override fun get(@StringRes resId: Int): String = when (resId) {
                    R.string.button_save_as_custom -> "保存为自定义"
                    else -> super.get(resId)
                }
            }
            val model = filterLabPageRenderModel(
                state = defaultSessionState(activeMode = ModeId.CHECK_IN),
                text = customResolver,
                selectedFamily = FilterLabFamily.PORTRAIT
            )
            assertTrue(model.saveCustomControl.buttonLabel.startsWith("保存为自定义"))
        }



        @Test
        fun `filter lab page uses text resolver for all key labels`() {
            val customResolver = object : TestAppTextResolver() {
                internal override fun get(@StringRes resId: Int): String = when (resId) {
                    R.string.style_panel_title -> "滤镜实验室"
                    R.string.button_save_as_custom -> "保存"
                    else -> super.get(resId)
                }
            }
            val model = filterLabPageRenderModel(
                state = defaultSessionState(activeMode = ModeId.PHOTO),
                text = customResolver
            )
            assertEquals("滤镜实验室", model.headline)
            assertTrue(model.saveCustomControl.buttonLabel.startsWith("保存"))
        }



        @Test
        fun `style filter page shows mode toggle button`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )

            assertTrue(model.showModeToggle, "Style Lab should show mode toggle button")
        }



        @Test
        fun `style filter family tabs use short family labels`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )

            assertEquals("Photo", model.photoTab.label)
            assertEquals("Humanistic", model.humanisticTab.label)
            assertEquals("Portrait", model.portraitTab.label)
            assertEquals("Video", model.videoTab.label)
            listOf(model.photoTab, model.humanisticTab, model.portraitTab, model.videoTab).forEach { tab ->
                assertFalse(tab.label.contains('\n'), "Family tab labels must fit one compact line: ${tab.label}")
            }
        }



        @Test
        fun `style filter page hides advanced controls`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )

            assertFalse(model.showAdvancedControls)
            assertFalse(model.showFamilyTabs)
            assertTrue(model.showFilterItems)
        }



        @Test
        fun `style filter page carries style strength from persisted settings`() {
            val state = defaultSessionState(
                persistedPhotoSettings = PhotoSettings(styleStrength = 0.5f)
            )
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.STYLE
            )

            assertEquals(0.5f, model.styleStrength)
            assertNotNull(model.updateStyleStrengthAction as? PersistedSettingsAction.UpdatePhotoStyleStrength)
        }



        @Test
        fun `color lab panel render model title is color lab`() {
            val state = defaultSessionState()
            val model = colorLabPanelRenderModel(state, TestAppTextResolver())

            assertEquals("Color Lab", model.title)
        }



        @Test
        fun `color lab panel render model reflects persisted spec`() {
            val spec = ColorLabSpec(colorAxis = 0.5f, toneAxis = -0.25f, strength = 0.8f)
            val state = defaultSessionState(
                persistedPhotoSettings = PhotoSettings(colorLabSpec = spec)
            )
            val model = colorLabPanelRenderModel(state, TestAppTextResolver())

            assertEquals(0.5f, model.colorAxis)
            assertEquals(-0.25f, model.toneAxis)
            assertEquals(0.8f, model.strength)
        }



        @Test
        fun `color lab panel render model exposes reset action`() {
            val state = defaultSessionState()
            val model = colorLabPanelRenderModel(state, TestAppTextResolver())

            val resetSpec = model.resetAction.spec
            assertEquals(0f, resetSpec.colorAxis)
            assertEquals(0f, resetSpec.toneAxis)
            assertEquals(1f, resetSpec.strength)
        }



        @Test
        fun `color lab filter page does not show advanced controls`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertFalse(model.showAdvancedControls)
            assertFalse(model.showFamilyTabs)
            assertFalse(model.showFilterItems)
            assertTrue(model.showAdjustmentPanel)
        }



        @Test
        fun `color lab filter page does not show mode toggle button`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertFalse(model.showModeToggle, "Color Lab should not show mode toggle / 进阶 button")
        }



        @Test
        fun `color lab filter page omits style only content`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertEquals("", model.currentFilterSummary)
            assertEquals("", model.rosterText)
            assertEquals("", model.editingHint)
            assertEquals("", model.footer)
            assertTrue(model.filterItems.isEmpty(), "Color Lab should not carry hidden style filter rows")
            assertFalse(model.saveCustomControl.isEnabled, "Color Lab should not expose style custom save state")
        }



        @Test
        fun `color lab adjustment panel summarizes persisted color lab spec`() {
            val state = defaultSessionState(
                persistedPhotoSettings = PhotoSettings(
                    colorLabSpec = ColorLabSpec(colorAxis = 0.42f, toneAxis = -0.18f)
                )
            )
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertEquals("Warm / Deep Contrast", model.adjustmentPanel.selectedProfileLabel)
            assertEquals("", model.adjustmentPanel.lightPalette.summary)
        }



        @Test
        fun `color lab panel summary uses human readable format`() {
            val spec = ColorLabSpec(colorAxis = 0.42f, toneAxis = -0.18f, strength = 0.8f)
            val state = defaultSessionState(
                persistedPhotoSettings = PhotoSettings(colorLabSpec = spec)
            )
            val model = colorLabPanelRenderModel(state, TestAppTextResolver())

            assertTrue(
                model.summary.contains("偏暖") || model.summary.contains("Warm"),
                "Summary should describe warm color axis: ${model.summary}"
            )
            assertTrue(
                model.summary.contains("加深") || model.summary.contains("Deep"),
                "Summary should describe deep tone axis: ${model.summary}"
            )
            assertFalse(
                model.summary.contains("Color:") && model.summary.contains("Tone:"),
                "Summary should not be raw format like 'Color: X, Tone: Y': ${model.summary}"
            )
        }



        @Test
        fun `color lab panel has hasUserAdjustments flag when spec differs from defaults`() {
            val state = defaultSessionState().copy(
                settings = com.opencamera.core.settings.SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings(
                        photo = com.opencamera.core.settings.PhotoSettings(
                            colorLabSpec = com.opencamera.core.settings.ColorLabSpec(colorAxis = 0.3f)
                        )
                    )
                )
            )
            val model = colorLabPanelRenderModel(state, TestAppTextResolver())

            assertTrue(model.hasUserAdjustments)
        }



        @Test
        fun `color lab panel has no hasUserAdjustments flag when at defaults`() {
            val state = defaultSessionState().copy(
                settings = com.opencamera.core.settings.SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings()
                )
            )
            val model = colorLabPanelRenderModel(state, TestAppTextResolver())

            assertFalse(model.hasUserAdjustments)
        }



        @Test
        fun `filter lab page has reset action when style adjustments exist`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            assertTrue(model.hasStyleUserAdjustments)
            assertEquals(
                PersistedSettingsAction.ResetToDefaults(ResetTarget.STYLE),
                model.resetStyleAction
            )
        }



        @Test
        fun `filter lab page has no reset action when at defaults`() {
            val state = defaultSessionState(
                persistedPhotoSettings = com.opencamera.core.settings.PhotoSettings()
            ).copy(
                settings = com.opencamera.core.settings.SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings()
                )
            )
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            assertFalse(model.hasStyleUserAdjustments)
            assertEquals(null, model.resetStyleAction)
        }



        @Test
        fun `color lab page does not expose style reset action`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertFalse(model.hasStyleUserAdjustments)
            assertEquals(null, model.resetStyleAction)
        }

        // --- StyleSurfaceRole selection tests ---



        @Test
        fun `styleSurfaceRole returns PANEL for PHOTO mode`() {
            assertEquals(StyleSurfaceRole.PANEL, styleSurfaceRole(ModeId.PHOTO))
        }



        @Test
        fun `styleSurfaceRole returns PANEL for CHECK_IN mode`() {
            assertEquals(StyleSurfaceRole.PANEL, styleSurfaceRole(ModeId.CHECK_IN))
        }



        @Test
        fun `styleSurfaceRole returns PANEL for HUMANISTIC mode`() {
            assertEquals(StyleSurfaceRole.PANEL, styleSurfaceRole(ModeId.HUMANISTIC))
        }



        @Test
        fun `styleSurfaceRole returns FILTER_STRIP for VIDEO mode`() {
            assertEquals(StyleSurfaceRole.FILTER_STRIP, styleSurfaceRole(ModeId.VIDEO))
        }



        @Test
        fun `styleSurfaceRole returns FILTER_STRIP for DOCUMENT mode`() {
            assertEquals(StyleSurfaceRole.FILTER_STRIP, styleSurfaceRole(ModeId.DOCUMENT))
        }

        // --- FilterStripRenderModel tests ---



        @Test
        fun `filterStripRenderModel produces items for PHOTO mode`() {
            val state = defaultSessionState(activeMode = ModeId.PHOTO)
            val model = filterStripRenderModel(state, TestAppTextResolver())
            assertTrue(model.isVisible)
            assertTrue(model.items.isNotEmpty())
            assertEquals("Style", model.headline)
        }



        @Test
        fun `filterStripRenderModel marks selected item for current filter`() {
            val state = defaultSessionState(
                activeMode = ModeId.PHOTO,
                persistedPhotoSettings = PhotoSettings(
                    defaultFilterProfileId = "portrait-retro"
                )
            )
            val model = filterStripRenderModel(state, TestAppTextResolver())
            val selected = model.items.filter { it.isSelected }
            assertEquals(1, selected.size)
            assertEquals("portrait-retro", selected.first().filterProfileId)
            assertNull(selected.first().selectAction)
        }



        @Test
        fun `filterStripRenderModel non-selected items have select actions`() {
            val state = defaultSessionState(activeMode = ModeId.PHOTO)
            val model = filterStripRenderModel(state, TestAppTextResolver())
            val unselected = model.items.filter { !it.isSelected }
            for (item in unselected) {
                assertTrue(item.selectAction != null, "Item ${item.filterProfileId} should have selectAction")
            }
        }



        @Test
        fun `filterStripRenderModel returns items for VIDEO mode with video family`() {
            val state = defaultSessionState(
                activeMode = ModeId.VIDEO,
                availableModes = listOf(ModeId.PHOTO, ModeId.VIDEO, ModeId.HUMANISTIC),
                modeSnapshot = ModeSnapshot(
                    id = ModeId.VIDEO,
                    uiSpec = ModeUiSpec(title = "VIDEO", shutterLabel = "Record"),
                    state = ModeState(headline = "VIDEO", detail = "Ready")
                )
            )
            val model = filterStripRenderModel(state, TestAppTextResolver())
            // Video mode uses video filter family from catalog; items may exist even if
            // video recording is unsupported on this device (catalog is device-independent).
            assertTrue(model.items.isNotEmpty())
        }



        @Test
        fun `filterStripRenderModel ellipsize long filter names inherits from label`() {
            val state = defaultSessionState(activeMode = ModeId.PHOTO)
            val model = filterStripRenderModel(state, TestAppTextResolver())
            for (item in model.items) {
                assertTrue(item.title.isNotBlank(), "Filter strip item title should not be blank")
            }
        }

        // --- Style preset card rail tests ---

        @Test
        fun `style preset card rail shows photo presets in PHOTO mode`() {
            val state = defaultSessionState(activeMode = ModeId.PHOTO)
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            assertNotNull(model.stylePresetCardRail, "Card rail should exist in STYLE mode")
            assertEquals(FilterLabFamily.PHOTO, model.stylePresetCardRail!!.activeFamily)
            assertTrue(model.stylePresetCardRail!!.cards.isNotEmpty(), "Should have photo preset cards")
            assertEquals("Photo Styles", model.stylePresetCardRail!!.title)
            assertTrue(model.stylePresetCardRail!!.isEnabled)
        }

        @Test
        fun `style preset card rail shows humanistic presets in HUMANISTIC mode`() {
            val state = defaultSessionState(activeMode = ModeId.HUMANISTIC)
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.HUMANISTIC
            )

            assertNotNull(model.stylePresetCardRail)
            assertEquals(FilterLabFamily.HUMANISTIC, model.stylePresetCardRail!!.activeFamily)
            assertTrue(model.stylePresetCardRail!!.cards.isNotEmpty())
            val streetCard = model.stylePresetCardRail!!.cards.firstOrNull { it.profileId == "humanistic-street" }
            assertNotNull(streetCard, "Humanistic rail should contain street preset")
            assertEquals("Humanistic Styles", model.stylePresetCardRail!!.title)
        }

        @Test
        fun `style preset card rail shows portrait presets in PORTRAIT family`() {
            val state = defaultSessionState(activeMode = ModeId.CHECK_IN)
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                selectedFamily = FilterLabFamily.PORTRAIT
            )

            assertNotNull(model.stylePresetCardRail)
            assertEquals(FilterLabFamily.PORTRAIT, model.stylePresetCardRail!!.activeFamily)
            val retroCard = model.stylePresetCardRail!!.cards.firstOrNull { it.profileId == "portrait-retro" }
            assertNotNull(retroCard, "Portrait rail should contain retro preset")
            assertEquals("Portrait Styles", model.stylePresetCardRail!!.title)
        }

        @Test
        fun `style preset card rail selected card matches persisted filter`() {
            val state = defaultSessionState(
                activeMode = ModeId.PHOTO,
                persistedPhotoSettings = PhotoSettings(defaultFilterProfileId = "photo-vivid")
            )
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            val selectedCards = model.stylePresetCardRail!!.cards.filter { it.isSelected }
            assertEquals(1, selectedCards.size, "Exactly one card should be selected")
            assertEquals("photo-vivid", selectedCards.first().profileId)
            assertNull(selectedCards.first().applyAction, "Selected card should have no apply action")
        }

        @Test
        fun `style preset card rail unselected cards have apply actions`() {
            val state = defaultSessionState(
                activeMode = ModeId.PHOTO,
                persistedPhotoSettings = PhotoSettings(defaultFilterProfileId = "photo-vivid")
            )
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            val unselectedCards = model.stylePresetCardRail!!.cards.filter { !it.isSelected }
            for (card in unselectedCards) {
                assertTrue(card.applyAction is PersistedSettingsAction.UpdatePhotoFilter,
                    "Unselected Photo card should have UpdatePhotoFilter action: ${card.profileId}")
            }
        }

        @Test
        fun `style preset card rail disables cards when device does not support capture`() {
            val state = defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities(
                    supportsStillCapture = false,
                    supportsVideoRecording = false
                )
            )
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            assertNotNull(model.stylePresetCardRail)
            assertFalse(model.stylePresetCardRail!!.isEnabled,
                "Rail should be disabled when capture is unsupported")
            for (card in model.stylePresetCardRail!!.cards) {
                assertFalse(card.isEnabled, "Card should be disabled: ${card.profileId}")
            }
        }

        @Test
        fun `style preset card rail is null for COLOR_LAB mode`() {
            val state = defaultSessionState()
            val model = filterLabPageRenderModel(
                state = state,
                text = TestAppTextResolver(),
                panelRole = StyleAndColorLabRole.COLOR_LAB
            )

            assertNull(model.stylePresetCardRail, "Card rail should be null in COLOR_LAB mode")
        }

        @Test
        fun `style preset card rail CHECK_IN mode uses PHOTO family presets`() {
            val state = defaultSessionState(activeMode = ModeId.CHECK_IN)
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            assertNotNull(model.stylePresetCardRail)
            assertEquals(FilterLabFamily.PHOTO, model.stylePresetCardRail!!.activeFamily,
                "CHECK_IN mode should map to PHOTO family for preset lookup")
            assertTrue(model.stylePresetCardRail!!.cards.isNotEmpty())
            val vividCard = model.stylePresetCardRail!!.cards.firstOrNull { it.profileId == "photo-vivid" }
            assertNotNull(vividCard, "CHECK_IN rail should contain photo-vivid")
        }

        @Test
        fun `style preset card rail cards have non-empty mood labels`() {
            val state = defaultSessionState(activeMode = ModeId.PHOTO)
            val model = filterLabPageRenderModel(state, TestAppTextResolver())

            for (card in model.stylePresetCardRail!!.cards) {
                assertTrue(card.moodLabel.isNotBlank(),
                    "Card moodLabel should not be blank: ${card.profileId} → ${card.moodLabel}")
                assertTrue(card.title.isNotBlank(),
                    "Card title should not be blank: ${card.profileId}")
            }
        }

        @Test
        fun `style preset card rail dimensions are well defined`() {
            assertTrue(StylePresetCardDimensions.CARD_WIDTH_DP > 0)
            assertTrue(StylePresetCardDimensions.CARD_HEIGHT_DP > 0)
            assertTrue(StylePresetCardDimensions.PREVIEW_HEIGHT_DP > 0)
            assertTrue(StylePresetCardDimensions.ITEM_SPACING_DP >= 0)
        }

        @Test
        fun `style preset card rail supporting text is localized by family`() {
            val photoState = defaultSessionState(activeMode = ModeId.PHOTO)
            val photoModel = filterLabPageRenderModel(photoState, TestAppTextResolver())
            assertTrue(photoModel.stylePresetCardRail!!.supportingText.contains("Tap"))

            val humanisticState = defaultSessionState(activeMode = ModeId.HUMANISTIC)
            val humanisticModel = filterLabPageRenderModel(
                humanisticState, TestAppTextResolver(),
                selectedFamily = FilterLabFamily.HUMANISTIC
            )
            assertTrue(humanisticModel.stylePresetCardRail!!.supportingText.contains("Street"))
        }

        // --- CheckInStylePanelRenderModel tests ---

        private fun defaultSessionState(
            activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
            activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewStatus: PreviewStatus = PreviewStatus.ACTIVE,
            previewMetrics: PreviewMetrics = PreviewMetrics(),
            activeShot: ShotRequest? = null,
            latestSavedMediaType: SavedMediaType? = null,
            latestCapturePath: String? = null,
            latestVideoPath: String? = null,
            latestLivePhotoBundle: LivePhotoBundle? = null,
            latestPipelineNotes: List<String> = emptyList(),
            lastError: String? = null,
            activeMode: ModeId = ModeId.PHOTO,
            availableModes: List<ModeId> = listOf(
                ModeId.PHOTO,
                ModeId.CHECK_IN,
                ModeId.HUMANISTIC,
                ModeId.VIDEO
            ),
            modeSnapshot: ModeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(
                    title = "PHOTO",
                    shutterLabel = "Capture PHOTO"
                ),
                state = ModeState(
                    headline = "PHOTO mode active",
                    detail = "Ready"
                )
            ),
            persistedPhotoSettings: PhotoSettings = PhotoSettings(
                defaultFilterProfileId = "portrait-retro",
                defaultHumanisticFilterProfileId = "humanistic-street",
                defaultPortraitFilterProfileId = "portrait-original",
                defaultWatermarkTemplateId = "travel-polaroid",
                livePhotoEnabledByDefault = true,
                countdownDuration = CountdownDuration.SECONDS_3
            )
        ): SessionState {
            return SessionState(
                lifecycle = SessionLifecycle.RUNNING,
                permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
                previewHostAvailable = true,
                previewStatus = previewStatus,
                previewStatusDetail = null,
                activeMode = activeMode,
                availableModes = availableModes,
                captureStatus = CaptureStatus.IDLE,
                recordingStatus = RecordingStatus.IDLE,
                activeShot = activeShot,
                modeSnapshot = modeSnapshot,
                activeDeviceCapabilities = activeDeviceCapabilities,
                activeDeviceGraph = activeDeviceGraph,
                previewMetrics = previewMetrics,
                settings = SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        common = CommonSettings(
                            gridMode = CompositionGridMode.RULE_OF_THIRDS,
                            shutterSoundEnabled = false,
                            selfieMirrorEnabled = true
                        ),
                        photo = persistedPhotoSettings,
                        video = VideoSettings(
                            defaultVideoSpec = VideoSpec(
                                resolution = VideoResolution.UHD_4K,
                                frameRate = VideoFrameRate.FPS_25,
                                dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                                audioProfile = AudioProfile.CONCERT
                            ),
                            defaultFilterProfileId = "photo-rich"
                        )
                    )
                ),
                presentation = SessionPresentationState(
                    lastAction = "Ready",
                    latestCapturePath = latestCapturePath,
                    latestVideoPath = latestVideoPath,
                    latestLivePhotoBundle = latestLivePhotoBundle,
                    latestSavedMediaType = latestSavedMediaType,
                    latestPipelineNotes = latestPipelineNotes,
                    lastError = lastError
                )
            )
        }

}
