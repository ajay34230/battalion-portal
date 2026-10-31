package com.vision.assistant.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert suspend fun insert(m: ChatMessage): Long
    @Query("SELECT * FROM chat_message ORDER BY id ASC") fun all(): Flow<List<ChatMessage>>
    @Query("SELECT * FROM chat_message ORDER BY id DESC LIMIT :n") suspend fun recent(n: Int): List<ChatMessage>
    @Query("DELETE FROM chat_message") suspend fun clear()
}

@Dao
interface MemoryDao {
    @Insert suspend fun insert(m: MemoryItem): Long
    @Update suspend fun update(m: MemoryItem)
    @Delete suspend fun delete(m: MemoryItem)
    @Query("SELECT * FROM memory_item ORDER BY id DESC") fun all(): Flow<List<MemoryItem>>
    @Query("SELECT * FROM memory_item ORDER BY id DESC") suspend fun list(): List<MemoryItem>
    @Query("SELECT * FROM memory_item WHERE content LIKE '%' || :q || '%' ORDER BY id DESC LIMIT :limit")
    suspend fun search(q: String, limit: Int = 20): List<MemoryItem>
    @Query("DELETE FROM memory_item") suspend fun clear()
}

@Dao
interface TaskDao {
    @Insert suspend fun insert(t: TaskItem): Long
    @Update suspend fun update(t: TaskItem)
    @Query("SELECT * FROM task_item ORDER BY done ASC, dueAt IS NULL, dueAt ASC") fun all(): Flow<List<TaskItem>>
    @Query("SELECT * FROM task_item WHERE done = 0") suspend fun pending(): List<TaskItem>
    @Query("SELECT * FROM task_item WHERE id = :id") suspend fun byId(id: Long): TaskItem?
    @Delete suspend fun delete(t: TaskItem)
    @Query("SELECT * FROM task_item WHERE done = 0 AND dueAt IS NOT NULL ORDER BY dueAt ASC LIMIT :n") fun upcoming(n: Int): Flow<List<TaskItem>>
}

@Dao
interface AuditDao {
    @Insert suspend fun insert(a: AuditEntry): Long
    @Query("SELECT * FROM audit_entry ORDER BY id DESC LIMIT 200") fun recent(): Flow<List<AuditEntry>>
}

@Dao
interface NotificationDao {
    @Insert suspend fun insert(n: NotificationLog): Long
    @Query("SELECT * FROM notification_log ORDER BY id DESC LIMIT :n") suspend fun recent(n: Int): List<NotificationLog>
    @Query("DELETE FROM notification_log WHERE id NOT IN (SELECT id FROM notification_log ORDER BY id DESC LIMIT 100)") suspend fun trim()
    @Query("DELETE FROM notification_log") suspend fun clear()
}
