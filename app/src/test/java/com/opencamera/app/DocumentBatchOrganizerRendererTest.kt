package com.opencamera.app

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DocumentBatchOrganizerRendererTest {

    private lateinit var views: DocumentBatchOrganizerViews
    private var movedUpItemId: String? = null
    private var movedDownItemId: String? = null
    private var exportClickCount = 0

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val panel = LinearLayout(context)
        val scroll = NestedScrollView(context)
        val itemList = LinearLayout(context)
        scroll.addView(itemList)
        panel.addView(scroll)
        val title = TextView(context)
        val count = TextView(context)
        val close = Button(context)
        val emptyHint = TextView(context)
        val footer = LinearLayout(context)
        val continueShooting = Button(context)
        val exportButton = Button(context)
        footer.addView(continueShooting)
        footer.addView(exportButton)
        panel.addView(footer)
        val status = TextView(context)
        panel.addView(status)

        views = DocumentBatchOrganizerViews(
            panel = panel,
            scroll = scroll,
            title = title,
            count = count,
            itemList = itemList,
            close = close,
            emptyHint = emptyHint,
            footer = footer,
            continueShooting = continueShooting,
            exportButton = exportButton,
            status = status
        )
        movedUpItemId = null
        movedDownItemId = null
        exportClickCount = 0
    }

    private fun renderer(): DocumentBatchOrganizerRenderer {
        return DocumentBatchOrganizerRenderer(
            views = views,
            onRemoveItemClick = {},
            onMoveUpItemClick = { movedUpItemId = it },
            onMoveDownItemClick = { movedDownItemId = it },
            onCropEditItemClick = {},
            onContinueShooting = {},
            onExport = { exportClickCount++ }
        )
    }

    private fun organizerItem(
        itemId: String,
        pageNumber: Int,
        canMoveUp: Boolean,
        canMoveDown: Boolean
    ): DocumentBatchOrganizerItemRenderModel {
        return DocumentBatchOrganizerItemRenderModel(
            itemId = itemId,
            pageNumber = pageNumber,
            renderUri = "/images/$itemId.jpg",
            cropStatusLabel = null,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            cropEditLabel = null
        )
    }

    private fun model(
        items: List<DocumentBatchOrganizerItemRenderModel>,
        showExport: Boolean = items.isNotEmpty(),
        statusMessage: String? = null
    ): DocumentBatchOrganizerRenderModel {
        return DocumentBatchOrganizerRenderModel(
            visible = true,
            title = "文档批次",
            countText = "${items.size} pages",
            items = items,
            isBatchOverviewMode = true,
            showContinueShooting = true,
            showExport = showExport,
            emptyHint = "空",
            statusMessage = statusMessage
        )
    }

    @Test
    fun `non-selected rows do not show move arrows`() {
        val model = model(
            items = listOf(
                organizerItem("item-1", 1, canMoveUp = false, canMoveDown = true),
                organizerItem("item-2", 2, canMoveUp = true, canMoveDown = true),
                organizerItem("item-3", 3, canMoveUp = true, canMoveDown = false)
            )
        )

        renderer().render(model)

        // Default selection is the last item (item-3); item-1 and item-2 must not show arrows.
        assertNull(views.panel.findViewWithTag<TextView>("move_up_item-1"))
        assertNull(views.panel.findViewWithTag<TextView>("move_down_item-1"))
        assertNull(views.panel.findViewWithTag<TextView>("move_up_item-2"))
        assertNull(views.panel.findViewWithTag<TextView>("move_down_item-2"))
    }

    @Test
    fun `selected row shows large move controls`() {
        val model = model(
            items = listOf(
                organizerItem("item-1", 1, canMoveUp = false, canMoveDown = true),
                organizerItem("item-2", 2, canMoveUp = true, canMoveDown = true),
                organizerItem("item-3", 3, canMoveUp = true, canMoveDown = false)
            )
        )

        renderer().render(model)

        val moveUp = views.panel.findViewWithTag<TextView>("move_up_item-3")
        val moveDown = views.panel.findViewWithTag<TextView>("move_down_item-3")
        assertNotNull(moveUp, "Selected row should expose a move-up control")
        assertNotNull(moveDown, "Selected row should expose a move-down control")
        assertTrue(moveUp.visibility == View.VISIBLE)
        assertTrue(moveDown.visibility == View.VISIBLE)
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue(moveDown.layoutParams.height >= (36 * density).toInt(),
            "Selected move control height=${moveDown.layoutParams.height} should be >= 36dp")
    }

    @Test
    fun `selecting a row surfaces its move controls and targets that row`() {
        val model = model(
            items = listOf(
                organizerItem("item-1", 1, canMoveUp = false, canMoveDown = true),
                organizerItem("item-2", 2, canMoveUp = true, canMoveDown = true),
                organizerItem("item-3", 3, canMoveUp = true, canMoveDown = false)
            )
        )

        renderer().render(model)
        // Tap item-1 row to select it.
        val firstRow = views.itemList.getChildAt(0)
        firstRow.performClick()
        val moveDown = views.panel.findViewWithTag<TextView>("move_down_item-1")
        assertNotNull(moveDown)
        moveDown.performClick()
        assertEquals("item-1", movedDownItemId)
    }

    @Test
    fun `move keeps the moved item selected`() {
        val model = model(
            items = listOf(
                organizerItem("item-1", 1, canMoveUp = false, canMoveDown = true),
                organizerItem("item-2", 2, canMoveUp = true, canMoveDown = true)
            )
        )

        val r = renderer()
        r.render(model)
        // Select item-1, move it down.
        val firstRow = views.itemList.getChildAt(0)
        firstRow.performClick()
        val moveDown = views.panel.findViewWithTag<TextView>("move_down_item-1")
        assertNotNull(moveDown)
        moveDown.performClick()
        assertEquals("item-1", movedDownItemId)

        // After a re-render with item-1 still present, it should remain selected and
        // continue to expose its own move controls.
        r.render(model)
        assertNotNull(views.panel.findViewWithTag<TextView>("move_down_item-1"))
    }

    @Test
    fun `page numbers update after reorder`() {
        val model = model(
            items = listOf(
                organizerItem("item-1", 1, canMoveUp = false, canMoveDown = true),
                organizerItem("item-2", 2, canMoveUp = true, canMoveDown = true),
                organizerItem("item-3", 3, canMoveUp = true, canMoveDown = false)
            )
        )

        renderer().render(model)
        // After a reorder (simulated by re-rendering with a swapped list), page numbers
        // follow the new order.
        val reordered = model(
            items = listOf(
                organizerItem("item-2", 1, canMoveUp = false, canMoveDown = true),
                organizerItem("item-1", 2, canMoveUp = true, canMoveDown = true),
                organizerItem("item-3", 3, canMoveUp = true, canMoveDown = false)
            )
        )
        renderer().render(reordered)
        val firstRow = views.itemList.getChildAt(0) as LinearLayout
        val topRow = firstRow.getChildAt(0) as LinearLayout
        val pageLabel = topRow.getChildAt(0) as TextView
        assertEquals("1", pageLabel.text.toString())
    }

    @Test
    fun `status message is shown when present`() {
        val model = model(
            items = listOf(organizerItem("item-1", 1, canMoveUp = false, canMoveDown = false)),
            statusMessage = "已移动"
        )

        renderer().render(model)

        assertEquals("已移动", views.status.text.toString())
        assertTrue(views.status.visibility == View.VISIBLE)
    }

    @Test
    fun `status message is hidden when absent`() {
        val model = model(
            items = listOf(organizerItem("item-1", 1, canMoveUp = false, canMoveDown = false)),
            statusMessage = null
        )

        renderer().render(model)

        assertFalse(views.status.visibility == View.VISIBLE)
    }

    @Test
    fun `export button is always reachable outside scrollable item list`() {
        val items = (1..8).map { page ->
            organizerItem("item-$page", page, canMoveUp = page > 1, canMoveDown = page < 8)
        }
        val model = model(items = items, showExport = true)

        renderer().render(model)

        assertTrue(views.footer.visibility == View.VISIBLE)
        assertTrue(views.exportButton.visibility == View.VISIBLE)
        // Footer is a sibling of the scroll area, not a descendant of the scroll content.
        assertFalse(isDescendantOf(views.footer, views.scroll))
    }

    private fun isDescendantOf(candidate: View, ancestor: View): Boolean {
        var parent = candidate.parent
        while (parent != null) {
            if (parent === ancestor) return true
            parent = parent.parent
        }
        return false
    }
}
