package com.opencamera.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.opencamera.core.effect.WatermarkHintSpec

/** Loads and draws the optional bitmap material used by high-design watermark templates. */
internal class HighDesignWatermarkAssetRenderer(context: Context) {
    private val appContext = context.applicationContext
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { isDither = true }
    private val cache = mutableMapOf<String, Bitmap?>()

    fun draw(
        canvas: Canvas,
        spec: WatermarkHintSpec,
        frameRect: RectF
    ): Boolean {
        val destination = highDesignWatermarkFrameMetrics(
            photoWidth = frameRect.width(),
            photoHeight = frameRect.height()
        ).destinationAround(frameRect)
        if (destination.width() <= 0f || destination.height() <= 0f) return false
        val assetPath = assetPath(spec.templateId, destination) ?: return false
        val asset = asset(assetPath) ?: return false
        paint.alpha = (spec.opacity * 255).toInt().coerceIn(72, 235)
        canvas.drawBitmap(asset, null, destination, paint)
        paint.alpha = 255
        return true
    }

    private fun assetPath(templateId: String, destination: RectF): String? {
        val aspect = destination.width() / destination.height().coerceAtLeast(1f)
        val suffix = highDesignWatermarkAssetSuffix(aspect)
        return when (templateId) {
            "van-gogh-starry" -> "watermarks/van_gogh_starry_$suffix.png"
            "blue-hour" -> "watermarks/blue_hour_$suffix.png"
            else -> null
        }
    }

    private fun asset(path: String): Bitmap? = cache.getOrPut(path) {
        runCatching { appContext.assets.open(path).use(BitmapFactory::decodeStream) }.getOrNull()
    }
}
