package com.sitecam.app.core.export

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sitecam.app.core.media.PhotoQualityProfile

@Composable
fun ExportChoiceDialog(onDismiss: () -> Unit, onZip: (ExportOptions) -> Unit, onFolder: (ExportOptions) -> Unit) {
    var profile by remember { mutableStateOf<PhotoQualityProfile?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("导出工程档案") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("导出质量", style = MaterialTheme.typography.titleSmall)
            FilterChip(profile == null, { profile = null }, label = { Text("保持原文件") })
            PhotoQualityProfile.entries.forEach { option ->
                FilterChip(profile == option, { profile = option }, label = { Text(option.label) })
            }
            Button(onClick = { onZip(ExportOptions(profile)) }, modifier = Modifier.fillMaxWidth()) { Text("导出 ZIP 压缩包") }
            OutlinedButton(onClick = { onFolder(ExportOptions(profile)) }, modifier = Modifier.fillMaxWidth()) { Text("导出到文件夹") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
