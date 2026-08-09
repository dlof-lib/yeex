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
 *  - Image paragraphs when downloaded ([applyWatermark]).
 *  - Video paragraphs: every frame, not just the cover — [renderFrameOverlay]
 *    renders this same stamp as a transparent layer that [VideoWatermarkUtil]
 *    composites over the whole clip via Media3's Transformer/effect
 *    pipeline, plus a bouncing per-author "who posted this" bubble built
 *    from [renderAuthorBubble] (see that file for the moving-badge part).
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
        canvas.drawBitmap(
            renderFrameOverlay(
                width = output.width,
                height = output.height,
                appLabel = appLabel,
                authorIdentifier = authorIdentifier,
                authorDisplayName = authorDisplayName,
                authorAvatar = authorAvatar,
                authorVerified = authorVerified,
                likeCount = likeCount,
                viewCount = viewCount
            ),
            0f, 0f, null
        )
        return output
    }

    /**
     * Same stamp as [applyWatermark] (diagonal tile + author badge + brand
     * tag, or the centered brand badge when there's no author context), but
     * rendered onto a *transparent* [width]x[height] canvas instead of on
     * top of a source image.
     *
     * This is the piece [VideoWatermarkUtil] needs: Media3's `BitmapOverlay`
     * composites a transparent bitmap over every decoded video frame, so the
     * exact same drawing code used for photos/PDFs also burns the mark into
     * every frame of a downloaded video — no separate "video watermark"
     * design, just this layer replayed at the video's resolution.
     */
    fun renderFrameOverlay(
        width: Int,
        height: Int,
        appLabel: String = "yeex",
        authorIdentifier: String = "",
        authorDisplayName: String = "",
        authorAvatar: Bitmap? = null,
        authorVerified: Boolean = false,
        likeCount: Long = 0,
        viewCount: Long = 0
    ): Bitmap {
        val overlay = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)

        drawDiagonalTile(canvas, width, height, appLabel)
        if (authorIdentifier.isNotBlank()) {
            drawAuthorBadge(
                canvas, width, height,
                identifier = authorIdentifier,
                displayName = authorDisplayName.ifBlank { authorIdentifier },
                avatar = authorAvatar,
                verified = authorVerified,
                likeCount = likeCount,
                viewCount = viewCount
            )
            drawBrandTag(canvas, width, height, appLabel)
        } else {
            drawBrandBadge(canvas, width, height, appLabel)
        }

        return overlay
    }

    /**
     * Small circular "who posted this" bubble — avatar + a colored app-tag
     * chip clipped across its bottom edge + the "@identifier" handle under
     * it — meant to be replayed at a handful of different corners over a
     * video's duration (see [VideoWatermarkUtil]'s bouncing overlay), the
     * same pattern short-video apps use so a downloaded clip still carries
     * proof of its source even if a viewer crops/covers one fixed spot.
     *
     * @param diameterPx target avatar circle size; the returned bitmap is
     *   slightly taller/wider than this to fit the tag chip and handle text.
     */
    fun renderAuthorBubble(
        diameterPx: Int,
        appLabel: String = "yeex",
        authorIdentifier: String,
        authorAvatar: Bitmap?
    ): Bitmap {
        val tagHeight = diameterPx * 0.34f
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = diameterPx * 0.24f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val handle = "@$authorIdentifier"
        val handlePad = handlePaint.textSize * 0.55f
        val handleBoxHeight = handlePaint.textSize + handlePad
        val padding = diameterPx * 0.08f

        val width = maxOf(diameterPx, handlePaint.measureText(handle).toInt() + padding.toInt() * 2)
        val height = (diameterPx + tagHeight * 0.55f + handleBoxHeight + padding).toInt()
        val bubble = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bubble)

        val avatarRect = RectF(
            (width - diameterPx) / 2f, 0f,
            (width + diameterPx) / 2f, diameterPx.toFloat()
        )
        // Soft drop shadow so the bubble reads on both light and dark video frames.
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; alpha = 90 }
        canvas.drawOval(RectF(avatarRect).apply { offset(0f, diameterPx * 0.04f) }, shadowPaint)

        if (authorAvatar != null) {
            drawCircularBitmap(canvas, authorAvatar, avatarRect)
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_CRIMSON }
            canvas.drawOval(avatarRect, fallbackPaint)
            drawCatMark(canvas, avatarRect.centerX(), avatarRect.centerY(), diameterPx * 0.52f, Color.WHITE)
        }
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = diameterPx * 0.045f
        }
        canvas.drawOval(avatarRect, ringPaint)

        // App-tag chip straddling the avatar's bottom edge (like the "TEK"
        // strip in the reference screenshot), with the cat mark instead of
        // plain text so it's legible at the small size a bounced bubble ends
        // up rendered at.
        val chipWidth = diameterPx * 0.62f
        val chipRect = RectF(
            avatarRect.centerX() - chipWidth / 2f, avatarRect.bottom - tagHeight / 2f,
            avatarRect.centerX() + chipWidth / 2f, avatarRect.bottom + tagHeight / 2f
        )
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BRAND_NAVY }
        canvas.drawRoundRect(chipRect, tagHeight / 2f, tagHeight / 2f, chipPaint)
        drawCatMark(canvas, chipRect.left + tagHeight * 0.55f, chipRect.centerY(), tagHeight * 0.62f, Color.WHITE)
        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = tagHeight * 0.62f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val chipFm = chipTextPaint.fontMetrics
        canvas.drawText(
            appLabel.uppercase(Locale.US),
            chipRect.left + tagHeight * 1.05f,
            chipRect.centerY() - (chipFm.ascent + chipFm.descent) / 2f,
            chipTextPaint
        )

        val handleY = avatarRect.bottom + tagHeight * 0.55f + handlePaint.textSize
        canvas.drawText(handle, width / 2f, handleY, handlePaint)

        return bubble
    }

    /** Simple two-eared cat-head silhouette, matching the app's logo mark — used as a compact stand-in for a text label at small overlay sizes. */
    private fun drawCatMark(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val r = size / 2f
        val path = Path()
        // Left ear
        path.moveTo(cx - r * 0.75f, cy - r * 0.15f)
        path.lineTo(cx - r * 1.05f, cy - r * 1.05f)
        path.lineTo(cx - r * 0.15f, cy - r * 0.55f)
        path.close()
        // Right ear
        path.moveTo(cx + r * 0.75f, cy - r * 0.15f)
        path.lineTo(cx + r * 1.05f, cy - r * 1.05f)
        path.lineTo(cx + r * 0.15f, cy - r * 0.55f)
        path.close()
        canvas.drawPath(path, paint)
        // Head
        canvas.drawOval(RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.85f), paint)
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
