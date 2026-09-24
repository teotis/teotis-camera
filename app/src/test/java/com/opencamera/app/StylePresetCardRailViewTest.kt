package com.opencamera.app

import android.graphics.Bitmap
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.toStylePresetPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StylePresetCardRailViewTest {

    @Test
    fun `rail renders photographic cards and reports hold original lifecycle`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rail = StylePresetCardRailView(context)
        val comparisonEvents = mutableListOf<Boolean>()
        val model = StylePresetRailRenderModel(
            title = "照片风格",
            activeFamily = FilterLabFamily.PHOTO,
            cards = listOf(card("photo-original", true), card("photo-vivid", false)),
            isEnabled = true,
            supportingText = "",
            styleStrength = 0.72f
        )

        rail.render(
            model = model,
            previewBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
            onApplyStyle = {},
            onStrengthPreviewChanged = {},
            onStrengthChanged = {},
            onOriginalComparisonChanged = comparisonEvents::add
        )

        assertEquals(3, rail.childCount)
        val cardScroll = rail.getChildAt(1) as HorizontalScrollView
        assertEquals(2, (cardScroll.getChildAt(0) as LinearLayout).childCount)

        val compareButton = rail.getChildAt(0)
        compareButton.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN))
        compareButton.dispatchTouchEvent(event(MotionEvent.ACTION_UP))

        assertEquals(listOf(true, false), comparisonEvents)
    }

    @Test
    fun `explicit rail release restores active original comparison`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rail = StylePresetCardRailView(context)
        val events = mutableListOf<Boolean>()
        rail.render(
            model = StylePresetRailRenderModel(
                title = "照片风格",
                activeFamily = FilterLabFamily.PHOTO,
                cards = listOf(card("photo-original", true)),
                isEnabled = true,
                supportingText = "",
                styleStrength = 1f
            ),
            previewBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
            onApplyStyle = {},
            onStrengthPreviewChanged = {},
            onStrengthChanged = {},
            onOriginalComparisonChanged = events::add
        )

        rail.getChildAt(0).dispatchTouchEvent(event(MotionEvent.ACTION_DOWN))
        rail.releaseOriginalComparison()

        assertTrue(events.contains(true))
        assertEquals(false, events.last())
    }

    @Test
    fun `rail waits for a real preview frame instead of rendering abstract swatches`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rail = StylePresetCardRailView(context)

        rail.render(
            model = StylePresetRailRenderModel(
                title = "照片风格",
                activeFamily = FilterLabFamily.PHOTO,
                cards = listOf(card("photo-original", true)),
                isEnabled = true,
                supportingText = "",
                styleStrength = 1f
            ),
            previewBitmap = null,
            onApplyStyle = {},
            onStrengthPreviewChanged = {},
            onStrengthChanged = {},
            onOriginalComparisonChanged = {}
        )

        assertFalse(rail.getChildAt(0).isEnabled)
        assertEquals(android.view.View.GONE, rail.getChildAt(1).visibility)
        assertEquals(android.view.View.GONE, rail.getChildAt(2).visibility)
    }

    @Test
    fun `releasing transient rail state clears a pending strength preview`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rail = StylePresetCardRailView(context)
        val strengthEvents = mutableListOf<Float?>()
        rail.render(
            model = StylePresetRailRenderModel(
                title = "照片风格",
                activeFamily = FilterLabFamily.PHOTO,
                cards = listOf(card("photo-original", true)),
                isEnabled = true,
                supportingText = "",
                styleStrength = 1f
            ),
            previewBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888),
            onApplyStyle = {},
            onStrengthPreviewChanged = strengthEvents::add,
            onStrengthChanged = {},
            onOriginalComparisonChanged = {}
        )

        rail.releaseTransientStylePreview()

        assertEquals(listOf(null), strengthEvents)
    }

    private fun card(id: String, selected: Boolean): StylePresetCardRenderModel {
        val spec = FilterRenderSpec(saturation = if (id == "photo-vivid") 1.14f else 1f)
        return StylePresetCardRenderModel(
            profileId = id,
            title = id,
            family = FilterLabFamily.PHOTO,
            preview = spec.toStylePresetPreview(),
            isSelected = selected,
            isEnabled = true,
            applyAction = if (selected) null else PersistedSettingsAction.UpdatePhotoFilter(id),
            moodLabel = "",
            spec = spec
        )
    }

    private fun event(action: Int): MotionEvent = MotionEvent.obtain(0L, 0L, action, 0f, 0f, 0)
}
