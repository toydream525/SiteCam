package com.sitecam.app.core.text

import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextAppearanceControls(fontId: String, textColor: String?, onFont: (String) -> Unit, onColor: (String?) -> Unit,
    whiteboard: Boolean? = null, onWhiteboard: (Boolean) -> Unit = {}, previewText: String = "",
    allowDefaultColor: Boolean = true, contentColor: Color = MaterialTheme.colorScheme.onSurface) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var choices by remember(context) { FontLibrary.initialize(context); mutableStateOf(FontLibrary.choices()) }
    var picker by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val importFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                val choice = FontLibrary.importFont(context, uri)
                choices = FontLibrary.choices(); onFont(choice.id); picker = false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(context, "无法导入字体，请选择有效的 TTF 或 OTF 文件", Toast.LENGTH_LONG).show()
            } finally { importing = false }
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { choices = FontLibrary.choices(); picker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("字体", Modifier.weight(1f), color = contentColor)
            Text(choices.firstOrNull { it.id == fontId }?.name ?: "默认字体", color = contentColor)
        }
        Text("文字颜色", color = contentColor)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("#FFFFFF", "#FFB300", "#17212B", "#F44336", "#1262AE", "#168D80").forEach { value ->
                val color = Color(android.graphics.Color.parseColor(value))
                val selected = textColor?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() } == android.graphics.Color.parseColor(value)
                Box(Modifier.size(48.dp).border(if (selected) 3.dp else 1.dp,
                    if (selected) Color(0xFFFFB300) else Color.Gray, CircleShape).padding(6.dp).background(color, CircleShape)
                    .clickable { onColor(value) })
            }
        }
        if (allowDefaultColor) TextButton(onClick = { onColor(null) }) { Text("跟随样式") }
        if (whiteboard != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!whiteboard, { onWhiteboard(false) }, label = { Text("普通") })
            FilterChip(whiteboard, { onWhiteboard(true); onColor("#17212B") }, label = { Text("白板框") })
        }
        if (previewText.isNotEmpty()) Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val canvas = drawContext.canvas.nativeCanvas
            val pad = 12.dp.toPx()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = if (whiteboard == true) android.graphics.Color.parseColor("#FAFAF7") else android.graphics.Color.parseColor("#CC121212")
            canvas.drawRoundRect(0f, 0f, size.width, size.height, 4.dp.toPx(), 4.dp.toPx(), paint)
            if (whiteboard == true) {
                paint.color = android.graphics.Color.parseColor("#17212B"); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.dp.toPx()
                canvas.drawRect(paint.strokeWidth / 2, paint.strokeWidth / 2, size.width - paint.strokeWidth / 2, size.height - paint.strokeWidth / 2, paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = textColor?.let { android.graphics.Color.parseColor(it) } ?: android.graphics.Color.WHITE
            paint.typeface = FontLibrary.typeface(fontId); paint.isFakeBoldText = true; paint.textSize = 20.sp.toPx()
            val count = paint.breakText(previewText, true, (size.width - pad * 2).coerceAtLeast(0f), null)
            canvas.drawText(previewText.take(count), pad, (size.height - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2, paint)
        }
    }
    if (picker) AlertDialog(onDismissRequest = { if (!importing) picker = false }, title = { Text("字体") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            choices.forEach { choice ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !importing) { onFont(choice.id); picker = false }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(choice.id == fontId, onClick = { onFont(choice.id); picker = false }, enabled = !importing)
                    Text(choice.name, Modifier.weight(1f))
                }
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { importFont.launch(arrayOf("*/*")) }, enabled = !importing) { Text("导入字体") }
        }
    }, confirmButton = { TextButton(onClick = { picker = false }, enabled = !importing) { Text("完成") } })
}
