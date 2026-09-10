package com.sitecam.app.core.export

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sitecam.app.core.media.PhotoQualityProfile

@Composable
fun ExportChoiceDialog(onDismiss: () -> Unit, onZip: (ExportOptions) -> Unit, onFolder: (ExportOptions) -> Unit) {
    var profile by remember { mutableStateOf<PhotoQualityProfile?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("导出工程档案") }, text = {
        Column {
            Text("导出副本不会修改原始照片，视频保持原文件。")
            FilterChip(selected = profile == null, onClick = { profile = null }, label = { Text("保持原文件（默认）") })
            PhotoQualityProfile.entries.forEach { option ->
                FilterChip(selected = profile == option, onClick = { profile = option }, label = {
                    Text("${option.label} · ${option.maxLongEdge?.toString() ?: "原尺寸"} / ${option.jpegQuality}") })
            }
        }
    }, confirmButton = { TextButton(onClick = { onZip(ExportOptions(profile)) }) { Text("一个 ZIP") } },
        dismissButton = { Row { TextButton(onClick = { onFolder(ExportOptions(profile)) }) { Text("一个总文件夹") }; TextButton(onClick = onDismiss) { Text("取消") } } })
}
