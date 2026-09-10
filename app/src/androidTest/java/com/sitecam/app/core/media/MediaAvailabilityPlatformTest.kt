package com.sitecam.app.core.media

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaAvailabilityPlatformTest {
    @Test fun realMediaStoreObserverTracksTrashRestoreAndDelete() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SiteCam-issue1-test-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SiteCamIssueTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        })!!
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        var registry: LifecycleRegistry? = null
        val owner = object : LifecycleOwner { override val lifecycle: Lifecycle get() = registry!! }
        try {
            val bitmap = Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
            resolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)) }
            bitmap.recycle()
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING,0) },null,null)
            val project = db.projectDao().insertProject(ProjectEntity(name="Issue1 test",categoryName="建筑"))
            val id = db.mediaItemDao().insertMediaItem(MediaItemEntity(projectId=project,contentUri=uri.toString(),fileName="fixture.jpg",captureTimestamp=1))
            val sync = MediaAvailabilitySync(context,db)
            instrumentation.runOnMainSync {
                registry=LifecycleRegistry(owner)
                registry!!.addObserver(sync)
                registry!!.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry!!.handleLifecycleEvent(Lifecycle.Event.ON_START)
            }
            assertEquals(1,resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_TRASHED,1) },null,null))
            withTimeout(10000) { db.mediaItemDao().getAllMediaItems().first { it.isEmpty() } }
            assertEquals(0,db.mediaItemDao().getPhotoCountForProject(project).first())
            assertEquals(1,resolver.update(uri,ContentValues().apply { put(MediaStore.Images.Media.IS_TRASHED,0) },null,null))
            withTimeout(10000) { db.mediaItemDao().getAllMediaItems().first { it.size==1 } }
            assertEquals(1,resolver.delete(uri,null,null))
            withTimeout(10000) { db.mediaItemDao().getAllMediaItems().first { it.isEmpty() } }
            assertEquals(id,db.mediaItemDao().getMediaForAvailabilitySync().single().id)
        } finally {
            instrumentation.runOnMainSync {
                if (registry != null) {
                    registry!!.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                    registry!!.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                }
            }
            resolver.delete(uri,null,null)
            db.close()
        }
    }
}
