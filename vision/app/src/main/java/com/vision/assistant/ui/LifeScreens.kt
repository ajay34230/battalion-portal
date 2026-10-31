package com.vision.assistant.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.data.MemoryItem
import com.vision.assistant.data.TaskItem
import com.vision.assistant.tasks.EventKind
import com.vision.assistant.tasks.EventPlanner
import com.vision.assistant.tasks.ReminderScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** "in 3 days", "tomorrow", "today", "overdue" */
fun relativeDay(due: Long, now: Long = System.currentTimeMillis()): String {
    val a = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val b = Calendar.getInstance().apply { timeInMillis = due; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    val d = TimeUnit.MILLISECONDS.toDays(b.timeInMillis - a.timeInMillis).toInt()
    return when { d < 0 -> "overdue"; d == 0 -> "today"; d == 1 -> "tomorrow"; d < 31 -> "in $d days"; else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(due)) }
}

private fun kindColor(k: EventKind) = when (k) {
    EventKind.BILL -> androidx.compose.ui.graphics.Color(0xFFFF9100)
    EventKind.BIRTHDAY -> androidx.compose.ui.graphics.Color(0xFFFF4081)
    EventKind.ANNIVERSARY -> androidx.compose.ui.graphics.Color(0xFFB388FF)
    EventKind.MEDICINE -> VisionColors.Mint
    EventKind.REMINDER -> VisionColors.Cyan
}

@Composable
fun RemindersScreen(app: VisionApp) {
    val ctx = LocalContext.current
    val tasks by app.database.tasks().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    ScreenFrame("Reminders", "Bills, birthdays, anniversaries and more", action = {
        Button(onClick = { adding = !adding }) { Text(if (adding) "Close" else "+ New") }
    }) {
        if (adding) AddEventCard(app) { adding = false }
        val open = tasks.filter { !it.done }
        val done = tasks.filter { it.done }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (open.isEmpty() && !adding) item { EmptyState("🗓", "Nothing coming up", "Tap + New, or just say “remind me tomorrow at 6 to call mom” or “Rahul's birthday on 14 March”.") }
            items(open, key = { it.id }) { t ->
                val k = EventKind.of(t.kind)
                InfoCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${k.emoji}  ${t.title}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            val whenText = t.dueAt?.let { relativeDay(it) + " · " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(it)) }.orEmpty()
                            Text(whenText, fontSize = 12.sp, color = if (whenText.startsWith("overdue")) androidx.compose.ui.graphics.Color(0xFFFF6B6B) else VisionColors.Muted)
                            val rep = t.repeat?.let { r -> when (r.substringBefore(':')) { "yearly" -> "every year"; "monthly" -> "every month"; "weekly" -> "every week"; else -> null } }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                Pill(k.label, kindColor(k)); if (rep != null) Pill(rep, VisionColors.Muted, selected = false)
                                if (t.leadDays.isNotBlank()) Pill("+${t.leadDays} d before", VisionColors.Muted, selected = false)
                            }
                        }
                        Column {
                            TextButton(onClick = {
                                t.dueAt?.let { d -> try { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                    .putExtra(android.provider.CalendarContract.Events.TITLE, k.emoji + " " + t.title).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, d)
                                    .putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, k != EventKind.REMINDER).also { i -> t.repeat?.let { r -> when (r.substringBefore(':')) { "yearly" -> i.putExtra(android.provider.CalendarContract.Events.RRULE, "FREQ=YEARLY"); "monthly" -> i.putExtra(android.provider.CalendarContract.Events.RRULE, "FREQ=MONTHLY"); "weekly" -> i.putExtra(android.provider.CalendarContract.Events.RRULE, "FREQ=WEEKLY"); else -> {} } } }
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {} }
                            }) { Text("+ Calendar") }
                            TextButton(onClick = { scope.launch { app.database.tasks().update(t.copy(done = true)); ReminderScheduler.cancelTask(ctx, t) } }) { Text("Done") }
                            TextButton(onClick = { scope.launch { ReminderScheduler.cancelTask(ctx, t); app.database.tasks().delete(t) } }) { Text("Delete", color = androidx.compose.ui.graphics.Color(0xFFFF6B6B)) }
                        }
                    }
                }
            }
            if (done.isNotEmpty()) {
                item { SectionLabel("Completed") }
                items(done, key = { it.id }) { t ->
                    InfoCard { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("✓  ${t.title}", Modifier.weight(1f), color = VisionColors.Muted)
                        TextButton(onClick = { scope.launch { app.database.tasks().delete(t) } }) { Text("Remove") }
                    } }
                }
            }
        }
    }
}

@Composable
private fun AddEventCard(app: VisionApp, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(EventKind.BIRTHDAY) }
    var title by remember { mutableStateOf("") }
    val cal = remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0) }) }
    var stamp by remember { mutableStateOf(cal.value.timeInMillis) }
    InfoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (k in listOf(EventKind.BIRTHDAY, EventKind.ANNIVERSARY, EventKind.BILL, EventKind.REMINDER)) Pill(k.emoji + " " + k.label, kindColor(k), selected = kind == k) { kind = k }
        }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(when (kind) { EventKind.BIRTHDAY -> "Whose birthday?"; EventKind.ANNIVERSARY -> "Whose anniversary?"; EventKind.BILL -> "Which bill? (e.g. Electricity)"; else -> "What should I remind you?" }) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                DatePickerDialog(ctx, { _, y, m, d -> cal.value.set(y, m, d); stamp = cal.value.timeInMillis }, cal.value.get(Calendar.YEAR), cal.value.get(Calendar.MONTH), cal.value.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text(SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(stamp))) }
            if (kind == EventKind.REMINDER) OutlinedButton(onClick = {
                TimePickerDialog(ctx, { _, h, mi -> cal.value.set(Calendar.HOUR_OF_DAY, h); cal.value.set(Calendar.MINUTE, mi); stamp = cal.value.timeInMillis }, cal.value.get(Calendar.HOUR_OF_DAY), cal.value.get(Calendar.MINUTE), false).show()
            }) { Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(stamp))) }
        }
        Text(when (kind) {
            EventKind.BIRTHDAY -> "Repeats every year. You'll be told a day before and on the day, with a one-tap WhatsApp wish."
            EventKind.ANNIVERSARY -> "Repeats every year. Notices 7 days and 1 day before, and on the day."
            EventKind.BILL -> "Repeats every month on this date. Notices 3 days and 1 day before, and on the day."
            else -> "One-time reminder."
        }, fontSize = 12.sp, color = VisionColors.Muted)
        Button(enabled = title.isNotBlank(), onClick = {
            scope.launch {
                val c = Calendar.getInstance().apply { timeInMillis = stamp }
                val hour = if (kind == EventKind.REMINDER) c.get(Calendar.HOUR_OF_DAY) else if (kind == EventKind.BILL) 10 else 9
                val minute = if (kind == EventKind.REMINDER) c.get(Calendar.MINUTE) else 0
                val month = c.get(Calendar.MONTH) + 1; val day = c.get(Calendar.DAY_OF_MONTH)
                val (at, repeat) = when (kind) {
                    EventKind.BIRTHDAY, EventKind.ANNIVERSARY -> EventPlanner.nextYearly(month, day, hour, minute) to "yearly:%02d-%02d".format(month, day)
                    EventKind.BILL -> EventPlanner.nextMonthly(day, hour, minute) to "monthly:%02d".format(day)
                    else -> c.apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0) }.timeInMillis to null
                }
                val item = TaskItem(title = title.trim(), dueAt = at, repeat = repeat, kind = kind.id, leadDays = kind.defaultLeads.joinToString(","))
                val id = app.database.tasks().insert(item)
                ReminderScheduler.scheduleTask(ctx, item.copy(id = id))
                title = ""; onSaved()
            }
        }) { Text("Save") }
    }
}

@Composable
fun MemoryScreen(app: VisionApp) {
    val items by app.database.memory().all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("all") }
    var note by remember { mutableStateOf("") }
    var confirmErase by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var editText by remember { mutableStateOf("") }
    var editImp by remember { mutableStateOf(3) }
    val shown = items.filter { filter == "all" || it.category == filter }
    val cats = items.map { it.category }.distinct().sorted()
    ScreenFrame("Memory", "What I've learned about you — stored only on this phone") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(note, { note = it }, Modifier.weight(1f), singleLine = true, label = { Text("Tell me something to remember") })
            Button(enabled = note.isNotBlank(), onClick = { scope.launch { com.vision.assistant.memory.MemoryKeeper.save(app.database.memory(), note.trim()); com.vision.assistant.ai.Learning.refresh(app.database.memory()); note = "" } }, modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Pill("All ${items.size}", selected = filter == "all") { filter = "all" } }
            items(cats) { c -> Pill(c, selected = filter == c) { filter = c } }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (shown.isEmpty()) item { EmptyState("🧠", "Nothing here yet", "Tell me things like “my name is …”, “I love cricket” or “my mother is …” and I'll remember them.") }
            items(shown, key = { it.id }) { m ->
                InfoCard { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.content, fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            Pill(m.category, VisionColors.Cyan, selected = false)
                            if (m.source == "learned") Pill("learned from you", VisionColors.Mint)
                            if (m.importance != 3) Pill("importance ${m.importance}", VisionColors.Mint)
                            if (m.confidence < 1.0) Pill("may be outdated", VisionColors.Cyan, selected = false)
                        }
                    }
                    TextButton(onClick = { editing = m; editText = m.content; editImp = m.importance }) { Text("Edit") }
                    TextButton(onClick = { scope.launch { app.database.memory().delete(m); com.vision.assistant.ai.Learning.refresh(app.database.memory()) } }) { Text("Forget") }
                } }
            }
            item {
                editing?.let { m ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { editing = null },
                        title = { Text("Edit memory") },
                        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(editText, { editText = it }, Modifier.fillMaxWidth(), label = { Text("What I remember") })
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { for (i in 1..5) Pill("$i", selected = editImp == i) { editImp = i } }
                            Text("Importance: higher ranks first when I search my memory. Saving also marks it as current.", fontSize = 11.sp, color = VisionColors.Muted)
                        } },
                        confirmButton = { TextButton(enabled = editText.isNotBlank(), onClick = { scope.launch { app.database.memory().update(m.copy(content = editText.trim(), importance = editImp, confidence = 1.0, updatedAt = System.currentTimeMillis())); com.vision.assistant.ai.Learning.refresh(app.database.memory()); editing = null } }) { Text("Save") } },
                        dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
                    )
                }
                if (items.isNotEmpty()) {
                    if (!confirmErase) TextButton(onClick = { confirmErase = true }) { Text("Erase all memory…", color = androidx.compose.ui.graphics.Color(0xFFFF6B6B)) }
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { app.database.memory().clear(); com.vision.assistant.memory.MemoryEmbedder.clearCache(app); com.vision.assistant.ai.Learning.refresh(app.database.memory()); confirmErase = false } }) { Text("Yes, erase everything") }
                        OutlinedButton(onClick = { confirmErase = false }) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
