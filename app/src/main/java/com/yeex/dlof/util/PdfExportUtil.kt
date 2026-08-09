package com.yeex.dlof.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yeex.dlof.data.model.MomentStep

/**
 * Exports a paragraph as a single-page, watermarked PDF — the "pdf" branch
 * of the "علامة مائية لكل فقرة عند تنزيله ... او pdf" requirement, alongside
 * the image/gallery download in [DownloadUtil] and the gallery-video export.
 *
 * Two entry points:
 *  - [exportBitmap]: wraps an already-decoded (and already-watermarked, via
 *    [WatermarkUtil]) square bitmap — used for IMAGE/VIDEO-thumbnail
 *    paragraphs — into a one-page PDF.
 *  - [renderTextCard]: TEXT paragraphs have no bitmap at all, so this first
 *    draws the paragraph onto a square canvas matching the app's card look
 *    (dark background, centered text, author line) before [exportBitmap]
 *    can watermark + wrap it the same way.
 */
object PdfExportUtil {

    /** Renders a TEXT paragraph into a square card bitmap, ready for [WatermarkUtil.applyWatermark]. */
    fun renderTextCard(
        text: String,
        authorLine: String,
        size: Int = 1080
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#12185A")) // brand navy background

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.055f
            typeface = Typeface.DEFAULT
        }
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C81D3D")
            textSize = size * 0.045f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        val margin = size * 0.1f
        val maxWidth = size - margin * 2
        val lines = wrapText(text, bodyPaint, maxWidth)
        val lineHeight = bodyPaint.textSize * 1.4f
        val blockHeight = lines.size * lineHeight
        var y = (size - blockHeight) / 2f + bodyPaint.textSize

        for (line in lines) {
            val lineWidth = bodyPaint.measureText(line)
            canvas.drawText(line, (size - lineWidth) / 2f, y, bodyPaint)
            y += lineHeight
        }

        canvas.drawText(authorLine, margin, size - margin * 0.6f, authorPaint)
        return bitmap
    }

    /**
     * Renders a MOMENT paragraph's timeline into a tall card bitmap for PDF
     * export — mirrors [renderTextCard]'s look (dark brand background,
     * bold author line at the bottom) but stacks each stage's time/icon/title
     * and short text top to bottom instead of centering one block of text.
     * Height grows with the number of stages instead of being fixed square,
     * since a Moment can have anywhere from 2 to many stages.
     */
    fun renderMomentCard(
        title: String,
        authorLine: String,
        steps: List<MomentStep>,
        width: Int = 1080
    ): Bitmap {
        val margin = width * 0.08f
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = width * 0.06f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val stepHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = width * 0.036f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        val stepTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D8D8E4")
            textSize = width * 0.03f
        }
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C81D3D")
            textSize = width * 0.045f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }

        val maxWidth = width - margin * 2 - 34f
        val ordered = steps.sortedBy { it.order }
        val titleLines = if (title.isNotBlank()) wrapText(title, titlePaint, width - margin * 2) else emptyList()
        val blocks = ordered.map { step ->
            // The category icon is a real vector Icon in the app UI (see
            // ui/create/MomentIcons.kt) precisely because drawing it as a
            // raw character glyph is unreliable — the same reasoning applies
            // here even more: Canvas.drawText has no vector-icon fallback at
            // all, so the header only carries time/title text; the colored
            // dot drawn alongside it below still marks each stage's category color.
            val header = listOfNotNull(
                step.time.takeIf { it.isNotBlank() },
                step.title.takeIf { it.isNotBlank() }
            ).joinToString(" ")
            val bodyLines = if (step.text.isNotBlank()) wrapText(step.text, stepTextPaint, maxWidth) else emptyList()
            header to bodyLines
        }

        val titleBlockHeight = if (titleLines.isEmpty()) 0f else titleLines.size * titlePaint.textSize * 1.35f + margin * 0.5f
        val stepBlockHeights = blocks.map { (_, bodyLines) ->
            stepHeaderPaint.textSize * 1.5f + bodyLines.size * (stepTextPaint.textSize * 1.35f) + margin * 0.35f
        }
        val totalHeight = (margin * 2.4f + titleBlockHeight + stepBlockHeights.sum())
            .toInt()
            .coerceAtLeast(width)

        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#12185A"))

        var y = margin + titlePaint.textSize
        for (line in titleLines) {
            canvas.drawText(line, margin, y, titlePaint)
            y += titlePaint.textSize * 1.35f
        }
        if (titleLines.isNotEmpty()) y += margin * 0.3f

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for ((index, block) in blocks.withIndex()) {
            val (header, bodyLines) = block
            val step = ordered[index]
            dotPaint.color = step.colorHex.takeIf { it.isNotBlank() }
                ?.let { runCatching { Color.parseColor(it) }.getOrNull() }
                ?: Color.parseColor("#9B5CF6")
            canvas.drawCircle(margin + 10f, y - stepHeaderPaint.textSize * 0.35f, 9f, dotPaint)
            canvas.drawText(header, margin + 34f, y, stepHeaderPaint)
            y += stepHeaderPaint.textSize * 1.5f
            for (line in bodyLines) {
                canvas.drawText(line, margin + 34f, y, stepTextPaint)
                y += stepTextPaint.textSize * 1.35f
            }
            y += margin * 0.35f
        }

        canvas.drawText(authorLine, margin, totalHeight - margin * 0.6f, authorPaint)
        return bitmap
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "${current} $word"
            if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                lines.add(current.toString())
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /** Wraps an already-watermarked square bitmap into a single-page PDF and saves it to Downloads. */
    fun exportBitmap(context: Context, watermarked: Bitmap, displayName: String): Boolean {
        return try {
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(watermarked.width, watermarked.height, 1).create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawBitmap(watermarked, 0f, 0f, null)
            pdf.finishPage(page)

            val ok = savePdfToDownloads(context, pdf, "$displayName.pdf")
            pdf.close()
            ok
        } catch (e: Exception) {
            false
        }
    }

    private fun savePdfToDownloads(context: Context, pdf: PdfDocument, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/yeex")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, fileName)
                file.outputStream().use { out -> pdf.writeTo(out) }
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
