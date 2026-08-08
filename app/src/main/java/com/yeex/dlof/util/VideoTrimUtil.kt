package com.yeex.dlof.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

/**
 * Trims a picked video down to [MediaDuration.MAX_VIDEO_MS] instead of
 * outright rejecting anything longer, so a user who picks a 30s clip still
 * gets to post — just the first 10 seconds of it.
 *
 * This stream-copies the encoded samples via [MediaExtractor]/[MediaMuxer]
 * (no decode/re-encode), so it's fast and lossless rather than a full
 * transcode.
 */
object VideoTrimUtil {

    /**
     * Copies [uri] into [outputFile] as an MP4 containing only the
     * [0, trimToMs] window. Returns true on success; false (with no partial
     * file left behind) if the source couldn't be read/muxed, in which case
     * the caller should fall back to its existing "video too long/large"
     * error path.
     */
    fun trimToFile(context: Context, uri: Uri, outputFile: File, trimToMs: Long): Boolean {
        // MediaExtractor needs a real file path/FD, not an arbitrary
        // content:// stream, so copy the source into a scratch file first.
        val sourceFile = File.createTempFile("yeex_trim_src", ".mp4", context.cacheDir)
        var muxerStarted = false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                sourceFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            extractor.setDataSource(sourceFile.absolutePath)
            val newMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = newMuxer

            val trimToUs = trimToMs * 1000
            val trackIndexMap = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                trackIndexMap[i] = newMuxer.addTrack(format)
                extractor.selectTrack(i)
            }
            if (trackIndexMap.isEmpty()) return false

            newMuxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            while (true) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime > trimToUs) break

                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break

                bufferInfo.offset = 0
                bufferInfo.size = size
                bufferInfo.presentationTimeUs = sampleTime
                bufferInfo.flags = extractor.sampleFlags

                val dstIndex = trackIndexMap[trackIndex] ?: break
                newMuxer.writeSampleData(dstIndex, buffer, bufferInfo)
                extractor.advance()
            }
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        } finally {
            runCatching {
                if (muxerStarted) muxer?.stop()
            }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            sourceFile.delete()
        }
    }
}
