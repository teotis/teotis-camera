package com.opencamera.app.camera

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.impl.Camera2ImplConfig
import androidx.camera.core.Preview
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.media.ShotKind
import com.opencamera.core.settings.ManualCaptureParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraXCaptureAdapterManualRequestTest {
    @Test
    fun `manual ISO and shutter are installed on the preview use case`() {
        val builder = Preview.Builder()

        applyCamera2ManualPreviewConfig(
            builder,
            Camera2ManualCaptureConfig(
                iso = 800,
                shutterTimeNanos = 33_000_000L
            )
        )

        val camera2Config = Camera2ImplConfig(builder.useCaseConfig)
        assertEquals(
            CaptureRequest.CONTROL_AE_MODE_OFF,
            camera2Config.getCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE)
        )
        assertEquals(800, camera2Config.getCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY))
        assertEquals(
            33_000_000L,
            camera2Config.getCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME)
        )
    }

    @Test
    fun `manual request maps camera2 supported fields into adapter config`() {
        val config = resolveCamera2ManualCaptureConfig(
            DeviceShotRequest(
                shotId = "shot-1",
                template = CaptureTemplate.STILL_CAPTURE,
                shotKind = ShotKind.STILL_CAPTURE,
                manualCaptureParams = ManualCaptureParams(
                    rawEnabled = true,
                    iso = 320,
                    shutterSpeedMillis = 33L,
                    exposureCompensationSteps = 2,
                    focusDistanceDiopters = 1.5f,
                    apertureFNumber = 1.8f,
                    whiteBalanceKelvin = 4800
                )
            )
        )

        assertEquals(false, config?.rawEnabled)
        assertEquals(320, config?.iso)
        assertEquals(33_000_000L, config?.shutterTimeNanos)
        assertEquals(2, config?.exposureCompensationSteps)
        assertEquals(1.5f, config?.focusDistanceDiopters)
        assertEquals(1.8f, config?.apertureFNumber)
        assertEquals(4800, config?.whiteBalanceKelvin)
    }

    @Test
    fun `adapter manual diagnostics distinguish applied and saved only fields`() {
        val diagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = ManualCaptureParams(
                rawEnabled = true,
                iso = 200,
                shutterSpeedMillis = 20L,
                exposureCompensationSteps = 1,
                focusDistanceDiopters = 2.0f,
                apertureFNumber = 2.2f,
                whiteBalanceKelvin = 4200
            ),
            capabilityMatrix = ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT
        )

        assertTrue("adapter:manual-request=partial" in diagnostics)
        assertTrue("adapter:manual-applied=iso+shutter+ev+focus+aperture+wb" in diagnostics)
        assertTrue("adapter:manual-saved-only=raw" in diagnostics)
    }

    @Test
    fun `adapter manual diagnostics mark raw only draft as saved only`() {
        val diagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = ManualCaptureParams(
                rawEnabled = true
            ),
            capabilityMatrix = ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
        )

        assertEquals(
            listOf(
                "adapter:manual-request=saved-only",
                "adapter:manual-saved-only=raw"
            ),
            diagnostics
        )
    }

    @Test
    fun `explicit unsupported controls stay out of config and surface unsupported diagnostics`() {
        val request = DeviceShotRequest(
            shotId = "shot-unsupported",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.STILL_CAPTURE,
            manualCaptureParams = ManualCaptureParams(
                iso = 100,
                whiteBalanceKelvin = 5000
            ),
            manualControlCapabilities = ManualControlCapabilityMatrix(
                raw = ManualControlSupport.SAVED_ONLY,
                iso = ManualControlSupport.UNSUPPORTED,
                shutter = ManualControlSupport.APPLY,
                exposureCompensation = ManualControlSupport.APPLY,
                focusDistance = ManualControlSupport.APPLY,
                aperture = ManualControlSupport.APPLY,
                whiteBalance = ManualControlSupport.UNSUPPORTED
            )
        )

        val config = resolveCamera2ManualCaptureConfig(request)
        val diagnostics = manualCaptureExecutionDiagnostics(
            requestedParams = request.manualCaptureParams,
            capabilityMatrix = request.manualControlCapabilities
        )

        assertNull(config)
        assertTrue("adapter:manual-request=unsupported" in diagnostics)
        assertTrue("adapter:manual-unsupported=iso+wb" in diagnostics)
    }
}
