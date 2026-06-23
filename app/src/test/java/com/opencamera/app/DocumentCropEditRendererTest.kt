package com.opencamera.app

import android.widget.FrameLayout
import androidx.core.view.isVisible
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DocumentCropEditRendererTest {

    private lateinit var overlay: DocumentCropEditOverlay
    private var confirmCount = 0
    private var cancelCount = 0
    private var lastDraggedEdges: CropEdges? = null

    @Before
    fun setup() {
        confirmCount = 0
        cancelCount = 0
        lastDraggedEdges = null
        val context = RuntimeEnvironment.getApplication()
        overlay = DocumentCropEditOverlay(
            context = context,
            onConfirm = { confirmCount++ },
            onCancel = { cancelCount++ },
            onEdgeDragged = { lastDraggedEdges = it }
        )
        // Add to a parent so layout occurs
        val root = FrameLayout(context)
        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        // Trigger layout
        root.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(1920, android.view.View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 1920)
    }

    @Test
    fun `overlay starts hidden`() {
        assertFalse(overlay.isVisible)
    }

    @Test
    fun `render with visible model shows overlay`() {
        val model = visibleModel()

        overlay.render(model)

        assertTrue(overlay.isVisible)
    }

    @Test
    fun `render with hidden model hides overlay`() {
        overlay.render(visibleModel())
        assertTrue(overlay.isVisible)

        overlay.render(hiddenModel())
        assertFalse(overlay.isVisible)
    }

    @Test
    fun `confirm button click triggers confirm callback`() {
        overlay.render(visibleModel())

        // Find and click confirm button
        val root = overlay.getChildAt(1) as android.widget.LinearLayout
        val buttonRow = root.getChildAt(2) as android.widget.LinearLayout
        val confirmButton = buttonRow.getChildAt(1) as android.widget.Button
        confirmButton.performClick()

        assertEquals(1, confirmCount)
    }

    @Test
    fun `cancel button click triggers cancel callback`() {
        overlay.render(visibleModel())

        val root = overlay.getChildAt(1) as android.widget.LinearLayout
        val buttonRow = root.getChildAt(2) as android.widget.LinearLayout
        val cancelButton = buttonRow.getChildAt(0) as android.widget.Button
        cancelButton.performClick()

        assertEquals(1, cancelCount)
    }

    @Test
    fun `render sets title text`() {
        val model = visibleModel(pageNumber = 5)

        overlay.render(model)

        val root = overlay.getChildAt(1) as android.widget.LinearLayout
        val titleView = root.getChildAt(0) as android.widget.TextView
        assertEquals("Page 5", titleView.text.toString())
    }

    @Test
    fun `render sets confirm and cancel button text`() {
        val model = visibleModel()

        overlay.render(model)

        val root = overlay.getChildAt(1) as android.widget.LinearLayout
        val buttonRow = root.getChildAt(2) as android.widget.LinearLayout
        val confirmButton = buttonRow.getChildAt(1) as android.widget.Button
        val cancelButton = buttonRow.getChildAt(0) as android.widget.Button
        assertEquals("Confirm", confirmButton.text.toString())
        assertEquals("Cancel", cancelButton.text.toString())
    }

    @Test
    fun `getCurrentEdges returns default edges after render`() {
        overlay.render(visibleModel())

        val edges = overlay.getCurrentEdges()
        assertEquals(0f, edges.left)
        assertEquals(0f, edges.top)
        assertEquals(1f, edges.right)
        assertEquals(1f, edges.bottom)
    }

    @Test
    fun `hidden model does not change overlay state`() {
        overlay.render(visibleModel())
        assertTrue(overlay.isVisible)

        overlay.render(hiddenModel())
        assertFalse(overlay.isVisible)
    }

    private fun visibleModel(
        itemId: String = "item-1",
        pageNumber: Int = 1
    ) = DocumentCropEditRenderModel(
        visible = true,
        itemId = itemId,
        pageNumber = pageNumber,
        pageRenderUri = "/images/test.jpg",
        cropEdges = CropEdges.default(),
        controlPoints = cropEditControlPoints(CropEdges.default()),
        titleText = String.format("Page %d", pageNumber),
        confirmLabel = "Confirm",
        cancelLabel = "Cancel"
    )

    private fun hiddenModel() = DocumentCropEditRenderModel(
        visible = false,
        itemId = "",
        pageNumber = 0,
        pageRenderUri = null,
        cropEdges = CropEdges.default(),
        controlPoints = cropEditControlPoints(CropEdges.default()),
        titleText = "",
        confirmLabel = "Confirm",
        cancelLabel = "Cancel"
    )
}
