package com.opencamera.app

import android.content.Context
import com.opencamera.app.camera.AndroidDocumentAutoCropEditor
import com.opencamera.app.camera.AndroidPhotoFrameRatioEditor
import com.opencamera.app.camera.AndroidPhotoSelfieMirrorEditor
import com.opencamera.app.camera.AndroidPortraitRenderEditor
import com.opencamera.app.camera.AndroidPhotoWatermarkEditor
import com.opencamera.app.camera.AndroidPhotoAlgorithmEditor
import com.opencamera.app.camera.AndroidVideoWatermarkSubtitleEditor
import com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitor
import com.opencamera.app.camera.CameraSessionCoordinator
import com.opencamera.app.camera.CameraXCaptureAdapter
import com.opencamera.app.camera.CompositeRuntimeIssueMonitor
import com.opencamera.app.camera.DocumentAutoCropPostProcessor
import com.opencamera.app.camera.PhotoFrameRatioPostProcessor
import com.opencamera.app.camera.PhotoSelfieMirrorPostProcessor
import com.opencamera.app.camera.PortraitRenderPostProcessor
import com.opencamera.app.camera.PhotoAlgorithmPostProcessor
import com.opencamera.app.camera.PhotoWatermarkPostProcessor
import com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitor
import com.opencamera.app.camera.VideoWatermarkSubtitlePostProcessor
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.media.CompositeMediaPostProcessor
import com.opencamera.core.media.MultiFrameMergePlaceholderPostProcessor
import com.opencamera.core.media.PipelineMetadataPostProcessor
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.session.CameraSession
import com.opencamera.core.session.DefaultCameraSession
import com.opencamera.core.session.InMemorySessionTrace
import com.opencamera.feature.document.DocumentModePlugin
import com.opencamera.feature.humanistic.HumanisticModePlugin
import com.opencamera.feature.night.NightModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import com.opencamera.feature.portrait.PortraitModePlugin
import com.opencamera.feature.pro.ProModePlugin
import com.opencamera.feature.video.VideoModePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appContext = context.applicationContext
    private val settingsStore = SharedPreferencesPersistedSettingsStore(appContext)
    private val featureCatalogStore = SharedPreferencesFeatureCatalogStore(appContext)
    private val initialSettingsSnapshot = SessionSettingsSnapshot(
        persisted = settingsStore.load(),
        catalog = featureCatalogStore.load()
    )

    val trace = InMemorySessionTrace()
    private val shotExecutor = ShotExecutor()
    private val mediaPostProcessor = CompositeMediaPostProcessor(
        listOf(
            MultiFrameMergePlaceholderPostProcessor(),
            DocumentAutoCropPostProcessor(
                AndroidDocumentAutoCropEditor(appContext)
            ),
            PhotoFrameRatioPostProcessor(
                AndroidPhotoFrameRatioEditor(appContext)
            ),
            PortraitRenderPostProcessor(
                AndroidPortraitRenderEditor(appContext)
            ),
            PhotoAlgorithmPostProcessor(
                AndroidPhotoAlgorithmEditor(appContext)
            ),
            PhotoWatermarkPostProcessor(
                AndroidPhotoWatermarkEditor(appContext)
            ),
            PhotoSelfieMirrorPostProcessor(
                AndroidPhotoSelfieMirrorEditor(appContext)
            ),
            VideoWatermarkSubtitlePostProcessor(
                AndroidVideoWatermarkSubtitleEditor(appContext)
            ),
            PipelineMetadataPostProcessor()
        )
    )

    private val modeRegistry = ModeRegistry(
        listOf(
            DocumentModePlugin(),
            HumanisticModePlugin(),
            NightModePlugin(),
            PhotoModePlugin(),
            PortraitModePlugin(),
            ProModePlugin(),
            VideoModePlugin()
        )
    )

    private val cameraAdapter: CameraDeviceAdapter = CameraXCaptureAdapter(
        context = appContext,
        shotExecutor = shotExecutor,
        mediaPostProcessor = mediaPostProcessor
    )

    val cameraSession: CameraSession = DefaultCameraSession(
        registry = modeRegistry,
        trace = trace,
        baseDeviceCapabilities = cameraAdapter.capabilities,
        scope = applicationScope,
        settingsSnapshot = initialSettingsSnapshot,
        shotExecutor = shotExecutor
    )

    val sessionSettingsManager = SessionSettingsManager(
        session = cameraSession,
        store = settingsStore,
        catalogStore = featureCatalogStore
    )

    val cameraCoordinator = CameraSessionCoordinator(
        session = cameraSession,
        cameraAdapter = cameraAdapter,
        scope = applicationScope,
        runtimeIssueMonitor = CompositeRuntimeIssueMonitor(
            AndroidThermalRuntimeIssueMonitor(appContext),
            PreviewStartupRuntimeIssueMonitor(applicationScope)
        )
    )
}
