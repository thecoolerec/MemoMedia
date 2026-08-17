package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.CategoryDao
import com.example.data.local.dao.CaptureSessionDao
import com.example.data.local.dao.MediaAssetDao
import com.example.data.local.dao.SourceRuleDao
import com.example.data.local.dao.TagDao
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CaptureSessionEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaTagEntity
import com.example.data.local.entity.SourceRuleEntity
import com.example.data.local.entity.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MediaAssetEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        MediaTagEntity::class,
        CaptureSessionEntity::class,
        SourceRuleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaAssetDao(): MediaAssetDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun captureSessionDao(): CaptureSessionDao
    abstract fun sourceRuleDao(): SourceRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Rebuild media_asset table to normalize indices and eliminate any legacy UNIQUE(media_store_id) constraint
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `media_asset_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `media_store_id` INTEGER NOT NULL,
                        `content_uri` TEXT NOT NULL,
                        `media_type` TEXT NOT NULL,
                        `mime_type` TEXT,
                        `display_name` TEXT,
                        `owner_package` TEXT,
                        `relative_path` TEXT,
                        `bucket_name` TEXT,
                        `width` INTEGER,
                        `height` INTEGER,
                        `size_bytes` INTEGER,
                        `captured_at` INTEGER,
                        `added_at` INTEGER NOT NULL,
                        `primary_category_id` INTEGER,
                        `capture_session_id` INTEGER,
                        `expire_at` INTEGER,
                        `status` TEXT NOT NULL,
                        `indexed_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `media_asset_new` (
                        `id`, `media_store_id`, `content_uri`, `media_type`, `mime_type`,
                        `display_name`, `owner_package`, `relative_path`, `bucket_name`,
                        `width`, `height`, `size_bytes`, `captured_at`, `added_at`,
                        `primary_category_id`, `capture_session_id`, `expire_at`, `status`,
                        `indexed_at`, `created_at`, `updated_at`
                    )
                    SELECT
                        `id`, `media_store_id`, `content_uri`, `media_type`, `mime_type`,
                        `display_name`, `owner_package`, `relative_path`, `bucket_name`,
                        `width`, `height`, `size_bytes`, `captured_at`, `added_at`,
                        `primary_category_id`, `capture_session_id`, `expire_at`, `status`,
                        `indexed_at`, `created_at`, `updated_at`
                    FROM `media_asset`
                """.trimIndent())

                db.execSQL("DROP TABLE `media_asset`")
                db.execSQL("ALTER TABLE `media_asset_new` RENAME TO `media_asset`")

                // Create expected indices on media_asset
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_asset_content_uri` ON `media_asset` (`content_uri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_captured_at` ON `media_asset` (`captured_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_added_at` ON `media_asset` (`added_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_primary_category_id` ON `media_asset` (`primary_category_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_expire_at` ON `media_asset` (`expire_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_status` ON `media_asset` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_asset_owner_package` ON `media_asset` (`owner_package`)")

                // 2. Safely add missing columns to capture_session
                if (!hasColumn(db, "capture_session", "delivery_status")) {
                    db.execSQL("ALTER TABLE `capture_session` ADD COLUMN `delivery_status` TEXT NOT NULL DEFAULT 'NOT_DELIVERED'")
                }
                if (!hasColumn(db, "capture_session", "notification_mode")) {
                    db.execSQL("ALTER TABLE `capture_session` ADD COLUMN `notification_mode` TEXT")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_capture_session_started_at` ON `capture_session` (`started_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_capture_session_status` ON `capture_session` (`status`)")

                // 3. Safely add missing columns to category
                if (!hasColumn(db, "category", "system_key")) {
                    db.execSQL("ALTER TABLE `category` ADD COLUMN `system_key` TEXT")
                }
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_system_key` ON `category` (`system_key`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")

                // Seed system keys for existing categories
                db.execSQL("UPDATE category SET system_key = 'life' WHERE name = '生活' AND system_key IS NULL")
                db.execSQL("UPDATE category SET system_key = 'work' WHERE name = '工作' AND system_key IS NULL")
                db.execSQL("UPDATE category SET system_key = 'temporary' WHERE name = '临时' AND system_key IS NULL")
                db.execSQL("UPDATE category SET system_key = 'screenshots' WHERE name = '截图' AND system_key IS NULL")
            }

            private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
                db.query("PRAGMA table_info(`$table`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    if (nameIndex == -1) return false
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                            return true
                        }
                    }
                }
                return false
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_media.db"
                ).addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial categories and rules
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            seedDefaultData(database)
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaultData(db: AppDatabase) {
            val now = System.currentTimeMillis()
            val categories = listOf(
                CategoryEntity(
                    id = 1,
                    systemKey = "life",
                    name = "生活",
                    icon = "local_florist",
                    sortOrder = 1,
                    retentionDays = null,
                    expireAction = "REVIEW_DELETE",
                    notificationMode = "OVERLAY",
                    indexingEnabled = true,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now
                ),
                CategoryEntity(
                    id = 2,
                    systemKey = "work",
                    name = "工作",
                    icon = "work",
                    sortOrder = 2,
                    retentionDays = null,
                    expireAction = "REVIEW_DELETE",
                    notificationMode = "OVERLAY",
                    indexingEnabled = true,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now
                ),
                CategoryEntity(
                    id = 3,
                    systemKey = "temporary",
                    name = "临时",
                    icon = "hourglass_empty",
                    sortOrder = 3,
                    retentionDays = 120,
                    expireAction = "REVIEW_DELETE",
                    notificationMode = "OVERLAY",
                    indexingEnabled = false,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now
                ),
                CategoryEntity(
                    id = 4,
                    systemKey = "screenshots",
                    name = "截图",
                    icon = "screenshot_monitor",
                    sortOrder = 4,
                    retentionDays = 30,
                    expireAction = "REVIEW_DELETE",
                    notificationMode = "SILENT",
                    indexingEnabled = false,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
            db.categoryDao().insertAll(categories)

            // Resolve actual category id for screenshot rule
            val screenshotCat = db.categoryDao().getBySystemKey("screenshots") ?: db.categoryDao().getByName("截图")
            val screenshotCatId = screenshotCat?.id ?: 4L

            val rules = listOf(
                SourceRuleEntity(
                    id = 1,
                    name = "系统截图自动分类",
                    enabled = true,
                    priority = 100,
                    sourcePackage = null,
                    relativePathPattern = "%Screenshots%",
                    bucketPattern = "%Screenshot%",
                    mediaType = null,
                    targetCategoryId = screenshotCatId,
                    notificationMode = "SILENT",
                    autoClassify = true
                ),
                SourceRuleEntity(
                    id = 2,
                    name = "微信图片快速弹窗",
                    enabled = true,
                    priority = 80,
                    sourcePackage = "com.tencent.mm",
                    relativePathPattern = "%MicroMsg%",
                    bucketPattern = "%WeChat%",
                    mediaType = null,
                    targetCategoryId = null,
                    notificationMode = "OVERLAY",
                    autoClassify = false
                )
            )
            db.sourceRuleDao().insertAll(rules)
        }
    }
}
