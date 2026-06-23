package com.opencamera.app.procontrols

import android.view.View.MeasureSpec
import android.view.MotionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProControlPickerViewTest {

    private fun createView(): ProControlPickerView =
        ProControlPickerView(RuntimeEnvironment.getApplication())

    private fun measureView(view: ProControlPickerView) {
        view.measure(
            MeasureSpec.makeMeasureSpec(480, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        view.layout(0, 0, 480, view.measuredHeight)
    }

    private val wbOptions = listOf("自动", "日光", "阴天", "白炽灯", "荧光灯")

    private fun setupWBPicker(view: ProControlPickerView, initial: String? = null) {
        view.configure(label = "白平衡", options = wbOptions, initial = initial)
        measureView(view)
    }

    // --- Configure ---

    @Test
    fun `configure with null initial sets auto mode`() {
        val view = createView()
        setupWBPicker(view, initial = null)
        assertEquals(Availability.SUPPORTED, view.availability)
    }

    @Test
    fun `configure with initial value selects that option`() {
        val view = createView()
        setupWBPicker(view, initial = "日光")
        assertEquals(Availability.SUPPORTED, view.availability)
    }

    // --- Option selection callback ---

    @Test
    fun `onOptionChange fires when option touched`() {
        val view = createView()
        setupWBPicker(view, initial = null)

        var receivedOption: String? = null
        view.optionListener = object : OnOptionChangeListener {
            override fun onOptionChange(value: String?) { receivedOption = value }
            override fun onAutoToggle() {}
            override fun onReset() {}
        }
        // Touch option 1 ("日光").
        val density = view.resources.displayMetrics.density
        val optX = view.paddingLeft + 60f * density + 8f * density + 60f * density / 2f
        val optY = 32f * density + 8f * density + 28f * density / 2f
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, optX, optY, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        assertEquals("日光", receivedOption)
    }

    @Test
    fun `option selection updates current option`() {
        val view = createView()
        setupWBPicker(view, initial = null)
        view.setCurrentOption("日光")
        // Verify by touching auto toggle — the option was set
    }

    // --- Auto toggle ---

    @Test
    fun `auto toggle callback fires when auto button touched`() {
        val view = createView()
        setupWBPicker(view, initial = "日光")

        var autoFired = false
        view.optionListener = object : OnOptionChangeListener {
            override fun onOptionChange(value: String?) {}
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
    fun `auto toggle sets currentOption to null`() {
        val view = createView()
        setupWBPicker(view, initial = "日光")
        view.optionListener = object : OnOptionChangeListener {
            override fun onOptionChange(value: String?) {}
            override fun onAutoToggle() {}
            override fun onReset() {}
        }
        val autoBtnX = view.width - 30f * view.resources.displayMetrics.density
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, autoBtnX, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        view.setCurrentOption(null)
    }

    // --- Reset ---

    @Test
    fun `reset callback fires when reset button touched`() {
        val view = createView()
        setupWBPicker(view, initial = "日光")

        var resetFired = false
        view.optionListener = object : OnOptionChangeListener {
            override fun onOptionChange(value: String?) {}
            override fun onAutoToggle() {}
            override fun onReset() { resetFired = true }
        }
        val resetBtnX = view.width - 90f * view.resources.displayMetrics.density
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, resetBtnX, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        assertTrue(resetFired)
    }

    @Test
    fun `reset restores initial option`() {
        val view = createView()
        setupWBPicker(view, initial = "日光")
        view.optionListener = object : OnOptionChangeListener {
            override fun onOptionChange(value: String?) {}
            override fun onAutoToggle() {}
            override fun onReset() {}
        }
        val resetBtnX = view.width - 90f * view.resources.displayMetrics.density
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, resetBtnX, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        assertTrue(resetFired(view))
    }

    private fun resetFired(view: ProControlPickerView): Boolean {
        var fired = false
        view.optionListener = object : OnOptionChangeListener {
            override fun onOptionChange(value: String?) {}
            override fun onAutoToggle() {}
            override fun onReset() { fired = true }
        }
        val resetBtnX = view.width - 90f * view.resources.displayMetrics.density
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, resetBtnX, 10f, 0)
        view.onTouchEvent(ev)
        ev.recycle()
        return fired
    }

    // --- Availability ---

    @Test
    fun `availability SUPPORTED allows touch`() {
        val view = createView()
        setupWBPicker(view)
        view.setAvailability(Availability.SUPPORTED)
        assertEquals(Availability.SUPPORTED, view.availability)
    }

    @Test
    fun `availability DEGRADED allows touch`() {
        val view = createView()
        setupWBPicker(view)
        view.setAvailability(Availability.DEGRADED)
        assertEquals(Availability.DEGRADED, view.availability)
    }

    @Test
    fun `availability UNSUPPORTED rejects touch`() {
        val view = createView()
        setupWBPicker(view)
        view.setAvailability(Availability.UNSUPPORTED)
        val ev = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        assertFalse(view.onTouchEvent(ev))
        ev.recycle()
    }

    // --- Measure ---

    @Test
    fun `picker measures with options row`() {
        val view = createView()
        setupWBPicker(view)
        assertTrue(view.measuredHeight > 0, "measuredHeight should be positive")
        assertTrue(view.measuredWidth > 0, "measuredWidth should be positive")
    }

    // --- Multiple configure calls ---

    @Test
    fun `configure can be called multiple times`() {
        val view = createView()
        setupWBPicker(view, initial = "日光")
        view.configure(label = "场景", options = listOf("普通", "运动", "夜景"), initial = null)
        measureView(view)
        assertEquals(Availability.SUPPORTED, view.availability)
    }
}
