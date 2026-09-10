package com.sitecam.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult as Media3ExportResult
import androidx.media3.transformer.Transformer
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.model.WatermarkData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

data class VideoTranscodeResult(
    val outputFile: File,
    val durationMs: Long
)

/**
 * Burns the same layout used by the photo renderer into every video frame.
 * Media3 Transformer uses MediaCodec/OpenGL and keeps the source audio track;
 * the input is only deleted by the caller after a successful output copy.
 */
@UnstableApi
class VideoWatermarkTranscoder(private val context: Context) {

    suspend fun transcode(
        inputFile: File,
        outputFile: File,
        watermarkData: WatermarkData
    ): VideoTranscodeResult = withContext(Dispatchers.Main.immediate) {
        require(inputFile.isFile && inputFile.length() > 0L) { "录像源文件不存在或为空" }
        if (outputFile.exists()) outputFile.delete()

        val metadata = readMetadata(inputFile)
        require(metadata.width > 0 && metadata.height > 0) { "无法读取录像尺寸" }
        // Transformer configures TextureOverlay with the dimensions of the
        // actual frame entering its GL pipeline.  Depending on the decoder
        // and rotation metadata this can be coded (1280x720) or presented
        // (720x1280) dimensions.  Generate the overlay after configure() so
        // it always has the same aspect ratio as that frame; pre-rotating a
        // fixed bitmap here can otherwise be cropped or rotated out.
        val overlay = WatermarkBitmapOverlay(metadata, watermarkData)

        suspendCancellableCoroutine { continuation ->
            lateinit var transformer: Transformer
            var completed = false

            fun finish(result: Result<VideoTranscodeResult>) {
                if (completed) return
                completed = true
                if (result.isFailure) outputFile.delete()
                continuation.resumeWith(result)
            }

            val effects: List<Effect> = listOf(OverlayEffect(listOf(overlay)))
            val editedMediaItem = EditedMediaItem.Builder(
                MediaItem.fromUri(Uri.fromFile(inputFile))
            ).setEffects(Effects(emptyList(), effects)).build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: Media3ExportResult) {
                    finish(Result.success(VideoTranscodeResult(outputFile, metadata.durationMs)))
                }

                override fun onError(
                    composition: Composition,
                    result: Media3ExportResult,
                    exception: ExportException
                ) {
                    finish(Result.failure(exception))
                }
            }

            try {
                transformer = Transformer.Builder(context)
                    .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(listener)
                    .build()
                continuation.invokeOnCancellation {
                    transformer.cancel()
                    outputFile.delete()
                }
                transformer.start(editedMediaItem, outputFile.absolutePath)
            } catch (e: Exception) {
                // Transformer normally releases effects through its frame
                // processor.  If startup fails before that lifecycle exists,
                // release the overlay explicitly.
                runCatching { overlay.release() }
                finish(Result.failure(e))
            }
        }
    }

    private class WatermarkBitmapOverlay(
        private val metadata: VideoMetadata,
        private val watermarkData: WatermarkData
    ) : BitmapOverlay() {
        private var configuredBitmap: Bitmap? = null

        override fun configure(videoSize: Size) {
            configuredBitmap?.takeIf { !it.isRecycled }?.recycle()
            val displayBitmap = renderDisplayOverlay(
                codedWidth = metadata.width,
                codedHeight = metadata.height,
                rotationDegrees = metadata.rotation,
                data = watermarkData
            )
            configuredBitmap = orientOverlayForInput(
                displayBitmap = displayBitmap,
                inputWidth = videoSize.width,
                inputHeight = videoSize.height,
                codedWidth = metadata.width,
                codedHeight = metadata.height,
                rotationDegrees = metadata.rotation
            )
        }

        override fun getBitmap(presentationTimeUs: Long): Bitmap =
            configuredBitmap ?: throw IllegalStateException("视频水印 overlay 尚未配置")

        override fun release() {
            try {
                super.release()
            } finally {
                configuredBitmap?.takeIf { !it.isRecycled }?.recycle()
                configuredBitmap = null
            }
        }
    }

    companion object {
        internal fun renderDisplayOverlay(
            codedWidth: Int,
            codedHeight: Int,
            rotationDegrees: Int,
            data: WatermarkData
        ): Bitmap {
            val displaySize = displaySizeForVideoRotation(codedWidth, codedHeight, rotationDegrees)
            val displayBitmap = Bitmap.createBitmap(
                displaySize.width,
                displaySize.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(displayBitmap)
            val layout = WatermarkLayoutEngine.calculateLayout(
                displaySize.width.toFloat(),
                displaySize.height.toFloat(),
                data
            )

            com.sitecam.app.core.watermark.renderer.WatermarkCanvasPainter.draw(canvas, layout)
            return displayBitmap
        }

        /**
         * Matches the overlay texture to the dimensions Media3 actually
         * supplies to OverlayEffect.  The aspect-ratio choice is deliberate:
         * it handles both coded-frame and presentation-frame pipelines while
         * keeping the card in the same physical display corner.
        */
        internal fun orientOverlayForInput(
            displayBitmap: Bitmap,
            inputWidth: Int,
            inputHeight: Int,
            codedWidth: Int,
            codedHeight: Int,
            rotationDegrees: Int
        ): Bitmap {
            if (inputWidth <= 0 || inputHeight <= 0) return displayBitmap
            if (inputWidth == displayBitmap.width && inputHeight == displayBitmap.height) {
                return displayBitmap
            }

            val codedRotation = overlayRotationToCodedPixels(rotationDegrees)
            val codedBitmap = if (codedRotation == 0) {
                displayBitmap
            } else {
                val matrix = Matrix().apply { postRotate(codedRotation.toFloat()) }
                Bitmap.createBitmap(
                    displayBitmap,
                    0,
                    0,
                    displayBitmap.width,
                    displayBitmap.height,
                    matrix,
                    true
                )
            }

            val inputAspect = inputWidth.toFloat() / inputHeight.toFloat()
            val displayAspect = displayBitmap.width.toFloat() / displayBitmap.height.toFloat()
            val codedAspect = codedWidth.toFloat() / codedHeight.toFloat()
            val displayDistance = kotlin.math.abs(inputAspect - displayAspect)
            val codedDistance = kotlin.math.abs(inputAspect - codedAspect)
            val oriented = if (displayDistance <= codedDistance) displayBitmap else codedBitmap
            val other = if (oriented === displayBitmap) codedBitmap else displayBitmap
            if (other !== oriented && !other.isRecycled) other.recycle()
            if (oriented.width == inputWidth && oriented.height == inputHeight) return oriented
            val scaled = Bitmap.createScaledBitmap(oriented, inputWidth, inputHeight, true)
            if (scaled !== oriented) oriented.recycle()
            return scaled
        }
    }

    private fun readMetadata(file: File): VideoMetadata {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            VideoMetadata(
                width = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                rotation = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()?.let(::normalizedVideoRotation) ?: 0
            )
        } finally {
            retriever.release()
        }
    }

    private data class VideoMetadata(val width: Int, val height: Int, val durationMs: Long, val rotation: Int)
}
