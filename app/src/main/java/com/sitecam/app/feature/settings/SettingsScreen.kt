package com.sitecam.app.feature.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.BuildConfig
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置与设备诊断",
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
                                text = "自定义开启字段、标段/单位、字号及不透明度",
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

            // Watermark Template Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "默认水印样式",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    uiState.templates.forEach { template ->
                        val isSelected = template.id == uiState.activeTemplateId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setActiveTemplate(template.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setActiveTemplate(template.id) },
                                colors = RadioButtonDefaults.colors(selectedColor = EngineeringYellow)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = template.name,
                                    color = TextPrimaryDark,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                val subtitle = when (template.styleType) {
                                    "MINIMAL" -> "极简：日期 + 地点 + 工程名称"
                                    "INFO_BOARD" -> "工程信息板：大卡片现场记录"
                                    else -> "经典工程水印：左下角结构化信息卡"
                                }
                                Text(
                                    text = subtitle,
                                    color = TextSecondaryDark,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
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

                    val qualities = listOf(95 to "高质量（推荐，保留丰富现场细节）", 85 to "标准质量（节省存储空间）", 100 to "超高质量（仍为有损 JPEG）")
                    qualities.forEach { (q, desc) ->
                        val isSelected = uiState.jpegQuality == q
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setJpegQuality(q) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setJpegQuality(q) },
                                colors = RadioButtonDefaults.colors(selectedColor = EngineeringYellow)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = "$q%",
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
                        text = "纯本地离线运行 · 无账号体系 · 媒体存入公共工程相册",
                        color = TextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
