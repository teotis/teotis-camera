package com.opencamera.app

import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.HorizontalScrollView
import androidx.camera.view.PreviewView
import androidx.core.widget.NestedScrollView
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.RecordingStatus
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CockpitSurfaceRendererTest {

    @Test
    fun `renderPreviewMirror applies scaleX=-1 only for front camera with selfie mirror enabled`() {
        val previewView = PreviewView(org.robolectric.RuntimeEnvironment.getApplication())
        val renderer = createRenderer(previewView)

        val state = sessionState(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = true
        )
        renderer.renderPreviewMirror(state)

        assertEquals(-1f, previewView.scaleX, "Front camera + selfie mirror should set scaleX to -1")
    }

    @Test
    fun `renderPreviewMirror does not modify transform for rear camera`() {
        val previewView = PreviewView(org.robolectric.RuntimeEnvironment.getApplication())
        previewView.scaleX = 1f
        val renderer = createRenderer(previewView)

        val state = sessionState(
            lensFacing = LensFacing.BACK,
            selfieMirrorEnabled = true
        )
        renderer.renderPreviewMirror(state)

        assertEquals(1f, previewView.scaleX, "Rear camera should keep scaleX at 1")
    }

    @Test
    fun `renderPreviewMirror does not modify transform for front camera with selfie mirror disabled`() {
        val previewView = PreviewView(org.robolectric.RuntimeEnvironment.getApplication())
        previewView.scaleX = 1f
        val renderer = createRenderer(previewView)

        val state = sessionState(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = false
        )
        renderer.renderPreviewMirror(state)

        assertEquals(1f, previewView.scaleX, "Front camera + mirror disabled should keep scaleX at 1")
    }

    @Test
    fun `renderPreviewMirror does not stack transforms across multiple calls`() {
        val previewView = PreviewView(org.robolectric.RuntimeEnvironment.getApplication())
        val renderer = createRenderer(previewView)

        val frontMirrorState = sessionState(
            lensFacing = LensFacing.FRONT,
            selfieMirrorEnabled = true
        )
        renderer.renderPreviewMirror(frontMirrorState)
        assertEquals(-1f, previewView.scaleX)

        // Call again with same state - should still be -1, not stacked
        renderer.renderPreviewMirror(frontMirrorState)
        assertEquals(-1f, previewView.scaleX, "Multiple calls should not stack scaleX")

        // Switch to rear - should reset to 1
        val rearState = sessionState(
            lensFacing = LensFacing.BACK,
            selfieMirrorEnabled = true
        )
        renderer.renderPreviewMirror(rearState)
        assertEquals(1f, previewView.scaleX, "Rear camera should reset scaleX to 1")
    }

    @Test
    fun `renderPreviewMirror with identical state skips redundant update`() {
        val previewView = PreviewView(org.robolectric.RuntimeEnvironment.getApplication())
        val renderer = createRenderer(previewView)

        val state = sessionState(lensFacing = LensFacing.FRONT, selfieMirrorEnabled = true)
        renderer.renderPreviewMirror(state)
        assertEquals(-1f, previewView.scaleX)

        // Second call with same state should be a no-op (idempotent)
        renderer.renderPreviewMirror(state)
        assertEquals(-1f, previewView.scaleX)
    }

    private fun sessionState(
        lensFacing: LensFacing = LensFacing.BACK,
        selfieMirrorEnabled: Boolean = true
    ): SessionState = SessionState(
        lifecycle = SessionLifecycle.RUNNING,
        permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
        previewHostAvailable = true,
        previewStatus = PreviewStatus.ACTIVE,
        previewStatusDetail = null,
        activeMode = com.opencamera.core.mode.ModeId.PHOTO,
        availableModes = listOf(com.opencamera.core.mode.ModeId.PHOTO),
        captureStatus = CaptureStatus.IDLE,
        recordingStatus = RecordingStatus.IDLE,
        activeShot = null,
        modeSnapshot = ModeSnapshot(
            id = com.opencamera.core.mode.ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture"),
            state = ModeState(headline = "Ready", detail = "")
        ),
        activeDeviceCapabilities = com.opencamera.core.device.DeviceCapabilities.DEFAULT,
        activeDeviceGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = lensFacing, enablePreviewSnapshots = true
        ),
        previewMetrics = PreviewMetrics(),
        settings = SessionSettingsSnapshot(
            persisted = PersistedSettings(
                common = CommonSettings(
                    selfieMirrorEnabled = selfieMirrorEnabled,
                    gridMode = com.opencamera.core.settings.CompositionGridMode.OFF,
                    shutterSoundEnabled = false
                )
            )
        ),
        presentation = SessionPresentationState()
    )

    private fun createRenderer(previewView: PreviewView): CockpitSurfaceRenderer {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        return CockpitSurfaceRenderer(
            context = context,
            topBar = TopBarViews(
                titleText = allocateInstance(TextView::class.java),
                permissionStatus = allocateInstance(TextView::class.java),
                colorLabEntry = allocateInstance(Button::class.java),
                settingsEntry = allocateInstance(Button::class.java),
                filterEntry = allocateInstance(Button::class.java)
            ),
            quickPanel = QuickPanelViews(
                panel = allocateInstance(NestedScrollView::class.java),
                content = allocateInstance(LinearLayout::class.java),
                grid = allocateInstance(Button::class.java),
                resolution = allocateInstance(Button::class.java),
                brightnessSlider = allocateInstance(SeekBar::class.java),
                brightnessValueText = allocateInstance(TextView::class.java),
                frameRatio = allocateInstance(Button::class.java),
                watermark = allocateInstance(Button::class.java),
                livePhoto = allocateInstance(Button::class.java),
                timer = allocateInstance(Button::class.java),
                launcher = allocateInstance(Button::class.java),
                resetDefaults = allocateInstance(Button::class.java)
            ),
            floatingUtility = FloatingUtilityViews(
                quickLauncher = allocateInstance(Button::class.java),
                lowLightNightPrompt = allocateInstance(Button::class.java)
            ),
            bottomCockpit = BottomCockpitViews(
                shutter = allocateInstance(Button::class.java),
                lensFacing = allocateInstance(Button::class.java),
                focalLengthSlider = allocateInstance(FocalLengthSliderView::class.java),
                recordingIndicator = allocateInstance(TextView::class.java),
                stylePresetCardRail = allocateInstance(StylePresetCardRailView::class.java)
            ),
            modeTrack = ModeTrackViews(
                scroll = allocateInstance(HorizontalScrollView::class.java),
                photo = allocateInstance(Button::class.java),
                checkIn = allocateInstance(Button::class.java),
                video = allocateInstance(Button::class.java),
                document = allocateInstance(Button::class.java),
                humanistic = allocateInstance(Button::class.java),
                modeAction = allocateInstance(Button::class.java)
            ),
            filterStrip = FilterStripViews(
                scroll = allocateInstance(HorizontalScrollView::class.java),
                chips = allocateInstance(LinearLayout::class.java)
            ),
            preview = PreviewViews(
                previewView = previewView,
                overlayView = allocateInstance(PreviewOverlayView::class.java),
                thumbnail = allocateInstance(ImageView::class.java),
                captureOutput = allocateInstance(TextView::class.java)
            ),
            callbacks = CockpitCallbacks(
                onZoomRatioSelected = {},
                onZoomRatioChanged = null
            )
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
