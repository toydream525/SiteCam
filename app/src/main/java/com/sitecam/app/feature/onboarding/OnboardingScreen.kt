package com.sitecam.app.feature.onboarding

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sitecam.app.R
import com.sitecam.app.ui.theme.PageBackground as DarkBackground
import com.sitecam.app.ui.theme.EngineeringYellow
import com.sitecam.app.ui.theme.PageText as TextPrimaryDark
import com.sitecam.app.ui.theme.PageMuted as TextSecondaryDark

private data class GuidePage(val title: String, val illustrationDescription: String)

private val guidePages = listOf(
    GuidePage("先选工程，\n照片不混放", "工程卡片与分开的照片夹示意"),
    GuidePage("横拍竖拍，\n自己选择", "手机横竖方向与取景框示意"),
    GuidePage("按日期找照片，\n随手记问题", "日历、相册和问题标记示意"),
    GuidePage("资料统一导出，\n完工及时锁定", "导出文件夹与锁定工程示意")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    isReplay: Boolean,
    onFinish: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = guidePages[pageIndex]
    val isLastPage = pageIndex == guidePages.lastIndex

    androidx.activity.compose.BackHandler(enabled = pageIndex > 0) { pageIndex -= 1 }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val spacious = maxWidth >= 600.dp && maxHeight >= 600.dp
    val metrics = GuideLayout.metrics(maxWidth.value, maxHeight.value)
    val wide = metrics.wide
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val singleLineTitle = page.title.replace("\n", "")
    val titleFits = textMeasurer.measure(singleLineTitle,
        style = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
        maxLines = 1, softWrap = false).size.width <= with(density) { (minOf(maxWidth, 640.dp) - 32.dp).toPx() }
    val header: @Composable () -> Unit = {
        Text("工程水印相机", color = TextPrimaryDark, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
    val back: @Composable () -> Unit = {
        if (onNavigateBack != null) {
            IconButton(onClick = { if (pageIndex > 0) pageIndex -= 1 else onNavigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimaryDark)
            }
        }
    }
    val skip: @Composable RowScope.() -> Unit = {
        TextButton(onClick = onFinish) { Text("跳过", color = TextPrimaryDark, fontSize = 16.sp) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (spacious || wide) CenterAlignedTopAppBar(title = header, navigationIcon = back, actions = skip,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground))
            else TopAppBar(title = header, navigationIcon = back, actions = skip,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground))
        },
        bottomBar = {
            if (!spacious && !wide) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = if (spacious) 348.dp else 560.dp).fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    GuideDots(pageIndex)
                    Button(onClick = { if (isLastPage) onFinish() else pageIndex += 1 },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(if (spacious) 28.dp else 16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow, contentColor = Color.Black)) {
                        Text(if (isLastPage) "开始使用" else "下一步", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(Modifier.widthIn(max = if (wide) GuideLayout.BLOCK_MAX_DP.dp else 640.dp).fillMaxSize()) {
                if (wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = GuideLayout.PAGE_PADDING_DP.dp, vertical = GuideLayout.PAGE_PADDING_DP.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GuideLayout.COLUMN_GAP_DP.dp)) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            OnboardingIllustration(pageIndex, page.illustrationDescription, Modifier.fillMaxSize())
                        }
                        Column(Modifier.width(metrics.rightColumn.dp).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            val wideTitleFits = textMeasurer.measure(singleLineTitle,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
                                maxLines = 1, softWrap = false).size.width <=
                                with(density) { (metrics.rightColumn - 8f).dp.toPx() }
                            Text(if (wideTitleFits) singleLineTitle else page.title, color = TextPrimaryDark,
                                fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            GuideDots(pageIndex)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { if (isLastPage) onFinish() else pageIndex += 1 },
                                modifier = Modifier.fillMaxWidth().heightIn(min = GuideLayout.BUTTON_MIN_DP.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow, contentColor = Color.Black)) {
                                Text(if (isLastPage) "开始使用" else "下一步", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else if (spacious) {
                    val availableHeight = maxHeight
                    val illustrationHeight = minOf((maxWidth - 32.dp) * (445f / 386f),
                        (availableHeight - 220.dp).coerceAtLeast(160.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = availableHeight).padding(horizontal = 16.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        OnboardingIllustration(pageIndex, page.illustrationDescription,
                            Modifier.fillMaxWidth().height(illustrationHeight))
                        Spacer(Modifier.height(20.dp))
                        Text(if (titleFits) singleLineTitle else page.title, color = TextPrimaryDark,
                            fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        GuideDots(pageIndex)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { if (isLastPage) onFinish() else pageIndex += 1 },
                            modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().heightIn(min = 52.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EngineeringYellow, contentColor = Color.Black)) {
                            Text(if (isLastPage) "开始使用" else "下一步", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                val illustrationHeight = minOf(maxWidth * (445f / 386f), (maxHeight - 120.dp).coerceAtLeast(160.dp))
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    OnboardingIllustration(pageIndex, page.illustrationDescription,
                        Modifier.fillMaxWidth().height(illustrationHeight))
                    Text(if (spacious && titleFits) singleLineTitle else page.title, color = TextPrimaryDark, fontSize = 28.sp, lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                }
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
private fun GuideDots(pageIndex: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        guidePages.indices.forEach { index ->
            Box(Modifier.padding(horizontal = 5.dp).size(if (index == pageIndex) 10.dp else 8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (index == pageIndex) EngineeringYellow else TextSecondaryDark.copy(alpha = .24f)))
        }
    }
}

@Composable
private fun OnboardingIllustration(
    pageIndex: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val artwork = remember(context, pageIndex) {
        // Use the adopted illustration itself; crop out all source-page UI.
        context.resources.openRawResource(R.drawable.onboarding_steps_art).use { stream ->
            @Suppress("DEPRECATION")
            val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false))
            try {
                val left = 12 + 410 * pageIndex.coerceIn(0, 3)
                decoder.decodeRegion(Rect(left, 116, left + 386, 561), BitmapFactory.Options()).asImageBitmap()
            } finally {
                decoder.recycle()
            }
        }
    }
    Image(bitmap = artwork, contentDescription = description,
        modifier = modifier, contentScale = ContentScale.Fit)
}
