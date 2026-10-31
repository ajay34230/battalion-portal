package com.vision.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_message")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" | "assistant"
    val text: String,
    val lang: String, // "en" | "hi" | "hinglish"
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "memory_item")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // preference | note | research | document
    val content: String,
    val source: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val importance: Int = 3, // 1 (minor) .. 5 (very important)
    val confidence: Double = 1.0, // lowered when a newer fact contradicts it
    val updatedAt: Long = createdAt,
)

@Entity(tableName = "task_item")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueAt: Long? = null,
    val repeat: String? = null, // null | weekly:<DAY> | monthly:<DD> | yearly:<MM-DD>
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: String = "reminder", // reminder | bill | birthday | anniversary | medicine
    val notes: String = "",
    val leadDays: String = "", // "3,1" = also remind 3 and 1 days before
    val lang: String = "en",
)

@Entity(tableName = "audit_entry")
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tool: String,
    val summary: String,
    val outcome: String, // success | failure | denied | cancelled
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notification_log")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pkg: String,
    val title: String,
    val text: String,
    val sensitive: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

@androidx.room.Entity(tableName = "payment_record")
data class PaymentRecord(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String, val paise: Long, val party: String, val bill: Boolean, val source: String,
    val category: String = "", val place: String = "", val confirmed: Boolean = false,
    val at: Long = System.currentTimeMillis(),
)
