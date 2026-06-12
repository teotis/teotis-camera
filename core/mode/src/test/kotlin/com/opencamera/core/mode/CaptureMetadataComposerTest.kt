package com.opencamera.core.mode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CaptureMetadataComposerTest {

    // ── Disjoint layers ─────────────────────────────────────────────

    @Test
    fun `disjoint layers compose without conflict`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("filterProfile" to "vivid"),
            captureAidTags = mapOf("captureLensFacing" to "back"),
            modeTags = mapOf("mode" to "photo")
        )
        val result = layers.compose()

        assertEquals("vivid", result["filterProfile"])
        assertEquals("back", result["captureLensFacing"])
        assertEquals("photo", result["mode"])
        assertEquals(3, result.size)
    }

    // ── Equal duplicates ────────────────────────────────────────────

    @Test
    fun `equal value duplicates across layers are allowed`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("selfieMirrorApply" to "false"),
            captureAidTags = mapOf("selfieMirrorApply" to "false"),
            modeTags = mapOf("mode" to "photo")
        )
        val result = layers.compose()

        assertEquals("false", result["selfieMirrorApply"])
        assertEquals("photo", result["mode"])
        assertEquals(2, result.size)
    }

    // ── Differing duplicates rejected ───────────────────────────────

    @Test
    fun `differing value duplicates raise MetadataCollision`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("mode" to "portrait"),
            modeTags = mapOf("mode" to "check-in")
        )
        val ex = assertFailsWith<MetadataCollision> { layers.compose() }

        assertEquals("mode", ex.key)
        assertTrue(ex.leftValue == "portrait" && ex.rightValue == "check-in" ||
            ex.leftValue == "check-in" && ex.rightValue == "portrait")
    }

    @Test
    fun `effect and capture-aid collision is rejected`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("stillResolution" to "12MP"),
            captureAidTags = mapOf("stillResolution" to "8MP")
        )
        assertFailsWith<MetadataCollision> { layers.compose() }
        Unit
    }

    @Test
    fun `capture-aid and mode collision is rejected`() {
        val layers = CaptureMetadataLayers(
            captureAidTags = mapOf("captureLensFacing" to "back"),
            modeTags = mapOf("captureLensFacing" to "front")
        )
        assertFailsWith<MetadataCollision> { layers.compose() }
        Unit
    }

    // ── Explicit overrides ──────────────────────────────────────────

    @Test
    fun `overrideTags resolve collision without exception`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("mode" to "portrait"),
            modeTags = mapOf("mode" to "check-in"),
            overrideTags = mapOf("mode" to "check-in")
        )
        val result = layers.compose()

        assertEquals("check-in", result["mode"])
    }

    @Test
    fun `overrideTags takes precedence over all layers`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("filterProfile" to "vivid"),
            captureAidTags = mapOf("filterProfile" to "natural"),
            modeTags = mapOf("filterProfile" to "classic"),
            overrideTags = mapOf("filterProfile" to "override-value")
        )
        val result = layers.compose()

        assertEquals("override-value", result["filterProfile"])
    }

    @Test
    fun `overrideTags can add new key not present in other layers`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("filterProfile" to "vivid"),
            overrideTags = mapOf("customKey" to "customValue")
        )
        val result = layers.compose()

        assertEquals("vivid", result["filterProfile"])
        assertEquals("customValue", result["customKey"])
    }

    // ── Empty layers ────────────────────────────────────────────────

    @Test
    fun `all empty layers produce empty result`() {
        val result = CaptureMetadataLayers().compose()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `only overrideTags produces only override entries`() {
        val result = CaptureMetadataLayers(
            overrideTags = mapOf("mode" to "video")
        ).compose()

        assertEquals(1, result.size)
        assertEquals("video", result["mode"])
    }

    // ── Deterministic output ────────────────────────────────────────

    @Test
    fun `output order is deterministic across runs`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("filterProfile" to "vivid", "frameRatio" to "4:3"),
            captureAidTags = mapOf("captureLensFacing" to "back", "selfieMirrorEnabled" to "off"),
            modeTags = mapOf("mode" to "photo"),
            overrideTags = mapOf("stillResolution" to "12MP")
        )

        val result1 = layers.compose()
        val result2 = layers.compose()

        assertEquals(result1.keys.toList(), result2.keys.toList())
        assertEquals(result1.values.toList(), result2.values.toList())
    }

    @Test
    fun `layer ordering is effect then capture-aid then mode then override`() {
        val layers = CaptureMetadataLayers(
            modeTags = mapOf("mode" to "photo"),
            effectTags = mapOf("filterProfile" to "vivid"),
            captureAidTags = mapOf("captureLensFacing" to "back"),
            overrideTags = mapOf("customKey" to "customValue")
        )
        val keys = layers.compose().keys.toList()

        assertEquals("filterProfile", keys[0])
        assertEquals("captureLensFacing", keys[1])
        assertEquals("mode", keys[2])
        assertEquals("customKey", keys[3])
    }

    // ── Three-way collision ─────────────────────────────────────────

    @Test
    fun `three-way differing collision raises MetadataCollision`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("key" to "a"),
            captureAidTags = mapOf("key" to "b"),
            modeTags = mapOf("key" to "c")
        )
        assertFailsWith<MetadataCollision> { layers.compose() }
        Unit
    }

    @Test
    fun `three-way equal values compose without error`() {
        val layers = CaptureMetadataLayers(
            effectTags = mapOf("key" to "same"),
            captureAidTags = mapOf("key" to "same"),
            modeTags = mapOf("key" to "same")
        )
        val result = layers.compose()

        assertEquals("same", result["key"])
        assertEquals(1, result.size)
    }
}
