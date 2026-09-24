package com.opencamera.app.camera.live

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import java.io.FileDescriptor

private const val TAG = "MotionPhotoPlaybackController"

/**
 * Thin MediaPlayer wrapper that plays a resolved [MotionPlaybackSegment].
 *
 * - Embedded MP4: `setDataSource(fd, offset, length)` plays exactly the appended motion
 *   bytes of a Motion Photo JPEG (no copy needed).
 * - Sidecar MP4: plays the MediaStore content URI directly.
 *
 * Playback is the minimum in-app truth proof for motion data; it is not a substitute
 * for system-gallery recognition evidence.
 */
class MotionPhotoPlaybackController(
    private val context: Context,
    private val playerFactory: () -> MediaPlayer = { MediaPlayer() }
) {

    private var player: MediaPlayer? = null

    fun isPlaying(): Boolean = player?.isPlaying == true

    fun play(
        segment: MotionPlaybackSegment,
        surfaceHolder: SurfaceHolder?,
        onCompletion: () -> Unit = {}
    ): Result<Unit> = runCatching {
        stop()
        val mp = playerFactory()
        try {
            mp.setOnCompletionListener {
                onCompletion()
                releaseSafely(it)
                if (player === it) player = null
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "playback error what=$what extra=$extra")
                onCompletion()
                releaseSafely(mp)
                if (player === mp) player = null
                true
            }
            surfaceHolder?.let { mp.setDisplay(it) }

            when (segment.kind) {
                MotionPlaybackKind.EMBEDDED_MP4 -> {
                    val uri = Uri.parse(segment.location)
                    val offset = segment.offsetBytes ?: error("embedded segment requires offset")
                    val length = segment.lengthBytes ?: error("embedded segment requires length")
                    if (uri.scheme == "content") {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                            mp.setDataSource(fd.fileDescriptor, offset, length)
                        } ?: throw IllegalStateException("cannot open motion photo $uri")
                    } else {
                        context.contentResolver.openFileDescriptor(
                            Uri.fromFile(java.io.File(segment.location)), "r"
                        )?.use { fd ->
                            mp.setDataSource(fd.fileDescriptor, offset, length)
                        } ?: throw IllegalStateException("cannot open motion photo file ${segment.location}")
                    }
                }

                MotionPlaybackKind.SIDECAR_MP4 -> {
                    val uri = Uri.parse(segment.location)
                    if (uri.scheme == "content") {
                        mp.setDataSource(context, uri)
                    } else {
                        mp.setDataSource(segment.location)
                    }
                }
            }

            mp.prepare()
            player = mp
            mp.start()
        } catch (t: Throwable) {
            releaseSafely(mp)
            if (player === mp) player = null
            throw t
        }
    }.onFailure { e ->
        Log.w(TAG, "play failed segment=$segment", e)
    }

    fun stop() {
        player?.let { p ->
            p.setOnCompletionListener(null)
            p.setOnErrorListener(null)
            releaseSafely(p)
        }
        player = null
    }

    private fun releaseSafely(mp: MediaPlayer) {
        runCatching { mp.stop() }
        runCatching { mp.release() }
    }
}

/** Binds a [SurfaceHolder] and plays the segment; surfaces are replayed after re-creation. */
class MotionPhotoSurfacePlayer(
    private val controller: MotionPhotoPlaybackController
) : SurfaceHolder.Callback {

    private var pendingSegment: MotionPlaybackSegment? = null
    private var holder: SurfaceHolder? = null

    fun play(segment: MotionPlaybackSegment) {
        pendingSegment = segment
        val h = holder
        if (h != null) {
            controller.play(segment, h)
        }
    }

    fun stop() {
        pendingSegment = null
        controller.stop()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        this.holder = holder
        pendingSegment?.let { controller.play(it, holder) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        this.holder = null
        controller.stop()
    }
}
