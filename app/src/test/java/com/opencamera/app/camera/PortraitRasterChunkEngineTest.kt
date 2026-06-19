package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Reference whole-frame mask-aware renderer. Faithfully reproduces the per-pixel
 * formulas from PortraitRenderPostProcessor.applyMaskAwarePortraitRender().
 */
private fun referenceMaskAware(
    original: Bitmap,
    blurred: Bitmap,
    spec: PortraitRenderSpec,
    mask: SavedPhotoMaskPixels
) {
    val width = original.width
    val height = original.height
    val origPx = IntArray(width * height)
    val blurPx = IntArray(width * height)
    original.getPixels(origPx, 0, width, 0, 0, width, height)
    blurred.getPixels(blurPx, 0, width, 0, 0, width, height)

    val mapper = SceneMaskCoordinateMapper(mask.maskWidth, mask.maskHeight, width, height)
    val fcx = width / 2f
    val fcy = height / 2f
    val maxDist = max(1f, sqrt(fcx * fcx + fcy * fcy))

    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            val src = origPx[i]; val bld = blurPx[i]
            val alpha = src ushr 24 and 0xFF
            val sw = smoothstep(0.15f, 0.85f, mask.sampleAlpha(mapper.maskX(x), mapper.maskY(y)))
            val bm = 1f - sw
            var r = mixCh(((src ushr 16) and 0xFF).toFloat(), ((bld ushr 16) and 0xFF).toFloat(), bm)
            var g = mixCh(((src ushr 8) and 0xFF).toFloat(), ((bld ushr 8) and 0xFF).toFloat(), bm)
            var b = mixCh((src and 0xFF).toFloat(), (bld and 0xFF).toFloat(), bm)
            val fd = sqrt((x - fcx) * (x - fcx) + (y - fcy) * (y - fcy))
            val v = 1f - ((fd / maxDist) * spec.vignetteStrength).coerceIn(0f, 0.28f)
            r *= v; g *= v; b *= v
            val sm = (spec.subjectSmoothing * sw).coerceIn(0f, 0.28f)
            if (sm > 0f) { r = mixCh(r, ((bld ushr 16) and 0xFF).toFloat(), sm); g = mixCh(g, ((bld ushr 8) and 0xFF).toFloat(), sm); b = mixCh(b, (bld and 0xFF).toFloat(), sm) }
            val lum = r * 0.299f + g * 0.587f + b * 0.114f
            val s = spec.subjectSaturationBoost * sw
            if (s > 0f) { r = lum + (r - lum) * (1f + s); g = lum + (g - lum) * (1f + s); b = lum + (b - lum) * (1f + s) }
            val li = spec.subjectLift * sw
            if (li > 0f) { r += (255f - r) * li; g += (255f - g) * li; b += (255f - b) * li }
            val hf = ((lum / 255f) - 0.52f).coerceIn(0f, 1f)
            val tb = (spec.highlightBloom * sw * hf + spec.backgroundBloom * bm * hf).coerceIn(0f, 0.24f)
            if (tb > 0f) { r += (255f - r) * tb; g += (255f - g) * tb; b += (255f - b) * tb }
            origPx[i] = (alpha shl 24) or (clampCh(r).toInt() shl 16) or (clampCh(g).toInt() shl 8) or clampCh(b).toInt()
        }
    }
    original.setPixels(origPx, 0, width, 0, 0, width, height)
}

/**
 * Reference whole-frame focus renderer. Faithfully reproduces the per-pixel
 * formulas from PortraitRenderPostProcessor.applyPortraitRender().
 */
private fun referenceFocus(
    original: Bitmap,
    blurred: Bitmap,
    spec: PortraitRenderSpec
) {
    val width = original.width
    val height = original.height
    val origPx = IntArray(width * height)
    val blurPx = IntArray(width * height)
    original.getPixels(origPx, 0, width, 0, 0, width, height)
    blurred.getPixels(blurPx, 0, width, 0, 0, width, height)

    val fcx = width * 0.5f
    val fcy = height * if (spec.subjectTracking) 0.42f else 0.46f
    val rx = max(1f, width * spec.focusRadiusXFraction)
    val ry = max(1f, height * spec.focusRadiusYFraction)
    val frameCX = width / 2f
    val frameCY = height / 2f
    val maxDist = max(1f, sqrt(frameCX * frameCX + frameCY * frameCY))

    for (y in 0 until height) {
        for (x in 0 until width) {
            val i = y * width + x
            val src = origPx[i]; val bld = blurPx[i]
            val alpha = src ushr 24 and 0xFF
            val nd = sqrt(((x - fcx) / rx) * ((x - fcx) / rx) + ((y - fcy) / ry) * ((y - fcy) / ry))
            val bm = smoothstep(1f, 1f + spec.edgeSoftness, nd)
            val sw = 1f - bm
            var r = mixCh(((src ushr 16) and 0xFF).toFloat(), ((bld ushr 16) and 0xFF).toFloat(), bm)
            var g = mixCh(((src ushr 8) and 0xFF).toFloat(), ((bld ushr 8) and 0xFF).toFloat(), bm)
            var b = mixCh((src and 0xFF).toFloat(), (bld and 0xFF).toFloat(), bm)
            val fd = sqrt((x - frameCX) * (x - frameCX) + (y - frameCY) * (y - frameCY))
            val v = 1f - ((fd / maxDist) * spec.vignetteStrength).coerceIn(0f, 0.28f)
            r *= v; g *= v; b *= v
            val sm = (spec.subjectSmoothing * sw).coerceIn(0f, 0.28f)
            if (sm > 0f) { r = mixCh(r, ((bld ushr 16) and 0xFF).toFloat(), sm); g = mixCh(g, ((bld ushr 8) and 0xFF).toFloat(), sm); b = mixCh(b, (bld and 0xFF).toFloat(), sm) }
            val lum = r * 0.299f + g * 0.587f + b * 0.114f
            val s = spec.subjectSaturationBoost * sw
            if (s > 0f) { r = lum + (r - lum) * (1f + s); g = lum + (g - lum) * (1f + s); b = lum + (b - lum) * (1f + s) }
            val li = spec.subjectLift * sw
            if (li > 0f) { r += (255f - r) * li; g += (255f - g) * li; b += (255f - b) * li }
            val hf = ((lum / 255f) - 0.52f).coerceIn(0f, 1f)
            val tb = (spec.highlightBloom * sw * hf + spec.backgroundBloom * bm * hf).coerceIn(0f, 0.24f)
            if (tb > 0f) { r += (255f - r) * tb; g += (255f - g) * tb; b += (255f - b) * tb }
            origPx[i] = (alpha shl 24) or (clampCh(r).toInt() shl 16) or (clampCh(g).toInt() shl 8) or clampCh(b).toInt()
        }
    }
    original.setPixels(origPx, 0, width, 0, 0, width, height)
}

// Test-local copies of the private math functions matching production exactly.
private fun mixCh(s: Float, t: Float, m: Float) = s + (t - s) * m.coerceIn(0f, 1f)
private fun smoothstep(e0: Float, e1: Float, v: Float): Float {
    if (e0 == e1) return if (v >= e1) 1f else 0f
    val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
}
private fun clampCh(v: Float) = v.coerceIn(0f, 255f)
private fun max(a: Float, b: Float) = if (a > b) a else b
private fun max(a: Int, b: Int) = if (a > b) a else b

// --- Test helpers ---

private fun createGradientBitmap(w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    // Diagonal gradient: (0,0)=0xFF804020 to (w-1,h-1)=0xFF204080
    for (y in 0 until h) {
        val row = IntArray(w)
        for (x in 0 until w) {
            val t = if (w <= 1 && h <= 1) 0f else (x.toFloat() / (w - 1) * 0.5f + y.toFloat() / (h - 1) * 0.5f)
            val r = (0x80 + (0x20 - 0x80) * t).toInt().coerceIn(0, 255)
            val g = (0x40 + (0x40 - 0x40) * t).toInt().coerceIn(0, 255)
            val b = (0x20 + (0x80 - 0x20) * t).toInt().coerceIn(0, 255)
            row[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        bmp.setPixels(row, 0, w, 0, y, w, 1)
    }
    return bmp
}

private fun createUniformBitmap(w: Int, h: Int, color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val row = IntArray(w) { color }
    for (y in 0 until h) bmp.setPixels(row, 0, w, 0, y, w, 1)
    return bmp
}

// --- Test spec fixtures ---

private val MASK_SPEC = PortraitRenderSpec(
    mode = PortraitRenderMode.DEPTH,
    portraitProfile = PortraitProfile.NATIVE,
    beautyPreset = PortraitBeautyPreset.CLEAR,
    beautyStrengthLevel = PortraitBeautyStrength.BALANCED,
    bokehEffect = PortraitBokehEffect.NATURAL,
    lightSpot = PortraitBackgroundLightSpotSpec.NONE,
    blurScale = 8,
    focusRadiusXFraction = 0.26f,
    focusRadiusYFraction = 0.32f,
    edgeSoftness = 0.24f,
    vignetteStrength = 0.1f,
    subjectTracking = false,
    strength = 1.8f,
    subjectSmoothing = 0.22f,
    subjectLift = 0.06f,
    subjectSaturationBoost = 0.035f,
    highlightBloom = 0.035f,
    backgroundBloom = 0.06f
)

private val FOCUS_SPEC = PortraitRenderSpec(
    mode = PortraitRenderMode.FOCUS,
    portraitProfile = PortraitProfile.NATIVE,
    beautyPreset = PortraitBeautyPreset.CLEAR,
    beautyStrengthLevel = PortraitBeautyStrength.BALANCED,
    bokehEffect = PortraitBokehEffect.CREAMY,
    lightSpot = PortraitBackgroundLightSpotSpec.NONE,
    blurScale = 11,
    focusRadiusXFraction = 0.33f,
    focusRadiusYFraction = 0.42f,
    edgeSoftness = 0.25f,
    vignetteStrength = 0.06f,
    subjectTracking = true,
    strength = 1.0f,
    subjectSmoothing = 0.18f,
    subjectLift = 0.04f,
    subjectSaturationBoost = 0.025f,
    highlightBloom = 0.03f,
    backgroundBloom = 0.05f
)

// --- Tests ---

@RunWith(RobolectricTestRunner::class)
class PortraitRasterChunkEngineTest {

    // ---- Mask-aware ARGB equality ----

    @Test fun `mask-aware chunk=1 matches reference for gradient`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(64, 64)
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(1)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
            referenceMaskAware(origR, blurR, MASK_SPEC, mask)
            assertArgbEqual(origR, origC, dim, dim, "mask-aware chunk=1 dim=$dim")
        }
    }

    @Test fun `mask-aware chunk=7 matches reference`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(7)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
            referenceMaskAware(origR, blurR, MASK_SPEC, mask)
            assertArgbEqual(origR, origC, dim, dim, "mask-aware chunk=7 dim=$dim")
        }
    }

    @Test fun `mask-aware chunk=64 matches reference`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(64)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
            referenceMaskAware(origR, blurR, MASK_SPEC, mask)
            assertArgbEqual(origR, origC, dim, dim, "mask-aware chunk=64 dim=$dim")
        }
    }

    @Test fun `mask-aware chunk=200 exceeds height matches reference`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(200)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
            referenceMaskAware(origR, blurR, MASK_SPEC, mask)
            assertArgbEqual(origR, origC, dim, dim, "mask-aware chunk>h dim=$dim")
        }
    }

    // ---- Focus ARGB equality ----

    @Test fun `focus chunk=1 matches reference`() {
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(1)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderFocus(origC, blurC, FOCUS_SPEC)
            referenceFocus(origR, blurR, FOCUS_SPEC)
            assertArgbEqual(origR, origC, dim, dim, "focus chunk=1 dim=$dim")
        }
    }

    @Test fun `focus chunk=7 matches reference`() {
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(7)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderFocus(origC, blurC, FOCUS_SPEC)
            referenceFocus(origR, blurR, FOCUS_SPEC)
            assertArgbEqual(origR, origC, dim, dim, "focus chunk=7 dim=$dim")
        }
    }

    @Test fun `focus chunk=64 matches reference`() {
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(64)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderFocus(origC, blurC, FOCUS_SPEC)
            referenceFocus(origR, blurR, FOCUS_SPEC)
            assertArgbEqual(origR, origC, dim, dim, "focus chunk=64 dim=$dim")
        }
    }

    @Test fun `focus chunk=200 exceeds height matches reference`() {
        for (dim in listOf(7, 32, 64, 100)) {
            val origC = createGradientBitmap(dim, dim); val blurC = createUniformBitmap(dim, dim, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(200)
            val origR = createGradientBitmap(dim, dim); val blurR = createUniformBitmap(dim, dim, 0xFF888888.toInt())
            eng.renderFocus(origC, blurC, FOCUS_SPEC)
            referenceFocus(origR, blurR, FOCUS_SPEC)
            assertArgbEqual(origR, origC, dim, dim, "focus chunk>h dim=$dim")
        }
    }

    // ---- Odd dimensions ----

    @Test fun `mask-aware odd dimensions 101x97 chunk=7 matches`() {
        val w = 101; val h = 97
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(7)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "mask-aware 101x97")
    }

    @Test fun `focus odd dimensions 101x97 chunk=7 matches`() {
        val w = 101; val h = 97
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(7)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderFocus(origC, blurC, FOCUS_SPEC)
        referenceFocus(origR, blurR, FOCUS_SPEC)
        assertArgbEqual(origR, origC, w, h, "focus 101x97")
    }

    @Test fun `mask-aware 1x1 chunk=1 matches`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(1, 1)
        val origC = createGradientBitmap(1, 1); val blurC = createUniformBitmap(1, 1, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(1)
        val origR = createGradientBitmap(1, 1); val blurR = createUniformBitmap(1, 1, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, 1, 1, "mask-aware 1x1")
    }

    @Test fun `focus 1x1 chunk=1 matches`() {
        val origC = createGradientBitmap(1, 1); val blurC = createUniformBitmap(1, 1, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(1)
        val origR = createGradientBitmap(1, 1); val blurR = createUniformBitmap(1, 1, 0xFF888888.toInt())
        eng.renderFocus(origC, blurC, FOCUS_SPEC)
        referenceFocus(origR, blurR, FOCUS_SPEC)
        assertArgbEqual(origR, origC, 1, 1, "focus 1x1")
    }

    // ---- Different mask dimensions ----

    @Test fun `mask-aware small mask 32x32 on 128x128 output matches`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(32, 32)
        val w = 128; val h = 128
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "mask 32x32 on 128x128")
    }

    @Test fun `mask-aware large mask 256x256 on 64x64 output matches`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(256, 256)
        val w = 64; val h = 64
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "mask 256x256 on 64x64")
    }

    // ---- Alpha boundary values ----

    @Test fun `mask-aware uniform full-alpha mask matches`() {
        val mask = SceneMaskTestUtils.createUniformMask(64, 64, 1.0f)
        val w = 64; val h = 64
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "uniform alpha=1.0")
    }

    @Test fun `mask-aware uniform zero-alpha mask matches`() {
        val mask = SceneMaskTestUtils.createUniformMask(64, 64, 0.0f)
        val w = 64; val h = 64
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "uniform alpha=0.0")
    }

    @Test fun `mask-aware left-right split mask matches`() {
        val mask = SceneMaskTestUtils.createLeftRightSplitMask(64, 64, 0.5f)
        val w = 64; val h = 64
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "left-right split mask")
    }

    // ---- Buffer bounds ----

    @Test fun `buffer size bounded by width times min chunkRows and height`() {
        val engine = PortraitRasterChunkEngine(64)
        assertEquals(300, engine.bufferSize(100, 3))   // min(64, 3) = 3
        assertEquals(6400, engine.bufferSize(100, 200)) // min(64, 200) = 64
        assertEquals(100, engine.bufferSize(100, 1))    // min(64, 1) = 1
    }

    @Test fun `default chunk rows is 64`() {
        assertEquals(64, PortraitRasterChunkEngine.DEFAULT_CHUNK_ROWS)
    }

    // ---- Invalid chunkRows ----

    @Test(expected = IllegalArgumentException::class)
    fun `negative chunkRows throws`() { PortraitRasterChunkEngine(-1) }

    @Test(expected = IllegalArgumentException::class)
    fun `zero chunkRows throws`() { PortraitRasterChunkEngine(0) }

    // ---- Every row written exactly once ----

    @Test fun `every row is written exactly once with chunk=3`() {
        val w = 10; val h = 7
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val eng = PortraitRasterChunkEngine(3)
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt())
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "row-exactness chunk=3")
    }

    // ---- Non-square output dimensions ----

    @Test fun `mask-aware non-square 47x91 chunk=16 matches`() {
        val w = 47; val h = 91
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "mask-aware 47x91")
    }

    @Test fun `focus non-square 47x91 chunk=16 matches`() {
        val w = 47; val h = 91
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(16)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderFocus(origC, blurC, FOCUS_SPEC)
        referenceFocus(origR, blurR, FOCUS_SPEC)
        assertArgbEqual(origR, origC, w, h, "focus 47x91")
    }

    // ---- Final partial chunk ----

    @Test fun `mask-aware 15 rows with chunk=4 covers final partial chunk`() {
        val w = 10; val h = 15
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val origC = createGradientBitmap(w, h); val blurC = createUniformBitmap(w, h, 0xFF888888.toInt()); val eng = PortraitRasterChunkEngine(4)
        val origR = createGradientBitmap(w, h); val blurR = createUniformBitmap(w, h, 0xFF888888.toInt())
        eng.renderMaskAware(origC, blurC, MASK_SPEC, mask)
        referenceMaskAware(origR, blurR, MASK_SPEC, mask)
        assertArgbEqual(origR, origC, w, h, "partial-chunk 15 rows chunk=4")
    }

    // ---- Private helpers ----

    private fun assertArgbEqual(expected: Bitmap, actual: Bitmap, w: Int, h: Int, label: String) {
        val expPx = IntArray(w * h); val actPx = IntArray(w * h)
        expected.getPixels(expPx, 0, w, 0, 0, w, h)
        actual.getPixels(actPx, 0, w, 0, 0, w, h)
        for (i in expPx.indices) {
            assertEquals(expPx[i], actPx[i], "$label pixel[$i] mismatch")
        }
    }
}
