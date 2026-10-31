package com.vision.assistant.voice

import android.content.Context
import org.json.JSONArray
import kotlin.math.sqrt

/**
 * Speaker matching with Vosk's x-vector model. The voiceprint (a few lists of numbers, not audio) stays in this app's private storage.
 * This tells voices apart reasonably well but is a convenience filter, NOT security: a recording of you can pass, a cold or noisy room can fail.
 */
object Voiceprint {
    enum class Strictness(val label: String, val maxDistance: Double) { LOOSE("Loose", 0.75), NORMAL("Normal", 0.6), STRICT("Strict", 0.45) }

    fun cosineDistance(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size || a.isEmpty()) return 2.0
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        if (na == 0.0 || nb == 0.0) return 2.0
        return 1.0 - dot / (sqrt(na) * sqrt(nb))
    }

    fun mean(vs: List<DoubleArray>): DoubleArray? {
        if (vs.isEmpty()) return null
        val n = vs[0].size; if (vs.any { it.size != n }) return null
        return DoubleArray(n) { i -> vs.sumOf { it[i] } / vs.size }
    }

    /** Matches when the sample is close to the average voiceprint OR to any single enrolled sample. */
    fun matches(sample: DoubleArray, enrolled: List<DoubleArray>, s: Strictness): Boolean {
        if (enrolled.isEmpty()) return true
        val m = mean(enrolled)
        val d = minOf(m?.let { cosineDistance(sample, it) } ?: 2.0, enrolled.minOf { cosineDistance(sample, it) })
        return d <= s.maxDistance
    }

    /** Pulls the speaker vector out of a Vosk result JSON, or null when the result has none. */
    fun vectorFrom(json: String): DoubleArray? = try {
        val a = org.json.JSONObject(json).optJSONArray("spk")
        if (a == null || a.length() == 0) null else DoubleArray(a.length()) { a.getDouble(it) }
    } catch (_: Exception) { null }

    /** Phrases accepted during enrolment ("hey vision", "hi vision", "vision" + common mishearings). */
    fun isWakePhrase(text: String) = Regex("\\b(vision|visions|vishan|vizhan)\\b").containsMatchIn(text.lowercase())

    val PROMPTS = listOf("Hey Vision", "Hey Vision", "Hey Vision", "Vision", "Vision", "Hi Vision", "Hi Vision", "Hi Vision")

    private fun sp(c: Context) = c.getSharedPreferences("vision_voiceprint", Context.MODE_PRIVATE)
    /** Voice samples are stored sealed with a Keystore key. An older plain copy is read once and replaced by a sealed one. If the sealed copy cannot be read (key lost), the user simply re-enrols. */
    fun load(c: Context): List<DoubleArray> = try {
        val sealed = sp(c).getString("samples_enc", null)
        val json = if (sealed != null) com.vision.assistant.core.SecureStore.open(sealed) ?: "[]" else {
            val plain = sp(c).getString("samples", null)
            if (plain != null) { writeSamples(c, plain) }
            plain ?: "[]"
        }
        val arr = JSONArray(json)
        (0 until arr.length()).map { i -> val v = arr.getJSONArray(i); DoubleArray(v.length()) { v.getDouble(it) } }
    } catch (_: Exception) { emptyList() }
    private fun writeSamples(c: Context, json: String) {
        val sealed = com.vision.assistant.core.SecureStore.seal(json)
        // Plain storage only if this phone's Keystore is unusable; the privacy screen must not claim encryption then.
        if (sealed != null) sp(c).edit().putString("samples_enc", sealed).remove("samples").apply() else sp(c).edit().putString("samples", json).apply()
    }
    fun isEncrypted(c: Context) = sp(c).contains("samples_enc")
    fun save(c: Context, samples: List<DoubleArray>) { writeSamples(c, JSONArray(samples.map { s -> JSONArray(s.toList()) }).toString()) }
    fun clear(c: Context) { sp(c).edit().clear().apply() }
    fun enrolled(c: Context) = load(c).size >= 4
    fun dismissed(c: Context) = sp(c).getBoolean("dismissed", false)
    fun dismiss(c: Context) { sp(c).edit().putBoolean("dismissed", true).apply() }
    fun strictness(c: Context) = Strictness.values().firstOrNull { it.name == sp(c).getString("strict", "NORMAL") } ?: Strictness.NORMAL
    fun setStrictness(c: Context, s: Strictness) { sp(c).edit().putString("strict", s.name).apply() }
}
