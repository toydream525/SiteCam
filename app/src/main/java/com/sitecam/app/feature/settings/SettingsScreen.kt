package com.sitecam.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.BuildConfig
import com.sitecam.app.R
import com.sitecam.app.feature.icon.AppIconChoice
import com.sitecam.app.feature.icon.AppIconPicker
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWatermarkEditor: (Long) -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val legacyPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setSaveToSystemGallery(true)
        else Toast.makeText(context, "未授权，继续仅保存在应用内", Toast.LENGTH_SHORT).show()
    }
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsState()
    val iconPicker = remember(context) { AppIconPicker(context.applicationContext) }
    var selectedIcon by remember(iconPicker) { mutableStateOf(iconPicker.selectedChoice()) }
    var iconError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToHelp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "使用技巧与教程",
                        tint = EngineeringYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "使用技巧与教程",
                            color = EngineeringYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "拍照、整理、导出，按步骤快速上手",
                            color = TextSecondaryDark,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "打开教程",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Watermark Customization Entry Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToWatermarkEditor(uiState.activeTemplateId) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = EngineeringYellow
                        )
                        Column {
                            Text(
                                text = "自定义水印字段与样式",
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "自定义字段、字号、不透明度与八款水印样式",
                                color = TextSecondaryDark,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "进入定制",
                        tint = TextSecondaryDark,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            // JPEG Quality Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "照片保存质量",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val qualities = com.sitecam.app.core.media.PhotoQualityProfile.entries
                    qualities.forEach { q ->
                        val desc = q.maxLongEdge?.let { "长边最多 ${it} 像素 · JPEG ${q.jpegQuality}%" } ?: "保留原尺寸 · JPEG 95%"
                        val isSelected = uiState.photoQualityProfile == q
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setPhotoQualityProfile(q) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setPhotoQualityProfile(q) },
                                colors = RadioButtonDefaults.colors(selectedColor = EngineeringYellow)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = q.label,
                                    color = TextPrimaryDark,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = desc,
                                    color = TextSecondaryDark,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("同时在系统相册显示", color = TextPrimaryDark)
                    androidx.compose.material3.Switch(checked = uiState.saveToSystemGallery, onCheckedChange = { enabled ->
                        if (enabled && android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) legacyPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else viewModel.setSaveToSystemGallery(enabled)
                    })
                    Text("关闭：仅保存在本应用。开启：应用与系统相册共用同一文件，删除会同时影响系统相册。仅影响之后拍摄和编辑保存的内容，不搬动旧文件。", color = TextSecondaryDark, fontSize = 12.sp)
                    Text("画质仅影响之后保存的照片和编辑成品；保持比例，不放大小图。", color = TextSecondaryDark, fontSize = 12.sp)
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = DarkCard)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("拍照快门声音", color = TextPrimaryDark, fontWeight = FontWeight.Bold)
                        Text("拍照时播放系统快门声；关闭不影响录像声音。", color = TextSecondaryDark, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = uiState.shutterSoundEnabled,
                        onCheckedChange = viewModel::setShutterSoundEnabled
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "桌面图标",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "默认使用 D，可选四款图标；只更换桌面入口，不影响工程资料。",
                        color = TextSecondaryDark,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AppIconChoice.entries.forEach { choice ->
                        val selected = choice == selectedIcon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching { iconPicker.select(choice) }
                                        .onSuccess {
                                            selectedIcon = it
                                            iconError = null
                                        }
                                        .onFailure {
                                            selectedIcon = iconPicker.selectedChoice()
                                            iconError = it.message ?: "桌面图标切换失败"
                                        }
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    runCatching { iconPicker.select(choice) }
                                        .onSuccess {
                                            selectedIcon = it
                                            iconError = null
                                        }
                                        .onFailure {
                                            selectedIcon = iconPicker.selectedChoice()
                                            iconError = it.message ?: "桌面图标切换失败"
                                        }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = EngineeringYellow)
                            )
                            AppIconPreview(
                                choice = choice,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(48.dp)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = choice.label,
                                    color = TextPrimaryDark,
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = choice.description,
                                    color = TextSecondaryDark,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    iconError?.let { error ->
                        Text(
                            text = error,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Camera Diagnostics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "硬件与相机诊断",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "可一键提取当前手机型号、物理镜头焦段与多摄像头支持参数，便于兼容性排查。",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.copyDiagnostics(context)
                                Toast.makeText(context, "诊断信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制诊断信息",
                            tint = EngineeringYellow
                        )
                        Text(
                            text = "复制设备诊断信息",
                            color = EngineeringYellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // About Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "工程水印相机 (SiteCam)",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "版本：v${BuildConfig.VERSION_NAME} (Android 原生离线版)",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "作者：ninjaaqua",
                        color = TextSecondaryDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "GitHub：@toydream525",
                        color = EngineeringYellow,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/toydream525")
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "官网与下载：yuriaqua.com/sitecam/",
                        color = EngineeringYellow,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { uriHandler.openUri("https://yuriaqua.com/sitecam/") }
                            .padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "纯本地离线运行 · 无账号体系 · 保存位置由你选择",
                        color = TextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconPreview(
    choice: AppIconChoice,
    modifier: Modifier = Modifier
) {
    val (backgroundRes, foregroundRes) = when (choice) {
        AppIconChoice.A -> R.color.launcher_a_background to R.drawable.ic_launcher_a_foreground
        AppIconChoice.B -> R.color.launcher_b_background to R.drawable.ic_launcher_b_foreground
        AppIconChoice.C -> R.color.launcher_c_background to R.drawable.ic_launcher_c_foreground
        AppIconChoice.D -> R.color.launcher_d_background to R.drawable.ic_launcher_d_foreground
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorResource(backgroundRes)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(foregroundRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
