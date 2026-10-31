package com.sitecam.app.core.export

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportArchiveRepositoryTest {
    @Test fun selectedHistoricalDirectoryCanBeReadAndOnlyZipDocumentsDeleted() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val folder = File(context.cacheDir, "historical_directory").apply { mkdirs() }
        val zip = File(folder, "SiteCam_123.zip").apply { writeText("old export") }
        val source = File(folder, "source.jpg").apply { writeText("photo") }
        val provider = ProjectExportEngineIntegrationTest.FolderProvider(folder)
        val info = android.content.pm.ProviderInfo().apply {
            authority = "historical"; exported = true; grantUriPermissions = true
            packageName = context.packageName; name = provider.javaClass.name
            readPermission = "android.permission.MANAGE_DOCUMENTS"; writePermission = "android.permission.MANAGE_DOCUMENTS"
        }
        provider.attachInfo(context, info)
        org.robolectric.Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(
            android.content.Intent(android.provider.DocumentsContract.PROVIDER_INTERFACE),
            android.content.pm.ResolveInfo().apply { providerInfo = info })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("historical", provider)
        val preferences = context.getSharedPreferences("export_archive_folders", android.content.Context.MODE_PRIVATE)
        try {
            val repository = ExportArchiveRepository(context)
            val root = android.provider.DocumentsContract.buildTreeDocumentUri("historical", "root")
            repository.rememberDirectory(root)
            val listing = repository.list()
            assertEquals("Historical directory should be readable", 0, listing.unreadableDirectories)
            assertTrue(listing.files.toString(), listing.files.any { it.name == zip.name })
            val file = listing.files.single { it.name == zip.name }
            assertEquals(setOf(file.id), repository.delete(setOf(file.id)).deleted)
            assertFalse(zip.exists()); assertEquals("photo", source.readText())
        } finally { preferences.edit().clear().commit(); folder.deleteRecursively() }
    }
    @Test fun listsHistoricalArchivesAndDeletesOnlySelectedZipFiles() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val folder = File(context.cacheDir, "exports").apply { mkdirs() }
        val old = File(folder, "工程档案_123.zip").apply { writeText("old archive") }
        val current = File(folder, "建筑工程_2026-10-31.zip").apply { writeText("new archive") }
        val photo = File(folder, "photo.jpg").apply { writeText("source photograph") }
        val project = File(context.filesDir, "original-project.zip").apply { writeText("outside archive folder") }
        val nested = File(folder, "not-an-archive.zip").apply { mkdirs() }
        try {
            val repository = ExportArchiveRepository(context)
            val files = repository.list().files
            assertEquals(setOf(old.name, current.name), files.map { it.name }.toSet())
            val oldId = files.single { it.name == old.name }.id
            val result = repository.delete(setOf(oldId, Uri.fromFile(photo).toString(), Uri.fromFile(project).toString()))
            assertEquals(setOf(oldId), result.deleted)
            assertEquals(2, result.failed.size)
            assertFalse(old.exists())
            assertTrue(current.exists()); assertTrue(nested.isDirectory)
            assertEquals("source photograph", photo.readText())
            assertEquals("outside archive folder", project.readText())
        } finally { folder.deleteRecursively(); project.delete() }
    }
}
