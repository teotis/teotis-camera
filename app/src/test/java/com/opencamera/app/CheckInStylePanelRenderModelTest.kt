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
import com.opencamera.core.media.SaveRequest
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
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
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

class CheckInStylePanelRenderModelTest {


        @Test
        fun `checkInStylePanelRenderModel produces all four scenario cards`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                modeSnapshot = ModeSnapshot(
                    id = ModeId.CHECK_IN,
                    uiSpec = ModeUiSpec(title = "Check-in", shutterLabel = "Capture"),
                    state = ModeState(headline = "Check-in ready", detail = "Ready")
                ),
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait",
                    defaultFilterProfileId = "portrait-original",
                    defaultPortraitFilterProfileId = "portrait-original"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertEquals(4, model.scenarioCards.size)
            val ids = model.scenarioCards.map { it.scenarioId }
            assertTrue(ids.containsAll(listOf("portrait", "people-place", "object-place", "clarity")))
        }



        @Test
        fun `checkInStylePanelRenderModel marks active scenario`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "clarity",
                    defaultPortraitFilterProfileId = "portrait-blue"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            val activeCards = model.scenarioCards.filter { it.isActive }
            assertEquals(1, activeCards.size)
            assertEquals("clarity", activeCards.first().scenarioId)
        }



        @Test
        fun `checkInStylePanelRenderModel active scenario has no selectAction`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            val activeCard = model.scenarioCards.first { it.isActive }
            assertNull(activeCard.selectAction)
        }



        @Test
        fun `checkInStylePanelRenderModel non-active scenarios have selectActions`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            val inactiveCards = model.scenarioCards.filter { !it.isActive }
            for (card in inactiveCards) {
                assertNotNull(card.selectAction, "Inactive scenario ${card.scenarioId} should have selectAction")
            }
        }



        @Test
        fun `checkInStylePanelRenderModel shows style items from portrait catalog`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait",
                    defaultPortraitFilterProfileId = "portrait-original"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertTrue(model.styleItems.isNotEmpty())
            val selectedStyles = model.styleItems.filter { it.isSelected }
            assertEquals(1, selectedStyles.size)
        }



        @Test
        fun `checkInStylePanelRenderModel shows degradation when depth unsupported`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsPortraitDepthEffect = false
                ),
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertNotNull(model.degradationLabel)
            assertTrue(model.degradationLabel!!.contains("Focus"))
        }



        @Test
        fun `checkInStylePanelRenderModel no degradation when depth supported and not clarity`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsPortraitDepthEffect = true,
                    supportsNightMultiFrame = true
                ),
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertNull(model.degradationLabel)
        }



        @Test
        fun `checkInStylePanelRenderModel shows composition guidance`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "portrait"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertTrue(model.compositionGuidance.isNotEmpty())
            assertTrue(model.compositionGuidance.contains("subject"))
        }



        @Test
        fun `checkInStylePanelRenderModel headline is Check-in specific`() {
            val state = defaultSessionState(activeMode = ModeId.CHECK_IN)
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertEquals("Check-in Style", model.headline)
        }



        @Test
        fun `checkInStylePanelRenderModel disabled during active shot`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                activeShot = ShotRequest(
                    shotId = "test",
                    mediaType = MediaType.PHOTO,
                    shotKind = ShotKind.STILL_CAPTURE,
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "Pictures/test",
                        fileNamePrefix = "test"
                    ),
                    thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                    postProcessSpec = PostProcessSpec(),
                    captureProfile = CaptureProfile()
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            assertFalse(model.editingEnabled)
            // Active scenario should still have no selectAction even when editing disabled
            val activeCard = model.scenarioCards.first { it.isActive }
            assertNull(activeCard.selectAction)
        }



        @Test
        fun `checkInStylePanelRenderModel clarity degraded when night multi-frame unsupported`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsNightMultiFrame = false
                ),
                persistedPhotoSettings = PhotoSettings(
                    defaultCheckInScenario = "clarity"
                )
            )
            val model = checkInStylePanelRenderModel(state, TestAppTextResolver())

            val clarityCard = model.scenarioCards.first { it.scenarioId == "clarity" }
            assertTrue(clarityCard.isDegraded)
            assertNotNull(clarityCard.degradedLabel)
        }

        @Test
        fun `checkInStylePanelRenderModel uses localized focus label when depth is unsupported`() {
            val state = defaultSessionState(
                activeMode = ModeId.CHECK_IN,
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsPortraitDepthEffect = false
                ),
                persistedPhotoSettings = PhotoSettings(
                    defaultPortraitFilterProfileId = "portrait-original"
                )
            )
            val text = object : TestAppTextResolver() {
                internal override fun get(@StringRes resId: Int): String = when (resId) {
                    R.string.label_focus -> "对焦"
                    else -> super.get(resId)
                }
            }

            val model = checkInStylePanelRenderModel(state, text)

            assertTrue(model.styleItems.isNotEmpty())
            assertTrue(model.styleItems.all { it.bokehLabel == "对焦" })
        }

        private class ChineseUiTextResolver : TestAppTextResolver() {
            internal override fun get(@StringRes resId: Int): String = when (resId) {
                R.string.style_panel_title -> "风格"
                R.string.filter_family_photo -> "照片"
                R.string.status_current_default -> "当前默认"
                R.string.status_selected_default -> "当前默认"
                R.string.filter_lab_selected_default -> " | 当前默认"
                R.string.label_watermark_lab -> "水印"
                R.string.watermark_selector_supporting ->
                    "水印选择位于设置下一级。在此选择活跃模板，然后进入模板专属样式页面进行编辑。"
                R.string.watermark_selector_editing_enabled ->
                    "默认模板更改即时保存。每个模板保留自己的位置、大小、透明度和边框背景预设。"
                R.string.watermark_detail_editing_enabled ->
                    "模板专属样式即时保存，并保持与此水印预设关联。"
                R.string.watermark_detail_supporting_selected ->
                    "这是当前活跃的默认水印。此处更改将影响使用此模板渲染的下一张静态照片。"
                R.string.watermark_selector_current_default -> " | 当前默认"
                R.string.watermark_selector_edit_attrs_frame -> "位置、大小、透明度、背景"
                R.string.watermark_selector_edit_attrs_classic -> "位置、大小、透明度"
                R.string.watermark_template_classic_overlay -> "经典叠加"
                R.string.watermark_template_expanded_frame -> "扩展边框"
                R.string.watermark_template_pure_text -> "纯文字"
                R.string.watermark_template_blur_four_border -> "模糊四边框"
                R.string.watermark_template_travel_polaroid -> "旅行拍立得"
                R.string.watermark_template_retro_frame -> "复古边框"
                R.string.button_watermark_style_short -> "样式"
                R.string.label_tokens -> "标记"
                R.string.watermark_token_camera_params -> "相机参数"
                R.string.watermark_token_datetime -> "日期/时间"
                R.string.watermark_token_location -> "位置"
                R.string.watermark_token_model -> "机型"
                R.string.watermark_attr_placement_prefix -> "位置 "
                R.string.watermark_attr_scale_prefix -> "大小 "
                R.string.watermark_attr_opacity_prefix -> "透明度 "
                R.string.watermark_attr_background_prefix -> "背景 "
                R.string.label_text_placement -> "文字位置"
                R.string.label_text_scale -> "文字大小"
                R.string.label_text_opacity -> "文字透明度"
                R.string.label_background -> "边框背景"
                R.string.watermark_detail_tokens_prefix -> "标记："
                R.string.watermark_detail_footer_overlay ->
                    "经典叠加保持在源图像内，不扩展边框。"
                R.string.button_open_style_page -> "样式"
                else -> super.get(resId)
            }

            override fun filterLabCurrentDefault(filterLabel: String): String = "当前默认 $filterLabel"
            override fun watermarkPlacementLabel(value: com.opencamera.core.settings.WatermarkTextPlacement): String =
                when (value) {
                    com.opencamera.core.settings.WatermarkTextPlacement.TOP_LEFT -> "左上"
                    com.opencamera.core.settings.WatermarkTextPlacement.TOP_RIGHT -> "右上"
                    com.opencamera.core.settings.WatermarkTextPlacement.BOTTOM_LEFT -> "左下"
                    com.opencamera.core.settings.WatermarkTextPlacement.BOTTOM_RIGHT -> "右下"
                    com.opencamera.core.settings.WatermarkTextPlacement.BOTTOM_CENTER -> "底部居中"
                }
            override fun watermarkTextScaleLabel(value: com.opencamera.core.settings.WatermarkTextScale): String =
                when (value) {
                    com.opencamera.core.settings.WatermarkTextScale.COMPACT -> "紧凑"
                    com.opencamera.core.settings.WatermarkTextScale.NORMAL -> "正常"
                    com.opencamera.core.settings.WatermarkTextScale.LARGE -> "大"
                }
            override fun watermarkTextOpacityLabel(value: com.opencamera.core.settings.WatermarkTextOpacity): String =
                when (value) {
                    com.opencamera.core.settings.WatermarkTextOpacity.SUBTLE -> "淡"
                    com.opencamera.core.settings.WatermarkTextOpacity.SOFT -> "柔和"
                    com.opencamera.core.settings.WatermarkTextOpacity.SOLID -> "清晰"
                }
            override fun watermarkFrameBackgroundLabel(value: com.opencamera.core.settings.WatermarkFrameBackground): String =
                when (value) {
                    com.opencamera.core.settings.WatermarkFrameBackground.DARK -> "深色"
                    com.opencamera.core.settings.WatermarkFrameBackground.WHITE -> "白色"
                    com.opencamera.core.settings.WatermarkFrameBackground.SOURCE_BLUR -> "原图模糊"
                    com.opencamera.core.settings.WatermarkFrameBackground.SOURCE_LIGHT_BLUR -> "浅色模糊"
                    com.opencamera.core.settings.WatermarkFrameBackground.SOURCE_VIVID_BLUR -> "鲜明模糊"
                }
            override fun filterProfileLabel(profileId: String, fallback: String): String =
                when (profileId) {
                    "photo-vivid" -> "鲜明"
                    "photo-original" -> "原色"
                    else -> fallback
                }
            override fun placementsCount(count: Int): String = "$count 个位置"
            override fun stepsCount(count: Int): String = "$count 档"
            override fun moodsCount(count: Int): String = "$count 种背景"
        }

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
