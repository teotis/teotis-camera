package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.TypedValue
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.effect.WatermarkPreviewDecoration
import com.opencamera.core.effect.WatermarkPreviewShape
import com.opencamera.core.effect.TravelTicketPaperSpec
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.math.min

/**
 * Renders watermark preview treatments against the active camera-content frame.
 *
 * The overlay owns geometry; this collaborator owns template-specific visual material.
 */
internal class PreviewWatermarkHintRenderer(
    context: Context,
    private val density: Float,
    private val displayMetrics: DisplayMetrics,
    private val activeFrameRect: () -> RectF,
    private val viewWidth: () -> Int,
    private val viewHeight: () -> Int
) {
    private val watermarkHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            displayMetrics
        )
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val watermarkHintBaseTextSizeSp = 12f
    private val watermarkBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val watermarkPaperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val watermarkHairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val watermarkBlurBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val highDesignWatermarkAssetRenderer = HighDesignWatermarkAssetRenderer(context)

    fun draw(canvas: Canvas, spec: WatermarkHintSpec) {
        when (spec.shape) {
            WatermarkPreviewShape.FOUR_BORDER -> drawWatermarkFourBorderHint(canvas, spec)
            WatermarkPreviewShape.TEXT_ONLY,
            WatermarkPreviewShape.BACKED_TEXT -> drawWatermarkTextHint(canvas, spec)
            WatermarkPreviewShape.EXPANDED_FRAME -> drawWatermarkExpandedFrameHint(canvas, spec)
            WatermarkPreviewShape.BOTTOM_BAR -> drawWatermarkBottomBarHint(canvas, spec)
        }
    }

    private fun applyWatermarkTextScale(textScale: Float) {
        watermarkHintPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            watermarkHintBaseTextSizeSp * textScale,
            displayMetrics
        )
    }

    private fun drawWatermarkTextHint(canvas: Canvas, spec: WatermarkHintSpec) {
        applyWatermarkTextScale(spec.textScale)
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val rect = activeFrameRect()
        val padding = 16f * density
        val x: Float
        val y: Float
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT -> {
                x = rect.left + padding
                y = rect.top + padding + watermarkHintPaint.textSize
            }
            WatermarkTextPlacement.TOP_RIGHT -> {
                x = rect.right - padding
                y = rect.top + padding + watermarkHintPaint.textSize
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_LEFT -> {
                x = rect.left + padding
                y = rect.bottom - padding
            }
            WatermarkTextPlacement.BOTTOM_RIGHT -> {
                x = rect.right - padding
                y = rect.bottom - padding
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_CENTER -> {
                x = rect.centerX()
                y = rect.bottom - padding
                watermarkHintPaint.textAlign = Paint.Align.CENTER
            }
        }
        canvas.drawText(spec.previewText, x, y, watermarkHintPaint)
    }

    private fun drawWatermarkExpandedFrameHint(canvas: Canvas, spec: WatermarkHintSpec) {
        applyWatermarkTextScale(spec.textScale)
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val previousTextColor = watermarkHintPaint.color
        watermarkHintPaint.color = when (spec.decoration) {
            WatermarkPreviewDecoration.TRAVEL_TICKET -> TravelTicketPaperSpec.BLACK_COLOR
            WatermarkPreviewDecoration.ARCHIVAL_PAPER -> Color.rgb(224, 205, 154)
            WatermarkPreviewDecoration.NIGHT_MEMORY -> Color.rgb(226, 232, 240)
            WatermarkPreviewDecoration.STARRY_MOON -> Color.rgb(232, 205, 146)
            WatermarkPreviewDecoration.BLUE_HOUR -> Color.rgb(196, 218, 246)
            WatermarkPreviewDecoration.IMPRESSION_CHROMA -> Color.rgb(58, 55, 50)
            WatermarkPreviewDecoration.NONE -> Color.WHITE
        }
        val rect = activeFrameRect()
        val paperAlpha = expandedFramePaperAlpha(spec.templateId, spec.opacity)
        watermarkPaperPaint.color = when (spec.templateId) {
            "retro-frame" -> Color.argb((spec.opacity * 184).toInt().coerceIn(0, 184), 14, 36, 29)
            "night-street" -> Color.argb((spec.opacity * 210).toInt().coerceIn(0, 210), 7, 14, 36)
            "van-gogh-starry" -> Color.argb((spec.opacity * 218).toInt().coerceIn(0, 218), 5, 18, 48)
            "blue-hour" -> Color.argb((spec.opacity * 226).toInt().coerceIn(0, 226), 4, 24, 46)
            else -> Color.argb(paperAlpha, 252, 246, 229)
        }
        watermarkHairlinePaint.color = when (spec.templateId) {
            "retro-frame" -> Color.argb((spec.opacity * 148).toInt().coerceIn(0, 148), 218, 190, 126)
            "night-street" -> Color.argb((spec.opacity * 88).toInt().coerceIn(0, 88), 168, 178, 198)
            "van-gogh-starry" -> Color.argb((spec.opacity * 126).toInt().coerceIn(0, 126), 218, 170, 84)
            "blue-hour" -> Color.argb((spec.opacity * 156).toInt().coerceIn(0, 156), 156, 204, 250)
            else -> Color.argb((spec.opacity * 72).toInt().coerceIn(0, 72), 96, 68, 42)
        }
        val sideBand = if (spec.templateId == "travel-polaroid") {
            (rect.width() * TravelTicketPaperSpec.SIDE_BORDER_RATIO).coerceIn(14f * density, 42f * density)
        } else {
            (rect.width() * 0.035f).coerceIn(10f * density, 28f * density)
        }
        val topBand = if (spec.templateId == "travel-polaroid") {
            sideBand
        } else {
            (rect.height() * 0.035f).coerceIn(8f * density, 24f * density)
        }
        val leftBand = sideBand.coerceAtMost(rect.left)
        val rightBand = sideBand.coerceAtMost(viewWidth() - rect.right)
        val topFrameBand = topBand.coerceAtMost(rect.top)
        if (leftBand > 0f) {
            canvas.drawRect(rect.left - leftBand, rect.top, rect.left, rect.bottom, watermarkPaperPaint)
        }
        if (rightBand > 0f) {
            canvas.drawRect(rect.right, rect.top, rect.right + rightBand, rect.bottom, watermarkPaperPaint)
        }
        if (topFrameBand > 0f) {
            canvas.drawRect(rect.left - leftBand, rect.top - topFrameBand, rect.right + rightBand, rect.top, watermarkPaperPaint)
        }
        val bottomRect = expandedFrameBottomBandRect(rect, viewHeight(), density, spec.templateId)
        if (bottomRect != null) {
            canvas.drawRect(
                bottomRect.left - leftBand,
                bottomRect.top,
                bottomRect.right + rightBand,
                bottomRect.bottom,
                watermarkPaperPaint
            )
        }
        canvas.drawRect(rect, watermarkHairlinePaint)
        val drewHighDesignMaterial = highDesignWatermarkAssetRenderer.draw(
            canvas = canvas,
            spec = spec,
            frameRect = rect
        )
        if (!drewHighDesignMaterial) {
            when (spec.decoration) {
                WatermarkPreviewDecoration.TRAVEL_TICKET -> {
                    bottomRect?.let { drawTravelTicketPreviewDecoration(canvas, it, spec) }
                }
                WatermarkPreviewDecoration.ARCHIVAL_PAPER -> {
                    drawArchivalPaperPreviewDecoration(canvas, rect, spec.opacity)
                }
                WatermarkPreviewDecoration.NIGHT_MEMORY -> {
                    drawNightMemoryPreviewDecoration(canvas, rect, bottomRect, spec.opacity)
                }
                WatermarkPreviewDecoration.STARRY_MOON -> {
                    drawStarryMoonPreviewDecoration(canvas, rect, bottomRect, spec.opacity)
                }
                WatermarkPreviewDecoration.BLUE_HOUR -> {
                    drawBlueHourPreviewDecoration(canvas, rect, bottomRect, spec.opacity)
                }
                WatermarkPreviewDecoration.IMPRESSION_CHROMA,
                WatermarkPreviewDecoration.NONE -> Unit
            }
        }

        if (bottomRect != null && spec.decoration == WatermarkPreviewDecoration.STARRY_MOON) {
            drawStarryMoonPreviewText(canvas, spec, bottomRect)
            watermarkHintPaint.color = previousTextColor
            return
        }
        if (bottomRect != null && spec.decoration == WatermarkPreviewDecoration.BLUE_HOUR) {
            drawBlueHourPreviewText(canvas, spec, bottomRect)
            watermarkHintPaint.color = previousTextColor
            return
        }
        if (bottomRect != null && spec.decoration == WatermarkPreviewDecoration.TRAVEL_TICKET) {
            drawTravelTicketPreviewText(canvas, spec, bottomRect)
            watermarkHintPaint.color = previousTextColor
            return
        }

        val padding = 16f * density
        val textTop = if ((bottomRect?.height() ?: 0f) > watermarkHintPaint.textSize + padding) {
            rect.bottom + padding + watermarkHintPaint.textSize
        } else {
            rect.bottom - padding
        }
        val x: Float
        val y: Float
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT -> {
                x = rect.left + padding
                y = rect.top + padding + watermarkHintPaint.textSize
            }
            WatermarkTextPlacement.TOP_RIGHT -> {
                x = rect.right - padding
                y = rect.top + padding + watermarkHintPaint.textSize
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_LEFT -> {
                x = rect.left + padding
                y = textTop
            }
            WatermarkTextPlacement.BOTTOM_RIGHT -> {
                x = rect.right - padding
                y = textTop
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_CENTER -> {
                x = rect.centerX()
                y = textTop
                watermarkHintPaint.textAlign = Paint.Align.CENTER
            }
        }
        canvas.drawText(spec.previewText, x, y, watermarkHintPaint)
        watermarkHintPaint.color = previousTextColor
    }

    private fun drawTravelTicketPreviewDecoration(
        canvas: Canvas,
        bottomRect: RectF,
        spec: WatermarkHintSpec
    ) {
        val ticket = RectF(
            bottomRect.left + bottomRect.width() * TravelTicketPaperSpec.TICKET_LEFT_RATIO,
            bottomRect.top + bottomRect.height() * TravelTicketPaperSpec.TICKET_TOP_RATIO,
            bottomRect.right - bottomRect.width() * 0.035f,
            bottomRect.top + bottomRect.height() * TravelTicketPaperSpec.TICKET_BOTTOM_RATIO
        )
        if (ticket.width() <= 0f || ticket.height() <= 0f) return
        val visualAlpha = (spec.opacity * 318f).toInt().coerceIn(96, 255)
        val limeWidth = ticket.width() * TravelTicketPaperSpec.LIME_STUB_RATIO
        val blueRight = ticket.right - limeWidth
        val cobalt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.COBALT_COLOR
            alpha = visualAlpha
        }
        val lime = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.LIME_COLOR
            alpha = visualAlpha
        }
        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.PAPER_COLOR
            alpha = visualAlpha
        }
        val ticketRadius = 5f * density
        val ticketClip = Path().apply { addRoundRect(ticket, ticketRadius, ticketRadius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(ticketClip)
        canvas.drawRect(ticket, cobalt)
        canvas.drawRect(blueRight, ticket.top, ticket.right, ticket.bottom, lime)
        canvas.restore()

        val notchRadius = maxOf(2.2f * density, ticket.height() * 0.045f)
        var notchY = ticket.top + notchRadius * 2f
        while (notchY < ticket.bottom - notchRadius) {
            canvas.drawCircle(ticket.left, notchY, notchRadius, paper)
            notchY += notchRadius * 2.9f
        }

        val stampCenterX = ticket.left + (blueRight - ticket.left) * 0.43f
        val stampCenterY = ticket.centerY()
        val stampRadius = minOf((blueRight - ticket.left) * 0.24f, ticket.height() * 0.34f)
        val coral = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.CORAL_COLOR
            alpha = visualAlpha
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1.4f * density, ticket.width() * 0.008f)
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawCircle(stampCenterX, stampCenterY, stampRadius, coral)
        canvas.drawArc(
            RectF(
                stampCenterX - stampRadius * 0.28f,
                stampCenterY - stampRadius * 0.34f,
                stampCenterX + stampRadius * 0.28f,
                stampCenterY + stampRadius * 0.22f
            ),
            180f,
            180f,
            false,
            coral
        )
        repeat(3) { index ->
            val y = stampCenterY + stampRadius * (0.16f + index * 0.16f)
            val wave = Path().apply {
                moveTo(stampCenterX - stampRadius * 0.52f, y)
                cubicTo(
                    stampCenterX - stampRadius * 0.18f, y - stampRadius * 0.12f,
                    stampCenterX + stampRadius * 0.18f, y + stampRadius * 0.12f,
                    stampCenterX + stampRadius * 0.52f, y
                )
            }
            canvas.drawPath(wave, coral)
        }
        listOf(-0.62f, -0.31f, 0f, 0.31f, 0.62f).forEach { offset ->
            val angle = Math.toRadians((-90f + offset * 70f).toDouble())
            val inner = stampRadius * 0.48f
            val outer = stampRadius * 0.62f
            canvas.drawLine(
                stampCenterX + kotlin.math.cos(angle).toFloat() * inner,
                stampCenterY - stampRadius * 0.1f + kotlin.math.sin(angle).toFloat() * inner,
                stampCenterX + kotlin.math.cos(angle).toFloat() * outer,
                stampCenterY - stampRadius * 0.1f + kotlin.math.sin(angle).toFloat() * outer,
                coral
            )
        }
        val stampText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.CORAL_COLOR
            alpha = visualAlpha
            textSize = maxOf(6f * density, stampRadius * 0.24f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LET'S GO", stampCenterX, stampCenterY + stampRadius * 0.78f, stampText)

        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.PAPER_COLOR
            alpha = visualAlpha
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1.8f * density, ticket.width() * 0.01f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val arrowStart = blueRight - (blueRight - ticket.left) * 0.2f
        val arrowEnd = blueRight - (blueRight - ticket.left) * 0.06f
        val arrowHalf = ticket.height() * 0.09f
        canvas.drawLine(arrowStart, stampCenterY, arrowEnd, stampCenterY, arrow)
        canvas.drawLine(arrowEnd - arrowHalf, stampCenterY - arrowHalf, arrowEnd, stampCenterY, arrow)
        canvas.drawLine(arrowEnd - arrowHalf, stampCenterY + arrowHalf, arrowEnd, stampCenterY, arrow)

        val serial = TravelTicketPaperSpec.serial(spec.previewLabels)
        val serialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.BLACK_COLOR
            alpha = visualAlpha
            textSize = maxOf(6f * density, limeWidth * 0.3f)
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            serial,
            blueRight + limeWidth / 2f,
            ticket.bottom - ticket.height() * 0.07f - serialPaint.descent(),
            serialPaint
        )
    }

    private fun drawTravelTicketPreviewText(
        canvas: Canvas,
        spec: WatermarkHintSpec,
        bottomRect: RectF
    ) {
        val visualAlpha = (spec.opacity * 318f).toInt().coerceIn(96, 255)
        val left = bottomRect.left + bottomRect.width() * 0.035f
        val right = bottomRect.left + bottomRect.width() * 0.53f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.BLACK_COLOR
            alpha = visualAlpha
            textSize = bottomRect.height() * 0.29f * spec.textScale
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        while (titlePaint.textSize > 10f * density && titlePaint.measureText(TravelTicketPaperSpec.TITLE) > right - left) {
            titlePaint.textSize *= 0.95f
        }
        canvas.drawText(TravelTicketPaperSpec.TITLE, left, bottomRect.top + bottomRect.height() * 0.43f, titlePaint)

        val labels = spec.previewLabels.take(2).ifEmpty {
            listOf(TravelTicketPaperSpec.PLACEHOLDER_TRIP, TravelTicketPaperSpec.PLACEHOLDER_DATE)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TravelTicketPaperSpec.BLACK_COLOR
            alpha = visualAlpha
            textSize = bottomRect.height() * 0.105f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        var baseline = bottomRect.top + bottomRect.height() * 0.67f
        labels.forEach { label ->
            canvas.drawText(label.uppercase(), left, baseline, detailPaint)
            baseline += bottomRect.height() * 0.16f
        }
    }

    private fun drawArchivalPaperPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        opacity: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 190, 126)
            alpha = (opacity * 164).toInt().coerceIn(0, 164)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            strokeCap = Paint.Cap.SQUARE
        }
        val finePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(122, 98, 58)
            alpha = (opacity * 104).toInt().coerceIn(0, 104)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f * density
        }
        canvas.drawRect(frameRect, paint)

        val corner = 30f * density
        val inset = 8f * density
        fun drawCorner(left: Boolean, top: Boolean) {
            val x = if (left) frameRect.left + inset else frameRect.right - inset
            val y = if (top) frameRect.top + inset else frameRect.bottom - inset
            val xDir = if (left) 1f else -1f
            val yDir = if (top) 1f else -1f
            canvas.drawLine(x, y, x + xDir * corner, y, paint)
            canvas.drawLine(x, y, x, y + yDir * corner, paint)
            canvas.drawLine(
                x + xDir * corner * 0.42f,
                y + yDir * 5f * density,
                x + xDir * corner * 0.92f,
                y + yDir * 5f * density,
                finePaint
            )
        }
        drawCorner(left = true, top = true)
        drawCorner(left = false, top = true)
        drawCorner(left = true, top = false)
        drawCorner(left = false, top = false)

    }

    private fun drawNightMemoryPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        bottomRect: RectF?,
        opacity: Float
    ) {
        val coolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(166, 180, 204)
            alpha = (opacity * 60).toInt().coerceIn(0, 60)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
            strokeCap = Paint.Cap.ROUND
        }
        val warmLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 160, 82)
            alpha = (opacity * 140).toInt().coerceIn(0, 140)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            strokeCap = Paint.Cap.ROUND
        }
        val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(228, 164, 92)
            alpha = (opacity * 168).toInt().coerceIn(0, 168)
            style = Paint.Style.FILL
        }
        val inset = 9f * density
        val corner = 24f * density
        fun drawCorner(left: Boolean, top: Boolean) {
            val x = if (left) frameRect.left + inset else frameRect.right - inset
            val y = if (top) frameRect.top + inset else frameRect.bottom - inset
            val xDir = if (left) 1f else -1f
            val yDir = if (top) 1f else -1f
            canvas.drawLine(x, y, x + xDir * corner, y, coolPaint)
            canvas.drawLine(x, y, x, y + yDir * corner, coolPaint)
        }
        drawCorner(left = true, top = true)
        drawCorner(left = false, top = true)
        drawCorner(left = true, top = false)
        drawCorner(left = false, top = false)

        bottomRect?.let { band ->
            canvas.drawLine(
                band.left + 18f * density,
                band.top + 10f * density,
                band.right - 18f * density,
                band.top + 11f * density,
                warmLinePaint
            )
            val radius = 2.2f * density
            canvas.drawCircle(band.right - 24f * density, band.top + 20f * density, radius, lampPaint)
            canvas.drawCircle(band.right - 34f * density, band.top + 27f * density, radius * 0.55f, lampPaint)
        }
    }

    private fun drawStarryMoonPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        bottomRect: RectF?,
        opacity: Float
    ) {
        val warmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 190, 104)
            alpha = (opacity * 170).toInt().coerceIn(0, 170)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            strokeCap = Paint.Cap.ROUND
        }
        val coolPaint = Paint(warmPaint).apply {
            color = Color.rgb(54, 139, 218)
            alpha = (opacity * 112).toInt().coerceIn(0, 112)
            strokeWidth = 0.75f * density
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(246, 215, 146)
            alpha = (opacity * 210).toInt().coerceIn(0, 210)
            style = Paint.Style.FILL
        }
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 18, 48)
            alpha = (opacity * 230).toInt().coerceIn(0, 230)
            style = Paint.Style.FILL
        }
        val moonX = frameRect.left + 34f * density
        val moonY = frameRect.top + 28f * density
        val moonR = 10f * density
        canvas.drawCircle(moonX, moonY, moonR, fillPaint)
        canvas.drawCircle(moonX + moonR * 0.45f, moonY - moonR * 0.12f, moonR * 0.92f, maskPaint)

        fun wave(y: Float, paint: Paint, shift: Float) {
            val path = Path().apply {
                moveTo(frameRect.left + 72f * density, y)
                cubicTo(
                    frameRect.left + frameRect.width() * 0.34f,
                    y - 18f * density + shift,
                    frameRect.left + frameRect.width() * 0.62f,
                    y + 18f * density - shift,
                    frameRect.right - 24f * density,
                    y - 4f * density
                )
            }
            canvas.drawPath(path, paint)
        }
        wave(frameRect.top + 24f * density, warmPaint, 0f)
        wave(frameRect.top + 30f * density, coolPaint, 4f * density)
        bottomRect?.let { band ->
            wave(band.top + band.height() * 0.42f, warmPaint, 2f * density)
            wave(band.top + band.height() * 0.56f, coolPaint, -2f * density)
        }

        listOf(
            frameRect.left + 14f * density to frameRect.top + 48f * density,
            frameRect.right - 44f * density to frameRect.top + 34f * density,
            frameRect.right - 22f * density to frameRect.centerY(),
            frameRect.left + 28f * density to frameRect.bottom - 34f * density,
            frameRect.right - 32f * density to frameRect.bottom - 26f * density
        ).forEach { (x, y) ->
            canvas.drawLine(x - 3f * density, y, x + 3f * density, y, fillPaint)
            canvas.drawLine(x, y - 3f * density, x, y + 3f * density, fillPaint)
        }
    }

    private fun drawBlueHourPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        bottomRect: RectF?,
        opacity: Float
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(158, 202, 238)
            alpha = (opacity * 178).toInt().coerceIn(0, 178)
            style = Paint.Style.STROKE
            strokeWidth = 1.05f * density
            strokeCap = Paint.Cap.ROUND
        }
        val warmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 184, 104)
            alpha = (opacity * 178).toInt().coerceIn(0, 178)
            style = Paint.Style.STROKE
            strokeWidth = 1.05f * density
            strokeCap = Paint.Cap.ROUND
        }
        val inset = 8f * density
        val radius = 8f * density
        canvas.drawRoundRect(
            RectF(
                frameRect.left + inset,
                frameRect.top + inset,
                frameRect.right - inset,
                (bottomRect?.bottom ?: frameRect.bottom) - inset
            ),
            radius,
            radius,
            linePaint
        )
        val innerPaint = Paint(linePaint).apply {
            alpha = (opacity * 118).toInt().coerceIn(0, 118)
            strokeWidth = 0.7f * density
        }
        canvas.drawRoundRect(
            RectF(
                frameRect.left + inset * 2.0f,
                frameRect.top + inset * 1.8f,
                frameRect.right - inset * 2.0f,
                (bottomRect?.bottom ?: frameRect.bottom) - inset * 1.8f
            ),
            radius * 0.72f,
            radius * 0.72f,
            innerPaint
        )
        repeat(5) { index ->
            val y = frameRect.top + inset * (1.05f + index * 0.34f)
            canvas.drawLine(
                frameRect.left + inset * (1.2f + index * 0.25f),
                y,
                frameRect.right - inset * (1.4f + index * 0.12f),
                y + density * 1.2f,
                Paint(linePaint).apply {
                    alpha = (opacity * (62 + index * 15)).toInt().coerceIn(0, 142)
                    strokeWidth = 0.55f * density
                }
            )
        }
        bottomRect?.let { band ->
            val x = band.right - 58f * density
            val y = band.top + band.height() * 0.48f
            canvas.drawCircle(x, y, 6.2f * density, warmPaint)
            canvas.drawLine(x + 22f * density, y + 9f * density, x + 22f * density, band.bottom - 15f * density, warmPaint)
            canvas.drawLine(x + 16f * density, band.bottom - 15f * density, x + 28f * density, band.bottom - 15f * density, warmPaint)
            canvas.drawRect(
                x - 6f * density,
                y + 21f * density,
                x + 7f * density,
                y + 31f * density,
                warmPaint
            )
        }
    }

    private fun drawStarryMoonPreviewText(
        canvas: Canvas,
        spec: WatermarkHintSpec,
        bottomRect: RectF
    ) {
        val originalTextSize = watermarkHintPaint.textSize
        val originalTypeface = watermarkHintPaint.typeface
        watermarkHintPaint.textAlign = Paint.Align.CENTER
        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = originalTextSize * 0.84f
        watermarkHintPaint.alpha = (spec.opacity * 255 * 0.86f).toInt().coerceIn(0, 255)
        val metadata = spec.previewLabels.takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: spec.previewText
        val metrics = watermarkHintPaint.fontMetrics
        val baseline = bottomRect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(metadata, bottomRect.centerX(), baseline, watermarkHintPaint)
        watermarkHintPaint.typeface = originalTypeface
        watermarkHintPaint.textSize = originalTextSize
    }

    private fun drawBlueHourPreviewText(
        canvas: Canvas,
        spec: WatermarkHintSpec,
        bottomRect: RectF
    ) {
        val originalTextSize = watermarkHintPaint.textSize
        val originalTypeface = watermarkHintPaint.typeface
        val left = bottomRect.left + 24f * density
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        watermarkHintPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = originalTextSize * 1.42f
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val titleMetrics = watermarkHintPaint.fontMetrics
        val titleBaseline = bottomRect.top + bottomRect.height() * 0.38f - (titleMetrics.ascent + titleMetrics.descent) / 2f
        canvas.drawText(spec.previewText, left, titleBaseline, watermarkHintPaint)

        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = originalTextSize * 0.78f
        watermarkHintPaint.alpha = (spec.opacity * 255 * 0.78f).toInt().coerceIn(0, 255)
        val metadata = spec.previewLabels.joinToString(" · ")
        if (metadata.isNotBlank()) {
            canvas.drawText(
                metadata.take(42),
                left,
                titleBaseline + originalTextSize * 1.35f,
                watermarkHintPaint
            )
        }
        watermarkHintPaint.typeface = originalTypeface
        watermarkHintPaint.textSize = originalTextSize
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
    }

    private fun drawWatermarkFourBorderHint(canvas: Canvas, spec: WatermarkHintSpec) {
        applyWatermarkTextScale(spec.textScale)
        val rect = activeFrameRect()
        val band = fourBorderPreviewBandWidth(rect, density)
        val bottomBand = (min(rect.width(), rect.height()) * 0.09f).coerceIn(34f * density, 86f * density)
        val blurBandAlpha = (spec.opacity * 255 * 0.18f).toInt().coerceIn(0, 255)
        watermarkBlurBandPaint.alpha = blurBandAlpha
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + band, watermarkBlurBandPaint)
        canvas.drawRect(rect.left, rect.bottom - bottomBand, rect.right, rect.bottom, watermarkBlurBandPaint)
        canvas.drawRect(rect.left, rect.top + band, rect.left + band, rect.bottom - bottomBand, watermarkBlurBandPaint)
        canvas.drawRect(rect.right - band, rect.top + band, rect.right, rect.bottom - bottomBand, watermarkBlurBandPaint)

        watermarkBorderPaint.alpha = (spec.opacity * 255 * 0.42f).toInt().coerceIn(0, 255)
        canvas.drawRect(rect, watermarkBorderPaint)
        if (spec.decoration == WatermarkPreviewDecoration.IMPRESSION_CHROMA) {
            drawImpressionChromaPreviewDecoration(canvas, rect, bottomBand, spec.opacity)
        }

        val originalTextSize = watermarkHintPaint.textSize
        val originalTypeface = watermarkHintPaint.typeface
        val metadata = fourBorderPreviewMetadata(spec.previewLabels)
        val titleTextSize = originalTextSize * 1.08f
        val metadataTextSize = originalTextSize * 0.76f
        val lineGap = 5f * density

        watermarkHintPaint.textAlign = Paint.Align.CENTER
        watermarkHintPaint.setShadowLayer(3f * density, 0f, density, Color.argb(110, 0, 0, 0))
        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        watermarkHintPaint.textSize = titleTextSize
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val titleMetrics = watermarkHintPaint.fontMetrics
        val titleHeight = titleMetrics.descent - titleMetrics.ascent

        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = metadataTextSize
        val metadataMetrics = watermarkHintPaint.fontMetrics
        val metadataHeight = if (metadata.isNotEmpty()) {
            metadataMetrics.descent - metadataMetrics.ascent
        } else {
            0f
        }
        val blockHeight = titleHeight + if (metadata.isNotEmpty()) lineGap + metadataHeight else 0f
        val blockTop = rect.bottom - bottomBand + (bottomBand - blockHeight) / 2f

        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        watermarkHintPaint.textSize = titleTextSize
        val titleBaseline = blockTop - titleMetrics.ascent
        canvas.drawText(spec.previewText, rect.centerX(), titleBaseline, watermarkHintPaint)

        if (metadata.isNotEmpty()) {
            watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            watermarkHintPaint.textSize = metadataTextSize
            watermarkHintPaint.alpha = (spec.opacity * 255 * 0.78f).toInt().coerceIn(0, 255)
            val metadataBaseline = titleBaseline + titleMetrics.descent + lineGap - metadataMetrics.ascent
            canvas.drawText(metadata, rect.centerX(), metadataBaseline, watermarkHintPaint)
        }

        watermarkHintPaint.clearShadowLayer()
        watermarkHintPaint.typeface = originalTypeface
        watermarkHintPaint.textSize = originalTextSize
    }

    private fun drawImpressionChromaPreviewDecoration(
        canvas: Canvas,
        rect: RectF,
        bottomBand: Float,
        opacity: Float
    ) {
        val top = rect.bottom - bottomBand + 7f * density
        val start = RectF(
            rect.left + 18f * density,
            top,
            rect.centerX(),
            top + 1.2f * density
        )
        val end = RectF(
            rect.centerX(),
            top,
            rect.right - 18f * density,
            top + 1.2f * density
        )
        val rosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(198, 174, 205)
            alpha = (opacity * 96).toInt().coerceIn(0, 96)
            style = Paint.Style.FILL
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(168, 202, 210)
            alpha = (opacity * 88).toInt().coerceIn(0, 88)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(start, 1f * density, 1f * density, rosePaint)
        canvas.drawRoundRect(end, 1f * density, 1f * density, cyanPaint)
    }

    private val bottomBarBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bottomBarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            displayMetrics
        )
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private fun drawWatermarkBottomBarHint(canvas: Canvas, spec: WatermarkHintSpec) {
        val rect = activeFrameRect()
        val isTranslucentBottomBar = spec.templateId == "pure-text"
        val barRect = if (isTranslucentBottomBar) {
            val barHeight = maxOf(52f * density, rect.height() * 0.078f)
            RectF(rect.left, rect.bottom - barHeight, rect.right, rect.bottom)
        } else {
            bottomBarPreviewRect(rect, viewHeight(), density)
        }
        val bgColor = spec.barBackground
        if (bgColor != 0) {
            bottomBarBackgroundPaint.color = bgColor
            bottomBarBackgroundPaint.alpha = (spec.opacity * 200).toInt().coerceIn(0, 200)
            canvas.drawRect(barRect, bottomBarBackgroundPaint)
        }
        if (isTranslucentBottomBar) {
            val accentWidth = 3f * density
            val accentMargin = 11f * density
            bottomBarBackgroundPaint.color = Color.rgb(238, 214, 154)
            bottomBarBackgroundPaint.alpha = (spec.opacity * 225).toInt().coerceIn(0, 225)
            canvas.drawRoundRect(
                RectF(
                    barRect.left + accentMargin,
                    barRect.top + accentMargin,
                    barRect.left + accentMargin + accentWidth,
                    barRect.bottom - accentMargin
                ),
                accentWidth,
                accentWidth,
                bottomBarBackgroundPaint
            )
        }
        bottomBarTextPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val padding = 10f * density
        if (isTranslucentBottomBar) {
            bottomBarTextPaint.textAlign = Paint.Align.LEFT
            bottomBarTextPaint.color = Color.rgb(246, 250, 255)
            bottomBarTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            bottomBarTextPaint.textSize = 11.5f * density
            val leftX = barRect.left + padding + 14f * density
            val titleY = barRect.top + 23f * density
            val title = spec.previewLabels.firstOrNull() ?: spec.previewText
            canvas.drawText(title, leftX, titleY, bottomBarTextPaint)

            val secondary = spec.previewLabels.drop(1).joinToString("  ·  ").ifBlank { spec.previewText }
            bottomBarTextPaint.color = Color.rgb(196, 214, 232)
            bottomBarTextPaint.textSize = 8.5f * density
            canvas.drawText(secondary, leftX, titleY + 17f * density, bottomBarTextPaint)
            return
        }
        val textY = barRect.centerY() - (bottomBarTextPaint.ascent() + bottomBarTextPaint.descent()) / 2f
        if (spec.previewLabels.isNotEmpty()) {
            bottomBarTextPaint.textAlign = Paint.Align.LEFT
            val leftX = barRect.left + padding
            canvas.drawText(spec.previewLabels.first(), leftX, textY, bottomBarTextPaint)
            if (spec.previewLabels.size > 1) {
                bottomBarTextPaint.textAlign = Paint.Align.RIGHT
                val rightX = barRect.right - padding
                canvas.drawText(spec.previewLabels.last(), rightX, textY, bottomBarTextPaint)
            }
        } else {
            bottomBarTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(spec.previewText, barRect.centerX(), textY, bottomBarTextPaint)
        }
    }


}
