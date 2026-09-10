package com.sitecam.app.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.sitecam.app.core.database.entity.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ProjectDataTransactionsTest {
    @Test fun migration3To4PreservesExistingProjectsAndDefaults() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(RuntimeEnvironment.getApplication())
            .name(null).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE projects (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, isArchived INTEGER NOT NULL)")
                    db.execSQL("INSERT INTO projects VALUES (42, '旧工程', 1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.use {
            val db = it.writableDatabase
            AppDatabase.MIGRATION_3_4.migrate(db)
            db.query("SELECT id, name, isArchived, routeName, isCaptureLocked FROM projects").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(42,cursor.getInt(0)); assertEquals("旧工程",cursor.getString(1))
                assertEquals(1,cursor.getInt(2)); assertEquals("",cursor.getString(3)); assertEquals(0,cursor.getInt(4))
            }
            db.query("PRAGMA table_info(projects)").use { cursor ->
                val columns = mutableMapOf<String,String>()
                while(cursor.moveToNext()) columns[cursor.getString(1)] = cursor.getString(4).orEmpty()
                assertEquals("''",columns["routeName"]); assertEquals("0",columns["isCaptureLocked"])
            }
        }
    }
    @Test fun moveAndIssueChangesKeepBothProjectAndIssueFlagConsistent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val source = db.projectDao().insertProject(ProjectEntity(name="来源"))
            val target = db.projectDao().insertProject(ProjectEntity(name="锁定目标",isCaptureLocked=true))
            val id = db.mediaItemDao().insertMediaItem(MediaItemEntity(projectId=source,contentUri="file:///original.jpg",fileName="original.jpg",captureTimestamp=1234))
            db.issueDao().saveIssueAndMarkMedia(IssueEntity(projectId=source,mediaId=id,title="待整改",status="PENDING"))
            db.mediaItemDao().moveMediaToProject(listOf(id),target)
            val moved = db.mediaItemDao().getMediaItemById(id)!!
            assertEquals(target,moved.projectId); assertEquals(1234,moved.captureTimestamp); assertEquals("file:///original.jpg",moved.contentUri)
            assertEquals(target,db.issueDao().getIssueByMediaId(id)!!.projectId); assertTrue(moved.isIssue)
            // A detail editor holding stale source-project state must not move the issue back.
            db.issueDao().saveIssueAndMarkMedia(IssueEntity(projectId=source,mediaId=id,title="已整改",status="COMPLETED"))
            assertEquals(target,db.issueDao().getIssueByMediaId(id)!!.projectId)
            assertEquals(1,db.issueDao().getAllIssues().first().size)
            db.issueDao().removeIssueAndUnmarkMedia(id)
            assertNull(db.issueDao().getIssueByMediaId(id)); assertFalse(db.mediaItemDao().getMediaItemById(id)!!.isIssue)
        } finally { db.close() }
    }
}
