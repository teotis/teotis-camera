/**
 * SettingsPanelRenderer 单元测试
 *
 * 覆盖行为:
 * - renderPage 对 photoLive / photoTimer / liveSaveFormat 调用 renderControl 并写入 label / value
 * - 当 isInteractive == true 时，container 设置 click listener 且 isClickable == true
 * - 当 isInteractive == false 时，container.isClickable == false 且 statusText 显示在 settingsItemStatus
 * - editingEnabled == false 时，所有控件 isClickable == false
 *
 * 不适合单测的行为:
 * - 真实 binder 注册的 listener 是否触发回调 —— 由人工 / 真机验证
 */
package com.opencamera.app

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.PersistedSettingsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsPanelRendererTest {

    private lateinit var context: Context
    private lateinit var views: SettingsPanelViews
    private var lastAppliedAction: PersistedSettingsAction? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        views = buildSettingsPanelViews(context)
        lastAppliedAction = null
    }

    @Test
    fun `renderPage populates photoLive label and value when supported`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        renderer.renderPage(model)

        val liveContainer = views.photoLive
        assertEquals(
            "Live photo default",
            liveContainer.findViewById<TextView>(R.id.settingsItemTitle)?.text?.toString()
        )
        assertEquals(
            "On",
            liveContainer.findViewById<TextView>(R.id.settingsItemValue)?.text?.toString()
        )
        assertTrue(liveContainer.isClickable)
        assertTrue(liveContainer.hasOnClickListeners())
    }

    @Test
    fun `renderPage clears photoLive click listener when still capture is unsupported`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsStillCapture = false
                )
            ),
            TestAppTextResolver()
        )

        renderer.renderPage(model)

        val liveContainer = views.photoLive
        assertFalse(liveContainer.isClickable)
        val status = liveContainer.findViewById<TextView>(R.id.settingsItemStatus)
        assertNotNull(status)
        assertTrue((status?.text?.toString() ?: "").isNotEmpty())
    }

    @Test
    fun `renderPage populates photoTimer label and value and click listener when supported`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        renderer.renderPage(model)

        val timerContainer = views.photoTimer
        assertEquals(
            "Countdown",
            timerContainer.findViewById<TextView>(R.id.settingsItemTitle)?.text?.toString()
        )
        assertEquals(
            "3s",
            timerContainer.findViewById<TextView>(R.id.settingsItemValue)?.text?.toString()
        )
        assertTrue(timerContainer.isClickable)
        assertTrue(timerContainer.hasOnClickListeners())
    }

    @Test
    fun `renderPage clears photoTimer click listener when still capture is unsupported`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsStillCapture = false
                )
            ),
            TestAppTextResolver()
        )

        renderer.renderPage(model)

        val timerContainer = views.photoTimer
        assertFalse(timerContainer.isClickable)
        val status = timerContainer.findViewById<TextView>(R.id.settingsItemStatus)
        assertTrue((status?.text?.toString() ?: "").isNotEmpty())
    }

    @Test
    fun `photoLive click listener dispatches UpdateLivePhotoDefault action`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        renderer.renderPage(model)

        views.photoLive.performClick()

        val action = lastAppliedAction
        assertNotNull("photoLive click must dispatch an action", action)
        assertTrue(action is PersistedSettingsAction.UpdateLivePhotoDefault)
        assertEquals(false, (action as PersistedSettingsAction.UpdateLivePhotoDefault).enabled)
    }

    @Test
    fun `photoTimer click listener dispatches UpdateCountdownDuration action`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        renderer.renderPage(model)

        views.photoTimer.performClick()

        val action = lastAppliedAction
        assertNotNull("photoTimer click must dispatch an action", action)
        assertTrue(action is PersistedSettingsAction.UpdateCountdownDuration)
    }

    @Test
    fun `liveSaveFormat baseline remains interactive after renderPage`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        renderer.renderPage(model)

        val container = views.photoLiveSaveFormat
        assertTrue(container.isClickable)
        assertTrue(container.hasOnClickListeners())
    }

    @Test
    fun `renderPage disables all photo section controls when editingEnabled is false`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val state = defaultSessionState(
            activeShot = com.opencamera.core.media.ShotRequest(
                shotId = "shot-1",
                shotKind = com.opencamera.core.media.ShotKind.STILL_CAPTURE,
                mediaType = com.opencamera.core.media.MediaType.PHOTO,
                saveRequest = com.opencamera.core.media.SaveRequest.photoLibrary(),
                thumbnailPolicy = com.opencamera.core.media.ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
                captureProfile = com.opencamera.core.media.CaptureProfile()
            )
        )
        val model = sessionSettingsPageRenderModel(state, TestAppTextResolver())

        renderer.renderPage(model)

        assertFalse(views.photoLive.isClickable)
        assertFalse(views.photoTimer.isClickable)
        assertFalse(views.photoLiveSaveFormat.isClickable)
    }

    @Test
    fun `Activity-owned portrait lab stays non-interactive but visible`() {
        val renderer = SettingsPanelRenderer(
            context = context,
            views = views,
            onApplySettingsAction = { lastAppliedAction = it }
        )
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        renderer.renderPage(model)

        // portraitLab 由 Activity 单独接线，renderer 不应注入 nextAction listener
        val portraitContainer = views.photoPortraitLab
        assertFalse(portraitContainer.isClickable)
        // 但仍要展示非空 statusText（"部分支持"）
        val status = portraitContainer.findViewById<TextView>(R.id.settingsItemStatus)
        assertTrue((status?.text?.toString() ?: "").isNotEmpty())
    }

    private fun buildSettingsPanelViews(context: Context): SettingsPanelViews {
        fun itemRow(): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val leftColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { setId(R.id.settingsItemTitle) })
                addView(TextView(context).apply { setId(R.id.settingsItemSupport) })
            }
            val rightColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { setId(R.id.settingsItemValue) })
                addView(TextView(context).apply { setId(R.id.settingsItemStatus) })
            }
            addView(leftColumn)
            addView(rightColumn)
        }
        fun text(id: Int): TextView = TextView(context).apply { setId(id) }
        return SettingsPanelViews(
            panel = androidx.core.widget.NestedScrollView(context),
            close = android.widget.Button(context),
            back = android.widget.Button(context),
            rootContent = LinearLayout(context),
            portraitLabContent = LinearLayout(context),
            watermarkSelectorContent = LinearLayout(context),
            watermarkDetailContent = LinearLayout(context),
            headline = text(R.id.settingsHeadline),
            supportingText = text(R.id.settingsSupportingText),
            heroSummary = text(R.id.settingsHeroSummary),
            commonSummary = text(R.id.settingsCommonSummary),
            photoSummary = text(R.id.settingsPhotoSummary),
            videoSummary = text(R.id.settingsVideoSummary),
            editingHint = text(R.id.settingsEditingHint),
            tabCommon = android.widget.Button(context),
            tabPhoto = android.widget.Button(context),
            tabVideo = android.widget.Button(context),
            commonSection = LinearLayout(context),
            photoSection = LinearLayout(context),
            videoSection = LinearLayout(context),
            shutterSound = itemRow(),
            selfieMirror = itemRow(),
            appLanguage = itemRow(),
            photoFilter = itemRow(),
            photoPortraitLab = itemRow(),
            photoWatermark = itemRow(),
            photoLive = itemRow(),
            photoLiveSaveFormat = itemRow(),
            photoTimer = itemRow(),
            videoResolution = itemRow(),
            videoFrameRate = itemRow(),
            videoDynamicFps = itemRow(),
            videoAudio = itemRow(),
            videoFilter = itemRow(),
            portraitHeadline = text(R.id.portraitLabHeadline),
            portraitSupportingText = text(R.id.portraitLabSupportingText),
            portraitHeroSummary = text(R.id.portraitLabHeroSummary),
            portraitEditingHint = text(R.id.portraitLabEditingHint),
            portraitProfile = android.widget.Button(context),
            portraitBeautyPreset = android.widget.Button(context),
            portraitBeautyStrength = android.widget.Button(context),
            portraitBokehEffect = android.widget.Button(context),
            portraitDepthStrengthSeekBar = android.widget.SeekBar(context),
            portraitDepthStrengthValue = text(R.id.portraitDepthStrengthValue),
            portraitFooter = text(R.id.portraitLabFooter),
            watermarkSelectorHeadline = text(R.id.watermarkSelectorHeadline),
            watermarkSelectorSupportingText = text(R.id.watermarkSelectorSupportingText),
            watermarkSelectorHeroSummary = text(R.id.watermarkSelectorHeroSummary),
            watermarkSelectorList = LinearLayout(context),
            watermarkSelectorEditingHint = text(R.id.watermarkSelectorEditingHint),
            watermarkSelectorFooter = text(R.id.watermarkSelectorFooter),
            watermarkDetailHeadline = text(R.id.watermarkDetailHeadline),
            watermarkDetailSupportingText = text(R.id.watermarkDetailSupportingText),
            watermarkDetailHeroSummary = text(R.id.watermarkDetailHeroSummary),
            watermarkDetailEditingHint = text(R.id.watermarkDetailEditingHint),
            watermarkPlacement = android.widget.Button(context),
            watermarkTextScale = android.widget.Button(context),
            watermarkTextOpacity = android.widget.Button(context),
            watermarkFrameBackground = android.widget.Button(context),
            watermarkDetailFooter = text(R.id.watermarkDetailFooter),
            resetDefaults = android.widget.Button(context)
        )
    }

    private fun defaultSessionState(
        activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeShot: com.opencamera.core.media.ShotRequest? = null
    ): SessionState {
        return SessionState(
            lifecycle = com.opencamera.core.session.SessionLifecycle.RUNNING,
            permissionState = com.opencamera.core.session.PermissionState(
                cameraGranted = true,
                microphoneGranted = true
            ),
            previewHostAvailable = true,
            previewStatus = com.opencamera.core.session.PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = com.opencamera.core.mode.ModeId.PHOTO,
            availableModes = listOf(
                com.opencamera.core.mode.ModeId.PHOTO,
                com.opencamera.core.mode.ModeId.CHECK_IN,
                com.opencamera.core.mode.ModeId.HUMANISTIC,
                com.opencamera.core.mode.ModeId.VIDEO
            ),
            captureStatus = com.opencamera.core.session.CaptureStatus.IDLE,
            recordingStatus = com.opencamera.core.session.RecordingStatus.IDLE,
            activeShot = activeShot,
            modeSnapshot = com.opencamera.core.mode.ModeSnapshot(
                id = com.opencamera.core.mode.ModeId.PHOTO,
                uiSpec = com.opencamera.core.mode.ModeUiSpec(
                    title = "PHOTO",
                    shutterLabel = "Capture PHOTO"
                ),
                state = com.opencamera.core.mode.ModeState(
                    headline = "PHOTO mode active",
                    detail = "Ready"
                )
            ),
            activeDeviceCapabilities = activeDeviceCapabilities,
            activeDeviceGraph = com.opencamera.core.device.DeviceGraphSpec.stillCapture(
                preferredLensFacing = com.opencamera.core.device.LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = com.opencamera.core.session.PreviewMetrics(),
            settings = com.opencamera.core.settings.SessionSettingsSnapshot(
                persisted = com.opencamera.core.settings.PersistedSettings(
                    common = com.opencamera.core.settings.CommonSettings(
                        gridMode = com.opencamera.core.settings.CompositionGridMode.RULE_OF_THIRDS,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    ),
                    photo = com.opencamera.core.settings.PhotoSettings(
                        defaultFilterProfileId = "portrait-retro",
                        defaultHumanisticFilterProfileId = "humanistic-street",
                        defaultPortraitFilterProfileId = "portrait-original",
                        defaultWatermarkTemplateId = "travel-polaroid",
                        livePhotoEnabledByDefault = true,
                        countdownDuration = com.opencamera.core.settings.CountdownDuration.SECONDS_3
                    ),
                    video = com.opencamera.core.settings.VideoSettings(
                        defaultVideoSpec = com.opencamera.core.settings.VideoSpec(
                            resolution = com.opencamera.core.settings.VideoResolution.UHD_4K,
                            frameRate = com.opencamera.core.settings.VideoFrameRate.FPS_25,
                            dynamicFpsPolicy = com.opencamera.core.settings.DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                            audioProfile = com.opencamera.core.settings.AudioProfile.CONCERT
                        ),
                        defaultFilterProfileId = "photo-rich"
                    )
                )
            ),
            presentation = com.opencamera.core.session.SessionPresentationState(
                lastAction = "Ready",
                latestCapturePath = null,
                latestVideoPath = null,
                latestLivePhotoBundle = null,
                latestSavedMediaType = null,
                latestPipelineNotes = emptyList(),
                lastError = null
            )
        )
    }
}
