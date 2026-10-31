package com.vision.assistant.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vision.assistant.VisionApp
import com.vision.assistant.code.CodeStudio
import com.vision.assistant.code.ProjectStore
import com.vision.assistant.code.StaticChecker
import com.vision.assistant.code.Templates
import com.vision.assistant.code.TermuxRunner
import com.vision.assistant.termux.TermuxGateway
import com.vision.assistant.termux.TermuxPrefs
import kotlinx.coroutines.launch

/** Simple syntax colouring (keywords, strings, comments, numbers) for the editor. */
class CodeHighlighter(private val ext: String, private val find: String) : VisualTransformation {
    private val keywords = when (ext) {
        "py" -> "def|class|if|elif|else|for|while|return|import|from|as|try|except|finally|with|in|not|and|or|None|True|False|print|lambda|pass|break|continue|raise|yield"
        "js", "mjs", "kt", "java" -> "function|const|let|var|if|else|for|while|return|class|new|this|import|export|from|try|catch|finally|throw|null|true|false|undefined|await|async|fun|val|override|public|private|static"
        "css" -> "@media|@import|!important"
        "html", "htm" -> "html|head|body|div|span|script|style|link|meta|title|main|header|footer|nav|a|p|h1|h2|h3|button|form|input|textarea|label|img|ul|li|section"
        else -> ""
    }
    private val rx = Regex("(//[^\\n]*|#[^\\n]*|/\\*[\\s\\S]*?\\*/|<!--[\\s\\S]*?-->)|(\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')|(\\b\\d+(?:\\.\\d+)?\\b)" + if (keywords.isNotEmpty()) "|(\\b(?:$keywords)\\b)" else "")
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = buildAnnotatedString {
            append(s)
            if (s.length < 60_000) {
                for (m in rx.findAll(s)) {
                    val c = when { m.groups[1] != null -> Color(0xFF6B8AA8); m.groups[2] != null -> Color(0xFF9BE37A); m.groups[3] != null -> Color(0xFFFFB86C); else -> Color(0xFF7DD3FC) }
                    addStyle(SpanStyle(color = c), m.range.first, m.range.last + 1)
                }
                if (find.length >= 2) { var i = s.indexOf(find, ignoreCase = true); var n = 0; while (i >= 0 && n++ < 500) { addStyle(SpanStyle(background = Color(0x66FFD54F)), i, i + find.length); i = s.indexOf(find, i + find.length, ignoreCase = true) } }
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

private val symbolRx = Regex("^\\s*(?:function\\s+(\\w+)|def\\s+(\\w+)|class\\s+(\\w+)|fun\\s+(\\w+)|const\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\(|(\\w+)\\s*:\\s*function)", RegexOption.MULTILINE)

@Composable
fun CodingScreen(app: VisionApp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val studio = remember { CodeStudio(app, ctx) }
    var project by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var top by remember { mutableStateOf("Projects") }
    val name = project
    if (name != null) ProjectView(app, studio, name, onClose = { project = null; refresh++ })
    else Column(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(Modifier.padding(start = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(0.dp))
        }
        if (top == "Projects") Box(Modifier.weight(1f)) { ProjectList(studio, refresh, onOpen = { project = it }, onChanged = { refresh++ }, switchTools = { top = "Tools" }) }
        else ScreenFrame("Dev tools", "What's installed, what's missing, and safe installs", action = { TextButton(onClick = { top = "Projects" }) { Text("Projects") } }) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { DevToolsPanel(app, studio) }
        }
    }
}

@Composable
private fun ProjectList(studio: CodeStudio, refresh: Int, onOpen: (String) -> Unit, onChanged: () -> Unit, switchTools: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("calculator") }
    var msg by remember { mutableStateOf("") }
    val projects = remember(refresh) { studio.store.list() }
    ScreenFrame("Coding Studio", "Build, run and fix small programs — offline", action = { TextButton(onClick = switchTools) { Text("Dev tools") } }) {
        InfoCard {
            SectionLabel("New project")
            OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Project name") })
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Templates.all.map { it.id to it.title } + ("empty" to "Empty project")) { (id, title) -> Pill(title, selected = template == id) { template = id } }
            }
            Text(Templates.byId(template)?.description ?: "A blank project. Use “Ask VISION” to generate code with the installed model.", fontSize = 12.sp, color = VisionColors.Muted)
            Button(enabled = newName.isNotBlank(), onClick = {
                val r = if (template == "empty") studio.store.create(newName).map { } else studio.createFromTemplate(newName, template)
                r.onSuccess { onChanged(); onOpen(newName.trim()); newName = "" }.onFailure { msg = it.message.orEmpty() }
            }) { Text("Create") }
            if (msg.isNotBlank()) Text(msg, color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (projects.isEmpty()) item { EmptyState("💻", "No projects yet", "Create one above. The calculator, CSV tool and website templates work without any AI model.") }
            items(projects) { p ->
                InfoCard(onClick = { onOpen(p) }) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(p, fontSize = 16.sp); Text("${studio.store.files(p).size} files", fontSize = 12.sp, color = VisionColors.Muted) }
                        TextButton(onClick = { studio.store.delete(p); studio.log("Deleted project '$p'"); onChanged() }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ProjectView(app: VisionApp, studio: CodeStudio, name: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = studio.store
    var tab by remember { mutableStateOf("Files") }
    var files by remember { mutableStateOf(store.files(name)) }
    var openFile by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf(TextFieldValue("")) }
    var dirty by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var newFile by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var request by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var snapshots by remember { mutableStateOf(store.snapshots(name)) }
    var previewPage by remember { mutableStateOf("index.html") }
    var confirmRun by remember { mutableStateOf<Pair<String, String>?>(null) }
    val console = remember { mutableStateListOf<String>() }
    fun reload() { files = store.files(name); snapshots = store.snapshots(name) }
    fun save() { openFile?.let { f -> store.write(name, f, editor.text).onSuccess { dirty = false; status = "Saved $f"; studio.log("Saved $name/$f") }.onFailure { status = it.message.orEmpty() } } }
    fun problemsText(): String { val p = studio.problems(name); return if (p.isEmpty()) "No problems found by the built-in checks." else p.joinToString("\n") { "• $it" } }

    ScreenFrame(name, "${files.size} files" + if (dirty) " · unsaved" else "", action = { TextButton(onClick = { if (dirty) save(); onClose() }) { Text("Projects") } }) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listOf("Files", "Editor", "Preview", "Visual test", "Run", "Ask VISION", "Tools", "Notes", "Terminal", "History")) { t -> Pill(t, selected = tab == t) { tab = t } }
        }
        if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = VisionColors.Mint)
        when (tab) {
            "Files" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(newFile, { newFile = it }, Modifier.weight(1f), singleLine = true, label = { Text("New file (e.g. js/app.js)") })
                    Button(enabled = newFile.isNotBlank(), onClick = { store.write(name, newFile, "").onSuccess { openFile = newFile.trim(); editor = TextFieldValue(""); newFile = ""; reload(); tab = "Editor" }.onFailure { status = it.message.orEmpty() } }) { Text("Add") }
                }
                var q by remember { mutableStateOf("") }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search in all files") })
                val hits = remember(q, files, dirty) { studio.searchFiles(name, q) }
                if (hits.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().height(110.dp).verticalScroll(rememberScrollState())) {
                        for (h in hits) Text("${h.file}:${h.line}  ${h.text}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.clickable { if (dirty) save(); openFile = h.file; editor = TextFieldValue(store.read(name, h.file) ?: ""); dirty = false; find = q; tab = "Editor" }.padding(vertical = 2.dp))
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(files) { f ->
                        InfoCard(onClick = { if (dirty) save(); openFile = f; editor = TextFieldValue(store.read(name, f) ?: ""); dirty = false; tab = "Editor" }) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(f, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                TextButton(onClick = { store.snapshot(name, "Before deleting $f"); store.deleteFile(name, f); if (openFile == f) openFile = null; reload() }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { status = problemsText() }) { Text("Check") }
                    OutlinedButton(onClick = {
                        try {
                            val out = java.io.File(ctx.cacheDir, "${ProjectStore.slug(name)}.zip")
                            out.outputStream().use { store.exportZip(name, it) }
                            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export ${name}.zip").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            studio.log("Exported '$name' as ZIP"); status = "ZIP ready — pick where to send or save it."
                        } catch (t: Throwable) { status = "Export failed: ${t.message}" }
                    }) { Text("Export ZIP") }
                }
            }
            "Editor" -> if (openFile == null) EmptyState("📝", "No file open", "Pick a file in the Files tab.") else Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                val ext = openFile!!.substringAfterLast('.', "")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(openFile!!, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = VisionColors.Cyan)
                    Button(enabled = dirty, onClick = { save(); val p = studio.problems(name); if (p.isNotEmpty()) status = p.first().toString() }) { Text("Save") }
                }
                OutlinedTextField(find, { find = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Find in file${if (find.length >= 2) " — ${Regex(Regex.escape(find), RegexOption.IGNORE_CASE).findAll(editor.text).count()} matches" else ""}") })
                val syms = remember(editor.text) { symbolRx.findAll(editor.text).map { m -> (m.groups.drop(1).firstOrNull { it != null }?.value ?: "") to m.range.first }.filter { it.first.isNotBlank() }.toList() }
                if (syms.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(syms) { (n, pos) -> Pill("ƒ $n", VisionColors.Violet, selected = false) { editor = editor.copy(selection = TextRange(pos)) } } }
                Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF07101D)).padding(8.dp)) {
                    BasicTextField(editor, { editor = it; dirty = true }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        textStyle = TextStyle(color = Color(0xFFE6F3FF), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                        cursorBrush = Brush.verticalGradient(listOf(VisionColors.Cyan, VisionColors.Cyan)), visualTransformation = CodeHighlighter(ext.lowercase(), find))
                }
            }
            "Preview" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                val pages = files.filter { it.endsWith(".html") }
                if (pages.isEmpty()) EmptyState("🌐", "Nothing to preview", "Add an HTML file (index.html) to see it live here.") else {
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(pages) { p -> Pill(p, selected = previewPage == p) { previewPage = p } } }
                    Text("Runs sandboxed: no network, no access outside this project.", fontSize = 11.sp, color = VisionColors.Muted)
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
                        AndroidView(factory = { c ->
                            WebView(c).apply {
                                settings.javaScriptEnabled = true; settings.allowFileAccess = true; settings.allowContentAccess = false
                                settings.blockNetworkLoads = true
                                @Suppress("DEPRECATION") run { settings.allowFileAccessFromFileURLs = false; settings.allowUniversalAccessFromFileURLs = false }
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(m: ConsoleMessage): Boolean { console.add("${m.messageLevel().name.lowercase()}: ${m.message()} (${m.sourceId().substringAfterLast('/')}:${m.lineNumber()})"); return true }
                                }
                            }
                        }, update = { w ->
                            if (dirty) save()
                            console.clear()
                            w.loadUrl("file://" + java.io.File(store.dir(name), previewPage).absolutePath)
                        }, modifier = Modifier.fillMaxSize())
                    }
                    Text("Console", fontSize = 11.sp, color = VisionColors.Muted)
                    Column(Modifier.fillMaxWidth().height(70.dp).verticalScroll(rememberScrollState())) {
                        if (console.isEmpty()) Text("(no console output or errors)", fontSize = 12.sp, color = VisionColors.Muted)
                        console.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (it.startsWith("error")) Color(0xFFFF6B6B) else VisionColors.Text) }
                    }
                }
            }
            "Run" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Python, Node and shell files run inside Termux (Advanced mode must be on, and you confirm every run). HTML/JS preview runs in the Preview tab.", fontSize = 12.sp, color = VisionColors.Muted)
                val runnable = files.filter { TermuxRunner.runnerFor(it) != null }
                if (runnable.isEmpty()) Text("No runnable .py / .js / .sh files in this project.")
                for (f in runnable) InfoCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(f, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Button(onClick = { if (dirty) save(); confirmRun = f to TermuxRunner.runnerFor(f)!! }) { Text(if (f.substringAfterLast('/').startsWith("test")) "Test" else "Run") }
                    }
                }
                if (busy) Text("Running… (max 30 s)", color = VisionColors.Cyan)
                if (output.isNotBlank()) { SectionLabel("Output"); Text(output, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                OutlinedButton(onClick = { status = ""; output = problemsText() }) { Text("Built-in checks") }
            }
            "Ask VISION" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Describe what to build or change. Uses the installed on-device model; your code never leaves the phone. A snapshot is saved first so you can roll back.", fontSize = 12.sp, color = VisionColors.Muted)
                OutlinedTextField(request, { request = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("e.g. “add a dark mode toggle”") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = request.isNotBlank() && !busy, onClick = { busy = true; scope.launch { output = studio.build(name, request.trim()); busy = false; reload(); openFile?.let { editor = TextFieldValue(store.read(name, it) ?: ""); dirty = false } } }) { Text("Build / change") }
                    OutlinedButton(enabled = request.isNotBlank() && !busy, onClick = { busy = true; scope.launch { output = studio.research(request.trim()); busy = false } }) { Text("Research") }
                    OutlinedButton(enabled = request.isNotBlank() && !busy, onClick = { busy = true; scope.launch { output = studio.compareApproaches(request.trim()); busy = false } }) { Text("Compare approaches") }
                    OutlinedButton(enabled = !busy, onClick = { busy = true; scope.launch { val r = studio.autoFix(name); output = r.log.joinToString("\n") + (if (r.remaining.isNotEmpty()) "\nRemaining:\n" + r.remaining.joinToString("\n") { "• $it" } else ""); busy = false; reload(); openFile?.let { editor = TextFieldValue(store.read(name, it) ?: ""); dirty = false } } }) { Text("Find & fix errors") }
                }
                if (busy) Text("Working… on-device models can take a minute.", color = VisionColors.Cyan)
                if (output.isNotBlank()) Text(output, fontSize = 13.sp)
            }
            "Tools" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { DevToolsPanel(app, studio, request, files) }
            "Visual test" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { VisualTestPanel(studio, name, files) }
            "Notes" -> NotesTab(studio, name, Modifier.weight(1f))
            "Terminal" -> TerminalTab(studio, Modifier.weight(1f))
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (dirty) save(); store.snapshot(name, "Manual snapshot"); reload(); status = "Snapshot saved." }) { Text("Save snapshot") }
                }
                SectionLabel("Snapshots (tap Restore to roll back)")
                if (snapshots.isEmpty()) Text("None yet.", color = VisionColors.Muted)
                for (s in snapshots) InfoCard {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(s.label.ifBlank { "Snapshot" }); Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.time)), fontSize = 11.sp, color = VisionColors.Muted) }
                        TextButton(onClick = { if (store.rollback(name, s.id)) { studio.log("Rolled back '$name' to ${s.label}"); openFile = null; reload(); status = "Restored." } }) { Text("Restore") }
                    }
                }
                SectionLabel("Git (runs in Termux, Advanced mode, you confirm each action)")
                var commitMsg by remember { mutableStateOf("") }
                OutlinedTextField(commitMsg, { commitMsg = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Commit message") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (dirty) save(); confirmRun = "git init + first commit" to TermuxRunner.Git.initialCommit(commitMsg) }) { Text("Init + commit") }
                    OutlinedButton(onClick = { if (dirty) save(); confirmRun = "git commit" to TermuxRunner.Git.addCommit(commitMsg) }) { Text("Commit") }
                    OutlinedButton(onClick = { if (dirty) save(); confirmRun = "git status" to TermuxRunner.Git.STATUS }) { Text("Status") }
                    OutlinedButton(onClick = { if (dirty) save(); confirmRun = "git log" to TermuxRunner.Git.LOG }) { Text("Log") }
                    OutlinedButton(onClick = { if (dirty) save(); confirmRun = "git diff" to TermuxRunner.Git.DIFF }) { Text("Changes") }
                }
                if (output.isNotBlank()) Text(output, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                SectionLabel("Activity log")
                Text(studio.readLog().ifBlank { "(empty)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }
    }

    confirmRun?.let { (file, cmd) ->
        val adv = TermuxPrefs.advanced(ctx)
        AlertDialog(onDismissRequest = { confirmRun = null },
            title = { Text("Run in Termux?") },
            text = { Text(if (!adv) "This needs Termux Advanced mode (More → Termux control → Advanced). It copies the project to ~/projects/${ProjectStore.slug(name)} in Termux and runs:\n\n$cmd" else "This copies the project to ~/projects/${ProjectStore.slug(name)} in Termux and runs:\n\n$cmd\n\n(30 second limit)") },
            confirmButton = {
                TextButton(enabled = adv, onClick = {
                    confirmRun = null; busy = true; output = ""
                    scope.launch {
                        val data = store.files(name).filter { it.length < 120 }.associateWith { store.read(name, it).orEmpty() }
                        val script = if (cmd.startsWith("git ")) TermuxRunner.gitScript(ProjectStore.slug(name), data, cmd) else TermuxRunner.script(ProjectStore.slug(name), data, cmd)
                        if (script == null) output = "Project is too large to send to Termux in one command (limit ~90 KB)."
                        else {
                            studio.log("Run in Termux: $name/$file")
                            output = TermuxGateway(ctx).execute(TermuxRunner.requestFor(script)).fold({ it }, { "Could not run: ${it.message}" })
                            studio.log("Result of $name/$file: ${output.lineSequence().firstOrNull()}")
                        }
                        busy = false
                    }
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { confirmRun = null }) { Text("Cancel") } })
    }
}
