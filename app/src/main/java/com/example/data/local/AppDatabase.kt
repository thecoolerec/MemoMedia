package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 1,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "local_media.db"
                ).addCallback(object : Callback() {
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
                    targetCategoryId = 4,
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
