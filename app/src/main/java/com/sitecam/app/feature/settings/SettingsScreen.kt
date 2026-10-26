package com.sitecam.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.BuildConfig
import com.sitecam.app.R
import com.sitecam.app.core.layout.AdaptivePaneLayout
import com.sitecam.app.core.media.PhotoQualityProfile
import com.sitecam.app.feature.icon.AppIconChoice
import com.sitecam.app.feature.icon.AppIconPicker
import com.sitecam.app.ui.theme.EngineeringYellow

private const val WEBSITE = "https://yuriaqua.com/sitecam/"
private const val GITHUB = "https://github.com/toydream525/SiteCam"
private const val PRIVACY = "https://agreement-drcn.hispace.dbankcloud.cn/index.html?lang=zh&agreementId=2043675336696198208"
private val groups = listOf("拍摄与保存", "水印", "权限与隐私", "外观", "帮助与关于")

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit,
    onNavigateToWatermarkEditor: (Long) -> Unit = {}, onNavigateToHelp: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {}, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val uriHandler = LocalUriHandler.current
    val state by viewModel.uiState.collectAsState()
    val iconPicker = remember(context) { AppIconPicker(context.applicationContext) }
    var selectedIcon by remember { mutableStateOf(iconPicker.selectedChoice()) }
    var page by rememberSaveable { mutableStateOf("设置") }
    var selectedGroup by rememberSaveable { mutableStateOf(groups.first()) }
    var cameraAppearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var showEasterEgg by remember { mutableStateOf(false) }
    var versionTapCount by remember { mutableIntStateOf(0) }
    var versionTapAt by remember { mutableLongStateOf(0L) }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.setSaveToSystemGallery(true)
        else Toast.makeText(context, "未授权，继续仅保存在应用内", Toast.LENGTH_SHORT).show()
    }
    fun open(url: String) { runCatching { uriHandler.openUri(url) }.onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() } }
    fun selectIcon(choice: AppIconChoice) {
        runCatching { iconPicker.select(choice) }.onSuccess { selectedIcon = it }
            .onFailure { Toast.makeText(context, it.message ?: "图标切换失败", Toast.LENGTH_SHORT).show() }
    }
    fun toggleAlbum(enabled: Boolean) {
        if (enabled && android.os.Build.VERSION.SDK_INT < 29 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            legacyPermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else viewModel.setSaveToSystemGallery(enabled)
    }
    BackHandler(page != "设置") { page = "设置" }
    if (showEasterEgg) SiteCamEasterEggDialog(onDismiss = { showEasterEgg = false },
        onOpenStar = { open(GITHUB) }, onOpenIssue = { open("$GITHUB/issues") },
        onCopyProjectLink = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SiteCam", GITHUB))
            Toast.makeText(context, "项目链接已复制", Toast.LENGTH_SHORT).show()
        }, onOpenWebsite = { open(WEBSITE) })

    @Composable fun groupContent(group: String) {
        when (group) {
            "拍摄与保存" -> SettingsGroup(group) {
                SettingsToggle("抓拍模式", Icons.Default.Speed, state.snapshotEnabled, viewModel::setSnapshotEnabled)
                SettingsRow("抓拍快门", Icons.Default.ShutterSpeed, "1/${state.snapshotShutterSpeed}") { page = "抓拍快门" }
                SettingsRow("照片画质", Icons.Default.Photo, state.photoQualityProfile.label) { page = "照片画质" }
                SettingsToggle("快门声音", Icons.Default.VolumeUp, state.shutterSoundEnabled, viewModel::setShutterSoundEnabled)
                SettingsToggle("同时在系统相册显示", Icons.Default.PhotoLibrary, state.saveToSystemGallery, ::toggleAlbum)
            }
            "水印" -> SettingsGroup(group) {
                SettingsRow("水印样式与字段", Icons.Default.WaterDrop) { onNavigateToWatermarkEditor(state.activeTemplateId) }
            }
            "权限与隐私" -> SettingsGroup(group) {
                SettingsRow("权限设置", Icons.Default.Security, onClick = onNavigateToPermissions)
                SettingsRow("隐私政策", Icons.Default.Lock) { open(PRIVACY) }
            }
            "外观" -> SettingsGroup(group) {
                SettingsRow("桌面图标", Icons.Default.Palette, selectedIcon.label) { page = "桌面图标" }
                SettingsRow("相机控件外观", Icons.Default.CameraAlt,
                    if (state.cameraControlStyle == "TRANSPARENT") "透明" else "普通",
                    expanded = cameraAppearanceExpanded) { cameraAppearanceExpanded = !cameraAppearanceExpanded }
                if (cameraAppearanceExpanded) CameraAppearanceChoices(state.cameraControlStyle, viewModel::setCameraControlStyle)
            }
            "帮助与关于" -> SettingsGroup(group) {
                SettingsRow("使用指南", Icons.Default.MenuBook, onClick = onNavigateToHelp)
                SettingsRow("关于 SiteCam", Icons.Default.Info) { page = "关于 SiteCam" }
            }
        }
    }
    @Composable fun settingsContent(wide: Boolean, contentModifier: Modifier = Modifier) {
    Column(contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            when (page) {
                "设置" -> if (wide) groupContent(selectedGroup) else groups.forEach { groupContent(it) }
                "抓拍快门" -> SettingsGroup("") {
                    com.sitecam.app.core.camera.SnapshotPolicy.shutterSpeeds.forEach { speed ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.setSnapshotShutterSpeed(speed) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("1/$speed", Modifier.weight(1f))
                            RadioButton(state.snapshotShutterSpeed == speed, { viewModel.setSnapshotShutterSpeed(speed) })
                        }
                    }
                }
                "照片画质" -> SettingsGroup("") {
                    PhotoQualityProfile.entries.forEach { quality ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.setPhotoQualityProfile(quality) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(quality.label, fontWeight = FontWeight.Bold)
                                Text(quality.maxLongEdge?.let { "长边 $it 像素" } ?: "保留原尺寸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RadioButton(state.photoQualityProfile == quality, { viewModel.setPhotoQualityProfile(quality) })
                        }
                    }
                }
                "桌面图标" -> SettingsGroup("") {
                    AppIconChoice.entries.forEach { choice -> Row(Modifier.fillMaxWidth().clickable { selectIcon(choice) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppIconPreview(choice, Modifier.size(64.dp))
                        Text("${choice.name}  ${choice.label}", Modifier.weight(1f))
                        RadioButton(selectedIcon == choice, { selectIcon(choice) })
                    } }
                }
                "相机控件外观" -> SettingsGroup("") {
                    CameraAppearanceChoices(state.cameraControlStyle, viewModel::setCameraControlStyle)
                }
                "关于 SiteCam" -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIconPreview(AppIconChoice.D, Modifier.size(120.dp))
                        Text("工程水印相机", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("SiteCam", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("版本 ${BuildConfig.VERSION_NAME}", Modifier.clickable {
                            val now = SystemClock.elapsedRealtime()
                            versionTapCount = if (now - versionTapAt <= 1500) versionTapCount + 1 else 1
                            versionTapAt = now
                            if (versionTapCount >= 5) { versionTapCount = 0; showEasterEgg = true }
                        }.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsGroup("") {
                        SettingsRow("使用指南", Icons.Default.MenuBook, onClick = onNavigateToHelp)
                        SettingsRow("隐私政策", Icons.Default.Lock) { open(PRIVACY) }
                        SettingsRow("官网与下载", Icons.Default.OpenInNew) { open(WEBSITE) }
                        SettingsRow("设备诊断", Icons.Default.ContentCopy) {
                            viewModel.copyDiagnostics(context)
                        }
                    }
                }
            }
            if (page == "照片画质" || page == "桌面图标") Button(onClick = { page = "设置" },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.buttonColors(contentColor = Color.Black)) { Text("完成") }
        }
    }
    }
    AdaptivePaneLayout(modifier, allowTwoPane = page == "设置", primaryMinWidth = 240f,
        secondaryMinWidth = 400f, primaryFraction = .3f,
        primary = { wide ->
            Scaffold(Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = { TopAppBar(title = { Text(page, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { if (page == "设置") onNavigateBack() else page = "设置" }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
                if (wide) {
                    Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        groups.forEach { group ->
                            Surface(Modifier.fillMaxWidth().clickable { selectedGroup = group }, shape = RoundedCornerShape(12.dp),
                                color = if (group == selectedGroup) EngineeringYellow.copy(alpha = .22f) else Color.Transparent) {
                                Text(group, Modifier.padding(18.dp), fontWeight = if (group == selectedGroup) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                } else settingsContent(false, Modifier.padding(padding))
            }
        }, secondary = {
            settingsContent(true)
        })
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotEmpty()) Text(title, Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}
@Composable private fun SettingsRow(title: String, icon: ImageVector, value: String = "", expanded: Boolean? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp))
        Text(title, Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 160.dp))
        Icon(if (expanded == true) Icons.Default.ExpandLess else if (expanded == false) Icons.Default.ExpandMore
            else Icons.AutoMirrored.Filled.ArrowForwardIos, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun CameraAppearanceChoices(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
        listOf("NORMAL" to "普通", "TRANSPARENT" to "透明").forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.heightIn(min = 56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected == value, { onSelect(value) })
                Text(label)
            }
        }
    }
}
@Composable private fun SettingsToggle(title: String, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp)); Text(title, Modifier.weight(1f)); Switch(checked, onChange)
    }
}
@Composable private fun AppIconPreview(choice: AppIconChoice, modifier: Modifier = Modifier) {
    val (background, foreground) = when (choice) {
        AppIconChoice.A -> R.color.launcher_a_background to R.drawable.ic_launcher_a_foreground
        AppIconChoice.B -> R.color.launcher_b_background to R.drawable.ic_launcher_b_foreground
        AppIconChoice.C -> R.color.launcher_c_background to R.drawable.ic_launcher_c_foreground
        AppIconChoice.D -> R.color.launcher_d_background to R.drawable.ic_launcher_d_foreground
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(colorResource(background)), contentAlignment = Alignment.Center) {
        Image(painterResource(foreground), null, Modifier.fillMaxSize())
    }
}
