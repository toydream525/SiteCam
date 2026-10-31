package com.sitecam.app.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sitecam.app.R

@Composable
fun AuthorSupportDialog(onDismiss: () -> Unit) {
    var wechat by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .9f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("支持作者", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "支付宝", true to "微信").forEach { (value, label) ->
                        FilterChip(selected = wechat == value, onClick = { wechat = value },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = com.sitecam.app.ui.theme.EngineeringYellow,
                                selectedLabelColor = androidx.compose.ui.graphics.Color(0xFF231A00)),
                            label = { Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) })
                    }
                }
                Image(painterResource(if (wechat) R.drawable.author_support_wechat else R.drawable.author_support_alipay),
                    if (wechat) "微信收款二维码" else "支付宝收款二维码",
                    Modifier.fillMaxWidth().weight(1f, fill = false), contentScale = ContentScale.Fit)
            }
        }
    }
}
