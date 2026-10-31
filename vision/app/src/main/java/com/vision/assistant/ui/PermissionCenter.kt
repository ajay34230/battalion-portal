package com.vision.assistant.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Everything VISION needs for full functionality, in one place. Android only lets an app ask for these; the user always taps Allow. */
object PermissionCenter {
    const val TERMUX = "com.termux.permission.RUN_COMMAND"

    data class Item(val title: String, val why: String, val runtime: String? = null, val special: ((Context) -> Intent)? = null, val check: (Context) -> Boolean, val required: Boolean = true)

    fun termuxInstalled(c: Context) = try { c.packageManager.getPackageInfo("com.termux", 0); true } catch (_: Throwable) { false }
    const val TERMUX_SETUP = "mkdir -p ~/.termux && grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null || echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings"

    fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    fun notificationAccess(c: Context) = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners").orEmpty().contains(c.packageName)

    fun items(): List<Item> = buildList {
        add(Item("Microphone", "Voice commands and “Hey Vision”.", Manifest.permission.RECORD_AUDIO, null, { granted(it, Manifest.permission.RECORD_AUDIO) }))
        if (Build.VERSION.SDK_INT >= 33) add(Item("Notifications", "Reminders, birthday/bill alerts and the daily payment check.", Manifest.permission.POST_NOTIFICATIONS, null, { granted(it, Manifest.permission.POST_NOTIFICATIONS) }))
        add(Item("Contacts", "Calling people by name.", Manifest.permission.READ_CONTACTS, null, { granted(it, Manifest.permission.READ_CONTACTS) }))
        add(Item("Calendar", "Reading your schedule aloud.", Manifest.permission.READ_CALENDAR, null, { granted(it, Manifest.permission.READ_CALENDAR) }))
        add(Item("Notification access", "Reading WhatsApp/other notifications and recording payments (PhonePe, Paytm, banks). Opens Android settings: switch VISION on.", null, { Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }, { notificationAccess(it) }))
        add(Item("Run in background (battery)", "Keeps reminders, “Hey Vision” and the payment check from being killed. Opens Android settings: choose VISION → Don't optimise.", null, { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }, { (it.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(it.packageName) }, required = false))
        add(Item("Termux control", "Lets VISION run phone tools and the Coding Studio through Termux. Only needed if you use Termux; also enable “allow-external-apps” in Termux.", TERMUX, null, { granted(it, TERMUX) }, required = false))
    }

    private fun sp(c: Context) = c.getSharedPreferences("vision_prefs", Context.MODE_PRIVATE)
    fun missingRequired(c: Context) = items().filter { it.required && !it.check(c) }
    /** True at startup when something required is missing and the user has not chosen “don't ask again”. */
    fun needsAsk(c: Context) = !sp(c).getBoolean("perm_never", false) && missingRequired(c).isNotEmpty()
    fun neverAsk(c: Context) = sp(c).edit().putBoolean("perm_never", true).apply()
}

@Composable
fun PermissionCenterScreen(startup: Boolean, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val multi = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val one = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val items = PermissionCenter.items()
    val missingRuntime = items.filter { it.runtime != null && it.runtime != PermissionCenter.TERMUX && !it.check(ctx) }
    @Suppress("UNUSED_EXPRESSION") tick
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ScreenTitle(if (startup) "Let's set VISION up" else "Permissions")
        Text("For full functionality VISION needs the permissions below. Android always shows its own Allow button; VISION cannot grant itself anything, and you can take any of it back later in app settings. Nothing is used without these.", fontSize = 12.sp, color = VisionColors.Muted)
        if (missingRuntime.isNotEmpty()) Button(modifier = Modifier.fillMaxWidth(), onClick = { multi.launch(missingRuntime.mapNotNull { it.runtime }.toTypedArray()) }) { Text("Allow all basic permissions (${missingRuntime.size})") }
        for (it in items) {
            val ok = it.check(ctx)
            InfoCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.title + if (!it.required) "  (optional)" else "", fontSize = 15.sp)
                    Text(if (ok) "✓ allowed" else "not allowed", color = if (ok) VisionColors.Mint else VisionColors.Muted, fontSize = 12.sp)
                }
                Text(it.why, fontSize = 12.sp, color = VisionColors.Muted)
                if (!ok && it.runtime == PermissionCenter.TERMUX) {
                    val installed = PermissionCenter.termuxInstalled(ctx)
                    Text(if (installed) "Termux is installed." else "Termux is NOT installed. Install it from F-Droid or its GitHub releases (the Play Store version is too old and has no control feature), then come back.", fontSize = 12.sp, color = if (installed) VisionColors.Mint else Color(0xFFFFB74D))
                    Text("Android does not show a pop-up for this one. Grant it by hand: Open app settings → Permissions → Additional permissions → “Run commands in Termux environment” → Allow. Then in Termux run the setup command below once, and fully close/reopen Termux.", fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open app settings") }
                        OutlinedButton(onClick = { one.launch(it.runtime!!) }) { Text("Try pop-up") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("termux setup", PermissionCenter.TERMUX_SETUP))
                        }) { Text("Copy Termux setup command") }
                        if (installed) OutlinedButton(onClick = { ctx.packageManager.getLaunchIntentForPackage("com.termux")?.let { i -> ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Open Termux") }
                    }
                } else if (!ok) OutlinedButton(onClick = {
                    val sp = it.special
                    if (sp != null) ctx.startActivity(sp(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) else one.launch(it.runtime!!)
                }) { Text(if (it.special != null) "Open settings" else "Allow") }
            }
        }
        OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open app settings (to take permissions back)") }
        if (startup) {
            val all = PermissionCenter.missingRequired(ctx).isEmpty()
            Button(modifier = Modifier.fillMaxWidth(), onClick = onDone) { Text(if (all) "All set — continue" else "Continue for now") }
            if (!all) TextButton(onClick = { PermissionCenter.neverAsk(ctx); onDone() }) { Text("Don't ask me at startup again") }
        }
    }
}
