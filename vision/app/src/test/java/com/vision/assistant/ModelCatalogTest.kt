package com.vision.assistant

import com.vision.assistant.core.ModelCatalog
import com.vision.assistant.core.ModelCatalog.Compat
import org.junit.Assert.*
import org.junit.Test

class ModelCatalogTest {
    private val sha = "a".repeat(64)
    @Test fun parsesSearchAndRejectsForeignOrOddRepoIds() {
        val json = """[{"id":"litert-community/Qwen3-1.7B","downloads":900,"likes":5,"tags":["license:apache-2.0","text-generation"]},
            {"id":"evil/Qwen3","downloads":1},{"id":"litert-community/../x","downloads":1},{"id":"litert-community/ok name","downloads":1},{"nope":1}]"""
        val r = ModelCatalog.parseSearch(json)
        assertEquals(listOf("litert-community/Qwen3-1.7B"), r.map { it.id }); assertEquals("apache-2.0", r[0].license)
        assertTrue(ModelCatalog.parseSearch("not json").isEmpty())
    }
    @Test fun parsesTreeKeepingOnlyVerifiableLitertlmFiles() {
        val json = """[{"type":"file","path":"m.litertlm","size":100,"lfs":{"oid":"$sha","size":2000000000}},
            {"type":"file","path":"readme.md","size":10},{"type":"file","path":"nohash.litertlm","size":5},
            {"type":"file","path":"bad name.litertlm","lfs":{"oid":"$sha","size":5}},{"type":"file","path":"../x.litertlm","lfs":{"oid":"$sha","size":5}},
            {"type":"file","path":"badsha.litertlm","lfs":{"oid":"zz","size":5}},{"type":"file","path":"huge.litertlm","lfs":{"oid":"$sha","size":99999999999999}},
            {"type":"directory","path":"d"}]"""
        val f = ModelCatalog.parseTree(json); assertEquals(1, f.size); assertEquals(2_000_000_000L, f[0].sizeBytes)
    }
    @Test fun linksAreBuiltHereNotTakenFromTheResponse() {
        assertEquals("https://huggingface.co/litert-community/A/resolve/main/m.litertlm", ModelCatalog.fileUrl("litert-community/A", "m.litertlm"))
        assertTrue(ModelCatalog.searchUrl("qwen &evil=1").contains("search=qwen+evil1") || ModelCatalog.searchUrl("qwen &evil=1").contains("search=qwen"))
        assertFalse(ModelCatalog.searchUrl("a&b=c").contains("b=c"))
    }
    @Test fun compatibilityLevels() {
        val ram = 8000L; val free = 50_000L
        assertEquals(Compat.COMPATIBLE, ModelCatalog.assess(900, ram, free, true).level)
        assertEquals(Compat.LIMITED, ModelCatalog.assess(2700, ram, free, true).level)
        assertEquals(Compat.EXPERIMENTAL, ModelCatalog.assess(4500, ram, free, true).level)
        assertEquals(Compat.INCOMPATIBLE, ModelCatalog.assess(6900, ram, free, true).level)
        assertEquals(Compat.INCOMPATIBLE, ModelCatalog.assess(900, ram, 1500, true).level)
        assertEquals(Compat.INCOMPATIBLE, ModelCatalog.assess(100, ram, free, false).level)
    }
    @Test fun entryRoundTripAndTamperRejected() {
        val repo = ModelCatalog.Repo("litert-community/Qwen3-1.7B", 10, 1, listOf("license:apache-2.0"), "apache-2.0")
        val e = ModelCatalog.toEntry(repo, ModelCatalog.RemoteFile("Qwen3_1.7B.litertlm", 1_754_890_240, sha), 8000)!!
        assertTrue(e.id.startsWith("hf-")); assertEquals(sha, e.sha256)
        val back = ModelCatalog.decode(ModelCatalog.encode(e))!!; assertEquals(e.url, back.url)
        assertNull(ModelCatalog.decode(ModelCatalog.encode(e).put("url", "https://evil.example/x/resolve/main/Qwen3_1.7B.litertlm")))
        assertNull(ModelCatalog.decode(ModelCatalog.encode(e).put("sha", "short")))
        assertNull(ModelCatalog.decode(ModelCatalog.encode(e).put("id", "qwen3-1.7b")))
    }
    @Test fun categoriesAreGuessedFromNames() {
        assertTrue(ModelCatalog.categories("Qwen2.5-Coder-1.5B").contains("Programming"))
        assertTrue(ModelCatalog.categories("gemma-4-E2B-it-litert-lm").contains("Vision (images)"))
        assertFalse(ModelCatalog.categories("Granite-4.0-350m").contains("Programming"))
    }
}
