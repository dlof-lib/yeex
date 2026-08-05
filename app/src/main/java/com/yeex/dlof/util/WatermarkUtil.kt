package com.yeex.dlof.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Stamps a semi-transparent "yeex" watermark on any image before it is saved
 * to the device (download action). Applied to:
 *  - Image paragraphs when downloaded.
 *  - Video paragraphs: only the thumbnail/cover frame is watermarked by this
 *    utility; watermarking every frame of the video itself requires a
 *    frame-by-frame re-encode (e.g. via the FFmpeg-kit library) and is left
 *    as a follow-up — see README "Roadmap".
 *  - PDF exports: apply this same bitmap watermark to each rendered page
 *    before writing the PDF (see the `pdf` export flow).
 */
object WatermarkUtil {

    fun applyWatermark(source: Bitmap, label: String = "yeex"): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 160
            textSize = output.width * 0.06f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(6f, 0f, 0f, Color.argb(140, 0, 0, 0))
        }

        val textWidth = paint.measureText(label)
        val x = output.width - textWidth - (output.width * 0.04f)
        val y = output.height - (output.height * 0.05f)
        canvas.drawText(label, x, y, paint)

        return output
    }
}
