package com.sitecam.app.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.ui.theme.DarkBackground
import com.sitecam.app.ui.theme.DarkCard
import com.sitecam.app.ui.theme.EngineeringAmber
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.InfoBlue
import com.sitecam.app.ui.theme.SuccessGreen
import com.sitecam.app.ui.theme.TextPrimaryDark
import com.sitecam.app.ui.theme.TextSecondaryDark

private data class GuidePage(
    val title: String,
    val body: String,
    val detail: String,
    val illustrationDescription: String
)

private val guidePages = listOf(
    GuidePage(
        title = "先选工程，照片不混放",
        body = "拍摄前先选好工程和线路。照片、视频、问题记录会跟着当前工程保存。",
        detail = "进入工程项目，可以新建工程，填写名称、类别、地点和备注。",
        illustrationDescription = "工程卡片与分开的照片夹示意"
    ),
    GuidePage(
        title = "横拍竖拍，自己选择",
        body = "按现场需要握持手机。竖屏、左横屏、右横屏或自动，都可以记住你的选择。",
        detail = "取景画面会跟着方向调整，水印和成片也会保持一致。",
        illustrationDescription = "手机横竖方向与取景框示意"
    ),
    GuidePage(
        title = "按日期找照片，随手记问题",
        body = "相册可以按照片拍摄年月日区间快速查找，也能只看问题记录。",
        detail = "拍完发现问题，直接标记一般、重要或严重，并填写处理状态。",
        illustrationDescription = "日历、相册和问题标记示意"
    ),
    GuidePage(
        title = "资料统一导出，完工及时锁定",
        body = "导出时可选一个 ZIP 或总文件夹，每个工程都有独立目录和清单。",
        detail = "完工后锁定工程，暂时停用该工程快门，仍可编辑、移动、删除和导出。",
        illustrationDescription = "导出文件夹与锁定工程示意"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    isReplay: Boolean,
    onFinish: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = guidePages[pageIndex]
    val isLastPage = pageIndex == guidePages.lastIndex

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isReplay) "功能指引" else "开始使用工程水印相机",
                        color = TextPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = TextPrimaryDark
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onFinish) {
                        Text(
                            text = "跳过",
                            color = EngineeringYellow,
                            fontSize = 16.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "第 ${pageIndex + 1} / ${guidePages.size} 页",
                color = EngineeringAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            OnboardingIllustration(
                pageIndex = pageIndex,
                description = page.illustrationDescription,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Text(
                text = page.title,
                color = TextPrimaryDark,
                fontSize = 26.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = page.body,
                color = TextPrimaryDark,
                fontSize = 18.sp,
                lineHeight = 28.sp
            )
            Text(
                text = page.detail,
                color = TextSecondaryDark,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                guidePages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pageIndex) 12.dp else 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (index == pageIndex) EngineeringYellow else DarkCard)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (pageIndex > 0) pageIndex -= 1 },
                    enabled = pageIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(text = "上一步", fontSize = 16.sp)
                }
                Button(
                    onClick = {
                        if (isLastPage) onFinish() else pageIndex += 1
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EngineeringYellow,
                        contentColor = DarkBackground
                    )
                ) {
                    Text(
                        text = if (isLastPage) "开始使用" else "下一步",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun OnboardingLoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = EngineeringYellow)
    }
}

@Composable
private fun OnboardingIllustration(
    pageIndex: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    val buttonLabels = when (pageIndex) {
        0 -> listOf("工程项目", "工程相册")
        1 -> listOf("方向", "取景画面")
        2 -> listOf("日期筛选", "问题模式")
        else -> listOf("导出", "锁定拍摄")
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(DarkCard)
            .semantics { contentDescription = description }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val accent = EngineeringYellow
            val blue = InfoBlue
            val green = SuccessGreen

            when (pageIndex) {
            0 -> {
                // Two project cards with their media kept in separate folders.
                drawRoundRect(
                    color = blue,
                    topLeft = Offset(size.width * .16f, size.height * .2f),
                    size = Size(size.width * .68f, size.height * .26f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f),
                    style = stroke
                )
                drawLine(
                    color = accent,
                    start = Offset(size.width * .24f, size.height * .3f),
                    end = Offset(size.width * .57f, size.height * .3f),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .24f, size.height * .37f),
                    end = Offset(size.width * .45f, size.height * .37f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawRoundRect(
                    color = green,
                    topLeft = Offset(size.width * .16f, size.height * .53f),
                    size = Size(size.width * .68f, size.height * .26f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f),
                    style = stroke
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .24f, size.height * .63f),
                    end = Offset(size.width * .56f, size.height * .63f),
                    strokeWidth = 7f,
                    cap = StrokeCap.Round
                )
                drawCircle(accent, radius = 9f, center = Offset(size.width * .74f, size.height * .67f))
            }

            1 -> {
                // A phone and two intentional orientation choices.
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * .37f, size.height * .12f),
                    size = Size(size.width * .26f, size.height * .76f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f),
                    style = stroke
                )
                drawRect(
                    color = blue,
                    topLeft = Offset(size.width * .42f, size.height * .25f),
                    size = Size(size.width * .16f, size.height * .40f)
                )
                drawLine(
                    color = accent,
                    start = Offset(size.width * .45f, size.height * .45f),
                    end = Offset(size.width * .55f, size.height * .35f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(size.width * .55f, size.height * .35f),
                    end = Offset(size.width * .55f, size.height * .54f),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
                drawCircle(Color.White, radius = 7f, center = Offset(size.width * .5f, size.height * .75f))
                rotate(-12f, pivot = Offset(size.width * .18f, size.height * .72f)) {
                    drawRect(color = blue, topLeft = Offset(size.width * .1f, size.height * .67f), size = Size(size.width * .16f, size.height * .1f))
                    drawLine(color = accent, start = Offset(size.width * .14f, size.height * .72f), end = Offset(size.width * .22f, size.height * .72f), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                rotate(12f, pivot = Offset(size.width * .82f, size.height * .72f)) {
                    drawRect(color = green, topLeft = Offset(size.width * .74f, size.height * .67f), size = Size(size.width * .16f, size.height * .1f))
                    drawLine(color = Color.White, start = Offset(size.width * .78f, size.height * .72f), end = Offset(size.width * .86f, size.height * .72f), strokeWidth = 5f, cap = StrokeCap.Round)
                }
            }

            2 -> {
                // Calendar plus a photo card with an issue marker.
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * .13f, size.height * .2f),
                    size = Size(size.width * .42f, size.height * .55f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f),
                    style = stroke
                )
                drawLine(color = accent, start = Offset(size.width * .16f, size.height * .34f), end = Offset(size.width * .52f, size.height * .34f), strokeWidth = 8f)
                drawLine(color = blue, start = Offset(size.width * .22f, size.height * .45f), end = Offset(size.width * .35f, size.height * .45f), strokeWidth = 7f, cap = StrokeCap.Round)
                drawLine(color = blue, start = Offset(size.width * .22f, size.height * .56f), end = Offset(size.width * .44f, size.height * .56f), strokeWidth = 7f, cap = StrokeCap.Round)
                drawRoundRect(color = green, topLeft = Offset(size.width * .54f, size.height * .3f), size = Size(size.width * .3f, size.height * .42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f))
                drawCircle(Color.White, radius = 24f, center = Offset(size.width * .69f, size.height * .49f), style = stroke)
                drawLine(color = Color.White, start = Offset(size.width * .69f, size.height * .38f), end = Offset(size.width * .69f, size.height * .5f), strokeWidth = 6f, cap = StrokeCap.Round)
                drawLine(color = Color.White, start = Offset(size.width * .69f, size.height * .5f), end = Offset(size.width * .77f, size.height * .55f), strokeWidth = 6f, cap = StrokeCap.Round)
                val triangle = Path().apply {
                    moveTo(size.width * .78f, size.height * .72f)
                    lineTo(size.width * .88f, size.height * .72f)
                    lineTo(size.width * .83f, size.height * .84f)
                    close()
                }
                drawPath(triangle, color = Color(0xFFE65C5C))
                drawLine(color = Color.White, start = Offset(size.width * .83f, size.height * .75f), end = Offset(size.width * .83f, size.height * .8f), strokeWidth = 4f, cap = StrokeCap.Round)
            }

            else -> {
                // Export folder and a lock that signals a finished project.
                drawRoundRect(
                    color = blue,
                    topLeft = Offset(size.width * .12f, size.height * .33f),
                    size = Size(size.width * .56f, size.height * .36f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f)
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * .18f, size.height * .25f),
                    size = Size(size.width * .25f, size.height * .13f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f)
                )
                drawLine(color = accent, start = Offset(size.width * .25f, size.height * .5f), end = Offset(size.width * .52f, size.height * .5f), strokeWidth = 9f, cap = StrokeCap.Round)
                drawLine(color = accent, start = Offset(size.width * .38f, size.height * .4f), end = Offset(size.width * .52f, size.height * .5f), strokeWidth = 9f, cap = StrokeCap.Round)
                drawLine(color = accent, start = Offset(size.width * .52f, size.height * .5f), end = Offset(size.width * .38f, size.height * .6f), strokeWidth = 9f, cap = StrokeCap.Round)
                drawRoundRect(color = green, topLeft = Offset(size.width * .63f, size.height * .48f), size = Size(size.width * .2f, size.height * .22f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f))
                drawArc(color = Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(size.width * .67f, size.height * .35f), size = Size(size.width * .12f, size.height * .22f), style = stroke)
                drawCircle(Color.White, radius = 6f, center = Offset(size.width * .73f, size.height * .58f))
            }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            buttonLabels.forEach { label ->
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
