package com.sitecam.app.core.export

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportArchiveManager(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { ExportArchiveRepository(context) }
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf(emptyList<ExportArchive>()) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun message(value: String) { Toast.makeText(context, value, Toast.LENGTH_LONG).show() }
    suspend fun reload() {
        val listing = repository.list()
        files = listing.files
        selected = ArrayList(selected.filter { id -> files.any { it.id == id } })
        if (listing.unreadableDirectories > 0) message("部分目录无法读取，请重新添加旧包")
    }
    fun refresh() {
        if (loading) return
        scope.launch {
            loading = true
            try { reload() } catch (e: Exception) {
                if (e is CancellationException) throw e
                message("读取压缩包失败")
            } finally { loading = false }
        }
    }
    val chooseDirectory = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                repository.rememberDirectory(uri)
            }.onSuccess { refresh() }.onFailure { message("无法访问所选目录") }
        }
    }
    LaunchedEffect(repository) { refresh() }
    Dialog(onDismissRequest = { if (!loading) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(title = { Text("导出压缩包", maxLines = 1) }, navigationIcon = {
                    IconButton(onClick = onDismiss, enabled = !loading) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                }, actions = {
                    IconButton(onClick = ::refresh, enabled = !loading) { Icon(Icons.Default.Refresh, "刷新") }
                })
            }) { padding ->
                Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { selected = if (selected.size == files.size) arrayListOf() else ArrayList(files.map { it.id }) },
                            enabled = files.isNotEmpty() && !loading) { Text(if (files.isNotEmpty() && selected.size == files.size) "取消全选" else "全选") }
                        TextButton(onClick = { chooseDirectory.launch(null) }, enabled = !loading) { Text("添加旧包") }
                        Text("已选 ${selected.size} 个", modifier = Modifier.padding(vertical = 12.dp))
                        TextButton(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty() && !loading,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除所选") }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (files.isEmpty() && !loading) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("暂无压缩包") }
                    else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(files, key = { it.id }) { file ->
                            fun toggle() { selected = ArrayList(if (file.id in selected) selected - file.id else selected + file.id) }
                            Card(Modifier.fillMaxWidth().clickable(enabled = !loading) { toggle() }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = file.id in selected, onCheckedChange = { toggle() }, enabled = !loading)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.date))} · ${archiveSize(file.size)}",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除 ${selected.size} 个压缩包？") },
            confirmButton = { TextButton(onClick = {
                confirmDelete = false
                val ids = selected.toSet()
                scope.launch {
                    loading = true
                    try {
                        val result = repository.delete(ids)
                        selected = ArrayList(selected.filterNot { it in result.deleted })
                        reload()
                        if (result.failed.isNotEmpty()) message("${result.failed.size} 个压缩包未能删除")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        message("删除压缩包失败")
                    } finally { loading = false }
                }
            }) { Text("删除") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    }
}

private fun archiveSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
