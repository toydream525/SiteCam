package com.sitecam.app.core.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaAvailabilitySyncTest {
    class Provider : ContentProvider() {
        var status = "present"
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
            if (status == "denied") throw SecurityException("permission denied")
            if (status == "offline") return null
            return MatrixCursor(arrayOf("_id", "is_trashed")).apply {
                if (status != "deleted") addRow(arrayOf(1, if (status == "trashed") 1 else 0))
            }
        }
        override fun getType(uri: Uri) = "image/jpeg"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("No writes allowed")
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = error("No deletes allowed")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = error("No writes allowed")
    }
    @Test fun migrationPreservesOldMediaAndDefaultsToVisible() {
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
                .name(null).callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE media_items (id INTEGER PRIMARY KEY, contentUri TEXT)")
                        db.execSQL("INSERT INTO media_items VALUES (7, 'content://media/external/images/media/7')")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build())
        helper.use {
            AppDatabase.MIGRATION_4_5.migrate(it.writableDatabase)
            it.writableDatabase.query("SELECT id, contentUri, isUnavailable FROM media_items").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(7, c.getInt(0))
                assertEquals("content://media/external/images/media/7", c.getString(1)); assertEquals(0, c.getInt(2))
            }
        }
    }
    @Test fun externalDeletionTrashRestoreAndFailuresKeepIndexConsistent() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
        val provider = Provider()
        ShadowContentResolver.registerProviderInternal("media", provider)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val project = db.projectDao().insertProject(ProjectEntity(name="sync", categoryName="建筑"))
            val id = db.mediaItemDao().insertMediaItem(MediaItemEntity(projectId=project, contentUri="content://media/external/images/media/1", fileName="1.jpg", captureTimestamp=1))
            db.issueDao().insertIssue(com.sitecam.app.core.database.entity.IssueEntity(projectId=project, mediaId=id, title="保留问题"))
            val sync = MediaAvailabilitySync(context, db)
            for (status in listOf("denied", "offline", "present")) {
                provider.status = status; sync.reconcile()
                assertEquals(1, db.mediaItemDao().getAllMediaItems().first().size)
            }
            for (status in listOf("trashed", "deleted")) {
                provider.status = status; sync.reconcile()
                assertTrue(db.mediaItemDao().getAllMediaItems().first().isEmpty())
                assertNull(db.mediaItemDao().getLatestMediaItem().first())
                assertEquals(0, db.mediaItemDao().getPhotoCountForProject(project).first())
                assertEquals(id, db.mediaItemDao().getMediaForAvailabilitySync().single().id)
                assertEquals("保留问题", db.issueDao().getIssueByMediaId(id)!!.title)
            }
            provider.status="present"; sync.reconcile()
            assertEquals(id, db.mediaItemDao().getAllMediaItems().first().single().id)
            provider.status="deleted"
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_UNMOUNTED)
            sync.reconcile()
            assertEquals(1, db.mediaItemDao().getAllMediaItems().first().size)
            assertNull(sync.queryAvailability(Uri.parse("content://com.sitecam.app.fileprovider/private.jpg")))
        } finally { db.close() }
    }
}
