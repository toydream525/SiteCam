package com.sitecam.app.core.database

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.CursorWrapper
import android.os.CancellationSignal
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class AlbumCursorSnapshotIntegrationTest {
    @Test fun deletingWhileLargeAlbumCrossesCursorWindowsKeepsOneSnapshot(): Unit = runBlocking {
        val gate = WindowBoundaryGate()
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .openHelperFactory(gate.factory())
            .build()
        try {
            val dao = database.mediaItemDao()
            val ids = database.withTransaction {
                val project = database.projectDao().insertProject(ProjectEntity(name = "批量删除回归"))
                val snapshot = "x".repeat(8192)
                (1..384).map { index ->
                    dao.insertMediaItem(MediaItemEntity(projectId = project,
                        contentUri = "content://album-test/$index", fileName = "$index.jpg",
                        captureTimestamp = index.toLong(), watermarkSnapshotJson = snapshot))
                }
            }
            gate.enabled.set(true)
            val deletion = async(Dispatchers.IO) {
                check(gate.boundary.await(10, TimeUnit.SECONDS)) { "Album did not cross a cursor window" }
                gate.writerStarted.countDown()
                try { dao.deleteMediaItemById(ids.first()) }
                finally { gate.writerFinished.countDown() }
            }
            val album = withTimeout(15_000) {
                withContext(Dispatchers.IO) { dao.getAllMediaItems().first() }
            }
            withTimeout(15_000) { deletion.await() }
            assertTrue("The native cursor must span multiple windows", gate.crossedWindow.get())
            assertEquals(ids.asReversed(), album.map { it.id })
            assertEquals(ids.drop(1).asReversed(), dao.getAllMediaItems().first().map { it.id })
        } finally { database.close() }
    }

    /** Pause at the real native window boundary so the deletion competes with a refill. */
    private class WindowBoundaryGate {
        val enabled = AtomicBoolean(false)
        val crossedWindow = AtomicBoolean(false)
        val boundary = CountDownLatch(1)
        val writerStarted = CountDownLatch(1)
        val writerFinished = CountDownLatch(1)

        private fun wrap(query: SupportSQLiteQuery, cursor: Cursor): Cursor {
            if (query.sql != "SELECT * FROM media_items WHERE isUnavailable = 0 ORDER BY captureTimestamp DESC" ||
                !enabled.compareAndSet(true, false)) return cursor
            val windowed = cursor as AbstractWindowedCursor
            windowed.window = CursorWindow("album-regression", 128L * 1024)
            val count = windowed.count
            val firstWindowRows = requireNotNull(windowed.window).numRows
            check(firstWindowRows in 1 until count) { "Fixture must exceed one native CursorWindow" }
            return object : CursorWrapper(cursor) {
                override fun moveToNext(): Boolean {
                    val moved = super.moveToNext()
                    if (moved && position == firstWindowRows - 1) {
                        crossedWindow.set(true)
                        boundary.countDown()
                        check(writerStarted.await(10, TimeUnit.SECONDS))
                        // An unprotected read lets deletion finish before refill. A
                        // transactional read keeps the writer queued until mapping ends.
                        writerFinished.await(500, TimeUnit.MILLISECONDS)
                    }
                    return moved
                }
            }
        }

        fun factory() = SupportSQLiteOpenHelper.Factory { configuration ->
            val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
            fun wrapDatabase(database: SupportSQLiteDatabase) = object : SupportSQLiteDatabase by database {
                override fun query(query: SupportSQLiteQuery): Cursor = wrap(query, database.query(query))
                override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
                    wrap(query, database.query(query, cancellationSignal))
            }
            object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase: SupportSQLiteDatabase get() = wrapDatabase(helper.writableDatabase)
                override val readableDatabase: SupportSQLiteDatabase get() = wrapDatabase(helper.readableDatabase)
            }
        }
    }
}
