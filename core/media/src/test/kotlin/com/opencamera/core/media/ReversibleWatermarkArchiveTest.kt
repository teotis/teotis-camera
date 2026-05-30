/**
 * 覆盖行为:
 * - toJson() 生成合法 JSON 字符串
 * - fromJson() 解析合法 JSON 还原为 Manifest
 * - fromJson() 对缺少必需字段抛出 IllegalArgumentException
 * - fromJson() 对非法 schema/version/container 抛出异常
 * - fromJson() 对负 payloadLength 抛出异常
 * - toJson → fromJson round-trip 一致性
 * - 特殊字符转义（引号、反斜杠、换行等）
 * - parseJsonToMap() 内部 JSON 解析器的边界情况
 * - sha256Hex: 空字节数组、已知输入、输出长度
 *
 * 不适合单测的行为: 无
 */
package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ReversibleWatermarkArchiveTest {

    private fun sampleManifest(
        watermarkTemplateId: String = "template-001",
        visibleImageSha256: String = "a".repeat(64),
        payloadSha256: String = "b".repeat(64),
        payloadLength: Long = 1024L,
        originalWidth: Int = 1920,
        originalHeight: Int = 1080
    ) = ReversibleWatermarkArchiveManifest(
        watermarkTemplateId = watermarkTemplateId,
        visibleImageSha256 = visibleImageSha256,
        payloadSha256 = payloadSha256,
        payloadLength = payloadLength,
        originalWidth = originalWidth,
        originalHeight = originalHeight
    )

    // --- toJson ---

    @Test
    fun `toJson produces valid JSON string`() {
        val json = sampleManifest().toJson()
        assert(json.startsWith("{"))
        assert(json.endsWith("}"))
        assert(json.contains("\"schema\""))
        assert(json.contains("\"watermarkTemplateId\""))
    }

    @Test
    fun `toJson includes all fields`() {
        val json = sampleManifest().toJson()
        val expectedKeys = listOf(
            "schema", "version", "container", "payloadKind",
            "payloadMimeType", "payloadCompression", "pipelineStage",
            "watermarkTemplateId", "visibleImageSha256", "payloadSha256",
            "payloadLength", "originalWidth", "originalHeight"
        )
        for (key in expectedKeys) {
            assert(json.contains("\"$key\"")) { "missing key: $key" }
        }
    }

    // --- fromJson ---

    @Test
    fun `fromJson parses valid JSON`() {
        val json = sampleManifest().toJson()
        val parsed = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("org.opencamera.reversible-watermark", parsed.schema)
        assertEquals(1, parsed.version)
        assertEquals("jpeg-app15-ocwm", parsed.container)
        assertEquals("template-001", parsed.watermarkTemplateId)
        assertEquals(1024L, parsed.payloadLength)
        assertEquals(1920, parsed.originalWidth)
        assertEquals(1080, parsed.originalHeight)
    }

    @Test
    fun `fromJson with missing required field throws`() {
        // Remove watermarkTemplateId
        val json = """{"schema":"org.opencamera.reversible-watermark","version":1,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"after-upstream-postprocessors-before-watermark","visibleImageSha256":"aaaa","payloadSha256":"bbbb","payloadLength":100}"""
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun `fromJson with unsupported schema throws`() {
        val json = sampleManifest().toJson().replace(
            "org.opencamera.reversible-watermark",
            "org.opencamera.unknown"
        )
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun `fromJson with unsupported version throws`() {
        val json = sampleManifest().toJson().replace("\"version\":1", "\"version\":99")
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun `fromJson with unsupported container throws`() {
        val json = sampleManifest().toJson().replace(
            "jpeg-app15-ocwm",
            "unknown-container"
        )
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun `fromJson with negative payloadLength throws`() {
        val json = sampleManifest(payloadLength = -1L).toJson()
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun `fromJson with non-integer version throws`() {
        val json = sampleManifest().toJson().replace("\"version\":1", "\"version\":abc")
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    @Test
    fun `fromJson with non-long payloadLength throws`() {
        val json = sampleManifest().toJson().replace("\"payloadLength\":1024", "\"payloadLength\":notanumber")
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson(json)
        }
    }

    // --- round-trip ---

    @Test
    fun `toJson fromJson round-trip preserves all fields`() {
        val original = sampleManifest(
            watermarkTemplateId = "tmpl-xyz",
            payloadLength = 9999L,
            originalWidth = 3840,
            originalHeight = 2160
        )
        val restored = ReversibleWatermarkArchiveManifest.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun `round-trip with default optional fields`() {
        val original = ReversibleWatermarkArchiveManifest(
            watermarkTemplateId = "t",
            visibleImageSha256 = "a",
            payloadSha256 = "b",
            payloadLength = 0L
        )
        val restored = ReversibleWatermarkArchiveManifest.fromJson(original.toJson())
        assertEquals(original, restored)
        assertEquals(0, restored.originalWidth)
        assertEquals(0, restored.originalHeight)
    }

    // --- special characters ---

    @Test
    fun `toJson escapes double quotes in string values`() {
        val manifest = sampleManifest(watermarkTemplateId = "a\"b")
        val json = manifest.toJson()
        val restored = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("a\"b", restored.watermarkTemplateId)
    }

    @Test
    fun `toJson escapes backslash in string values`() {
        val manifest = sampleManifest(watermarkTemplateId = "a\\b")
        val json = manifest.toJson()
        val restored = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("a\\b", restored.watermarkTemplateId)
    }

    @Test
    fun `toJson escapes newline in string values`() {
        val manifest = sampleManifest(watermarkTemplateId = "a\nb")
        val json = manifest.toJson()
        val restored = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("a\nb", restored.watermarkTemplateId)
    }

    @Test
    fun `toJson escapes tab in string values`() {
        val manifest = sampleManifest(watermarkTemplateId = "a\tb")
        val json = manifest.toJson()
        val restored = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("a\tb", restored.watermarkTemplateId)
    }

    @Test
    fun `fromJson handles unicode escape`() {
        val json = """{"schema":"org.opencamera.reversible-watermark","version":1,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"stage","watermarkTemplateId":"AB","visibleImageSha256":"x","payloadSha256":"y","payloadLength":0}"""
        val parsed = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("AB", parsed.watermarkTemplateId)
    }

    // --- parseJsonToMap edge cases ---

    @Test
    fun `fromJson with empty JSON object throws`() {
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson("{}")
        }
    }

    @Test
    fun `fromJson with whitespace-padded JSON works`() {
        val json = "  ${sampleManifest().toJson()}  "
        val parsed = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals("org.opencamera.reversible-watermark", parsed.schema)
    }

    @Test
    fun `fromJson with non-object JSON throws`() {
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson("not json")
        }
    }

    @Test
    fun `fromJson with array JSON throws`() {
        assertFailsWith<IllegalArgumentException> {
            ReversibleWatermarkArchiveManifest.fromJson("[1,2,3]")
        }
    }

    @Test
    fun `fromJson with numeric values parsed correctly`() {
        val json = sampleManifest().toJson()
        val parsed = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals(1, parsed.version)
        assertEquals(1024L, parsed.payloadLength)
        assertEquals(1920, parsed.originalWidth)
    }

    @Test
    fun `fromJson with missing optional fields defaults to 0`() {
        val json = """{"schema":"org.opencamera.reversible-watermark","version":1,"container":"jpeg-app15-ocwm","payloadKind":"embedded-original-jpeg","payloadMimeType":"image/jpeg","payloadCompression":"none","pipelineStage":"stage","watermarkTemplateId":"t","visibleImageSha256":"v","payloadSha256":"p","payloadLength":10}"""
        val parsed = ReversibleWatermarkArchiveManifest.fromJson(json)
        assertEquals(0, parsed.originalWidth)
        assertEquals(0, parsed.originalHeight)
    }

    // --- sha256Hex ---

    @Test
    fun `sha256Hex of empty byte array`() {
        val result = sha256Hex(ByteArray(0))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result
        )
    }

    @Test
    fun `sha256Hex of known input`() {
        val result = sha256Hex("hello".toByteArray())
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            result
        )
    }

    @Test
    fun `sha256Hex output is always 64 hex characters`() {
        assertEquals(64, sha256Hex(ByteArray(0)).length)
        assertEquals(64, sha256Hex("a".toByteArray()).length)
        assertEquals(64, sha256Hex(ByteArray(1000)).length)
    }

    @Test
    fun `sha256Hex output is lowercase hex`() {
        val result = sha256Hex("test".toByteArray())
        assertEquals(result, result.lowercase())
        assert(result.all { it in "0123456789abcdef" })
    }

    @Test
    fun `sha256Hex is deterministic`() {
        val data = "deterministic".toByteArray()
        assertEquals(sha256Hex(data), sha256Hex(data))
    }
}
