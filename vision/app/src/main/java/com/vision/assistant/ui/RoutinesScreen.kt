package com.vision.assistant.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.tasks.Routine
import com.vision.assistant.tasks.Routines
import kotlinx.coroutines.launch

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
fun RoutinesScreen(app: VisionApp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(Routines.load(ctx)) }
    var adding by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    ScreenFrame("Routines", "Run several commands with one phrase — or on a schedule", action = { Button(onClick = { adding = !adding }) { Text(if (adding) "Close" else "+ New") } }) {
        if (adding) AddRoutine(onSave = { r -> Routines.upsert(ctx, r); list = Routines.load(ctx); adding = false })
        if (result.isNotBlank()) InfoCard { Text(result, fontSize = 13.sp) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty() && !adding) item { EmptyState("▶", "No routines yet", "Example: “Good morning” → what's the time; my battery; my pending tasks. Then say “run good morning routine”.") }
            items(list, key = { it.id }) { r ->
                InfoCard {
                    Text(r.name, fontSize = 16.sp)
                    r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 12.sp, color = VisionColors.Muted) }
                    Text(r.timeMinutes?.let { "Every day it runs at %02d:%02d on %s".format(it / 60, it % 60, dayNames.filterIndexed { i, _ -> r.daysMask and (1 shl i) != 0 }.joinToString(" ")) } ?: "Runs only when you ask", fontSize = 12.sp, color = VisionColors.Cyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !running, onClick = { running = true; scope.launch { result = "${r.name}:\n" + Routines.run(app, r); running = false } }) { Text("Run now") }
                        TextButton(onClick = { Routines.delete(ctx, r.id); list = Routines.load(ctx) }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                    }
                }
            }
        }
        Text("Scheduled routines post their result as a notification (timing can drift a few minutes under battery saving). Steps that need your confirmation stop the routine.", fontSize = 11.sp, color = VisionColors.Muted)
    }
}

@Composable
private fun AddRoutine(onSave: (Routine) -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("what's the time\nmy battery\nmy pending tasks") }
    var time by remember { mutableStateOf<Int?>(null) }
    var days by remember { mutableStateOf(0b1111111) }
    InfoCard {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Routine name (e.g. Good morning)") })
        OutlinedTextField(steps, { steps = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("One command per line") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { TimePickerDialog(ctx, { _, h, m -> time = h * 60 + m }, (time ?: 480) / 60, (time ?: 480) % 60, false).show() }) { Text(time?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "Schedule (optional)") }
            if (time != null) TextButton(onClick = { time = null }) { Text("No schedule") }
        }
        if (time != null) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(dayNames.indices.toList()) { i -> Pill(dayNames[i], selected = days and (1 shl i) != 0) { days = days xor (1 shl i) } }
        }
        Button(enabled = name.isNotBlank() && steps.lines().any { it.isNotBlank() }, onClick = { onSave(Routine(0, name.trim(), steps.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(10), time, days)) }) { Text("Save") }
    }
}
