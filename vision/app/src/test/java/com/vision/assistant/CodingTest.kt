package com.vision.assistant

import com.vision.assistant.code.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CodingTest {
    private fun store() = ProjectStore(Files.createTempDirectory("vision-proj").toFile())
    private fun load(s: ProjectStore, t: Templates.Template, name: String = "demo") {
        s.create(name).getOrThrow(); t.files.forEach { (p, c) -> s.write(name, p, c).getOrThrow() }
    }
    private fun staticProblems(s: ProjectStore, name: String): List<Problem> {
        val all = s.files(name)
        return StaticChecker.check(all.associateWith { s.read(name, it).orEmpty() })
    }

    @Test fun templatesPassStaticChecks() {
        for (t in Templates.all) { val s = store(); load(s, t); assertEquals("${t.id}: ${staticProblems(s, "demo")}", emptyList<Problem>(), staticProblems(s, "demo")) }
    }
    @Test fun pathTraversalIsRejected() {
        val s = store(); s.create("p").getOrThrow()
        for (bad in listOf("../x.txt", "/etc/passwd", "a/../../b", ".hidden", "a//b", "C:\\x", "bad<name>.txt")) assertTrue(bad, s.write("p", bad, "x").isFailure)
        assertTrue(s.write("p", "src/ok.txt", "x").isSuccess)
    }
    @Test fun saveCloseReopenAndContinue() {
        val dir = Files.createTempDirectory("vision-reopen").toFile()
        val a = ProjectStore(dir); a.create("keep").getOrThrow(); a.write("keep", "a.txt", "one").getOrThrow()
        val b = ProjectStore(dir) // "app restarted"
        assertEquals(listOf("keep"), b.list()); assertEquals("one", b.read("keep", "a.txt"))
        b.write("keep", "a.txt", "two").getOrThrow(); assertEquals("two", ProjectStore(dir).read("keep", "a.txt"))
    }
    @Test fun zipExportContainsEveryFileAndNoSnapshots() {
        val s = store(); load(s, Templates.website); s.snapshot("demo", "x")
        val out = ByteArrayOutputStream(); val n = s.exportZip("demo", out)
        val names = ArrayList<String>(); ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z -> var e = z.nextEntry; while (e != null) { names.add(e.name); e = z.nextEntry } }
        assertEquals(Templates.website.files.size, n); assertEquals(n, names.size)
        assertTrue(names.all { it.startsWith("demo/") && !it.contains(".snapshots") }); assertTrue("demo/css/site.css" in names)
    }
    @Test fun zipRoundTrip() {
        val s = store(); load(s, Templates.calculator)
        val out = ByteArrayOutputStream(); s.exportZip("demo", out)
        val n = s.importZip("copy", ByteArrayInputStream(out.toByteArray())).getOrThrow()
        assertEquals(Templates.calculator.files.size, n); assertEquals(s.read("demo", "calc.js"), s.read("copy", "calc.js"))
    }
    @Test fun snapshotAndRollback() {
        val s = store(); s.create("p").getOrThrow(); s.write("p", "a.txt", "v1").getOrThrow()
        val snap = s.snapshot("p", "v1"); s.write("p", "a.txt", "v2").getOrThrow(); s.write("p", "b.txt", "new").getOrThrow()
        assertTrue(s.rollback("p", snap.id)); assertEquals("v1", s.read("p", "a.txt")); assertNull(s.read("p", "b.txt"))
    }
    @Test fun detectsMissingBraceQuoteAndLink() {
        assertNotNull(StaticChecker.balance("function f() {\n  return 1;\n", true))
        assertNotNull(StaticChecker.balance("const s = \"abc;\n", true))
        assertNull(StaticChecker.balance("const s = \"}\"; // { ignored\nfunction f(){ return [1,2]; }", true))
        val p = StaticChecker.check(mapOf("index.html" to "<html><head><title>t</title><script src=\"missing.js\"></script></head></html>"))
        assertTrue(p.any { it.message.contains("missing.js") })
        assertNotNull(StaticChecker.pythonProblems("def f(x)\n    return x\n"))
        assertNull(StaticChecker.pythonProblems("def f(x):\n    return x  # ok\n"))
    }

    @Test fun repairsAnIntentionalError() = runBlocking {
        val s = store(); load(s, Templates.calculator)
        val good = s.read("demo", "calc.js")!!
        s.write("demo", "calc.js", good.replaceFirst("function apply(a, b, o) {", "function apply(a, b, o) {{")) // introduce an error
        assertTrue(staticProblems(s, "demo").isNotEmpty())
        var asked = 0
        val loop = FixLoop(s, { _, _ -> asked++; """{"files":[{"path":"calc.js","content":${org.json.JSONObject.quote(good)}}]}""" })
        val r = loop.run("demo", "fix the calculator")
        assertTrue(r.log.joinToString(), r.fixed); assertEquals(1, asked); assertEquals(good, s.read("demo", "calc.js"))
    }
    @Test fun doesNotRepeatTheSameFailedFix() = runBlocking {
        val s = store(); s.create("p").getOrThrow(); s.write("p", "a.js", "function f( {").getOrThrow()
        var asked = 0
        val r = FixLoop(s, { _, _ -> asked++; """{"files":[{"path":"a.js","content":"function f( {"}]}""" }).run("p")
        assertFalse(r.fixed); assertTrue("asked $asked times", asked <= 2); assertTrue(r.log.any { it.contains("not repeating") || it.contains("worse") || it.contains("usable") })
    }
    @Test fun worseFixIsRolledBack() = runBlocking {
        val s = store(); s.create("p").getOrThrow(); s.write("p", "a.js", "function f( {").getOrThrow()
        val r = FixLoop(s, { _, _ -> """{"files":[{"path":"a.js","content":"function f( { ( ["},{"path":"b.js","content":"var x = ("}]}""" }, maxRounds = 1).run("p")
        assertFalse(r.fixed); assertEquals("function f( {", s.read("p", "a.js")); assertNull(s.read("p", "b.js"))
    }
    @Test fun parsesModelOutput() {
        val f = CodeParser.parse("Sure!\n{\"files\":[{\"path\":\"a.txt\",\"content\":\"hi\"}],\"notes\":\"x\"}")!!
        assertEquals("a.txt", f[0].path); assertNull(CodeParser.parse("no json here"))
    }
}

class TemplateExportTest {
    /** In CI the workflow sets VISION_TEMPLATE_OUT and then runs the exported projects with real Node and Python. */
    @Test fun exportTemplatesForAcceptance() {
        val out = System.getenv("VISION_TEMPLATE_OUT") ?: return
        val store = ProjectStore(java.io.File(out))
        for (t in Templates.all) { store.create(t.id).getOrThrow(); t.files.forEach { (p, c) -> store.write(t.id, p, c).getOrThrow() } }
    }
}

class TermuxRunnerTest {
    @Test fun choosesRunnerByExtension() {
        assertEquals("python3 'report.py'", TermuxRunner.runnerFor("report.py"))
        assertEquals("python3 'test_report.py' -v", TermuxRunner.runnerFor("test_report.py"))
        assertEquals("node 'test.js'", TermuxRunner.runnerFor("test.js")); assertNull(TermuxRunner.runnerFor("a.png"))
    }
    @Test fun scriptCarriesFilesAsBase64AndStaysInProjectsFolder() {
        val s = TermuxRunner.script("demo", mapOf("a.py" to "print('hi')\n", "src/b.txt" to "x"), "python3 'a.py'")!!
        assertTrue(s.contains("\$HOME/projects/demo")); assertTrue(s.contains("mkdir -p 'src'")); assertFalse(s.contains("print('hi')"))
        assertTrue(s.endsWith("python3 'a.py'"))
    }
    @Test fun tooLargeProjectIsRefused() = assertNull(TermuxRunner.script("big", mapOf("x.txt" to "a".repeat(200_000)), "true"))
}

class DevEnvTest {
    private fun spec(id: String) = ToolCatalogue.byId(id)!!
    @Test fun parsesVersions() {
        assertEquals("3.11.6", VersionUtil.parse("Python 3.11.6")); assertEquals("17.0.9", VersionUtil.parse("openjdk version \"17.0.9\" 2023"))
        assertTrue(VersionUtil.compare("3.10", "3.9") > 0); assertTrue(VersionUtil.compare("18", "18.0.0") == 0); assertTrue(VersionUtil.compare("2.1", "2.30") < 0)
    }
    @Test fun statusesAreDetected() {
        assertEquals(ToolStatus.INSTALLED_AND_READY, DevInventory.status(spec("python"), "Python 3.12.1", true).status)
        assertEquals(ToolStatus.INSTALLED_BUT_OUTDATED, DevInventory.status(spec("python"), "Python 3.6.0", true).status)
        assertEquals(ToolStatus.NOT_INSTALLED, DevInventory.status(spec("git"), "bash: git: command not found", true).status)
        assertEquals(ToolStatus.PERMISSION_REQUIRED, DevInventory.status(spec("git"), null, false).status)
        assertEquals(ToolStatus.UNSUPPORTED, DevInventory.status(spec("android-sdk"), "", true).status)
        assertEquals(ToolStatus.PARTIALLY_INSTALLED, DevInventory.status(spec("git"), "weird output", true).status)
        val states = mapOf("java" to ToolState("java", ToolStatus.NOT_INSTALLED, "", 0))
        assertEquals(ToolStatus.INSTALLATION_REQUIRED, DevInventory.status(spec("gradle"), "gradle: not found", true, states).status)
    }
    @Test fun probeOutputRoundTrip() {
        val m = DevInventory.parseProbe("python=Python 3.12.1\ngit=bash: git: command not found\nnoise line\n")
        assertEquals("Python 3.12.1", m["python"]); assertTrue(m["git"]!!.contains("not found")); assertEquals(2, m.size)
        val js = DevInventory.toJson(listOf(ToolState("python", ToolStatus.INSTALLED_AND_READY, "3.12.1", 5)))
        assertEquals("3.12.1", DevInventory.fromJson(js)["python"]!!.version)
    }
    @Test fun requirementsDependOnTheProject() {
        assertTrue(RequirementDetector.detect("make a calculator website", listOf("index.html", "style.css", "app.js")).isEmpty())
        assertTrue(RequirementDetector.detect("report from csv", listOf("report.py")).containsAll(listOf("python", "pip")))
        assertTrue(RequirementDetector.detect("build an android app", emptyList()).containsAll(listOf("java", "gradle", "android-sdk")))
        assertTrue("express" in "express" && RequirementDetector.detect("express server", emptyList()).contains("node"))
    }
    @Test fun installSummaryNeverStartsAnythingAndIsComplete() {
        val s = InstallSummary.build(spec("git"), "needed for history", 20000)
        for (k in listOf("Resource", "Source", "License", "Cost", "storage", "Permissions", "Method", "Security", "Why now")) assertTrue(k, s.contains(k))
        assertEquals("pkg install -y git", InstallSummary.command(spec("git")))
        assertTrue(InstallSummary.build(spec("android-sdk"), "x", 1).contains("cannot be installed"))
        assertTrue(InstallSummary.build(spec("python"), "x", 500).contains("too low"))
    }
}

class ProbeScriptTest {
    @Test fun probeScriptHasNoArithmeticExpansion() {
        val s = DevInventory.probeScript()
        assertFalse(s.contains("\$(("))
        assertTrue(s.lines().all { it.startsWith("echo \"") })
        assertEquals(ToolCatalogue.all.size, s.lines().size)
    }
}
