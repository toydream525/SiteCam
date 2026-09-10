package com.sitecam.app.core.media

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sitecam.app.core.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Preserve the index and associated issues so a system recycle-bin restore is reversible. */
class MediaAvailabilitySync(private val context: Context, private val database: AppDatabase) : DefaultLifecycleObserver {
    private var requests: Channel<Unit>? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { requests?.trySend(Unit) }
    }
    override fun onStart(owner: LifecycleOwner) {
        val queue = Channel<Unit>(Channel.CONFLATED)
        requests = queue
        runCatching { context.contentResolver.registerContentObserver(Uri.parse("content://media"), true, observer) }
        owner.lifecycleScope.launch {
            for (request in queue) {
                try { reconcile() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.w("MediaAvailabilitySync", "Media sync unavailable", e) }
            }
        }
        queue.trySend(Unit)
    }
    override fun onStop(owner: LifecycleOwner) {
        context.contentResolver.unregisterContentObserver(observer)
        requests?.close()
        requests = null
    }

    suspend fun reconcile() = withContext(Dispatchers.IO) {
        MediaOperationCoordinator.withExclusive {
            database.mediaItemDao().getMediaForAvailabilitySync().forEach { item ->
                if (item.processingStatus == "PROCESSING") return@forEach
                val available = queryAvailability(Uri.parse(item.contentUri)) ?: return@forEach
                if (item.isUnavailable == available) {
                    database.mediaItemDao().setUnavailable(item.id, item.contentUri, !available)
                }
            }
        }
    }

    /** null means unknown, never a reason to remove/hide a record. Private copies are untouched. */
    internal fun queryAvailability(uri: Uri): Boolean? {
        if (uri.scheme != "content" || uri.authority != MediaStore.AUTHORITY) return null
        if (Environment.getExternalStorageState() !in setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) return null
        return try {
            val projection = if (Build.VERSION.SDK_INT >= 30)
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED)
            else arrayOf(MediaStore.MediaColumns._ID)
            val cursor = if (Build.VERSION.SDK_INT >= 30) {
                context.contentResolver.query(uri, projection, Bundle().apply {
                    putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
                }, null)
            } else context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (!it.moveToFirst()) false
                else if (Build.VERSION.SDK_INT >= 30) {
                    val column = it.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                    if (column < 0) null else it.getInt(column) == 0
                } else true
            }
        } catch (_: SecurityException) { null }
          catch (_: RuntimeException) { null }
    }
}
