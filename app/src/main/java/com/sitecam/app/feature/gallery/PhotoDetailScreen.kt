@file:android.annotation.SuppressLint("UnsafeOptInUsageError")

package com.sitecam.app.feature.gallery

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.ErrorRed
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark
import com.sitecam.app.ui.theme.WarningYellow
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.media.normalizedVideoRotation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    viewModel: PhotoDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAnnotation: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    onMissingMedia: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val window = (view.context as? android.app.Activity)?.window
    // Gallery details are dark even when their containing management page follows light mode.
    androidx.compose.runtime.DisposableEffect(window, systemDark) {
        onDispose {
            window?.let {
                androidx.core.view.WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = !systemDark
                    isAppearanceLightNavigationBars = !systemDark
                }
            }
        }
    }
    androidx.compose.runtime.SideEffect {
        window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    val item by viewModel.mediaItem.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    var detailClosed by remember(viewModel) { mutableStateOf(false) }
    fun closeDetail() {
        if (detailClosed) return
        detailClosed = true
        (onMissingMedia ?: onNavigateBack).invoke()
    }
    val annotation by viewModel.annotation.collectAsState()
    var hadMedia by remember(viewModel) { mutableStateOf(false) }
    LaunchedEffect(item, isDeleting) {
        if (item != null) hadMedia = true
        else if (hadMedia && !isDeleting) closeDetail()
    }
    var showEdited by rememberSaveable { mutableStateOf(false) }
    var showIssueDialog by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshAnnotation() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val issue by viewModel.issue.collectAsState()
    val isRetrying by viewModel.isRetrying.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    // Android 10/11+ deletes media the app does not own only after this system request.
    var pendingDeleteItem by remember { mutableStateOf<com.sitecam.app.core.database.entity.MediaItemEntity?>(null) }
    val deleteConsent = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val item = pendingDeleteItem
        if (result.resultCode == android.app.Activity.RESULT_OK && item != null) viewModel.retryDeleteAfterConsent(item)
        else Toast.makeText(context, "已取消删除", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PhotoDetailEvent.Share -> {
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = if (event.item.mediaType == "VIDEO") "video/mp4" else "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(event.item.contentUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, if (event.item.mediaType == "VIDEO") "分享工程视频" else "分享工程照片"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "打开分享失败，请检查文件是否仍存在", Toast.LENGTH_LONG).show()
                    }
                }
                PhotoDetailEvent.Deleted -> {
                    Toast.makeText(context, "媒体已删除", Toast.LENGTH_SHORT).show()
                    closeDetail()
                }
                is PhotoDetailEvent.PendingDeleteConsent -> {
                    pendingDeleteItem = event.item
                    deleteConsent.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }
                is PhotoDetailEvent.RetryCompleted -> Toast.makeText(
                    context,
                    if (event.cleanupWarning) "视频水印已成功，但旧原片清理失败，请稍后检查" else "视频水印已重试成功",
                    Toast.LENGTH_LONG
                ).show()
                is PhotoDetailEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val issueItem = item
    if (showIssueDialog && issueItem != null && !isDeleting) {
        com.sitecam.app.feature.issue.IssueDialog(
            mediaId = issueItem.id, existingIssue = issue,
            onDismiss = { showIssueDialog = false },
            onConfirm = { title, severity, description, status -> viewModel.saveIssue(title, severity, description, status); showIssueDialog = false },
            onRemove = if (issue != null) ({ viewModel.removeIssue(); showIssueDialog = false }) else null
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = item?.fileName ?: "媒体详情",
                        color = TextPrimaryDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        // Not just "返回": on a wide split screen this closes the preview pane while
                        // the gallery list next to it keeps its own distinct back action.
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭预览", tint = Color.White)
                    }
                },
                actions = {
                    if (item?.mediaType != "VIDEO") {
                        IconButton(onClick = { item?.let { onNavigateToAnnotation(it.id) } }) {
                            Icon(Icons.Default.Brush, contentDescription = "现场标注", tint = EngineeringYellow)
                        }
                    }
                    IconButton(onClick = { showInfoSheet = !showInfoSheet }) {
                        Icon(Icons.Default.Info, contentDescription = "详细信息", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.requestShare(showEdited) }) {
                        Icon(Icons.Default.Share, contentDescription = "分享", tint = Color.White)
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = !isDeleting && item != null) {
                        Icon(Icons.Default.Delete, contentDescription = "删除", tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            if (item == null) {
                Text("媒体不可用，可能已在系统相册删除。请返回相册；从系统回收站恢复后会重新同步。",
                    color = TextSecondaryDark, modifier = Modifier.padding(24.dp))
            }
            item?.takeUnless { isDeleting || detailClosed }?.let { media ->
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (media.mediaType == "VIDEO") {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                key(media.id, media.contentUri) {
                                    InlineVideoPlayer(media.contentUri, Modifier.fillMaxSize())
                                }
                            }
                        } else {
                            ZoomablePhoto(if (showEdited) annotation?.annotatedContentUri ?: media.contentUri else media.contentUri, media.fileName)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        if (media.mediaType != "VIDEO" && annotation != null) {
                            TextButton(onClick = { showEdited = !showEdited }) { Text(if (showEdited) "查看原图" else "查看编辑成品") }
                        }
                        TextButton(onClick = { showIssueDialog = true }) { Text(if (issue == null) "登记问题" else "编辑问题") }
                    }
                    issue?.let { issueRecord ->
                        IssueSummaryCard(issueRecord, Modifier.fillMaxWidth().padding(12.dp))
                    }
                }

                if (showInfoSheet) {
                    Card(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(if (media.mediaType == "VIDEO") "视频详细信息" else "照片详细信息", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("拍摄时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(media.captureTimestamp))}", color = TextSecondaryDark, fontSize = 13.sp)
                            if (media.addressText.isNotBlank()) Text("地点: ${media.addressText}", color = TextSecondaryDark, fontSize = 13.sp)
                            val displayRotation = normalizedVideoRotation(media.orientation)
                            val displayWidth = if (displayRotation == 90 || displayRotation == 270) media.height else media.width
                            val displayHeight = if (displayRotation == 90 || displayRotation == 270) media.width else media.height
                            val dimensionText = if (media.mediaType == "VIDEO" && (displayWidth != media.width || displayHeight != media.height)) {
                                "$displayWidth × $displayHeight（编码 ${media.width} × ${media.height}）"
                            } else "$displayWidth × $displayHeight"
                            Text("显示分辨率: $dimensionText", color = TextSecondaryDark, fontSize = 13.sp)
                            if (media.mediaType == "VIDEO") {
                                val status = if (media.processingStatus == "READY") "已烧录工程水印" else "水印转码待重试，当前保留原片"
                                Text("时长: ${media.duration / 1000}s · $status", color = TextSecondaryDark, fontSize = 13.sp)
                                if (media.processingStatus != "READY") {
                                    Button(
                                        onClick = { viewModel.retryVideoWatermark(context) },
                                        enabled = !isRetrying,
                                        colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow),
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text(if (isRetrying) "正在转码…" else "重试视频水印", color = Color.Black)
                                    }
                                }
                            }
                            val storageLabel = if (android.net.Uri.parse(media.contentUri).authority == android.provider.MediaStore.AUTHORITY) "系统相册" else "应用相册"
                            Text("保存位置: $storageLabel", color = TextSecondaryDark, fontSize = 13.sp)
                            Text("文件名: ${media.fileName}", color = TextSecondaryDark, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = DarkCard,
            title = { Text("确认删除", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除该${if (item?.mediaType == "VIDEO") "视频" else "照片"}吗？", color = TextSecondaryDark) },
            confirmButton = {
                Button(onClick = { showDeleteConfirm = false; viewModel.delete() }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)) {
                    Text("删除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextSecondaryDark) } }
        )
    }
}

@Composable
private fun IssueSummaryCard(issue: IssueEntity, modifier: Modifier = Modifier) {
    val severityColor = when (issue.severity) {
        "CRITICAL" -> ErrorRed
        "IMPORTANT" -> WarningYellow
        else -> EngineeringYellow
    }
    val severityLabel = when (issue.severity) {
        "CRITICAL" -> "严重"
        "IMPORTANT" -> "重要"
        else -> "一般"
    }
    Card(
        modifier = modifier.heightIn(min = 88.dp, max = 190.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.96f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, severityColor.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("现场问题记录", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Text(severityLabel, color = severityColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(issue.title, color = TextPrimaryDark, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            if (issue.description.isNotBlank()) {
                Text("问题原因 / 整改要求 / 备注", color = TextSecondaryDark, fontSize = 11.sp)
                Text(issue.description, color = TextPrimaryDark, fontSize = 13.sp)
            }
            Text(
                "记录时间：${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(issue.createdAt))}",
                color = TextSecondaryDark,
                fontSize = 11.sp
            )
        }
    }
}
