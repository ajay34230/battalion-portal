package com.vision.assistant.backup

import com.vision.assistant.data.MemoryItem
import com.vision.assistant.data.TaskItem
import org.json.JSONArray
import org.json.JSONObject

/** Portable JSON export of what VISION knows about you. No credentials or API keys are included. */
object Backup {
    fun toJson(memory: List<MemoryItem>, tasks: List<TaskItem>, exportedAt: Long = System.currentTimeMillis()): String {
        val o = JSONObject()
        o.put("app", "VISION"); o.put("format", 1); o.put("exportedAt", exportedAt)
        o.put("memory", JSONArray().also { a -> memory.forEach { a.put(JSONObject().put("category", it.category).put("content", it.content).put("source", it.source ?: JSONObject.NULL).put("createdAt", it.createdAt)) } })
        o.put("tasks", JSONArray().also { a -> tasks.forEach { a.put(JSONObject().put("title", it.title).put("kind", it.kind).put("dueAt", it.dueAt ?: JSONObject.NULL).put("repeat", it.repeat ?: JSONObject.NULL).put("leadDays", it.leadDays).put("done", it.done)) } })
        return o.toString(2)
    }

    /** Restores memory and tasks from [toJson] output. Returns (memoryCount, taskCount) or null if the file is not a VISION export. */
    fun parse(json: String): Pair<List<MemoryItem>, List<TaskItem>>? {
        val o = try { JSONObject(json) } catch (_: Exception) { return null }
        if (o.optString("app") != "VISION") return null
        val mem = o.optJSONArray("memory")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { m -> m.optString("content").takeIf { c -> c.isNotBlank() }?.let { c -> MemoryItem(category = m.optString("category", "note"), content = c, source = m.optString("source").takeIf { s -> s.isNotBlank() && s != "null" }) } } } }.orEmpty()
        val tasks = o.optJSONArray("tasks")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { t -> t.optString("title").takeIf { s -> s.isNotBlank() }?.let { title -> TaskItem(title = title, kind = t.optString("kind", "reminder"), dueAt = if (t.isNull("dueAt")) null else t.optLong("dueAt"), repeat = if (t.isNull("repeat")) null else t.optString("repeat"), leadDays = t.optString("leadDays"), done = t.optBoolean("done")) } } } }.orEmpty()
        return mem to tasks
    }
}

/** Writes a parsed export into the database, skipping memory lines that already exist and re-scheduling future reminders. */
suspend fun restoreBackup(app: com.vision.assistant.VisionApp, json: String): String {
    val (mem, tasks) = Backup.parse(json) ?: return "That file is not a VISION export."
    val have = app.database.memory().list().map { it.content }.toSet()
    var m = 0; var t = 0
    for (x in mem) if (x.content !in have) { app.database.memory().insert(x); m++ }
    val now = System.currentTimeMillis()
    for (x in tasks) if (!x.done && (x.dueAt == null || x.dueAt >= now || !x.repeat.isNullOrBlank())) {
        val due = x.dueAt?.let { d -> if (d < now && !x.repeat.isNullOrBlank()) com.vision.assistant.tasks.EventPlanner.nextAfter(x.repeat, now, java.util.Calendar.getInstance().apply { timeInMillis = d }.get(java.util.Calendar.HOUR_OF_DAY), java.util.Calendar.getInstance().apply { timeInMillis = d }.get(java.util.Calendar.MINUTE)) ?: d else d }
        val item = x.copy(dueAt = due)
        val id = app.database.tasks().insert(item); com.vision.assistant.tasks.ReminderScheduler.scheduleTask(app, item.copy(id = id)); t++
    }
    com.vision.assistant.ai.Learning.refresh(app.database.memory())
    return "Restored $m memory item(s) and $t reminder(s)."
}
