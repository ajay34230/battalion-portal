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
