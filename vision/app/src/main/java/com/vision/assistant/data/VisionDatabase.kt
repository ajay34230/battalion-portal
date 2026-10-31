package com.vision.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessage::class, MemoryItem::class, TaskItem::class, AuditEntry::class, NotificationLog::class, PaymentRecord::class],
    version = 4,
    exportSchema = false,
)
abstract class VisionDatabase : RoomDatabase() {
    abstract fun chat(): ChatDao
    abstract fun memory(): MemoryDao
    abstract fun tasks(): TaskDao
    abstract fun audit(): AuditDao
    abstract fun notifications(): NotificationDao
    abstract fun payments(): PaymentDao

    companion object {
        fun build(context: Context): VisionDatabase =
            Room.databaseBuilder(context.applicationContext, VisionDatabase::class.java, "vision.db")
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4).fallbackToDestructiveMigration().build()

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `payment_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `direction` TEXT NOT NULL, `paise` INTEGER NOT NULL, `party` TEXT NOT NULL, `bill` INTEGER NOT NULL, `source` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT '', `place` TEXT NOT NULL DEFAULT '', `confirmed` INTEGER NOT NULL DEFAULT 0, `at` INTEGER NOT NULL)")
            }
        }

        /** Keeps existing chat, memory and tasks when smart reminders were added. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE task_item ADD COLUMN kind TEXT NOT NULL DEFAULT 'reminder'")
                db.execSQL("ALTER TABLE task_item ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE task_item ADD COLUMN leadDays TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE task_item ADD COLUMN lang TEXT NOT NULL DEFAULT 'en'")
            }
        }
    }
}
