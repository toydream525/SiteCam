package com.sitecam.app.core.watermark

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.watermark.model.*
import com.sitecam.app.core.watermark.engine.WatermarkLayoutEngine
import com.sitecam.app.core.watermark.renderer.WatermarkBitmapRenderer
import com.sitecam.app.core.media.VideoWatermarkTranscoder
import com.sitecam.app.feature.watermark.WatermarkCustomizationScreen
import com.sitecam.app.feature.watermark.WatermarkEditorViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class WatermarkFieldEditingPlatformTest {
    @get:Rule val compose = createComposeRule()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun settingsRenameAndReorderPersistAndRenderAcrossStyles() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = AppContainer(instrumentation.targetContext)
        val dao = container.database.watermarkDao()
        val templateId = dao.insertTemplate(WatermarkTemplateEntity(name = "Issue2 regression fixture"))
        val store = ViewModelStore()
        try {
            dao.insertFields(builtInWatermarkFieldsForTemplate(templateId).map {
                if (it.fieldKey == "PROJECT_NAME") it.copy(defaultValue = "原有工程内容") else it
            })
            lateinit var vm: WatermarkEditorViewModel
            instrumentation.runOnMainSync {
                vm = ViewModelProvider(store, WatermarkEditorViewModel.provideFactory(container, templateId))[WatermarkEditorViewModel::class.java]
            }
            compose.setContent { MaterialTheme { WatermarkCustomizationScreen(vm, {}) } }
            compose.waitUntil(10000) { vm.uiState.value.fields.size == 6 }
            compose.onNodeWithText("工程名称").performScrollTo()
            compose.onAllNodesWithContentDescription("编辑")[0].performClick()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
            compose.onNode(hasSetTextAction()).performTextReplacement("今日水印")
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(10000) { vm.uiState.value.fields.first().label == "今日水印" }
            assertEquals("原有工程内容", dao.getFieldsForTemplateSync(templateId).first().defaultValue)

            compose.onNodeWithText("新增字段").performScrollTo().performClick()
            compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
            compose.onNode(hasSetTextAction()).performTextReplacement("首行标签")
            compose.onNodeWithText("添加", useUnmergedTree = true).performClick()
            compose.waitUntil(10000) { vm.uiState.value.fields.size == 7 }
            val added = dao.getFieldsForTemplateSync(templateId).last()
            assertEquals("", added.defaultValue)
            // Contents are entered in the camera editor; supply that saved value here.
            dao.updateField(added.copy(defaultValue = "首行内容"))
            repeat(6) {
                instrumentation.runOnMainSync { vm.moveFieldUp(vm.uiState.value.fields.indexOfFirst { it.id == added.id }) }
                val expected = 5 - it
                compose.waitUntil(10000) { vm.uiState.value.fields.indexOfFirst { f -> f.id == added.id } == expected }
            }
            val saved = dao.getFieldsForTemplateSync(templateId)
            assertEquals(added.id, saved.first().id)
            assertEquals("首行内容", saved.first().defaultValue)
            assertEquals("今日水印", saved[1].label)
            assertEquals("原有工程内容", saved[1].defaultValue)
            // A new resolver reads persisted rows, as it does after restarting the app.
            val resolved = resolveWatermarkFields(saved)
            val data = WatermarkData(projectName = "实时工程", addressText = "测试地点",
                enabledSystemFields = resolved.enabledSystemFields, systemValueOverrides = resolved.systemValueOverrides,
                customFields = resolved.customFields, fieldLabels = resolved.fieldLabels, fieldOrder = resolved.fieldOrder)
            val output = File(container.context.cacheDir, "issue2-review").apply { mkdirs() }
            for (style in WatermarkStyleCatalog.styles) {
                val current = WatermarkSnapshotCodec.decode(WatermarkSnapshotCodec.encode(data.copy(styleType = style.id)))!!
                val layout = WatermarkLayoutEngine.calculateLayout(1080f,1440f,current)
                assertEquals(added.fieldKey, layout.lines.first { it.fieldKey != null }.fieldKey)
                val text = layout.lines.filter { it.fieldKey == "PROJECT_NAME" }.joinToString("") { it.label + it.value }
                assertTrue(style.id, text.contains("今日水印"))
                val photo = WatermarkBitmapRenderer.renderWatermarkOnBitmap(Bitmap.createBitmap(1080,1440,Bitmap.Config.ARGB_8888), current)
                val video = VideoWatermarkTranscoder.renderDisplayOverlay(1080,1440,0,current)
                try {
                    assertTrue("${style.id}: photo/video agreement", photo.sameAs(video))
                    File(output,"${style.id}.png").outputStream().use { photo.compress(Bitmap.CompressFormat.PNG,100,it) }
                } finally { photo.recycle(); video.recycle() }
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            dao.getTemplateById(templateId)?.let { dao.deleteTemplate(it) }
        }
    }
}
