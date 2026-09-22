package com.sitecam.app.feature.permissions

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sitecam.app.feature.onboarding.OnboardingPreferences
import com.sitecam.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PermissionGuideScreen(
    preferences: OnboardingPreferences,
    requestedPermissions: Set<String>,
    onInteractionStarted: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var requestedHere by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var hasRequested by rememberSaveable { mutableStateOf(false) }
    var inFlight by remember { mutableStateOf(false) }
    var access by remember { mutableStateOf(PermissionAccess.read(context, requestedPermissions)) }
    val latestRequested by rememberUpdatedState(requestedPermissions + requestedHere)
    fun refresh() { access = PermissionAccess.read(context, latestRequested) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onInteractionStarted()
        inFlight = false
        hasRequested = true
        refresh() // Callback maps are not the source of truth (e.g. approximate location).
        scope.launch { preferences.markPermissionGuideHandled() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(requestedPermissions) { refresh() }

    fun openSettings() {
        onInteractionStarted()
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("开始前，一次设置好权限", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
            Text("系统会依次询问。定位和麦克风都可不允许，之后仍能拍照、整理和导出资料。", color = TextSecondaryDark)
            PermissionCard("相机", "用于拍照和录像。不开启也能浏览、管理已有工程资料。", access.camera, access.cameraNeedsSettings)
            PermissionCard("位置 · 可选", "用于水印中的现场地点。允许大致位置也可以；不开启时照片不含定位。", access.location, access.locationNeedsSettings,
                if (access.coarseLocation && !access.fineLocation) "已允许大致位置" else null)
            PermissionCard("麦克风 · 可选", "用于录制视频声音。不开启时可以录制无声视频。", access.microphone, access.microphoneNeedsSettings)
            if (hasRequested) {
                Text(if (access.allGranted) "所需权限已就绪，可以开始使用。" else "授权结果已更新。未开启的项目可稍后补充，不会再次自动弹出申请。", color = EngineeringYellow)
            }
            val requestable = access.permissionsToRequest()
            Button(
                onClick = {
                    refresh()
                    if (hasRequested || access.allGranted) onContinue()
                    else if (access.permissionsToRequest().isEmpty()) openSettings()
                    else {
                        val missing = access.permissionsToRequest()
                        onInteractionStarted()
                        requestedHere = missing
                        inFlight = true
                        scope.launch {
                            try {
                                preferences.markPermissionsRequested(missing)
                                launcher.launch(missing.toTypedArray())
                            } catch (error: Exception) {
                                inFlight = false
                                Toast.makeText(context, "无法打开权限申请，请稍后重试", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, enabled = !inFlight, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow, contentColor = DarkBackground)
            ) {
                Text(when {
                    inFlight -> "请完成系统权限询问…"
                    hasRequested || access.allGranted -> "进入相机"
                    requestable.isEmpty() -> "去系统设置开启"
                    else -> "开启所需权限"
                }, modifier = Modifier.padding(vertical = 6.dp))
            }
            if (hasRequested && (access.cameraNeedsSettings || access.locationNeedsSettings || access.microphoneNeedsSettings)) {
                OutlinedButton(onClick = ::openSettings, enabled = !inFlight, modifier = Modifier.fillMaxWidth()) { Text("去系统设置补充权限") }
            }
            TextButton(onClick = onContinue, enabled = !inFlight, modifier = Modifier.fillMaxWidth()) { Text(if (hasRequested) "稍后补充，先管理资料" else "暂时跳过") }
            Text("不会申请后台定位、通知或存储权限。系统相册默认关闭，需要时再到设置开启。", color = TextSecondaryDark, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PermissionCard(title: String, description: String, granted: Boolean, needsSettings: Boolean, grantedLabel: String? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = DarkCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(description, color = TextSecondaryDark, fontSize = 14.sp)
            Text(when { granted -> grantedLabel ?: "已允许"; needsSettings -> "未开启 · 需前往系统设置"; else -> "未开启" }, color = if (granted) SuccessGreen else EngineeringYellow, fontSize = 13.sp)
        }
    }
}
