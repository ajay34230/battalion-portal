package com.vision.assistant.tasks

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vision.assistant.VisionApp
import com.vision.assistant.ai.Orchestrator
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** A named list of commands. [timeMinutes] (minutes after midnight) + [daysMask] (bit0=Mon..bit6=Sun) schedule it; null time = run only on request. */
data class Routine(val id: Long, val name: String, val steps: List<String>, val timeMinutes: Int?, val daysMask: Int = 0b1111111)

object Routines {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("vision_routines", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Routine> = try {
        val a = JSONArray(sp(ctx).getString("list", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            Routine(o.getLong("id"), o.getString("name"), o.getJSONArray("steps").let { s -> (0 until s.length()).map { s.getString(it) } },
                if (o.isNull("time")) null else o.getInt("time"), o.optInt("days", 0b1111111))
        } }
    } catch (_: Exception) { emptyList() }

    fun save(ctx: Context, list: List<Routine>) {
        val a = JSONArray()
        list.forEach { r -> a.put(JSONObject().put("id", r.id).put("name", r.name).put("steps", JSONArray(r.steps)).put("time", r.timeMinutes ?: JSONObject.NULL).put("days", r.daysMask)) }
        sp(ctx).edit().putString("list", a.toString()).apply()
    }

    fun upsert(ctx: Context, r: Routine): Routine {
        val all = load(ctx)
        val withId = if (r.id == 0L) r.copy(id = (all.maxOfOrNull { it.id } ?: 0L) + 1) else r
        save(ctx, all.filter { it.id != withId.id } + withId); schedule(ctx, withId); return withId
    }

    fun delete(ctx: Context, id: Long) { save(ctx, load(ctx).filter { it.id != id }); WorkManager.getInstance(ctx).cancelUniqueWork("routine-$id") }
    fun find(ctx: Context, name: String) = load(ctx).firstOrNull { it.name.equals(name.trim(), true) } ?: load(ctx).firstOrNull { name.trim().length >= 3 && it.name.contains(name.trim(), true) }

    /** Next millis strictly after [now] that falls on an allowed weekday at [timeMinutes]. */
    fun nextTrigger(timeMinutes: Int, daysMask: Int, now: Calendar = Calendar.getInstance()): Long? {
        if (daysMask and 0b1111111 == 0) return null
        for (add in 0..7) {
            val c = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, add); set(Calendar.HOUR_OF_DAY, timeMinutes / 60); set(Calendar.MINUTE, timeMinutes % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            val bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0 .. Sun=6
            if (c.timeInMillis > now.timeInMillis && daysMask and (1 shl bit) != 0) return c.timeInMillis
        }
        return null
    }

    fun schedule(ctx: Context, r: Routine) {
        val wm = WorkManager.getInstance(ctx)
        val t = r.timeMinutes ?: run { wm.cancelUniqueWork("routine-${r.id}"); return }
        val at = nextTrigger(t, r.daysMask) ?: return
        val req = OneTimeWorkRequestBuilder<RoutineWorker>().setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong("id", r.id).build()).build()
        wm.enqueueUniqueWork("routine-${r.id}", ExistingWorkPolicy.REPLACE, req)
    }

    fun scheduleAll(ctx: Context) = load(ctx).forEach { schedule(ctx, it) }

    /** Runs the steps in order; stops at the first one that needs a confirmation, permission or unlock. Returns a readable log. */
    suspend fun run(app: VisionApp, r: Routine): String {
        val orch = Orchestrator(app)
        val out = ArrayList<String>()
        for ((i, step) in r.steps.withIndex()) {
            if (com.vision.assistant.ai.IntentParser.parse(step) is com.vision.assistant.ai.Intent.RunRoutine) { out.add("${i + 1}. (skipped: a routine cannot start another routine)"); continue }
            val reply = orch.handle(step, recordUser = false)
            out.add("${i + 1}. ${reply.text}")
            if (reply.confirm != null || reply.needsPermission != null || reply.needsUnlock || reply.needsImage != null) { out.add("Stopped: step ${i + 1} needs you to confirm."); break }
        }
        return out.joinToString("\n")
    }
}

class RoutineWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as VisionApp
        val r = Routines.load(app).firstOrNull { it.id == inputData.getLong("id", 0) } ?: return Result.success()
        val text = try { Routines.run(app, r) } catch (t: Throwable) { "Routine failed: ${t.message}" }
        ReminderScheduler.notifyText(applicationContext, 700_000 + r.id.toInt(), "▶ ${r.name}", text)
        Routines.schedule(applicationContext, r) // next day
        return Result.success()
    }
}
