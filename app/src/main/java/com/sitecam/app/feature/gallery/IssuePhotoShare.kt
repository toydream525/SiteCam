package com.sitecam.app.feature.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.sitecam.app.core.database.entity.IssueEntity
import java.io.File

internal suspend fun createIssuePhotoShare(context: Context, source: Uri, issue: IssueEntity): Uri {
    val request = ImageRequest.Builder(context).data(source).size(1920).scale(Scale.FIT)
        .allowHardware(false).build()
    val decoded = context.imageLoader.execute(request) as? SuccessResult ?: error("无法读取照片")
    val photo = decoded.drawable.toBitmap()
    val width = photo.width.coerceAtLeast(480)
    val padding = width / 24
    val font = width / 27f
    val severity = when (issue.severity) {
        "CRITICAL" -> "严重"
        "IMPORTANT" -> "重要"
        else -> "一般"
    }
    val status = when (issue.status) {
        "COMPLETED" -> "已完成"
        "IN_PROGRESS" -> "处理中"
        else -> "待处理"
    }
    fun layout(text: String, bold: Boolean, color: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = font
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            this.color = color
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 2 * padding)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f).build()
    }
    val blocks = listOf(
        layout("问题记录", true, 0xffffca55.toInt()),
        layout(issue.title, true, Color.WHITE),
        layout("$severity · $status", false, Color.LTGRAY)
    ) + listOfNotNull(issue.description.takeIf { it.isNotBlank() }?.let { layout(it, false, Color.WHITE) })
    val gap = padding / 2
    val height = photo.height + 2 * padding + blocks.sumOf { it.height } + (blocks.size - 1) * gap
    check(height <= 4096) { "问题记录过长，无法生成分享照片" }
    val directory = File(context.cacheDir, "issue-shares").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - 86_400_000L
    directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    var file: File? = null
    try {
        val canvas = Canvas(output)
        canvas.drawColor(0xff10171d.toInt())
        canvas.drawBitmap(photo, (width - photo.width) / 2f, 0f, null)
        canvas.translate(padding.toFloat(), (photo.height + padding).toFloat())
        for (block in blocks) {
            block.draw(canvas)
            canvas.translate(0f, (block.height + gap).toFloat())
        }
        val target = File.createTempFile("问题照片_", ".jpg", directory)
        file = target
        target.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 92, it)) { "无法生成分享照片" } }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    } catch (failure: Throwable) {
        file?.delete()
        throw failure
    } finally {
        output.recycle()
    }
}
