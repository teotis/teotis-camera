package com.opencamera.app

import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val itemList = LinearLayout(context)
        val moveUpButton = Button(context)
        val moveDownButton = Button(context)
        val overviewButton = Button(context)

        rail.addView(chip)
        rail.addView(thumbnail)
        rail.addView(itemList)
        rail.addView(moveUpButton)
        rail.addView(moveDownButton)
        rail.addView(overviewButton)

        views = DocumentBatchRailViews(
            rail = rail,
            chip = chip,
            thumbnail = thumbnail,
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
            onOverviewRequested = { overviewClickCount++ }
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
    fun `slim shooting shows overview button`() {
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

        assertTrue(views.overviewButton.visibility == android.view.View.VISIBLE)
        assertEquals("查看批次", views.overviewButton.text.toString())
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
    fun `thumbnail click triggers overview callback`() {
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

        assertEquals(1, overviewClickCount)
    }

    @Test
    fun `chip click triggers overview callback`() {
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

        assertEquals(1, overviewClickCount)
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
        assertEquals("上移", views.moveUpButton.text.toString())
        assertTrue(views.moveUpButton.isEnabled)
        assertEquals("下移", views.moveDownButton.text.toString())
        assertFalse(views.moveDownButton.isEnabled)
    }

    @Test
    fun `move buttons target latest page in the rail`() {
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
        views.moveUpButton.performClick()
        views.moveDownButton.performClick()

        assertEquals("item-2", movedUpItemId)
        assertEquals("item-2", movedDownItemId)
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
