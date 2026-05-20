package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ThumbnailRenderCommandTest {
    @Test
    fun `same thumbnail path keeps current image`() {
        val command = nextThumbnailRenderCommand(
            previousRequestedUri = "file:///tmp/thumb.jpg",
            nextRequestedUri = "file:///tmp/thumb.jpg"
        )

        assertEquals(ThumbnailRenderCommand.NoOp, command)
    }

    @Test
    fun `null thumbnail path clears current image`() {
        val command = nextThumbnailRenderCommand(
            previousRequestedUri = "file:///tmp/thumb.jpg",
            nextRequestedUri = null
        )

        assertEquals(ThumbnailRenderCommand.Clear, command)
    }

    @Test
    fun `new thumbnail path requests reload`() {
        val command = nextThumbnailRenderCommand(
            previousRequestedUri = "file:///tmp/thumb-a.jpg",
            nextRequestedUri = "content://media/external/images/media/7"
        )

        assertEquals(
            ThumbnailRenderCommand.Load("content://media/external/images/media/7"),
            command
        )
    }
}
