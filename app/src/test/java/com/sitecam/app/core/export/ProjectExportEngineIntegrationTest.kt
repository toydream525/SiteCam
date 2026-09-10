package com.sitecam.app.core.export

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import com.sitecam.app.core.database.AppDatabase
import com.sitecam.app.core.database.entity.*
import com.sitecam.app.core.media.PhotoQualityProfile
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectExportEngineIntegrationTest {
    class OutputProvider(private val dir: File) : ContentProvider() {
        var next = 0
        val files = mutableMapOf<String, File>()
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val target = Uri.parse("content://media/test/${++next}")
            files[target.toString()] = File(dir,"result_$next.zip")
            return target
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(files.getValue(uri.toString()),
            if(mode.startsWith("w")) ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY else ParcelFileDescriptor.MODE_READ_ONLY)
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 1
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int { files[uri.toString()]?.delete(); return 1 }
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri) = "application/zip"
    }
    class FolderProvider(private val dir: File) : android.provider.DocumentsProvider() {
        override fun onCreate() = true
        private fun file(id: String) = if(id == "root") dir else File(dir,id.removePrefix("root/"))
        private fun cursor(projection: Array<out String>?) = android.database.MatrixCursor(projection ?: arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
            android.provider.DocumentsContract.Document.COLUMN_FLAGS))
        private fun add(cursor: android.database.MatrixCursor, id: String) {
            val target = file(id)
            cursor.addRow(cursor.columnNames.map { column -> when(column) {
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME -> target.name
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE -> if(target.isDirectory) android.provider.DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                android.provider.DocumentsContract.Document.COLUMN_FLAGS -> android.provider.DocumentsContract.Document.FLAG_SUPPORTS_WRITE or android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE or android.provider.DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                else -> null
            } }.toTypedArray())
        }
        override fun queryRoots(projection: Array<out String>?) = android.database.MatrixCursor(projection ?: arrayOf("root_id"))
        override fun queryDocument(documentId: String, projection: Array<out String>?) = cursor(projection).also { add(it,documentId) }
        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) = cursor(projection).also { result ->
            file(parentDocumentId).listFiles().orEmpty().forEach { add(result,"$parentDocumentId/${it.name}") }
        }
        override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId == parentDocumentId || documentId.startsWith("$parentDocumentId/")
        override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
            val id = "$parentDocumentId/$displayName"
            val target = file(id)
            if(mimeType == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
            return id
        }
        override fun deleteDocument(documentId: String) { file(documentId).deleteRecursively() }
        override fun openDocument(documentId: String, mode: String, signal: android.os.CancellationSignal?): ParcelFileDescriptor {
            if(mode.startsWith("w") && documentId.endsWith("selected.jpg")) throw java.io.FileNotFoundException("fixture: destination write refused")
            return ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.parseMode(mode))
        }
    }
    @Test fun realFolderEngineReportsDestinationFailureInDeliveredCsvAndReport() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val destination = File(context.cacheDir,"folder_provider").apply { mkdirs() }
        val provider = FolderProvider(destination)
        provider.attachInfo(context,android.content.pm.ProviderInfo().apply {
            authority = "exports"; exported = true; grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS"
        })
        ShadowContentResolver.registerProviderInternal("exports",provider)
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        val source = File(context.cacheDir,"folder_source.jpg").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        try {
            val project = db.projectDao().insertProject(ProjectEntity(name="目标失败工程"))
            val id = db.mediaItemDao().insertMediaItem(MediaItemEntity(projectId=project,contentUri=Uri.fromFile(source).toString(),fileName="selected.jpg",captureTimestamp=123))
            val engine = ProjectExportEngine(context,db)
            val result = engine.exportProjectsToFolder(setOf(project),android.provider.DocumentsContract.buildTreeDocumentUri("exports","root"),setOf(id))
            assertEquals(0,result.copiedFileCount); assertEquals(1,result.missingMediaCount); assertEquals(1,result.destinationFailureCount)
            val csv = destination.walkTopDown().single { it.name == "photo_index.csv" }.readText()
            assertTrue(csv.contains("FAILED")); assertFalse(csv.contains("COPIED"))
            val report = destination.walkTopDown().filter { it.name == "export_report.json" }.map { JSONObject(it.readText()) }.single { it.has("files") }
            assertFalse(report.getJSONArray("files").getJSONObject(0).getBoolean("success"))
            assertEquals("FAILED",report.getJSONArray("files").getJSONObject(0).getString("deliveryStatus"))
            assertEquals(1,report.getJSONArray("deliveryFailures").length())
            assertArrayEquals(byteArrayOf(1,2,3,4),source.readBytes())
        } finally { db.close(); destination.deleteRecursively(); source.delete() }
    }
    @Test fun realEngineExportsSelectedProjectsMissingReportAndCompressedCopyWithCaptureExif() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val dir = File(context.cacheDir,"export_integration").apply { mkdirs() }
        val provider = OutputProvider(dir)
        provider.attachInfo(context, android.content.pm.ProviderInfo().apply { authority = "media"; exported = true; grantUriPermissions = true })
        ShadowContentResolver.registerProviderInternal("media",provider)
        val db = Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val a = db.projectDao().insertProject(ProjectEntity(name="同名",routeName="线路甲",isCaptureLocked=true))
            val b = db.projectDao().insertProject(ProjectEntity(name="同名"))
            val source = File(dir,"source.jpg")
            val bitmap = Bitmap.createBitmap(2400,1600,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,96,it) }; bitmap.recycle()
            ExifInterface(source.absolutePath).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL,"2025:12:31 23:59:01")
                setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString())
                setLatLong(31.2,121.5); saveAttributes()
            }
            val originalBytes = source.readBytes()
            suspend fun media(project: Long, name: String, uri: String) = db.mediaItemDao().insertMediaItem(
                MediaItemEntity(projectId=project,contentUri=uri,fileName=name,captureTimestamp=1767196741000,width=2400,height=1600,latitude=31.2,longitude=121.5))
            val selected = media(a,"selected.jpg",Uri.fromFile(source).toString())
            val excluded = media(a,"excluded.jpg",Uri.fromFile(source).toString())
            val missing = media(b,"missing.jpg",Uri.fromFile(File(dir,"absent.jpg")).toString())
            db.issueDao().saveIssueAndMarkMedia(IssueEntity(projectId=a,mediaId=selected,title="临边防护",status="IN_PROGRESS"))
            db.issueDao().insertAnnotation(AnnotationEntity(mediaId=selected,annotatedContentUri=Uri.fromFile(source).toString()))
            val result = ProjectExportEngine(context,db).exportProjectsToZip(setOf(a,b),setOf(selected,missing),ExportOptions(PhotoQualityProfile.SMALL))
            assertEquals(1,result.copiedFileCount); assertEquals(1,result.missingMediaCount); assertEquals(1,result.copiedAnnotationCount)
            val entries = mutableMapOf<String,ByteArray>()
            ZipInputStream(provider.files.getValue(result.zipUri.toString()).inputStream()).use { zip ->
                while(true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() }
            }
            assertEquals(2,entries.keys.map { it.substringBefore('/') }.distinct().size)
            assertFalse(entries.keys.any { it.contains("excluded") })
            assertTrue(entries.keys.any { it.contains("_p$a/") }); assertTrue(entries.keys.any { it.contains("_p$b/") })
            val photoBytes = entries.entries.single { it.key.contains("/Photos/") }.value
            val output = File(dir,"compressed.jpg").apply { writeBytes(photoBytes) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds=true }
            BitmapFactory.decodeFile(output.absolutePath,bounds)
            assertEquals(1280,bounds.outWidth); assertEquals(853,bounds.outHeight)
            val exif = ExifInterface(output.absolutePath)
            assertEquals("2025:12:31 23:59:01",exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals(ExifInterface.ORIENTATION_ROTATE_90,exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,0))
            assertEquals(31.2,exif.latLong!![0],0.00001)
            assertArrayEquals(originalBytes,source.readBytes())
            val info = JSONObject(entries.entries.single { it.key.contains("_p$a/") && it.key.endsWith("project_info.json") }.value.toString(Charsets.UTF_8))
            assertEquals("线路甲",info.getString("routeName")); assertTrue(info.getBoolean("isCaptureLocked")); assertTrue(info.has("videoWatermarkBurnIn"))
            val csv = entries.entries.single { it.key.contains("_p$a/") && it.key.endsWith("photo_index.csv") }.value.toString(Charsets.UTF_8)
            assertTrue(csv.contains("GPS坐标")); assertTrue(csv.contains("处理中")); assertTrue(csv.contains("是否为问题"))
            val report = JSONObject(entries.entries.single { it.key.contains("_p$b/") && it.key.endsWith("export_report.json") }.value.toString(Charsets.UTF_8))
            assertFalse(report.getJSONArray("files").getJSONObject(0).getBoolean("success")); assertEquals(1,report.getJSONArray("failures").length())
            assertNotNull(db.mediaItemDao().getMediaItemById(excluded))
        } finally { db.close(); dir.deleteRecursively() }
    }
}
