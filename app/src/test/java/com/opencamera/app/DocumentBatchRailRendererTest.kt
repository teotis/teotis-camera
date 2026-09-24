package com.opencamera.app

import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
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
class DocumentBatchRailRendererTest {

    private lateinit var views: DocumentBatchRailViews
    private var overviewClickCount = 0
    private var clearClickCount = 0
    private var movedUpItemId: String? = null
    private var movedDownItemId: String? = null
    private var removedItemId: String? = null

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val overlay = FrameLayout(context)
        val rail = LinearLayout(context)
        val chip = TextView(context)
        val itemScroll = NestedScrollView(context)
        val itemList = LinearLayout(context)
        val actionContainer = LinearLayout(context)
        val moveUpButton = Button(context)
        val moveDownButton = Button(context)
        val removeButton = Button(context)
        val overviewButton = Button(context)
        val clearButton = Button(context)

        rail.addView(chip)
        itemScroll.addView(itemList)
        rail.addView(itemScroll)
        rail.addView(clearButton)
        actionContainer.addView(moveUpButton)
        actionContainer.addView(moveDownButton)
        actionContainer.addView(removeButton)
        actionContainer.addView(overviewButton)
        overlay.addView(rail)
        overlay.addView(actionContainer)

        views = DocumentBatchRailViews(
            overlay = overlay,
            rail = rail,
            chip = chip,
            itemScroll = itemScroll,
            itemList = itemList,
            actionContainer = actionContainer,
            moveUpButton = moveUpButton,
            moveDownButton = moveDownButton,
            removeButton = removeButton,
            overviewButton = overviewButton,
            clearButton = clearButton
        )
        overviewClickCount = 0
        clearClickCount = 0
        movedUpItemId = null
        movedDownItemId = null
        removedItemId = null
    }

    private fun renderer(): DocumentBatchRailRenderer = DocumentBatchRailRenderer(
        views = views,
        onRemoveItemClick = { removedItemId = it },
        onMoveUpItemClick = { movedUpItemId = it },
        onMoveDownItemClick = { movedDownItemId = it },
        onExportRequested = { overviewClickCount++ },
        onClearBatchClick = { clearClickCount++ }
    )

    private fun threePageModel(): DocumentBatchRailRenderModel = DocumentBatchRailRenderModel(
        visible = true,
        countText = "3 pages",
        items = listOf(
            railItem("item-1", 1, isLatest = false, canMoveUp = false, canMoveDown = true),
            railItem("item-2", 2, isLatest = true, canMoveUp = true, canMoveDown = true),
            railItem("item-3", 3, isLatest = false, canMoveUp = true, canMoveDown = false)
        ),
        latestItemId = "item-2",
        organizeEnabled = true,
        isSlimShooting = true,
        overviewLabel = "导出文件",
        moveUpLabel = "上移",
        moveDownLabel = "下移"
    )

    @Test
    fun `hides rail when model not visible`() {
        renderer().render(
            DocumentBatchRailRenderModel(
                visible = false,
                countText = "",
                items = emptyList(),
                latestItemId = null,
                organizeEnabled = false
            )
        )

        assertFalse(views.rail.visibility == View.VISIBLE)
    }

    @Test
    fun `shooting rail immediately shows ordered thumbnails without expand step`() {
        renderer().render(threePageModel())

        assertEquals("3 pages", views.chip.text.toString())
        assertEquals(3, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertTrue(views.itemList.getChildAt(0).contentDescription.toString().contains("1"))
        assertTrue(views.itemList.getChildAt(1).contentDescription.toString().contains("2"))
        assertTrue(views.itemList.getChildAt(2).contentDescription.toString().contains("3"))
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val browsingCellChrome = (44 * density).toInt()
        assertTrue(
            views.rail.layoutParams.width >= pageThumbnail(0).layoutParams.width + browsingCellChrome,
            "Browsing rail must fit thumbnail, page label, margins, and padding"
        )
    }

    @Test
    fun `first thumbnail tap immediately selects page and exposes floating actions`() {
        val r = renderer()
        r.render(threePageModel())

        pageCell(1).performClick()

        assertTrue(pageCell(1).isSelected)
        assertEquals("move_up_item-2", views.moveUpButton.tag)
        assertEquals("move_down_item-2", views.moveDownButton.tag)
        assertEquals("remove_item-2", views.removeButton.tag)
        assertEquals("export_batch", views.overviewButton.tag)
        assertTrue(views.actionContainer.visibility == View.VISIBLE)
        assertTrue(views.actionContainer.parent === views.overlay)
        assertFalse(views.actionContainer.parent === views.rail)
        val actionParams = views.actionContainer.layoutParams as FrameLayout.LayoutParams
        assertTrue(actionParams.marginStart >= views.rail.layoutParams.width)
        assertEquals(0, overviewClickCount)
    }

    @Test
    fun `selected thumbnail grows while other thumbnails stay compact`() {
        val r = renderer()
        r.render(threePageModel())
        val compactWidth = pageThumbnail(0).layoutParams.width
        val compactHeight = pageThumbnail(0).layoutParams.height

        pageCell(1).performClick()

        assertTrue(pageThumbnail(1).layoutParams.width > compactWidth)
        assertTrue(pageThumbnail(1).layoutParams.height > compactHeight)
        assertEquals(compactWidth, pageThumbnail(0).layoutParams.width)
        assertEquals(compactHeight, pageThumbnail(0).layoutParams.height)
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val selectedCellChrome = (44 * density).toInt()
        assertTrue(
            views.rail.layoutParams.width >= pageThumbnail(1).layoutParams.width + selectedCellChrome,
            "Selected rail must fit the enlarged thumbnail, page label, margins, and padding"
        )
    }

    @Test
    fun `tapping selected page hides actions but keeps every thumbnail visible`() {
        val r = renderer()
        r.render(threePageModel())
        pageCell(0).performClick()

        pageCell(0).performClick()

        assertEquals(3, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertFalse(pageCell(0).isSelected)
        assertNull(views.moveDownButton.tag)
        assertEquals("clear_batch", views.clearButton.tag)
    }

    @Test
    fun `rail blank area dismisses actions without collapsing thumbnail list`() {
        val r = renderer()
        r.render(threePageModel())
        pageCell(1).performClick()

        views.rail.performClick()

        assertEquals(3, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertNull(views.moveDownButton.tag)
        assertEquals("clear_batch", views.clearButton.tag)
    }

    @Test
    fun `page count is informational and never hides thumbnails`() {
        val r = renderer()
        r.render(threePageModel())
        pageCell(1).performClick()

        views.chip.performClick()

        assertEquals(3, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertNull(views.moveDownButton.tag)
        assertEquals(0, overviewClickCount)
    }

    @Test
    fun `preview dismissal clears actions but preserves one-step page access`() {
        val r = renderer()
        r.render(threePageModel())
        pageCell(1).performClick()

        assertTrue(r.dismissTransientActions())

        assertEquals(3, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertNull(views.moveUpButton.tag)
        assertNull(views.moveDownButton.tag)
        assertNull(views.overviewButton.tag)
        assertEquals("clear_batch", views.clearButton.tag)
    }

    @Test
    fun `quiet rail ignores preview dismissal`() {
        val r = renderer()
        r.render(threePageModel())

        assertFalse(r.dismissTransientActions())
        assertEquals(3, views.itemList.childCount)
    }

    @Test
    fun `new capture appends thumbnail and clears stale page actions`() {
        val model = threePageModel()
        val r = renderer()
        r.render(model)
        pageCell(0).performClick()

        r.render(
            model.copy(
                countText = "4 pages",
                items = model.items + railItem(
                    "item-4",
                    4,
                    isLatest = true,
                    canMoveUp = true,
                    canMoveDown = false
                ),
                latestItemId = "item-4"
            )
        )

        assertEquals(4, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertNull(views.moveDownButton.tag)
    }

    @Test
    fun `removing selected page keeps remaining thumbnails and removes stale actions`() {
        val model = threePageModel()
        val r = renderer()
        r.render(model)
        pageCell(0).performClick()

        r.render(
            model.copy(
                countText = "2 pages",
                items = model.items.drop(1),
                latestItemId = "item-3"
            )
        )

        assertEquals(2, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertNull(views.moveDownButton.tag)
    }

    @Test
    fun `selecting another thumbnail moves actions to that page`() {
        val r = renderer()
        r.render(threePageModel())
        pageCell(0).performClick()
        assertEquals("move_down_item-1", views.moveDownButton.tag)

        pageCell(2).performClick()

        assertFalse(pageCell(0).isSelected)
        assertTrue(pageCell(2).isSelected)
        assertEquals("move_down_item-3", views.moveDownButton.tag)
        assertFalse(views.moveDownButton.isEnabled)
        assertEquals("move_up_item-3", views.moveUpButton.tag)
    }

    @Test
    fun `selected action callbacks target selected page`() {
        val r = renderer()
        r.render(threePageModel())
        pageCell(1).performClick()

        views.moveUpButton.performClick()
        views.moveDownButton.performClick()
        views.removeButton.performClick()
        views.overviewButton.performClick()

        assertEquals("item-2", movedUpItemId)
        assertEquals("item-2", movedDownItemId)
        assertEquals("item-2", removedItemId)
        assertEquals(1, overviewClickCount)
    }

    @Test
    fun `batch clear remains guarded in browsing state`() {
        val r = renderer()
        r.render(threePageModel())

        views.clearButton.performClick()
        assertEquals(0, clearClickCount)
        views.clearButton.performClick()

        assertEquals(1, clearClickCount)
        assertEquals(3, views.itemList.childCount)
    }

    @Test
    fun `long page sequence stays scrollable from default state`() {
        val items = (1..12).map { page ->
            railItem(
                itemId = "item-$page",
                pageNumber = page,
                isLatest = page == 12,
                canMoveUp = page > 1,
                canMoveDown = page < 12
            )
        }
        val model = threePageModel().copy(
            countText = "12 pages",
            items = items,
            latestItemId = "item-12"
        )

        renderer().render(model)

        assertEquals(12, views.itemList.childCount)
        assertTrue(views.itemScroll.isNestedScrollingEnabled)
        assertTrue(views.itemScroll.isVerticalScrollBarEnabled)
    }

    @Test
    fun `mode route reset returns to visible browsing rail`() {
        val model = threePageModel()
        val r = renderer()
        r.render(model)
        pageCell(0).performClick()

        r.render(model.copy(visible = false, isSlimShooting = false))
        r.render(model)

        assertEquals(3, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == View.VISIBLE)
        assertNull(views.moveDownButton.tag)
    }

    private fun pageCell(index: Int): LinearLayout =
        views.itemList.getChildAt(index) as LinearLayout

    private fun pageThumbnail(index: Int): ImageView {
        val topRow = pageCell(index).getChildAt(0) as LinearLayout
        return topRow.getChildAt(1) as ImageView
    }

    private fun railItem(
        itemId: String,
        pageNumber: Int,
        isLatest: Boolean,
        canMoveUp: Boolean,
        canMoveDown: Boolean
    ): DocumentBatchRailItemRenderModel = DocumentBatchRailItemRenderModel(
        itemId = itemId,
        pageNumber = pageNumber,
        renderUri = "/images/$itemId.jpg",
        statusLabel = null,
        isLatest = isLatest,
        canMoveUp = canMoveUp,
        canMoveDown = canMoveDown,
        removeContentDescription = "Remove"
    )
}
