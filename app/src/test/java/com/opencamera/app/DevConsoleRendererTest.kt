package com.opencamera.app

import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DevConsoleRendererTest {

    @Test
    fun `bottom scroll target clamps to scrollable content`() {
        assertEquals(0, devConsoleBottomScrollY(viewHeight = 480, contentHeight = 320))
        assertEquals(360, devConsoleBottomScrollY(viewHeight = 480, contentHeight = 840))
    }

    @Test
    fun `renderer installs layout manager for event list`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_OpenCamera
        )
        val recycler = RecyclerView(context)

        DevConsoleRenderer(context, devConsoleViews(context, recycler))

        assertNotNull(recycler.layoutManager)
    }

    @Test
    fun `renderer shows summary text on first dev panel render`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_OpenCamera
        )
        val recycler = RecyclerView(context)
        val views = devConsoleViews(context, recycler)
        val renderer = DevConsoleRenderer(context, views)

        renderer.render(
            DevLogRenderModel(
                isAvailable = true,
                selectedTab = DevLogTab.ALL,
                title = "全部事件",
                summaryText = "状态:ACTIVE | 模式:PHOTO | 拍摄:IDLE",
                visibleEvents = emptyList()
            )
        )

        assertEquals("状态:ACTIVE | 模式:PHOTO | 拍摄:IDLE", views.summary.text.toString())
        assertTrue(views.summary.isVisible)
    }

    private fun devConsoleViews(
        context: android.content.Context,
        recycler: RecyclerView
    ): DevConsoleViews {
        return DevConsoleViews(
            entry = Button(context),
            panel = MaterialCardView(context),
            scroll = NestedScrollView(context),
            tabKey = Button(context),
            tabCore = Button(context),
            tabError = Button(context),
            tabAll = Button(context),
            title = TextView(context),
            summary = TextView(context),
            eventsRecycler = recycler,
            storageInfo = TextView(context),
            export = Button(context),
            vendorProbe = Button(context),
            close = Button(context),
            scrollTop = Button(context),
            scrollBottom = Button(context)
        )
    }
}
