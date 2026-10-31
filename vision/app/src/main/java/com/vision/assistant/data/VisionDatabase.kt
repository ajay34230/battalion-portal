package com.vision.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessage::class, MemoryItem::class, TaskItem::class, AuditEntry::class, NotificationLog::class],
    version = 3,
    exportSchema = false,
)
abstract class VisionDatabase : RoomDatabase() {
    abstract fun chat(): ChatDao
    abstract fun memory(): MemoryDao
    abstract fun tasks(): TaskDao
    abstract fun audit(): AuditDao
    abstract fun notifications(): NotificationDao

    companion object {
        fun build(context: Context): VisionDatabase =
            Room.databaseBuilder(context.applicationContext, VisionDatabase::class.java, "vision.db")
                .addMigrations(MIGRATION_2_3).fallbackToDestructiveMigration().build()

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
