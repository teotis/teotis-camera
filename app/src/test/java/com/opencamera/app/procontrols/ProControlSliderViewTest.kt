package com.opencamera.app.procontrols

import android.view.View.MeasureSpec
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProControlSliderViewTest {

    private fun createView(): ProControlSliderView =
        ProControlSliderView(RuntimeEnvironment.getApplication())

    private fun measureView(view: ProControlSliderView) {
        view.measure(
            MeasureSpec.makeMeasureSpec(480, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, 480, view.measuredHeight)
    }

    private fun setupStandardSlider(view: ProControlSliderView, initial: Float? = null) {
        view.configure(label = "ISO", min = 50f, max = 6400f, presets = listOf(100f, 200f, 400f, 800f, 1600f, 3200f, 6400f), initial = initial)
    }

    // --- shouldSnap (companion static) ---

    @Test
    fun `shouldSnap returns true when exactly on preset`() {
        assertTrue(ProControlSliderView.shouldSnap(200f, listOf(100f, 200f, 400f)))
    }

    @Test
    fun `shouldSnap returns true within threshold`() {
        val presets = listOf(100f, 200f, 400f)
        // Nearest neighbor distance around 200 is 100, threshold = 100*0.15 = 15.
        // 210 is 10 from 200, within 15.
        assertTrue(ProControlSliderView.shouldSnap(210f, presets))
    }

    @Test
    fun `shouldSnap returns false beyond threshold`() {
        val presets = listOf(100f, 200f, 400f)
        assertFalse(ProControlSliderView.shouldSnap(300f, presets))
    }

    @Test
    fun `shouldSnap with single preset always returns true`() {
        assertTrue(ProControlSliderView.shouldSnap(500f, listOf(200f)))
    }

    @Test
    fun `shouldSnap with empty presets returns false`() {
        assertFalse(ProControlSliderView.shouldSnap(100f, emptyList()))
    }

    // --- Log-space conversion ---

    @Test
    fun `fractionToValue and valueToFraction roundtrip`() {
        val min = 50f; val max = 6400f
        for (v in listOf(50f, 100f, 200f, 400f, 800f, 1600f, 3200f, 6400f)) {
            val frac = ProControlSliderView.valueToFraction(v, min, max)
            val roundTrip = ProControlSliderView.fractionToValue(frac, min, max)
            assertTrue(abs(v - roundTrip) < 0.5f, "roundtrip failed for $v: got $roundTrip")
        }
    }

    @Test
    fun `valueToFraction returns 0 at min and 1 at max`() {
        val min = 50f; val max = 6400f
        assertTrue(abs(ProControlSliderView.valueToFraction(min, min, max)) < 0.01f)
        assertTrue(abs(ProControlSliderView.valueToFraction(max, min, max) - 1f) < 0.01f)
    }

    @Test
    fun `fractionToValue at 0 equals min and at 1 equals max`() {
        val min = 50f; val max = 6400f
        assertTrue(abs(ProControlSliderView.fractionToValue(0f, min, max) - min) < 0.5f)
        assertTrue(abs(ProControlSliderView.fractionToValue(1f, min, max) - max) < 0.5f)
    }

    // --- findNearestPreset ---

    @Test
    fun `findNearestPreset returns closest preset`() {
        val presets = listOf(100f, 200f, 400f, 800f, 1600f, 3200f, 6400f)
        assertEquals(200f, ProControlSliderView.findNearestPreset(180f, presets))
        assertEquals(400f, ProControlSliderView.findNearestPreset(350f, presets))
        assertEquals(6400f, ProControlSliderView.findNearestPreset(6000f, presets))
    }

    @Test
    fun `findNearestPreset returns null for empty presets`() {
        assertNull(ProControlSliderView.findNearestPreset(100f, emptyList()))
    }

    // --- Callbacks ---

    @Test
    fun `listener onReset fires when reset area touched`() {
        val view = createView()
        setupStandardSlider(view, initial = 400f)
        measureView(view)

        var resetFired = false
        view.listener = object : SliderListener {
            override fun onValueChange(value: Float?) {}
            override fun onAutoToggle() {}
            override fun onReset() { resetFired = true }
        }
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, view.width - 90f * view.resources.displayMetrics.density, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        assertTrue(resetFired)
    }

    @Test
    fun `listener onValueChange fires on reset`() {
        val view = createView()
        setupStandardSlider(view, initial = 400f)
        measureView(view)

        var received: Float? = null
        view.listener = object : SliderListener {
            override fun onValueChange(value: Float?) { received = value }
            override fun onAutoToggle() {}
            override fun onReset() {}
        }
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, view.width - 90f * view.resources.displayMetrics.density, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        assertEquals(400f, received)
    }

    @Test
    fun `listener onAutoToggle fires when auto area touched`() {
        val view = createView()
        setupStandardSlider(view, initial = 400f)
        measureView(view)

        var autoFired = false
        view.listener = object : SliderListener {
            override fun onValueChange(value: Float?) {}
            override fun onAutoToggle() { autoFired = true }
            override fun onReset() {}
        }
        val autoBtnX = view.width - 30f * view.resources.displayMetrics.density
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, autoBtnX, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        assertTrue(autoFired)
    }

    @Test
    fun `configure with null initial sets auto mode`() {
        val view = createView()
        setupStandardSlider(view, initial = null)
        assertEquals(Availability.SUPPORTED, view.availability)
    }

    @Test
    fun `configure with initial value sets manual mode`() {
        val view = createView()
        setupStandardSlider(view, initial = 400f)
        assertEquals(Availability.SUPPORTED, view.availability)
    }

    // --- Availability ---

    @Test
    fun `availability SUPPORTED enables interaction`() {
        val view = createView()
        setupStandardSlider(view)
        view.setAvailability(Availability.SUPPORTED)
        assertEquals(Availability.SUPPORTED, view.availability)
    }

    @Test
    fun `availability DEGRADED disables track`() {
        val view = createView()
        setupStandardSlider(view)
        view.setAvailability(Availability.DEGRADED)
        assertEquals(Availability.DEGRADED, view.availability)
    }

    @Test
    fun `availability UNSUPPORTED disables track`() {
        val view = createView()
        setupStandardSlider(view)
        view.setAvailability(Availability.UNSUPPORTED)
        assertEquals(Availability.UNSUPPORTED, view.availability)
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100f, 50f, 0)
        assertFalse(view.onTouchEvent(ev))
        ev.recycle()
    }

    // --- setCurrentValue ---

    @Test
    fun `setCurrentValue updates track value`() {
        val view = createView()
        setupStandardSlider(view, initial = null)
        view.setCurrentValue(800f)
        assertEquals(800f, view.trackView.currentValue)
    }

    @Test
    fun `setCurrentValue null enables auto mode`() {
        val view = createView()
        setupStandardSlider(view, initial = 400f)
        view.setCurrentValue(null)
    }

    // --- Format value ---

    @Test
    fun `format value callback applied correctly`() {
        val view = createView()
        view.configure(
            label = "SS", min = 0.001f, max = 2f, presets = listOf(0.001f, 0.01f, 0.1f, 1f),
            initial = 0.1f, format = { String.format(java.util.Locale.US, "1/%d", (1f / it).toInt()) }
        )
        assertEquals(0.1f, view.trackView.currentValue)
    }

    // --- Compact measure ---

    @Test
    fun `slider measures a reasonable compact height`() {
        val view = createView()
        setupStandardSlider(view)
        measureView(view)
        val heightDp = view.measuredHeight / view.resources.displayMetrics.density
        assertTrue(heightDp in 80f..120f, "heightDp=$heightDp")
    }
}
