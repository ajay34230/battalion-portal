package com.vision.assistant.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import com.vision.assistant.code.CodeStudio
import com.vision.assistant.code.VisualCheck
import com.vision.assistant.termux.CommandPolicy
import com.vision.assistant.termux.TermuxGateway
import com.vision.assistant.termux.TermuxPrefs
import com.vision.assistant.termux.Validation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Per-project knowledge: decisions, conventions, known errors. Stored as NOTES.md in the project (so it is exported and editable), and given to the model when it builds or fixes code. */
@Composable
fun NotesTab(studio: CodeStudio, project: String, modifier: Modifier) {
    var text by remember { mutableStateOf(studio.store.read(project, "NOTES.md") ?: "") }
    var saved by remember { mutableStateOf(true) }
    var add by remember { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What VISION should remember about this project: language, structure, decisions, known problems. The model reads this before building or fixing.", fontSize = 12.sp, color = VisionColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(add, { add = it }, Modifier.weight(1f), singleLine = true, label = { Text("Add a decision / known issue") })
            Button(enabled = add.isNotBlank(), onClick = { text = (text.trimEnd() + "\n- ${java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date())}: ${add.trim()}\n").trimStart(); add = ""; saved = false }, modifier = Modifier.padding(top = 8.dp)) { Text("Add") }
        }
        OutlinedTextField(text, { text = it; saved = false }, Modifier.weight(1f).fillMaxWidth(), label = { Text("NOTES.md") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !saved, onClick = { studio.store.write(project, "NOTES.md", text).onSuccess { saved = true; studio.log("Notes saved for '$project'") } }) { Text("Save notes") }
            if (saved) Text("Saved", color = VisionColors.Mint, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** Restricted terminal: only commands the Termux allow-list accepts (Advanced mode is NOT used here). Every run is logged. */
@Composable
fun TerminalTab(studio: CodeStudio, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var line by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Validation.Ok?>(null) }
    var busy by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<String>() }
    fun run(v: Validation.Ok) { busy = true; scope.launch { studio.log("Terminal: ${v.binary} ${v.args.joinToString(" ")}"); val r = TermuxGateway(ctx).execute(v).fold({ it }, { "Could not run: ${it.message}" }); history.add(0, "$ ${v.binary} ${v.args.joinToString(" ")}\n$r"); busy = false } }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Allow-listed commands only (e.g. ls, cat, python3 script.py, node file.js, git status, pwd, date). Anything else is refused with a reason. 30 second limit. Needs Termux set up.", fontSize = 12.sp, color = VisionColors.Muted)
        OutlinedTextField(line, { line = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Command") }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace))
        Button(enabled = line.isNotBlank() && !busy, onClick = {
            when (val v = CommandPolicy.validateLine(line, advanced = false)) {
                is Validation.Rejected -> history.add(0, "$ $line\nRefused: ${v.reason}")
                is Validation.Ok -> if (v.sensitive || v.high) pending = v else run(v)
            }
            line = ""
        }) { Text(if (busy) "Running…" else "Run") }
        for (h in history.take(12)) InfoCard { Text(h, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
    }
    pending?.let { v ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { pending = null }, title = { Text("Run this command?") },
            text = { Text("${v.binary} ${v.args.joinToString(" ")}\n\nThis command is marked sensitive.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { val x = v; pending = null; run(x) }) { Text("Run") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
}

private data class Shot(val width: Int, val bitmap: Bitmap?, val findings: List<VisualCheck.Finding>, val ink: Double)

/** Opens the project's page at phone, large-phone and tablet widths, takes real screenshots and measures layout problems (overflow, tiny text, small tap targets, missing alt/viewport). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VisualTestPanel(studio: CodeStudio, project: String, files: List<String>) {
    val ctx = LocalContext.current
    val widths = listOf(360, 412, 768)
    val pages = files.filter { it.endsWith(".html") }
    var page by remember { mutableStateOf(pages.firstOrNull { it == "index.html" } ?: pages.firstOrNull().orEmpty()) }
    var runIndex by remember { mutableStateOf(-1) }
    val shots = remember { mutableStateListOf<Shot>() }
    var loaded by remember { mutableStateOf(0) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pages.isEmpty()) { EmptyState("📸", "No HTML page", "Add an index.html first."); return@Column }
        Text("Screenshots and layout checks of the real rendered page at ${widths.joinToString(" / ")} px wide. Checks are automatic and limited; they do not judge whether it looks beautiful.", fontSize = 12.sp, color = VisionColors.Muted)
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(pages.size) { i -> Pill(pages[i], selected = page == pages[i]) { page = pages[i] } } }
        Button(enabled = runIndex < 0, onClick = { shots.clear(); runIndex = 0 }) { Text(if (runIndex >= 0) "Testing ${widths[runIndex.coerceAtMost(widths.lastIndex)]}px…" else "Run visual test") }
        if (runIndex in widths.indices) {
            val w = widths[runIndex]
            Box(Modifier.width(w.dp).height(560.dp)) {
                AndroidView(factory = { c ->
                    WebView(c).apply {
                        settings.javaScriptEnabled = true; settings.allowFileAccess = true; settings.blockNetworkLoads = true; settings.allowContentAccess = false
                        // The page under test is project code, possibly written by an AI: it may not read other files or open windows.
                        @Suppress("DEPRECATION") run { settings.allowFileAccessFromFileURLs = false; settings.allowUniversalAccessFromFileURLs = false }
                        settings.setGeolocationEnabled(false); settings.javaScriptCanOpenWindowsAutomatically = false; settings.setSupportMultipleWindows(false)
                        webViewClient = object : WebViewClient() { override fun onPageFinished(v: WebView?, url: String?) { loaded++ } }
                        webRef = this
                    }
                }, update = { v -> if (v.tag != "$page@$w") { v.tag = "$page@$w"; v.loadUrl("file://" + java.io.File(studio.store.dir(project), page).absolutePath) } }, modifier = Modifier.width(w.dp).height(560.dp))
            }
            val thisLoad = loaded
            LaunchedEffect(runIndex, thisLoad) {
                if (thisLoad == 0) return@LaunchedEffect
                delay(600)
                val v = webRef ?: return@LaunchedEffect
                var json = ""
                v.evaluateJavascript(VisualCheck.SCRIPT) { json = it.orEmpty().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\") }
                delay(400)
                val bmp = try { v.drawToBitmap() } catch (_: Throwable) { null }
                val ink = bmp?.let { b -> val px = IntArray(b.width * minOf(b.height, 300)); b.getPixels(px, 0, b.width, 0, 0, b.width, minOf(b.height, 300)); VisualCheck.inkRatio(px) } ?: 0.0
                val findings = VisualCheck.interpret(json, w).toMutableList()
                if (bmp != null && ink < 0.005) findings.add(VisualCheck.Finding("error", "The screenshot is a single flat colour at ${w}px (nothing visible rendered)."))
                shots.add(Shot(w, bmp, findings, ink))
                studio.log("Visual test '$project/$page' @${w}px: ${findings.size} finding(s)")
                loaded = 0; runIndex = if (runIndex + 1 < widths.size) runIndex + 1 else -1
            }
        }
        for (s in shots) InfoCard {
            Text("${s.width}px wide", fontSize = 15.sp)
            if (s.findings.isEmpty()) Text("✓ No layout problems found by the automatic checks.", color = VisionColors.Mint, fontSize = 12.sp)
            for (f in s.findings) Text((if (f.severity == "error") "✗ " else "! ") + f.text, color = if (f.severity == "error") Color(0xFFFF6B6B) else Color(0xFFFFB74D), fontSize = 12.sp)
            s.bitmap?.let { b -> Image(b.asImageBitmap(), "Screenshot at ${s.width}px", Modifier.fillMaxWidth().height(220.dp)) }
        }
    }
}
