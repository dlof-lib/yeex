package com.yeex.dlof.util

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.hypot
import kotlin.math.min

/**
 * Stamps a professional, stock-photo-style "yeex" watermark on any image
 * before it is saved to the device (download action). Three layers, matching
 * the pattern used by Shutterstock/Getty and similar to deter easy removal
 * while staying legible:
 *  1. A diagonal tile of the "yeex" mark repeated across the whole image, so
 *     cropping out a single corner still leaves the mark visible.
 *  2. An author badge (bottom-right) — avatar (or initial fallback),
 *     display name, and "@identifier" — so a re-shared download is
 *     traceable to who posted it, not just to the app.
 *  3. A small standalone brand tag (bottom-left) so "yeex" itself stays
 *     legible even if the author badge is cropped or covered.
 *
 * Sizes are all relative to image width, deliberately larger than earlier
 * revisions of this util (author feedback: the old mark was too small to
 * read at normal viewing size / after re-compression by sharing apps).
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
    private const val BRAND_NAVY = 0xFF12185A.toInt()

    /**
     * @param source the image to stamp.
     * @param appLabel the small standalone brand tag text — normally "yeex".
     * @param authorIdentifier the poster's "@handle" (e.g. "yeex.open"). If
     *   blank, the author badge is skipped entirely (falls back to the old
     *   brand-only badge behavior).
     * @param authorDisplayName the poster's shown name. Falls back to
     *   [authorIdentifier] if blank.
     * @param authorAvatar the poster's decoded profile icon, or null to draw
     *   an initial-letter fallback circle instead.
     */
    fun applyWatermark(
        source: Bitmap,
        appLabel: String = "yeex",
        authorIdentifier: String = "",
        authorDisplayName: String = "",
        authorAvatar: Bitmap? = null
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width
        val h = output.height

        drawDiagonalTile(canvas, w, h, appLabel)
        if (authorIdentifier.isNotBlank()) {
            drawAuthorBadge(canvas, w, h, authorIdentifier, authorDisplayName.ifBlank { authorIdentifier }, authorAvatar)
            drawBrandTag(canvas, w, h, appLabel)
        } else {
            // No author context available (e.g. legacy call site) — keep the
            // single centered brand badge so the image is never unmarked.
            drawBrandBadge(canvas, w, h, appLabel)
        }

        return output
    }

    /** Repeating mark across the whole frame, rotated -30°. Bigger + a touch bolder than before so it survives re-compression. */
    private fun drawDiagonalTile(canvas: Canvas, w: Int, h: Int, label: String) {
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 60
            textSize = w * 0.095f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        val diagonal = hypot(w.toDouble(), h.toDouble()).toFloat()
        val stepX = tilePaint.measureText(label) + w * 0.18f
        val stepY = tilePaint.textSize * 3.4f

        canvas.save()
        canvas.rotate(-30f, w / 2f, h / 2f)
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

    /**
     * Large, legible author badge in the bottom-right corner: circular
     * avatar + display name + "@identifier", on a solid rounded pill so it
     * reads clearly even over busy photos/thumbnails.
     */
    private fun drawAuthorBadge(
        canvas: Canvas,
        w: Int,
        h: Int,
        identifier: String,
        displayName: String,
        avatar: Bitmap?
    ) {
        val margin = w * 0.035f
        val avatarSize = w * 0.13f
        val pad = avatarSize * 0.22f

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.05f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 210
            textSize = w * 0.038f
            typeface = Typeface.DEFAULT
        }

        val handle = "@$identifier"
        val textWidth = maxOf(namePaint.measureText(displayName), handlePaint.measureText(handle))
        val badgeHeight = avatarSize + pad * 2
        val badgeWidth = pad + avatarSize + pad + textWidth + pad

        val badgeRight = w - margin
        val badgeBottom = h - margin
        val badgeLeft = badgeRight - badgeWidth
        val badgeTop = badgeBottom - badgeHeight

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 150
        }
        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        val corner = badgeHeight * 0.5f
        canvas.drawRoundRect(badgeRect, corner, corner, badgePaint)

        val avatarLeft = badgeLeft + pad
        val avatarTop = badgeTop + pad
        val avatarRect = RectF(avatarLeft, avatarTop, avatarLeft + avatarSize, avatarTop + avatarSize)
        if (avatar != null) {
            drawCircularBitmap(canvas, avatar, avatarRect)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_CRIMSON }
            canvas.drawOval(avatarRect, fallbackPaint)
            val initial = (displayName.firstOrNull() ?: '?').uppercaseChar().toString()
            val initialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = avatarSize * 0.5f
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fm = initialPaint.fontMetrics
            val cx = avatarRect.centerX()
            val cy = avatarRect.centerY() - (fm.ascent + fm.descent) / 2f
            canvas.drawText(initial, cx, cy, initialPaint)
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 200
            style = Paint.Style.STROKE
            strokeWidth = avatarSize * 0.035f
        }
        canvas.drawOval(avatarRect, ringPaint)

        val textLeft = avatarRect.right + pad
        val lineGap = handlePaint.textSize * 0.35f
        val blockHeight = namePaint.textSize + lineGap + handlePaint.textSize
        val blockTop = badgeRect.centerY() - blockHeight / 2f
        val nameBaseline = blockTop + namePaint.textSize - (namePaint.fontMetrics.descent * 0.3f)
        val handleBaseline = nameBaseline + lineGap + handlePaint.textSize
        canvas.drawText(displayName, textLeft, nameBaseline, namePaint)
        canvas.drawText(handle, textLeft, handleBaseline, handlePaint)
    }

    /** Small standalone "yeex" tag, bottom-left, so the app mark survives even if the author badge is cropped/covered. */
    private fun drawBrandTag(canvas: Canvas, w: Int, h: Int, label: String) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.042f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val padH = textPaint.textSize * 0.6f
        val padV = textPaint.textSize * 0.4f
        val textWidth = textPaint.measureText(label)

        val margin = w * 0.035f
        val badgeLeft = margin
        val badgeBottom = h - margin
        val badgeRight = badgeLeft + textWidth + padH * 2
        val badgeTop = badgeBottom - textPaint.textSize - padV * 2

        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_NAVY
            alpha = 200
        }
        val corner = (badgeBottom - badgeTop) * 0.3f
        canvas.drawRoundRect(badgeRect, corner, corner, badgePaint)
        canvas.drawText(label, badgeLeft + padH, badgeBottom - padV * 0.9f, textPaint)
    }

    /** Legacy fallback: solid, legible brand mark anchored to the bottom-right corner (used only when no author context is available). */
    private fun drawBrandBadge(canvas: Canvas, w: Int, h: Int, label: String) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.06f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val padH = w * 0.035f
        val padV = textPaint.textSize * 0.55f
        val textWidth = textPaint.measureText(label)

        val badgeRight = w - w * 0.035f
        val badgeBottom = h - h * 0.035f
        val badgeLeft = badgeRight - textWidth - padH * 2
        val badgeTop = badgeBottom - textPaint.textSize - padV

        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BRAND_CRIMSON
            alpha = 220
        }
        val corner = (badgeBottom - badgeTop) * 0.35f
        canvas.drawRoundRect(badgeRect, corner, corner, badgePaint)

        val textX = badgeLeft + padH
        val textY = badgeBottom - padV * 0.65f
        canvas.drawText(label, textX, textY, textPaint)
    }

    /** Draws [bitmap] clipped to an oval matching [dest], cropped (not stretched) to fill it. */
    private fun drawCircularBitmap(canvas: Canvas, bitmap: Bitmap, dest: RectF) {
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = min(dest.width() / bitmap.width, dest.height() / bitmap.height)
        val dx = dest.left - (bitmap.width * scale - dest.width()) / 2f
        val dy = dest.top - (bitmap.height * scale - dest.height()) / 2f
        val matrix = android.graphics.Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawOval(dest, paint)
    }
}
