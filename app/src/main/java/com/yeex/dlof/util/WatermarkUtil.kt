package com.yeex.dlof.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.hypot

/**
 * Stamps a professional, stock-photo-style "yeex" watermark on any image
 * before it is saved to the device (download action). Two layers, matching
 * the pattern used by Shutterstock/Getty and similar to deter easy removal
 * while staying legible:
 *  1. A faint diagonal tile of the mark repeated across the whole image, so
 *     cropping out a single corner still leaves the mark visible.
 *  2. A solid brand badge (crimson, rounded) in the bottom-right corner that
 *     reads clearly at thumbnail size.
 *
 * Applied to:
 *  - Image paragraphs when downloaded.
 *  - Video paragraphs: only the thumbnail/cover frame is watermarked by this
 *    utility; watermarking every frame of the video itself requires a
 *    frame-by-frame re-encode (e.g. via the FFmpeg-kit library) and is left
 *    as a follow-up — see README "Roadmap".
 *  - PDF exports: apply this same bitmap watermark to each rendered page
 *    before writing the PDF (see the `pdf` export flow).
 */
object WatermarkUtil {

    private const val BRAND_CRIMSON = 0xFFC81D3D.toInt()

    fun applyWatermark(source: Bitmap, label: String = "yeex"): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width
        val h = output.height

        drawDiagonalTile(canvas, w, h, label)
        drawBrandBadge(canvas, w, h, label)

        return output
    }

    /** Faint repeating mark across the whole frame, rotated -30°. */
    private fun drawDiagonalTile(canvas: Canvas, w: Int, h: Int, label: String) {
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 46
            textSize = w * 0.075f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        val diagonal = hypot(w.toDouble(), h.toDouble()).toFloat()
        val stepX = tilePaint.measureText(label) + w * 0.16f
        val stepY = tilePaint.textSize * 3.2f

        canvas.save()
        canvas.rotate(-30f, w / 2f, h / 2f)
        // Over-cover the rotated canvas so corners are never left blank.
        var y = -diagonal
        while (y < diagonal) {
            var x = -diagonal
            while (x < diagonal) {
                canvas.drawText(label, x, y, tilePaint)
                x += stepX
            }
            y += stepY
        }
        canvas.restore()
    }

    /** Solid, legible brand mark anchored to the bottom-right corner. */
    private fun drawBrandBadge(canvas: Canvas, w: Int, h: Int, label: String) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.05f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val padH = w * 0.03f
        val padV = textPaint.textSize * 0.5f
        val textWidth = textPaint.measureText(label)

        val badgeRight = w - w * 0.035f
        val badgeBottom = h - h * 0.035f
        val badgeLeft = badgeRight - textWidth - padH * 2
        val badgeTop = badgeBottom - textPaint.textSize - padV

        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_CRIMSON
            alpha = 210
        }
        val corner = (badgeBottom - badgeTop) * 0.35f
        canvas.drawRoundRect(badgeRect, corner, corner, badgePaint)

        val textX = badgeLeft + padH
        val textY = badgeBottom - padV * 0.65f
        canvas.drawText(label, textX, textY, textPaint)
    }
}
