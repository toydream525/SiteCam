package com.sitecam.app.feature.gallery

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoThumbnail(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                    retriever.setDataSource(descriptor.fileDescriptor)
                    val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    frame?.let {
                        if (it.width > 512 || it.height > 512) {
                            val scale = minOf(512f / it.width, 512f / it.height)
                            Bitmap.createScaledBitmap(it, (it.width * scale).toInt(), (it.height * scale).toInt(), true)
                                .also { scaled -> if (scaled !== it && !it.isRecycled) it.recycle() }
                        } else it
                    }
                }
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "视频缩略图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF303030)))
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "视频",
            tint = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(4.dp)
        )
    }
}
