package com.sitecam.app.core.database

import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.sitecam.app.core.database.dao.WatermarkDao
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import com.sitecam.app.core.database.repository.WatermarkTemplateMutations
import com.sitecam.app.core.di.AppContainer
import com.sitecam.app.core.watermark.model.WatermarkStyleCatalog
import com.sitecam.app.feature.watermark.WatermarkEditorViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WatermarkTemplateStateTest {
    @Test fun inFlightFontCannotRevertStyleAndRapidRequestsKeepLastInput(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dao = database.watermarkDao()
            val id = dao.insertTemplate(WatermarkTemplateEntity(name = "当前模板"))
            // Occupy Room's transaction executor so the first font write really is
            // in flight when the other entry submits its style and opacity edits.
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val transaction = launch(Dispatchers.IO) {
                database.withTransaction {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val changes = WatermarkTemplateMutations(dao, writerScope)
            changes.fontSize(id, 1.7f)
            changes.style(id, "INFO_BOARD")
            changes.opacity(id, .42f)
            repeat(100) {
                changes.fontSize(id, 1f + it / 100f)
                changes.style(id, WatermarkStyleCatalog.styles[it % WatermarkStyleCatalog.styles.size].id)
            }
            changes.fontSize(id, 2f)
            changes.style(id, "MINIMAL")
            changes.position(id, "TOP_RIGHT")
            release.complete(Unit)
            transaction.join()
            val saved = withTimeout(15_000) { dao.observeTemplate(id).first { it?.position == "TOP_RIGHT" } }!!
            assertEquals(2f, saved.fontSizeScale)
            assertEquals("MINIMAL", saved.styleType)
            assertEquals(.42f, saved.opacity)
        } finally {
            writerScope.cancel()
            database.close()
        }
    }

    @Test fun failedWriteExposesErrorAndDoesNotStopLaterEdits(): Unit = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dao = database.watermarkDao()
            val id = dao.insertTemplate(WatermarkTemplateEntity(name = "当前模板"))
            val failingDao = object : WatermarkDao by dao {
                override suspend fun updateTemplateFontSize(id: Long, scale: Float) {
                    throw IllegalStateException("Injected write failure")
                }
            }
            val changes = WatermarkTemplateMutations(failingDao, writerScope)
            changes.fontSize(id, 2f)
            withTimeout(10_000) { changes.saveError.first { it != null } }
            assertEquals(1f, dao.getTemplateById(id)!!.fontSizeScale)
            changes.style(id, "MINIMAL")
            withTimeout(10_000) { dao.observeTemplate(id).first { it?.styleType == "MINIMAL" } }
            withTimeout(10_000) { changes.saveError.first { it == null } }
        } finally {
            writerScope.cancel()
            database.close()
        }
    }

    @Test fun everyStylePreservesFieldsSettingsOldRowsAndSurvivesDatabaseReopen(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "watermark-style-state-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        var database = open()
        try {
            val dao = database.watermarkDao()
            val id = dao.insertTemplate(WatermarkTemplateEntity(name = "旧模板", styleType = "INFO_BOARD",
                fontSizeScale = 2f, opacity = .36f, marginDp = 29, position = "TOP_RIGHT", isDefault = true))
            dao.insertTemplate(WatermarkTemplateEntity(name = "旧极简", styleType = "MINIMAL"))
            val legacyId = dao.insertTemplate(WatermarkTemplateEntity(name = "旧现场拍照", styleType = "SITE_PHOTO"))
            val legacy = dao.getTemplateById(legacyId)!!
            assertEquals("MINIMAL", WatermarkStyleCatalog.resolve(legacy.styleType).id)
            dao.insertField(WatermarkFieldEntity(templateId = id, fieldKey = "CUSTOM_test", label = "自定义",
                defaultValue = "保留当前输入和开关", isEnabled = false, displayOrder = 8))
            val original = dao.getTemplateById(id)!!
            val fields = dao.getFieldsForTemplateSync(id)
            for (style in WatermarkStyleCatalog.styles) {
                dao.changeTemplateStyle(id, style.id)
                assertEquals(original.copy(styleType = style.id), dao.getTemplateById(id))
                assertEquals(fields, dao.getFieldsForTemplateSync(id))
                assertEquals(3, dao.getTemplateCount())
                assertEquals(legacy, dao.getTemplateById(legacyId))
            }
            dao.changeTemplateStyle(id, "UNKNOWN")
            assertEquals(WatermarkStyleCatalog.styles.last().id, dao.getTemplateById(id)!!.styleType)
            val final = dao.getTemplateById(id)
            database.close()
            database = open()
            assertEquals(final, database.watermarkDao().getTemplateById(id))
            assertEquals(fields, database.watermarkDao().getFieldsForTemplateSync(id))
            assertEquals(3, database.watermarkDao().getTemplateCount())
            assertEquals(legacy, database.watermarkDao().getTemplateById(legacyId))
            assertEquals("SITE_PHOTO", database.watermarkDao().getTemplateById(legacyId)!!.styleType)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun editorPreviewRetainsEnteredSystemValuesAndCustomContentAcrossAllStyles(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var editor: WatermarkEditorViewModel? = null
        try {
            val dao = database.watermarkDao()
            val id = dao.insertTemplate(WatermarkTemplateEntity(name = "当前模板", isDefault = true))
            val values = mapOf(
                "PROJECT_NAME" to "已填写工程名称",
                "PROJECT_CATEGORY" to "已填写工程类型",
                "DATE_TIME" to "已填写拍摄时间",
                "ADDRESS" to "已填写长地址：第三区配电室设备安装位置",
                "GPS" to "已填写坐标",
                "USER_NAME" to "已填写拍摄人"
            )
            values.entries.forEachIndexed { order, (key, value) ->
                dao.insertField(WatermarkFieldEntity(templateId = id, fieldKey = key, label = key,
                    defaultValue = value, isEnabled = true, displayOrder = order))
            }
            dao.insertField(WatermarkFieldEntity(templateId = id, fieldKey = "CUSTOM_note", label = "备注",
                defaultValue = "已填写自定义内容", isEnabled = true, displayOrder = 6))
            val changes = WatermarkTemplateMutations(dao, writerScope)
            val container = mockk<AppContainer>()
            every { container.database } returns database
            every { container.watermarkTemplateMutations } returns changes
            val model = WatermarkEditorViewModel(container, id).also { editor = it }
            withTimeout(10_000) { model.template.first { it != null } }
            for (style in WatermarkStyleCatalog.styles) {
                model.updateStyleType(style.id)
                val preview = withTimeout(10_000) {
                    model.uiState.first {
                        it.previewData.styleType == style.id && it.previewData.customFields.size == 1
                    }
                }.previewData
                values.forEach { (key, value) -> assertEquals(value, preview.builtInValue(key)) }
                assertEquals(values.keys, preview.enabledSystemFields)
                assertEquals("已填写自定义内容", preview.customFields.single().value)
            }
        } finally {
            editor?.viewModelScope?.cancel()
            writerScope.cancel()
            database.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun editorObservesQuickEntryChangesAndAcceptedWritesSurviveEditorExit(): Unit = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val models = mutableListOf<WatermarkEditorViewModel>()
        try {
            val dao = database.watermarkDao()
            val id = dao.insertTemplate(WatermarkTemplateEntity(name = "当前模板", isDefault = true))
            val changes = WatermarkTemplateMutations(dao, writerScope)
            val container = mockk<AppContainer>()
            every { container.database } returns database
            every { container.watermarkTemplateMutations } returns changes
            val editor = WatermarkEditorViewModel(container, id).also(models::add)
            withTimeout(10_000) { editor.template.first { it != null } }
            // Quick camera entry uses the same application-owned mutation object.
            changes.style(id, "INFO_BOARD")
            withTimeout(10_000) { editor.template.first { it?.styleType == "INFO_BOARD" } }
            editor.updateFontSizeScale(1.8f)
            editor.updateOpacity(.55f)
            editor.updateStyleType("MINIMAL")
            editor.viewModelScope.cancel()
            withTimeout(10_000) { dao.observeTemplate(id).first { it?.styleType == "MINIMAL" } }
            val reopened = WatermarkEditorViewModel(container, id).also(models::add)
            val saved = withTimeout(10_000) { reopened.template.first { it != null } }!!
            assertEquals("MINIMAL", saved.styleType)
            assertEquals(1.8f, saved.fontSizeScale)
            assertEquals(.55f, saved.opacity)
            // Camera's Room observer sees an editor-originated style selection.
            assertEquals(saved, dao.observeTemplate(id).first())
        } finally {
            models.forEach { it.viewModelScope.cancel() }
            writerScope.cancel()
            database.close()
            Dispatchers.resetMain()
        }
    }
}
