package com.opencamera.app

import android.widget.Button
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
    private var movedUpItemId: String? = null
    private var movedDownItemId: String? = null

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        val rail = LinearLayout(context)
        val chip = TextView(context)
        val thumbnail = ImageView(context)
        val itemScroll = NestedScrollView(context)
        val itemList = LinearLayout(context)
        val moveUpButton = Button(context)
        val moveDownButton = Button(context)
        val overviewButton = Button(context)

        rail.addView(chip)
        rail.addView(thumbnail)
        itemScroll.addView(itemList)
        rail.addView(itemScroll)
        rail.addView(moveUpButton)
        rail.addView(moveDownButton)
        rail.addView(overviewButton)

        views = DocumentBatchRailViews(
            rail = rail,
            chip = chip,
            thumbnail = thumbnail,
            itemScroll = itemScroll,
            itemList = itemList,
            moveUpButton = moveUpButton,
            moveDownButton = moveDownButton,
            overviewButton = overviewButton
        )
        overviewClickCount = 0
        movedUpItemId = null
        movedDownItemId = null
    }

    private fun renderer(): DocumentBatchRailRenderer {
        return DocumentBatchRailRenderer(
            views = views,
            onRemoveItemClick = {},
            onMoveUpItemClick = { movedUpItemId = it },
            onMoveDownItemClick = { movedDownItemId = it },
            onExportRequested = { overviewClickCount++ }
        )
    }

    @Test
    fun `hides rail when model not visible`() {
        val model = DocumentBatchRailRenderModel(
            visible = false,
            countText = "",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = false
        )

        renderer().render(model)

        assertFalse(views.rail.visibility == android.view.View.VISIBLE)
    }

    @Test
    fun `slim shooting shows chip with count text`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "3 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = "/images/test.jpg",
            isSlimShooting = true,
            overviewLabel = "查看批次"
        )

        renderer().render(model)

        assertTrue(views.chip.visibility == android.view.View.VISIBLE)
        assertEquals("3 pages", views.chip.text.toString())
    }

    @Test
    fun `slim shooting keeps legacy thumbnail hidden when URI present`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "1 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = "/images/test.jpg",
            isSlimShooting = true,
            overviewLabel = "查看批次"
        )

        renderer().render(model)

        assertFalse(views.thumbnail.visibility == android.view.View.VISIBLE)
    }

    @Test
    fun `slim shooting hides thumbnail when no URI`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "1 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = null,
            isSlimShooting = true,
            overviewLabel = "查看批次"
        )

        renderer().render(model)

        assertFalse(views.thumbnail.visibility == android.view.View.VISIBLE)
    }

    @Test
    fun `slim shooting shows only export action below the page rail`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "2 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = null,
            isSlimShooting = true,
            overviewLabel = "导出文件"
        )

        renderer().render(model)

        assertFalse(views.moveUpButton.visibility == android.view.View.VISIBLE)
        assertFalse(views.moveDownButton.visibility == android.view.View.VISIBLE)
        assertTrue(views.overviewButton.visibility == android.view.View.VISIBLE)
        assertEquals("导出文件", views.overviewButton.text.toString())
    }

    @Test
    fun `overview button click triggers callback`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "2 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = null,
            isSlimShooting = true,
            overviewLabel = "查看批次"
        )

        renderer().render(model)
        views.overviewButton.performClick()

        assertEquals(1, overviewClickCount)
    }

    @Test
    fun `thumbnail click does not open the batch overview panel`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "1 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = "/images/test.jpg",
            isSlimShooting = true,
            overviewLabel = "查看批次"
        )

        renderer().render(model)
        views.thumbnail.performClick()

        assertEquals(0, overviewClickCount)
    }

    @Test
    fun `chip click does not open the batch overview panel`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "1 pages",
            items = emptyList(),
            latestItemId = null,
            organizeEnabled = true,
            latestThumbnailUri = null,
            isSlimShooting = true,
            overviewLabel = "查看批次"
        )

        renderer().render(model)
        views.chip.performClick()

        assertEquals(0, overviewClickCount)
    }

    @Test
    fun `slim shooting renders readable vertical page cells`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "2 pages",
            items = listOf(
                railItem("item-1", 1, isLatest = false, canMoveUp = false, canMoveDown = true),
                railItem("item-2", 2, isLatest = true, canMoveUp = true, canMoveDown = false)
            ),
            latestItemId = "item-2",
            organizeEnabled = true,
            latestThumbnailUri = "/images/item-2.jpg",
            isSlimShooting = true,
            overviewLabel = "整理",
            moveUpLabel = "上移",
            moveDownLabel = "下移"
        )

        renderer().render(model)

        assertEquals(2, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == android.view.View.VISIBLE)
        assertFalse(views.moveUpButton.visibility == android.view.View.VISIBLE)
        assertFalse(views.moveDownButton.visibility == android.view.View.VISIBLE)
    }

    @Test
    fun `slim shooting keeps long page list scrollable`() {
        val items = (1..12).map { page ->
            railItem(
                itemId = "item-$page",
                pageNumber = page,
                isLatest = page == 12,
                canMoveUp = page > 1,
                canMoveDown = page < 12
            )
        }
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "12 pages",
            items = items,
            latestItemId = "item-12",
            organizeEnabled = true,
            latestThumbnailUri = "/images/item-12.jpg",
            isSlimShooting = true,
            overviewLabel = "导出文件",
            moveUpLabel = "上移",
            moveDownLabel = "下移"
        )

        renderer().render(model)

        assertEquals(12, views.itemList.childCount)
        assertTrue(views.itemScroll.visibility == android.view.View.VISIBLE)
        assertTrue(views.itemScroll.isNestedScrollingEnabled)
        assertTrue(views.itemScroll.isVerticalScrollBarEnabled)
    }

    @Test
    fun `page cell click selects that page without opening the batch overview panel`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "3 pages",
            items = listOf(
                railItem("item-1", 1, isLatest = false, canMoveUp = false, canMoveDown = true),
                railItem("item-2", 2, isLatest = true, canMoveUp = true, canMoveDown = true),
                railItem("item-3", 3, isLatest = false, canMoveUp = true, canMoveDown = false)
            ),
            latestItemId = "item-2",
            organizeEnabled = true,
            latestThumbnailUri = "/images/item-2.jpg",
            isSlimShooting = true,
            overviewLabel = "整理",
            moveUpLabel = "上移",
            moveDownLabel = "下移"
        )

        renderer().render(model)
        val firstCell = views.itemList.getChildAt(0) as LinearLayout
        firstCell.performClick()
        val selectedFirstCell = views.itemList.getChildAt(0) as LinearLayout

        assertTrue(selectedFirstCell.isSelected)
        assertEquals(0, overviewClickCount)
        assertEquals(null, movedUpItemId)
        assertEquals(null, movedDownItemId)
    }

    @Test
    fun `non-selected page cells do not show move arrows`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "3 pages",
            items = listOf(
                railItem("item-1", 1, isLatest = false, canMoveUp = false, canMoveDown = true),
                railItem("item-2", 2, isLatest = true, canMoveUp = true, canMoveDown = true),
                railItem("item-3", 3, isLatest = false, canMoveUp = true, canMoveDown = false)
            ),
            latestItemId = "item-2",
            organizeEnabled = true,
            latestThumbnailUri = "/images/item-2.jpg",
            isSlimShooting = true,
            overviewLabel = "整理",
            moveUpLabel = "上移",
            moveDownLabel = "下移"
        )

        renderer().render(model)

        // Default selection falls to latestItemId = item-2; cells for item-1 and item-3
        // must not expose move arrows.
        assertNull(views.rail.findViewWithTag<android.widget.TextView>("move_up_item-1"))
        assertNull(views.rail.findViewWithTag<android.widget.TextView>("move_down_item-1"))
        assertNull(views.rail.findViewWithTag<android.widget.TextView>("move_up_item-3"))
        assertNull(views.rail.findViewWithTag<android.widget.TextView>("move_down_item-3"))
    }

    @Test
    fun `selected page cell shows large move controls`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "3 pages",
            items = listOf(
                railItem("item-1", 1, isLatest = false, canMoveUp = false, canMoveDown = true),
                railItem("item-2", 2, isLatest = true, canMoveUp = true, canMoveDown = true),
                railItem("item-3", 3, isLatest = false, canMoveUp = true, canMoveDown = false)
            ),
            latestItemId = "item-2",
            organizeEnabled = true,
            latestThumbnailUri = "/images/item-2.jpg",
            isSlimShooting = true,
            overviewLabel = "整理",
            moveUpLabel = "上移",
            moveDownLabel = "下移"
        )

        renderer().render(model)

        val moveUp = views.rail.findViewWithTag<android.widget.TextView>("move_up_item-2")
        val moveDown = views.rail.findViewWithTag<android.widget.TextView>("move_down_item-2")
        assertNotNull(moveUp, "Selected cell should expose a move-up control")
        assertNotNull(moveDown, "Selected cell should expose a move-down control")
        assertTrue(moveUp.visibility == android.view.View.VISIBLE)
        assertTrue(moveDown.visibility == android.view.View.VISIBLE)
        // Large touch target: at least 32dp tall.
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertTrue(moveDown.layoutParams.height >= (32 * density).toInt(),
            "Selected move control height=${moveDown.layoutParams.height} should be >= 32dp")
    }

    @Test
    fun `move arrow on selected cell targets that page instead of only latest page`() {
        val model = DocumentBatchRailRenderModel(
            visible = true,
            countText = "3 pages",
            items = listOf(
                railItem("item-1", 1, isLatest = false, canMoveUp = false, canMoveDown = true),
                railItem("item-2", 2, isLatest = true, canMoveUp = true, canMoveDown = true),
                railItem("item-3", 3, isLatest = false, canMoveUp = true, canMoveDown = false)
            ),
            latestItemId = "item-2",
            organizeEnabled = true,
            latestThumbnailUri = "/images/item-2.jpg",
            isSlimShooting = true,
            overviewLabel = "整理",
            moveUpLabel = "上移",
            moveDownLabel = "下移"
        )

        renderer().render(model)
        // Select item-1, then its move-down arrow should target item-1.
        val firstCell = views.itemList.getChildAt(0) as LinearLayout
        firstCell.performClick()
        val firstCellMoveDown = views.rail.findViewWithTag<android.widget.TextView>("move_down_item-1")
        assertNotNull(firstCellMoveDown)
        firstCellMoveDown.performClick()
        assertEquals("item-1", movedDownItemId)

        // Select item-3, then its move-up arrow should target item-3.
        val thirdCell = views.itemList.getChildAt(2) as LinearLayout
        thirdCell.performClick()
        val thirdCellMoveUp = views.rail.findViewWithTag<android.widget.TextView>("move_up_item-3")
        assertNotNull(thirdCellMoveUp)
        thirdCellMoveUp.performClick()
        assertEquals("item-3", movedUpItemId)
    }

    private fun railItem(
        itemId: String,
        pageNumber: Int,
        isLatest: Boolean,
        canMoveUp: Boolean,
        canMoveDown: Boolean
    ): DocumentBatchRailItemRenderModel {
        return DocumentBatchRailItemRenderModel(
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
}
