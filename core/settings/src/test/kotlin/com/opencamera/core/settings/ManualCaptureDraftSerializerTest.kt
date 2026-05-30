// 覆盖行为:
// - ManualCaptureDraftSerializer serialize/deserialize round-trip（全字段、null 字段、混合）
// - null/空输入 deserialize 返回全默认值
// - "auto" 占位符正确解码为 null
// - 非法数值 graceful fallback（toIntOrNull/toFloatOrNull）
//
// 暂时不适合单测:
// - 无（纯数据序列化，完全可测）

package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ManualCaptureDraftSerializerTest {

    @Test
    fun `round trips full ManualCaptureParams with all fields set`() {
        val params = ManualCaptureParams(
            rawEnabled = true,
            iso = 800,
            shutterSpeedMillis = 50L,
            exposureCompensationSteps = 3,
            focusDistanceDiopters = 2.5f,
            apertureFNumber = 1.8f,
            whiteBalanceKelvin = 5600
        )

        val serialized = ManualCaptureDraftSerializer.serialize(params)
        val decoded = ManualCaptureDraftSerializer.deserialize(serialized)

        assertEquals(params, decoded)
    }

    @Test
    fun `round trips ManualCaptureParams with all null optional fields`() {
        val params = ManualCaptureParams(rawEnabled = false)

        val serialized = ManualCaptureDraftSerializer.serialize(params)
        val decoded = ManualCaptureDraftSerializer.deserialize(serialized)

        assertEquals(params, decoded)
        assertFalse(decoded.rawEnabled)
        assertNull(decoded.iso)
        assertNull(decoded.shutterSpeedMillis)
        assertNull(decoded.exposureCompensationSteps)
        assertNull(decoded.focusDistanceDiopters)
        assertNull(decoded.apertureFNumber)
        assertNull(decoded.whiteBalanceKelvin)
    }

    @Test
    fun `deserialize null returns default ManualCaptureParams`() {
        val decoded = ManualCaptureDraftSerializer.deserialize(null)
        assertEquals(ManualCaptureParams(), decoded)
    }

    @Test
    fun `deserialize empty string returns default ManualCaptureParams`() {
        val decoded = ManualCaptureDraftSerializer.deserialize("")
        assertEquals(ManualCaptureParams(), decoded)
    }

    @Test
    fun `serialize encodes null fields as auto`() {
        val params = ManualCaptureParams(rawEnabled = false)
        val serialized = ManualCaptureDraftSerializer.serialize(params)

        assert(serialized.contains("iso=auto"))
        assert(serialized.contains("shutterSpeedMillis=auto"))
        assert(serialized.contains("exposureCompensationSteps=auto"))
        assert(serialized.contains("focusDistanceDiopters=auto"))
        assert(serialized.contains("apertureFNumber=auto"))
        assert(serialized.contains("whiteBalanceKelvin=auto"))
    }

    @Test
    fun `deserialize handles auto placeholder as null`() {
        val serialized = """
            rawEnabled=false
            iso=auto
            shutterSpeedMillis=auto
            exposureCompensationSteps=auto
            focusDistanceDiopters=auto
            apertureFNumber=auto
            whiteBalanceKelvin=auto
        """.trimIndent()

        val decoded = ManualCaptureDraftSerializer.deserialize(serialized)

        assertNull(decoded.iso)
        assertNull(decoded.shutterSpeedMillis)
        assertNull(decoded.exposureCompensationSteps)
        assertNull(decoded.focusDistanceDiopters)
        assertNull(decoded.apertureFNumber)
        assertNull(decoded.whiteBalanceKelvin)
    }

    @Test
    fun `deserialize gracefully handles malformed numeric values`() {
        val serialized = """
            rawEnabled=true
            iso=notanumber
            shutterSpeedMillis=xyz
            focusDistanceDiopters=abc
        """.trimIndent()

        val decoded = ManualCaptureDraftSerializer.deserialize(serialized)

        assertEquals(true, decoded.rawEnabled)
        assertNull(decoded.iso)
        assertNull(decoded.shutterSpeedMillis)
        assertNull(decoded.focusDistanceDiopters)
    }

    @Test
    fun `deserialize ignores unknown keys`() {
        val serialized = """
            rawEnabled=true
            unknownKey=value
            iso=400
        """.trimIndent()

        val decoded = ManualCaptureDraftSerializer.deserialize(serialized)

        assertEquals(true, decoded.rawEnabled)
        assertEquals(400, decoded.iso)
    }

    @Test
    fun `round trips partial fields`() {
        val params = ManualCaptureParams(
            rawEnabled = true,
            iso = 200,
            whiteBalanceKelvin = 3200
        )

        val serialized = ManualCaptureDraftSerializer.serialize(params)
        val decoded = ManualCaptureDraftSerializer.deserialize(serialized)

        assertEquals(params, decoded)
    }
}
