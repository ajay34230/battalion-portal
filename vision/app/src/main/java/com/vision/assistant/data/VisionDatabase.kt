package com.vision.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessage::class, MemoryItem::class, TaskItem::class, AuditEntry::class, NotificationLog::class],
    version = 2,
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
            Room.databaseBuilder(context.applicationContext, VisionDatabase::class.java, "vision.db").fallbackToDestructiveMigration().build()
    }
}
