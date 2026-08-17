package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.core.enum.ExpireAction
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MediaAssetEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun testEarlyV1ToV2Migration() {
        runBlocking {
            val dbName = "test_early_v1_to_v2.db"
        context.deleteDatabase(dbName)

        // 1. Create Early V1 schema directly via SQLite
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `media_asset` (
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
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_asset_media_store_id` ON `media_asset` (`media_store_id`)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `category` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `icon` TEXT,
                            `sort_order` INTEGER NOT NULL,
                            `retention_days` INTEGER,
                            `expire_action` TEXT NOT NULL,
                            `notification_mode` TEXT,
                            `indexing_enabled` INTEGER NOT NULL,
                            `is_system` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `tag` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `color` TEXT,
                            `created_at` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `capture_session` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `source_package` TEXT,
                            `media_type` TEXT NOT NULL,
                            `started_at` INTEGER NOT NULL,
                            `ended_at` INTEGER NOT NULL,
                            `media_count` INTEGER NOT NULL,
                            `status` TEXT NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `source_rule` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `priority` INTEGER NOT NULL,
                            `enabled` INTEGER NOT NULL,
                            `owner_package_pattern` TEXT,
                            `relative_path_pattern` TEXT,
                            `bucket_pattern` TEXT,
                            `media_type` TEXT,
                            `target_category_id` INTEGER,
                            `notification_mode` TEXT,
                            `auto_classify` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // Insert sample early v1 data
                    db.execSQL("INSERT INTO category (id, name, sort_order, expire_action, indexing_enabled, is_system, created_at, updated_at) VALUES (1, '生活', 1, 'KEEP_FOREVER', 1, 1, 100, 100)")
                    db.execSQL("INSERT INTO category (id, name, sort_order, expire_action, indexing_enabled, is_system, created_at, updated_at) VALUES (2, '临时', 2, 'AUTO_TRASH', 1, 1, 100, 100)")
                    db.execSQL("INSERT INTO capture_session (id, source_package, media_type, started_at, ended_at, media_count, status) VALUES (10, 'com.camera', 'IMAGE', 1000, 2000, 1, 'READY')")
                    db.execSQL("INSERT INTO media_asset (id, media_store_id, content_uri, media_type, added_at, status, created_at, updated_at) VALUES (100, 555, 'content://media/external/images/media/555', 'IMAGE', 1000, 'PENDING', 1000, 1000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }).build()

        helperFactory.create(config).writableDatabase.close()

        // 2. Open via Room with MIGRATION_1_2
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

        // 3. Verify Room DB opened and data migrated correctly
        val categories = roomDb.categoryDao().getAll()
        assertEquals(2, categories.size)
        assertEquals("life", categories.first { it.id == 1L }.systemKey)
        assertEquals("temporary", categories.first { it.id == 2L }.systemKey)

        val session = roomDb.captureSessionDao().getById(10L)
        assertNotNull(session)
        assertEquals("NOT_DELIVERED", session?.deliveryStatus)

        val asset = roomDb.mediaAssetDao().getById(100L)
        assertNotNull(asset)
        assertEquals(555L, asset?.mediaStoreId)

        // Verify that same mediaStoreId for different contentUri (e.g. video vs image) CAN now coexist
        val videoAssetWithSameId = MediaAssetEntity(
            id = 0,
            mediaStoreId = 555L,
            contentUri = "content://media/external/video/media/555",
            mediaType = MediaType.VIDEO.name,
            addedAt = 2000L,
            status = MediaStatus.UNCLASSIFIED.name,
            createdAt = 2000L,
            updatedAt = 2000L
        )
        val newId = roomDb.mediaAssetDao().insert(videoAssetWithSameId)
        assertTrue(newId > 0)

        roomDb.close()
        context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testLateV1VariantToV2Migration() {
        runBlocking {
            val dbName = "test_late_v1_to_v2.db"
            context.deleteDatabase(dbName)

            val helperFactory = FrameworkSQLiteOpenHelperFactory()
            val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `media_asset` (
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
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_asset_content_uri` ON `media_asset` (`content_uri`)")

                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `category` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `icon` TEXT,
                                `sort_order` INTEGER NOT NULL,
                                `retention_days` INTEGER,
                                `expire_action` TEXT NOT NULL,
                                `notification_mode` TEXT,
                                `indexing_enabled` INTEGER NOT NULL,
                                `is_system` INTEGER NOT NULL,
                                `created_at` INTEGER NOT NULL,
                                `updated_at` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)")

                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `tag` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `color` TEXT,
                                `created_at` INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tag_name` ON `tag` (`name`)")

                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `capture_session` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `source_package` TEXT,
                                `media_type` TEXT NOT NULL,
                                `started_at` INTEGER NOT NULL,
                                `ended_at` INTEGER NOT NULL,
                                `media_count` INTEGER NOT NULL,
                                `status` TEXT NOT NULL,
                                `delivery_status` TEXT NOT NULL DEFAULT 'NOT_DELIVERED'
                            )
                        """.trimIndent())

                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `source_rule` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `name` TEXT NOT NULL,
                                `priority` INTEGER NOT NULL,
                                `enabled` INTEGER NOT NULL,
                                `owner_package_pattern` TEXT,
                                `relative_path_pattern` TEXT,
                                `bucket_pattern` TEXT,
                                `media_type` TEXT,
                                `target_category_id` INTEGER,
                                `notification_mode` TEXT,
                                `auto_classify` INTEGER NOT NULL,
                                `created_at` INTEGER NOT NULL,
                                `updated_at` INTEGER NOT NULL
                            )
                        """.trimIndent())

                        db.execSQL("INSERT INTO category (id, name, sort_order, expire_action, indexing_enabled, is_system, created_at, updated_at) VALUES (1, '工作', 1, 'KEEP_FOREVER', 1, 1, 100, 100)")
                        db.execSQL("INSERT INTO capture_session (id, source_package, media_type, started_at, ended_at, media_count, status, delivery_status) VALUES (20, 'com.app', 'IMAGE', 1000, 2000, 1, 'READY', 'DELIVERED_NOTIFICATION')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()

            helperFactory.create(config).writableDatabase.close()

            val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .build()

            val session = roomDb.captureSessionDao().getById(20L)
            assertNotNull(session)
            assertEquals("DELIVERED_NOTIFICATION", session?.deliveryStatus)

            val category = roomDb.categoryDao().getById(1L)
            assertEquals("work", category?.systemKey)

            roomDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testFreshV2DatabaseCreation() {
        runBlocking {
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            AppDatabase.seedDefaultData(db)

            val categories = db.categoryDao().getAll()
            assertTrue(categories.isNotEmpty())
            assertTrue(categories.any { it.systemKey == "life" })
            assertTrue(categories.any { it.systemKey == "work" })
            assertTrue(categories.any { it.systemKey == "temporary" })
            assertTrue(categories.any { it.systemKey == "screenshots" })

            db.close()
        }
    }
}
