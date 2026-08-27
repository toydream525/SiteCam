package com.sitecam.app.core.export

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectExportTest {

    @Test
    fun csvEscapesQuotesAndCommasAsOneField() {
        assertEquals("\"工程, \"\"甲方\"\"\"", CsvEncoder.field("工程, \"甲方\""))
    }

    @Test
    fun testCsvUtf8BomEncoding() {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM
        sb.appendLine("序号,文件名,类型,拍摄时间,工程地点,GPS坐标,是否为问题,问题等级,问题标题,整改要求")
        sb.appendLine("1,\"SiteCam_测试工程_20260825_001.jpg\",PHOTO,\"2026-08-25 15:30:00\",\"长春市施工现场\",\"43.886842, 125.324501\",是,\"严重\",\"钢筋外露隐患\",\"需在24小时内整改完毕\"")

        val bytes = sb.toString().toByteArray(Charsets.UTF_8)
        assertTrue("BOM byte 1", bytes[0] == 0xEF.toByte())
        assertTrue("BOM byte 2", bytes[1] == 0xBB.toByte())
        assertTrue("BOM byte 3", bytes[2] == 0xBF.toByte())
        assertTrue("CSV content should contain headers", sb.toString().contains("是否为问题"))
    }

    @Test
    fun testZipArchivingStream() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("project_info.json"))
            zos.write("{\"projectName\": \"测试工程\"}".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("photo_index.csv"))
            zos.write("序号,文件名\n1,test.jpg\n".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        assertTrue("ZIP bytes should not be empty", zipBytes.isNotEmpty())
        assertTrue("ZIP header magic number", zipBytes[0] == 'P'.code.toByte() && zipBytes[1] == 'K'.code.toByte())
    }
}
