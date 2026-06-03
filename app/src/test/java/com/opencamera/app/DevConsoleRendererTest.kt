package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals

class DevConsoleRendererTest {

    @Test
    fun `bottom scroll target clamps to scrollable content`() {
        assertEquals(0, devConsoleBottomScrollY(viewHeight = 480, contentHeight = 320))
        assertEquals(360, devConsoleBottomScrollY(viewHeight = 480, contentHeight = 840))
    }
}
