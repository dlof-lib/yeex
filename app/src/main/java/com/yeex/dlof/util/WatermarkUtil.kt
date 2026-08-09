package com.yeex.dlof.util

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.util.Locale
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
 *     display name, "@identifier", and a like/view stats row (heart +
 *     eye glyphs drawn as vector paths so they render identically on every
 *     device font) — so a re-shared download is traceable to who posted it
 *     and shows the same social-proof numbers the post had in-app, not just
 *     the app's name.
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
     * @param authorVerified when true, draws a small verified checkmark next
     *   to the display name, matching the in-app profile badge.
     * @param likeCount the paragraph's like count at download time, shown
     *   next to a heart glyph in the stats row.
     * @param viewCount the paragraph's view count at download time, shown
     *   next to an eye glyph in the stats row.
     */
    fun applyWatermark(
        source: Bitmap,
        appLabel: String = "yeex",
        authorIdentifier: String = "",
        authorDisplayName: String = "",
        authorAvatar: Bitmap? = null,
        authorVerified: Boolean = false,
        likeCount: Long = 0,
        viewCount: Long = 0
    ): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val w = output.width
        val h = output.height

        drawDiagonalTile(canvas, w, h, appLabel)
        if (authorIdentifier.isNotBlank()) {
            drawAuthorBadge(
                canvas, w, h,
                identifier = authorIdentifier,
                displayName = authorDisplayName.ifBlank { authorIdentifier },
                avatar = authorAvatar,
                verified = authorVerified,
                likeCount = likeCount,
                viewCount = viewCount
            )
            drawBrandTag(canvas, w, h, appLabel)
        } else {
            // No author context available (e.g. legacy call site) — keep the
            // single centered brand badge so the image is never unmarked.
            drawBrandBadge(canvas, w, h, appLabel)
        }

        return output
    }

    /** "1234" -> "1.2K", "2500000" -> "2.5M" — compact, locale-neutral counters matching in-app RailAction formatting. */
    private fun formatCount(count: Long): String {
        fun trimZero(s: String) = if (s.endsWith(".0K") || s.endsWith(".0M")) s.dropLast(2) + s.last() else s
        return when {
            count < 1_000 -> count.toString()
            count < 1_000_000 -> trimZero(String.format(Locale.US, "%.1fK", count / 1_000.0))
            else -> trimZero(String.format(Locale.US, "%.1fM", count / 1_000_000.0))
        }
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
     * avatar (account icon) + display name (with an optional verified
     * checkmark) + "@identifier" + a like/view stats row, on a solid
     * rounded pill so it reads clearly even over busy photos/thumbnails —
     * the same "saved with proof" mark other social apps stamp on
     * downloaded media, but yeex-branded.
     */
    private fun drawAuthorBadge(
        canvas: Canvas,
        w: Int,
        h: Int,
        identifier: String,
        displayName: String,
        avatar: Bitmap?,
        verified: Boolean,
        likeCount: Long,
        viewCount: Long
    ) {
        val margin = w * 0.035f
        val avatarSize = w * 0.15f
        val pad = avatarSize * 0.2f

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = w * 0.048f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 205
            textSize = w * 0.036f
            typeface = Typeface.DEFAULT
        }
        val statsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 235
            textSize = w * 0.036f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        val handle = "@$identifier"
        val likeLabel = formatCount(likeCount)
        val viewLabel = formatCount(viewCount)
        val iconSize = statsPaint.textSize * 0.85f
        val iconTextGap = iconSize * 0.28f
        val statsGroupGap = iconSize * 1.1f
        val verifiedBadgeSize = namePaint.textSize * 0.8f
        val verifiedGap = if (verified) verifiedBadgeSize * 0.4f else 0f

        val statsRowWidth = iconSize + iconTextGap + statsPaint.measureText(likeLabel) +
            statsGroupGap + iconSize + iconTextGap + statsPaint.measureText(viewLabel)
        val nameRowWidth = namePaint.measureText(displayName) + verifiedGap + (if (verified) verifiedBadgeSize else 0f)
        val textWidth = maxOf(nameRowWidth, handlePaint.measureText(handle), statsRowWidth)

        val badgeHeight = avatarSize + pad * 2
        val badgeWidth = pad + avatarSize + pad + textWidth + pad

        val badgeRight = w - margin
        val badgeBottom = h - margin
        val badgeLeft = badgeRight - badgeWidth
        val badgeTop = badgeBottom - badgeHeight

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 160
        }
        val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        val corner = badgeHeight * 0.32f
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

        // Three stacked rows: name(+verified), handle, like/view stats —
        // vertically centered as a block against the avatar.
        val textLeft = avatarRect.right + pad
        val rowGap = handlePaint.textSize * 0.32f
        val blockHeight = namePaint.textSize + rowGap + handlePaint.textSize + rowGap + statsPaint.textSize
        val blockTop = badgeRect.centerY() - blockHeight / 2f

        val nameBaseline = blockTop + namePaint.textSize - (namePaint.fontMetrics.descent * 0.3f)
        canvas.drawText(displayName, textLeft, nameBaseline, namePaint)
        if (verified) {
            val badgeCx = textLeft + namePaint.measureText(displayName) + verifiedGap + verifiedBadgeSize / 2f
            val badgeCy = nameBaseline - namePaint.textSize * 0.32f
            drawVerifiedBadge(canvas, badgeCx, badgeCy, verifiedBadgeSize / 2f)
        }

        val handleBaseline = nameBaseline + rowGap + handlePaint.textSize
        canvas.drawText(handle, textLeft, handleBaseline, handlePaint)

        val statsBaseline = handleBaseline + rowGap + statsPaint.textSize
        val statsIconTop = statsBaseline - iconSize * 0.85f
        val heartPaint = Paint(statsPaint).apply { color = 0xFFFF3B5C.toInt(); alpha = 255 }
        drawHeartIcon(canvas, textLeft, statsIconTop, iconSize, heartPaint)
        var cursor = textLeft + iconSize + iconTextGap
        canvas.drawText(likeLabel, cursor, statsBaseline, statsPaint)
        cursor += statsPaint.measureText(likeLabel) + statsGroupGap
        drawEyeIcon(canvas, cursor, statsIconTop, iconSize, statsPaint)
        cursor += iconSize + iconTextGap
        canvas.drawText(viewLabel, cursor, statsBaseline, statsPaint)
    }

    /** Small filled heart glyph, drawn as a vector path so it looks identical on every device (no emoji-font dependency). */
    private fun drawHeartIcon(canvas: Canvas, left: Float, top: Float, size: Float, paint: Paint) {
        val path = Path()
        val cx = left + size / 2f
        path.moveTo(cx, top + size * 0.92f)
        path.cubicTo(left - size * 0.08f, top + size * 0.55f, left + size * 0.06f, top - size * 0.05f, cx, top + size * 0.28f)
        path.cubicTo(left + size * 0.94f, top - size * 0.05f, left + size * 1.08f, top + size * 0.55f, cx, top + size * 0.92f)
        path.close()
        canvas.drawPath(path, paint)
    }

    /** Small open-eye glyph (almond outline + pupil), drawn as a vector path — the "views" counterpart to [drawHeartIcon]. */
    private fun drawEyeIcon(canvas: Canvas, left: Float, top: Float, size: Float, paint: Paint) {
        val h = size * 0.62f
        val midY = top + h / 2f
        val path = Path()
        path.moveTo(left, midY)
        path.quadTo(left + size / 2f, top - h * 0.18f, left + size, midY)
        path.quadTo(left + size / 2f, top + h * 1.18f, left, midY)
        path.close()
        val outlinePaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.09f
        }
        canvas.drawPath(path, outlinePaint)
        val pupilPaint = Paint(paint).apply { style = Paint.Style.FILL }
        canvas.drawCircle(left + size / 2f, midY, h * 0.24f, pupilPaint)
    }

    /** Small filled circle + checkmark, matching the in-app "verified" badge next to a display name. */
    private fun drawVerifiedBadge(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3897F0.toInt() }
        canvas.drawCircle(cx, cy, radius, circlePaint)
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.32f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        path.moveTo(cx - radius * 0.5f, cy)
        path.lineTo(cx - radius * 0.12f, cy + radius * 0.4f)
        path.lineTo(cx + radius * 0.5f, cy - radius * 0.35f)
        canvas.drawPath(path, checkPaint)
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
