package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.effect.EffectCapabilityQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 覆盖行为:
 * - supportsPortraitDepth() 委托到 capabilities.supportsPortraitDepthEffect
 * - supportsDocumentGeometry() 委托到 capabilities.supportsDocumentScanEnhancement
 * - supportsManualControls() 委托到 capabilities.supportsAppliedManualControls
 * - asEffectCapabilityQuery() 扩展函数返回正确类型
 *
 * 不适合单测的行为: 无
 */
class DeviceCapabilitiesEffectQueryTest {

    @Test
    fun `supportsPortraitDepth delegates to capabilities`() {
        val caps = DeviceCapabilities(supportsPortraitDepthEffect = true)
        val query = DeviceCapabilitiesEffectQuery(caps)
        assertTrue(query.supportsPortraitDepth())
    }

    @Test
    fun `supportsPortraitDepth returns false when capability is false`() {
        val caps = DeviceCapabilities(supportsPortraitDepthEffect = false)
        val query = DeviceCapabilitiesEffectQuery(caps)
        assertFalse(query.supportsPortraitDepth())
    }

    @Test
    fun `supportsDocumentGeometry delegates to capabilities`() {
        val caps = DeviceCapabilities(supportsDocumentScanEnhancement = true)
        val query = DeviceCapabilitiesEffectQuery(caps)
        assertTrue(query.supportsDocumentGeometry())
    }

    @Test
    fun `supportsDocumentGeometry returns false when capability is false`() {
        val caps = DeviceCapabilities(supportsDocumentScanEnhancement = false)
        val query = DeviceCapabilitiesEffectQuery(caps)
        assertFalse(query.supportsDocumentGeometry())
    }

    @Test
    fun `supportsManualControls delegates to supportsAppliedManualControls`() {
        // supportsAppliedManualControls depends on manualControlCapabilities having applied controls
        val caps = DeviceCapabilities(
            supportsManualControls = true,
            manualControlCapabilities = com.opencamera.core.device.ManualControlCapabilityMatrix.CAMERA2_INTEROP_DEFAULT
        )
        val query = DeviceCapabilitiesEffectQuery(caps)
        assertTrue(query.supportsManualControls())
    }

    @Test
    fun `supportsManualControls returns false when no applied controls`() {
        val caps = DeviceCapabilities(
            supportsManualControls = false,
            manualControlCapabilities = com.opencamera.core.device.ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
        )
        val query = DeviceCapabilitiesEffectQuery(caps)
        assertFalse(query.supportsManualControls())
    }

    @Test
    fun `asEffectCapabilityQuery extension returns DeviceCapabilitiesEffectQuery`() {
        val caps = DeviceCapabilities.DEFAULT
        val query: EffectCapabilityQuery = caps.asEffectCapabilityQuery()
        assertTrue(query is DeviceCapabilitiesEffectQuery)
    }

    @Test
    fun `asEffectCapabilityQuery preserves capability delegation`() {
        val caps = DeviceCapabilities(
            supportsPortraitDepthEffect = false,
            supportsDocumentScanEnhancement = true,
            supportsManualControls = false,
            manualControlCapabilities = com.opencamera.core.device.ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
        )
        val query = caps.asEffectCapabilityQuery()
        assertFalse(query.supportsPortraitDepth())
        assertTrue(query.supportsDocumentGeometry())
        assertFalse(query.supportsManualControls())
    }
}
