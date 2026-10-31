package com.sitecam.app.core.text

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class FontChoice(val id: String, val name: String)

object FontLibrary {
    @Volatile private var root: File? = null
    private val faces = ConcurrentHashMap<String, Typeface>()
    fun initialize(context: Context) {
        val directory = File(context.applicationContext.filesDir, "fonts")
        if (root != directory) { faces.clear(); root = directory }
    }
    fun choices(): List<FontChoice> = listOf(FontChoice("", "默认字体")) + root?.listFiles().orEmpty()
        .filter { it.isFile && it.extension.lowercase() in setOf("ttf", "otf") }
        .map { FontChoice(it.name, it.name.substringAfter("__").substringBeforeLast('.')) }.sortedBy { it.name }

    fun typeface(id: String = ""): Typeface {
        if (id.isEmpty() || File(id).name != id) return Typeface.DEFAULT
        faces[id]?.let { return it }
        val file = root?.let { File(it, id) } ?: return Typeface.DEFAULT
        if (!file.isFile) return Typeface.DEFAULT
        return runCatching { Typeface.createFromFile(file).also { faces[id] = it } }.getOrDefault(Typeface.DEFAULT)
    }

    suspend fun importFont(context: Context, uri: Uri): FontChoice = withContext(Dispatchers.IO) {
        initialize(context)
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val extension = name.substringAfterLast('.').lowercase()
        require(extension in setOf("ttf", "otf")) { "请选择 TTF 或 OTF 字体" }
        val label = name.substringBeforeLast('.').replace("[/\\\\:*?\"<>|\r\n]".toRegex(), "_").take(50).ifBlank { "字体" }
        val directory = requireNotNull(root).apply { mkdirs() }
        val id = "${UUID.randomUUID()}__$label.$extension"
        val pending = File(directory, "$id.pending")
        val target = File(directory, id)
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取字体" }.use { input ->
                pending.outputStream().use { input.copyTo(it) }
            }
            val header = ByteArray(4)
            val read = pending.inputStream().use { it.read(header) }
            val signature = String(header, Charsets.ISO_8859_1)
            require(read == 4 && (header.contentEquals(byteArrayOf(0, 1, 0, 0)) || signature == "OTTO" || signature == "true")) { "字体文件无效" }
            val face = Typeface.createFromFile(pending)
            check(pending.renameTo(target)) { "无法保存字体" }
            faces[id] = face
            FontChoice(id, label)
        } catch (t: Throwable) { pending.delete(); target.delete(); throw t }
    }
}
