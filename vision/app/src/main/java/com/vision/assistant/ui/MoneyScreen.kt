package com.vision.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vision.assistant.VisionApp
import com.vision.assistant.data.PaymentRecord
import com.vision.assistant.finance.Direction
import com.vision.assistant.finance.SpendSummary
import kotlinx.coroutines.launch

@Composable
fun MoneyScreen(app: VisionApp) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf(emptyList<PaymentRecord>()) }
    fun load() = scope.launch { rows = app.database.payments().recent(300) }
    LaunchedEffect(Unit) { load() }
    val monthStart = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0) }.timeInMillis
    val t = SpendSummary.totals(rows.filter { it.at >= monthStart }.map { SpendSummary.Row(Direction.valueOf(it.direction), it.paise, it.party, it.bill, it.category) })
    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            InfoCard {
                Text("This month", fontSize = 13.sp, color = VisionColors.Muted)
                Text("Spent ${SpendSummary.rupees(t.paid)}", fontSize = 22.sp, color = Color(0xFFFF8A80))
                Text("Received ${SpendSummary.rupees(t.received)}  ·  Bills ${SpendSummary.rupees(t.bills)}", fontSize = 14.sp)
                t.top.forEach { Text("${it.first}: ${SpendSummary.rupees(it.second)}", fontSize = 12.sp, color = VisionColors.Muted) }
            }
        }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var on by remember { mutableStateOf(com.vision.assistant.finance.PaymentReview.enabled(ctx)) }
            var min by remember { mutableStateOf(com.vision.assistant.finance.PaymentReview.minutes(ctx)) }
            InfoCard {
                Text("Daily check: VISION asks you to confirm what each payment was for and where, so the record stays accurate.", fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Switch(on, { on = it; com.vision.assistant.finance.PaymentReview.set(ctx, it, min) })
                    OutlinedButton(onClick = { android.app.TimePickerDialog(ctx, { _, h, m -> min = h * 60 + m; com.vision.assistant.finance.PaymentReview.set(ctx, on, min) }, min / 60, min % 60, false).show() }) { Text("Ask at %d:%02d %s".format(if (min / 60 % 12 == 0) 12 else min / 60 % 12, min % 60, if (min / 60 < 12) "AM" else "PM")) }
                }
            }
        }
        item { Text("Built from payment notifications (PhonePe, Paytm, Google Pay, bank alerts) that arrive while Notification access is on. Payments made before that, or notifications you swiped away with the access off, are not here. Notification text can be faked by other apps, so treat totals as a helper, not an accounting record. Account and card numbers are never stored. Ask VISION: \"how much did I spend this month\".", fontSize = 11.sp, color = VisionColors.Muted) }
        if (rows.isEmpty()) item { EmptyState("₹", "No payments yet", "Turn on Notification access in More → Permissions.") }
        items(rows, key = { it.id }) { r ->
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(r.party + if (r.place.isNotBlank()) " · ${r.place}" else "", fontSize = 15.sp)
                        Text(java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(r.at)) + if (r.bill) " · bill" else "", fontSize = 11.sp, color = VisionColors.Muted)
                    }
                    Text((if (r.direction == "PAID") "− " else "+ ") + SpendSummary.rupees(r.paise), color = if (r.direction == "PAID") Color(0xFFFF8A80) else VisionColors.Mint)
                }
                if (r.direction == "PAID") {
                    var cat by remember(r.id) { mutableStateOf(r.category) }
                    var place by remember(r.id) { mutableStateOf(r.place) }
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(com.vision.assistant.finance.PaymentParser.CATEGORIES.size) { i -> val c = com.vision.assistant.finance.PaymentParser.CATEGORIES[i]; Pill(c, selected = cat == c) { cat = c } } }
                    androidx.compose.material3.OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Where (shop / area / city)") })
                    Row {
                        OutlinedButton(enabled = cat.isNotBlank(), onClick = { scope.launch { app.database.payments().confirm(r.id, cat, place.trim()); load() } }) { Text(if (r.confirmed) "Update" else "Confirm") }
                        if (r.confirmed) Text("  ✓ confirmed", color = VisionColors.Mint, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                TextButton(onClick = { scope.launch { app.database.payments().delete(r.id); load() } }) { Text("Remove") }
            }
        }
        item { if (rows.isNotEmpty()) OutlinedButton(onClick = { scope.launch { app.database.payments().clear(); load() } }) { Text("Delete all payment records") } }
    }
}
