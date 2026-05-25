package com.opencamera.app

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.opencamera.app.camera.AndroidDocumentAutoCropEditor
import com.opencamera.app.camera.AndroidPhotoFrameRatioEditor
import com.opencamera.app.camera.AndroidPhotoSelfieMirrorEditor
import com.opencamera.app.camera.AndroidPortraitRenderEditor
import com.opencamera.app.camera.AndroidPhotoWatermarkEditor
import com.opencamera.app.camera.AndroidPhotoAlgorithmEditor
import com.opencamera.app.camera.MlKitSelfiePreviewSceneMaskSource
import com.opencamera.app.camera.NoOpPreviewSceneMaskSource
import com.opencamera.app.camera.MlKitSavedPhotoSceneMaskProvider
import com.opencamera.app.camera.NoOpSavedPhotoSceneMaskProvider
import com.opencamera.app.camera.PreviewSceneMaskSource
import com.opencamera.app.camera.SavedPhotoSceneMaskProvider
import com.opencamera.app.camera.toSnapshot
import com.opencamera.app.camera.AndroidThermalRuntimeIssueMonitor
import com.opencamera.app.camera.CameraSessionCoordinator
import com.opencamera.app.camera.CameraXCaptureAdapter
import com.opencamera.app.camera.PreviewSceneBrightnessMonitor
import com.opencamera.app.camera.toSignalSource
import com.opencamera.app.camera.live.CameraXLivePreviewFrameSource
import com.opencamera.app.camera.CompositeRuntimeIssueMonitor
import com.opencamera.app.camera.DocumentAutoCropPostProcessor
import com.opencamera.app.camera.PhotoFrameRatioPostProcessor
import com.opencamera.app.camera.PhotoSelfieMirrorPostProcessor
import com.opencamera.app.camera.PortraitRenderPostProcessor
import com.opencamera.app.camera.PhotoAlgorithmPostProcessor
import com.opencamera.app.camera.PhotoWatermarkPostProcessor
import com.opencamera.app.camera.PreviewStartupRuntimeIssueMonitor
import com.opencamera.app.camera.device.CameraDeviceAdapter
import com.opencamera.core.capability.CapabilityGraphResolver
import com.opencamera.core.device.asCapabilityGraphQuery
import com.opencamera.core.effect.EffectCapabilityResolver
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.effect.PreviewSceneMaskSnapshot
import com.opencamera.core.media.MediaProcessorAvailability
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
    private val savedMaskProvider: SavedPhotoSceneMaskProvider = try {
        MlKitSavedPhotoSceneMaskProvider()
    } catch (_: Throwable) {
        NoOpSavedPhotoSceneMaskProvider()
    }

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
                AndroidPortraitRenderEditor(appContext),
                savedMaskProvider,
                maskBitmapSource = { target ->
                    when (target) {
                        is com.opencamera.core.media.ProcessorTarget.FilePath ->
                            BitmapFactory.decodeFile(target.path)
                        is com.opencamera.core.media.ProcessorTarget.ContentUri ->
                            appContext.contentResolver.openInputStream(Uri.parse(target.value))?.use {
                                BitmapFactory.decodeStream(it)
                            }
                    }
                }
            ),
            PhotoAlgorithmPostProcessor(
                AndroidPhotoAlgorithmEditor(appContext),
                savedMaskProvider,
                maskBitmapSource = { target ->
                    when (target) {
                        is com.opencamera.core.media.ProcessorTarget.FilePath ->
                            BitmapFactory.decodeFile(target.path)
                        is com.opencamera.core.media.ProcessorTarget.ContentUri ->
                            appContext.contentResolver.openInputStream(Uri.parse(target.value))?.use {
                                BitmapFactory.decodeStream(it)
                            }
                    }
                }
            ),
            PhotoWatermarkPostProcessor(
                AndroidPhotoWatermarkEditor(appContext)
            ),
            PhotoSelfieMirrorPostProcessor(
                AndroidPhotoSelfieMirrorEditor(appContext)
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

    private val livePreviewFrameSource = CameraXLivePreviewFrameSource()

    private val sceneMaskSource: PreviewSceneMaskSource = try {
        MlKitSelfiePreviewSceneMaskSource()
    } catch (_: Throwable) {
        NoOpPreviewSceneMaskSource()
    }

    private val cameraAdapter: CameraDeviceAdapter = CameraXCaptureAdapter(
        context = appContext,
        shotExecutor = shotExecutor,
        mediaPostProcessor = mediaPostProcessor,
        livePreviewFrameSource = livePreviewFrameSource
    )

    val effectCapabilityResolver = EffectCapabilityResolver(
        cameraAdapter.capabilities.asEffectCapabilityQuery()
    )
    val capabilityGraphResolver = CapabilityGraphResolver(
        deviceQuery = cameraAdapter.capabilities.asCapabilityGraphQuery(),
        mediaProcessors = MediaProcessorAvailability.ALL_AVAILABLE
    )
    val previewEffectAdapter = PreviewEffectAdapter()

    val previewMaskSnapshot: PreviewSceneMaskSnapshot
        get() = sceneMaskSource.latestMask().toSnapshot()

    val cameraSession: CameraSession = DefaultCameraSession(
        registry = modeRegistry,
        trace = trace,
        baseDeviceCapabilities = cameraAdapter.capabilities,
        scope = applicationScope,
        settingsSnapshot = initialSettingsSnapshot,
        shotExecutor = shotExecutor,
        effectCapabilityResolver = effectCapabilityResolver,
        capabilityGraphResolver = capabilityGraphResolver
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
        ),
        sceneBrightnessSource = PreviewSceneBrightnessMonitor(applicationScope).toSignalSource()
    )
}
