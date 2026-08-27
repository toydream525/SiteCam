package com.sitecam.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sitecam.app.core.database.dao.IssueDao
import com.sitecam.app.core.database.dao.MediaItemDao
import com.sitecam.app.core.database.dao.ProjectCategoryDao
import com.sitecam.app.core.database.dao.ProjectDao
import com.sitecam.app.core.database.dao.WatermarkDao
import com.sitecam.app.core.database.entity.AnnotationEntity
import com.sitecam.app.core.database.entity.IssueEntity
import com.sitecam.app.core.database.entity.MediaItemEntity
import com.sitecam.app.core.database.entity.ProjectCategoryEntity
import com.sitecam.app.core.database.entity.ProjectEntity
import com.sitecam.app.core.database.entity.WatermarkFieldEntity
import com.sitecam.app.core.database.entity.WatermarkTemplateEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

@Database(
    entities = [
        ProjectEntity::class,
        ProjectCategoryEntity::class,
        MediaItemEntity::class,
        IssueEntity::class,
        AnnotationEntity::class,
        WatermarkTemplateEntity::class,
        WatermarkFieldEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun projectCategoryDao(): ProjectCategoryDao
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun issueDao(): IssueDao
    abstract fun watermarkDao(): WatermarkDao

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Repair old duplicate rows before enforcing one issue and
                // one annotation product per media item.
                db.execSQL("DELETE FROM annotations WHERE id NOT IN (SELECT MAX(id) FROM annotations GROUP BY mediaId)")
                db.execSQL("DELETE FROM issues WHERE id NOT IN (SELECT MAX(id) FROM issues GROUP BY mediaId)")
                db.execSQL("DROP INDEX IF EXISTS index_annotations_mediaId")
                db.execSQL("DROP INDEX IF EXISTS index_issues_mediaId")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_annotations_mediaId ON annotations(mediaId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_issues_mediaId ON issues(mediaId)")
                db.execSQL("ALTER TABLE media_items ADD COLUMN locationTimestamp INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN locationStatus TEXT NOT NULL DEFAULT 'UNAVAILABLE'")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE media_items ADD COLUMN watermarkSnapshotJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sitecam_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Seed defaults exactly once per database and make the operation safe
         * when several ViewModels are created during first launch. All reads
         * and inserts run in one Room transaction, so no collector can observe
         * a half-seeded template/project set.
         */
        suspend fun ensureDefaultData(db: AppDatabase) = db.withTransaction {
            // 1. Prepopulate default categories
            val defaultCategories = listOf(
                "建筑", "道路", "桥梁", "电力", "通信",
                "给排水", "暖通", "园林", "装修", "市政", "其他"
            ).mapIndexed { index, name ->
                ProjectCategoryEntity(
                    name = name,
                    isSystemDefault = true,
                    displayOrder = index
                )
            }
            val existingCategoryNames = db.projectCategoryDao().getAllCategories().first().map { it.name }.toSet()
            db.projectCategoryDao().insertCategories(defaultCategories.filterNot { it.name in existingCategoryNames })

            // 2. Prepopulate default project
            if (db.projectDao().getProjectCount() == 0) {
                db.projectDao().insertProject(
                    ProjectEntity(
                        name = "示例工程项目",
                        categoryName = "建筑",
                        address = "现场施工区",
                        description = "默认工程档案"
                    )
                )
            }

            // 3. Prepopulate default watermark templates
            val templates = db.watermarkDao().getAllTemplates().first()
            val classicTemplate = templates.firstOrNull { it.isDefault }
                ?: templates.firstOrNull { it.name == "经典工程水印" }
                ?: run {
                    val id = db.watermarkDao().insertTemplate(
                        WatermarkTemplateEntity(
                            name = "经典工程水印",
                            styleType = "CLASSIC",
                            fontSizeScale = 1.0f,
                            opacity = 0.85f,
                            marginDp = 16,
                            position = "BOTTOM_LEFT",
                            isDefault = true
                        )
                    )
                    db.watermarkDao().getTemplateById(id)
                }
            val classicTemplateId = classicTemplate?.id ?: 1L
            if (templates.none { it.name == "极简水印" }) {
                db.watermarkDao().insertTemplate(
                    WatermarkTemplateEntity(
                        name = "极简水印",
                        styleType = "MINIMAL",
                        fontSizeScale = 0.9f,
                        opacity = 0.8f,
                        marginDp = 16,
                        position = "BOTTOM_LEFT",
                        isDefault = false
                    )
                )
            }
            if (templates.none { it.name == "工程信息板" }) {
                db.watermarkDao().insertTemplate(
                    WatermarkTemplateEntity(
                        name = "工程信息板",
                        styleType = "INFO_BOARD",
                        fontSizeScale = 1.1f,
                        opacity = 0.9f,
                        marginDp = 16,
                        position = "BOTTOM_LEFT",
                        isDefault = false
                    )
                )
            }

            // 4. Prepopulate default fields for classic template
            val defaultFields = listOf(
                WatermarkFieldEntity(
                    templateId = classicTemplateId,
                    fieldKey = "PROJECT_NAME",
                    label = "工程名称",
                    displayOrder = 0,
                    isEnabled = true
                ),
                WatermarkFieldEntity(
                    templateId = classicTemplateId,
                    fieldKey = "PROJECT_CATEGORY",
                    label = "工程类型",
                    displayOrder = 1,
                    isEnabled = true
                ),
                WatermarkFieldEntity(
                    templateId = classicTemplateId,
                    fieldKey = "DATE_TIME",
                    label = "拍摄时间",
                    displayOrder = 2,
                    isEnabled = true
                ),
                WatermarkFieldEntity(
                    templateId = classicTemplateId,
                    fieldKey = "ADDRESS",
                    label = "拍摄地点",
                    displayOrder = 3,
                    isEnabled = true
                ),
                WatermarkFieldEntity(
                    templateId = classicTemplateId,
                    fieldKey = "GPS",
                    label = "经纬度",
                    displayOrder = 4,
                    isEnabled = true
                ),
                WatermarkFieldEntity(
                    templateId = classicTemplateId,
                    fieldKey = "USER_NAME",
                    label = "拍摄人",
                    defaultValue = "施工员",
                    displayOrder = 5,
                    isEnabled = false
                )
            )
            db.watermarkDao().ensureBuiltInFields(classicTemplateId, defaultFields)
        }
    }
}
