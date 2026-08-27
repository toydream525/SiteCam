package com.sitecam.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NamingEngineTest {

    @Test
    fun testGenerateFileNameDefault() {
        val timestamp = 1787654321000L
        val fileName = NamingEngine.generateFileName(
            projectName = "长春配电改造",
            categoryName = "电力",
            timestamp = timestamp,
            pattern = "{project}_{date}_{time}"
        )

        assertFalse("Filename should not contain invalid characters", fileName.contains("/") || fileName.contains("\\"))
        assert(fileName.startsWith("长春配电改造_"))
        assert(fileName.endsWith(".jpg"))
    }

    @Test
    fun testGenerateFileNameWithIndex() {
        val timestamp = 1787654321000L
        val fileName = NamingEngine.generateFileName(
            projectName = "桥梁桩基施工",
            timestamp = timestamp,
            pattern = "{project}_{date}_{time}",
            index = 1
        )

        assert(fileName.contains("_001.jpg"))
    }

    @Test
    fun testSanitizeFileName() {
        val raw = "工程/项目:标段*1?#<2>|3"
        val sanitized = NamingEngine.sanitizeFileName(raw)
        assertEquals("工程_项目_标段_1_#_2__3", sanitized)
    }
}
