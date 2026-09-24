package com.opencamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.opencamera.core.settings.ColorIntentEngine
import com.opencamera.core.settings.ColorIntentPreset
import com.opencamera.core.settings.ColorIntentRequest
import com.opencamera.core.settings.ColorLabSpec
import com.opencamera.core.settings.FilterRenderSpec
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PhotoAlgorithmColorGuardTest {
    private val appContext: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `blue tone preserves highlight steps in an already blue sky`() {
        val sourceColors = intArrayOf(
            Color.rgb(42, 110, 220),
            Color.rgb(45, 118, 235),
            Color.rgb(48, 126, 248)
        )
        val bitmap = Bitmap.createBitmap(sourceColors.size, 1, Bitmap.Config.ARGB_8888).apply {
            setPixels(sourceColors, 0, sourceColors.size, 0, 0, sourceColors.size, 1)
        }

        AndroidPhotoAlgorithmEditor(appContext).applyStyle(bitmap, blueTonePhotoSpec())

        val output = IntArray(sourceColors.size)
        bitmap.getPixels(output, 0, output.size, 0, 0, output.size, 1)
        val outputBlue = output.map(Color::blue)
        val outputRed = output.map(Color::red)
        val sourceSaturation = sourceColors.map(::saturation)
        val outputSaturation = output.map(::saturation)

        assertTrue(
            outputBlue.zipWithNext().all { (left, right) -> left < right },
            "Blue-tone rendering must keep cloud/sky highlight steps distinct, output=$outputBlue"
        )
        assertTrue(
            outputBlue.last() <= 253,
            "Blue-tone rendering must keep blue-channel headroom, output=$outputBlue"
        )
        assertTrue(
            outputBlue.count { it > 245 } <= 1,
            "Blue-tone rendering must not turn most sky steps into near-clipped blue, output=$outputBlue"
        )
        assertTrue(
            outputSaturation.last() <= sourceSaturation.last() - 0.03f,
            "Already-pure sky blue should regain chroma reserve, source=$sourceSaturation output=$outputSaturation"
        )
        assertTrue(
            outputRed.min() >= 8,
            "Blue-tone rendering must not crush the complementary channel, output=$outputRed"
        )
    }

    @Test
    fun `blue tone keeps a warm safety accent recognizably warm`() {
        val source = Color.rgb(226, 72, 32)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(source)
        }

        AndroidPhotoAlgorithmEditor(appContext).applyStyle(bitmap, blueTonePhotoSpec())

        val output = bitmap.getPixel(0, 0)
        assertTrue(
            Color.red(output) >= 210 && Color.red(output) > Color.blue(output) + 150,
            "Blue-tone rendering must preserve warm safety accents, output=${channels(output)}"
        )
    }

    @Test
    fun `blue tone restores chroma reserve in dark water`() {
        val sourceColors = intArrayOf(
            Color.rgb(3, 60, 140),
            Color.rgb(5, 70, 160),
            Color.rgb(8, 80, 180)
        )
        val bitmap = Bitmap.createBitmap(sourceColors.size, 1, Bitmap.Config.ARGB_8888).apply {
            setPixels(sourceColors, 0, sourceColors.size, 0, 0, sourceColors.size, 1)
        }

        AndroidPhotoAlgorithmEditor(appContext).applyStyle(bitmap, blueTonePhotoSpec())

        val output = IntArray(sourceColors.size)
        bitmap.getPixels(output, 0, output.size, 0, 0, output.size, 1)
        val sourceAverage = sourceColors.map(::saturation).average().toFloat()
        val outputAverage = output.map(::saturation).average().toFloat()
        assertTrue(
            outputAverage <= sourceAverage - 0.08f,
            "Dark blue water should be visibly less over-pure, source=$sourceAverage output=$outputAverage"
        )
        assertTrue(
            output.map(Color::blue).zipWithNext().all { (left, right) -> left < right },
            "Water tone steps must remain distinct, output=${output.map(Color::blue)}"
        )
    }

    @Test
    fun `mask aware color lab protects subject while restoring background sky gradation`() {
        val sourceColors = intArrayOf(
            Color.rgb(42, 110, 220),
            Color.rgb(45, 118, 235),
            Color.rgb(48, 126, 248),
            Color.rgb(226, 72, 32)
        )
        val bitmap = Bitmap.createBitmap(sourceColors.size, 1, Bitmap.Config.ARGB_8888).apply {
            setPixels(sourceColors, 0, sourceColors.size, 0, 0, sourceColors.size, 1)
        }
        val mask = SceneMaskTestUtils.createSyntheticMask(sourceColors.size, 1) { x, _ ->
            if (x == sourceColors.lastIndex) 1f else 0f
        }

        AndroidPhotoAlgorithmEditor(appContext).applyStyleWithMask(
            bitmap = bitmap,
            spec = blueTonePhotoSpec(),
            mask = mask
        )

        val output = IntArray(sourceColors.size)
        bitmap.getPixels(output, 0, output.size, 0, 0, output.size, 1)
        val skyBlue = output.take(3).map(Color::blue)
        val subject = output.last()
        assertTrue(
            skyBlue.zipWithNext().all { (left, right) -> left < right } && skyBlue.last() <= 253,
            "Mask-aware background sky must preserve gradation, output=$skyBlue"
        )
        assertTrue(
            Color.red(subject) >= 210 && Color.red(subject) > Color.blue(subject) + 150,
            "Mask-aware subject protection must preserve warm safety accents, output=${channels(subject)}"
        )
    }

    @Test
    fun `non color lab vivid style keeps its existing high chroma behavior`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(20, 127, 250))
        }

        AndroidPhotoAlgorithmEditor(appContext).applyStyle(
            bitmap = bitmap,
            spec = PhotoAlgorithmSpec(
                profile = "vivid-without-color-lab",
                saturation = 1.11f
            )
        )

        assertTrue(
            Color.blue(bitmap.getPixel(0, 0)) == 255,
            "Gamut guard must stay scoped to active Color Lab recipes"
        )
    }

    private fun blueTonePhotoSpec(): PhotoAlgorithmSpec {
        val plan = ColorIntentEngine.resolve(
            ColorIntentRequest(
                styleProfileId = "photo-original",
                baseRenderSpec = FilterRenderSpec(),
                colorLabSpec = ColorLabSpec(presetId = ColorIntentPreset.BLUE_TONE.id)
            )
        )
        return plan.finalRenderSpec.toPhotoAlgorithmSpec(
            profile = "photo-original",
            recipe = plan.recipe
        )
    }

    private fun channels(color: Int): String =
        "r=${Color.red(color)},g=${Color.green(color)},b=${Color.blue(color)}"

    private fun saturation(color: Int): Float {
        val maximum = maxOf(Color.red(color), Color.green(color), Color.blue(color))
        val minimum = minOf(Color.red(color), Color.green(color), Color.blue(color))
        return if (maximum == 0) 0f else (maximum - minimum).toFloat() / maximum
    }
}
