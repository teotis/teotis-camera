package com.opencamera.app.camera.live

import android.app.AlertDialog
import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Minimal in-app playback surface for a motion photo. Renders the exact motion segment
 * (embedded MP4 or sidecar) with [android.media.MediaPlayer]; this is the app's own
 * truth proof that the motion data decodes, independent of system galleries.
 */
class MotionPhotoPlaybackDialog(
    private val context: Context,
    private val segment: MotionPlaybackSegment,
    private val controller: MotionPhotoPlaybackController = MotionPhotoPlaybackController(context)
) {

    private var surfaceView: SurfaceView? = null
    private var surfacePlayer: MotionPhotoSurfacePlayer? = null
    private var dialog: AlertDialog? = null

    fun show() {
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val surface = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        frame.addView(surface)
        surfaceView = surface

        val surfacePlayer = MotionPhotoSurfacePlayer(controller)
        this.surfacePlayer = surfacePlayer
        surface.holder.addCallback(surfacePlayer)

        dialog = AlertDialog.Builder(context)
            .setTitle("实况片段播放（应用内验证）")
            .setView(frame)
            .setPositiveButton("关闭") { d, _ -> d.dismiss() }
            .setCancelable(true)
            .setOnDismissListener { surfacePlayer.stop() }
            .create()
        dialog?.show()

        // If the surface was already created synchronously, start playback now.
        surfacePlayer.play(segment)
    }

    fun dismiss() {
        dialog?.dismiss()
        surfaceView?.holder?.removeCallback(surfacePlayer)
        surfacePlayer?.stop()
        surfacePlayer = null
        dialog = null
    }
}
