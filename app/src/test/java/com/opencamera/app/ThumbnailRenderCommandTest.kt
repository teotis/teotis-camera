package com.opencamera.app

import com.opencamera.core.session.SavedMediaType
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

    @Test
    fun `photo source loads original content uri`() {
        val command = nextThumbnailRenderCommand(
            previousRequestedUri = null,
            nextRequestedUri = "content://media/external/images/media/42"
        )

        assertEquals(
            ThumbnailRenderCommand.Load("content://media/external/images/media/42"),
            command
        )
    }

    @Test
    fun `video source requests materialized thumbnail uri`() {
        val cachedUri = "file:///cache/video-thumbnails/42.jpg"
        val identity = sourceIdentityFor("content://media/external/video/media/42", SavedMediaType.VIDEO)

        val command = nextThumbnailRenderCommand(
            previousRequestedUri = null,
            nextRequestedUri = cachedUri,
            previousSourceIdentity = null,
            nextSourceIdentity = identity
        )

        assertEquals(
            ThumbnailRenderCommand.Load(cachedUri, "video:content://media/external/video/media/42"),
            command
        )
    }

    @Test
    fun `same source and same media type is no op`() {
        val cachedUri = "file:///cache/video-thumbnails/42.jpg"
        val identity = sourceIdentityFor("content://media/external/video/media/42", SavedMediaType.VIDEO)

        val command = nextThumbnailRenderCommand(
            previousRequestedUri = cachedUri,
            nextRequestedUri = cachedUri,
            previousSourceIdentity = identity,
            nextSourceIdentity = identity
        )

        assertEquals(ThumbnailRenderCommand.NoOp, command)
    }

    @Test
    fun `same uri with different media type is not the same render request`() {
        val uri = "content://media/external/images/media/42"
        val photoIdentity = sourceIdentityFor(uri, SavedMediaType.PHOTO)
        val videoIdentity = sourceIdentityFor(uri, SavedMediaType.VIDEO)

        val command = nextThumbnailRenderCommand(
            previousRequestedUri = uri,
            nextRequestedUri = "file:///cache/video-thumbnails/42.jpg",
            previousSourceIdentity = photoIdentity,
            nextSourceIdentity = videoIdentity
        )

        assert(command is ThumbnailRenderCommand.Load)
    }

    @Test
    fun `source identity for photo is uri itself`() {
        val identity = sourceIdentityFor("content://media/external/images/media/42", SavedMediaType.PHOTO)
        assertEquals("content://media/external/images/media/42", identity)
    }

    @Test
    fun `source identity for video includes prefix`() {
        val identity = sourceIdentityFor("content://media/external/video/media/42", SavedMediaType.VIDEO)
        assertEquals("video:content://media/external/video/media/42", identity)
    }
}
