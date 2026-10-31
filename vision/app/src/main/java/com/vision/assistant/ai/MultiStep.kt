package com.vision.assistant.ai

/** Splits "open maps then call mom and remind me at 6" style requests into ordered steps. Only splits when every part is a recognised command. */
object MultiStep {
    private val sep = Regex("\\s*(?:,?\\s+and then\\s+|,?\\s+then\\s+|\\s+phir\\s+|\\s+fir\\s+|\\s+uske baad\\s+|\\s+uske bad\\s+|\\s+उसके बाद\\s+|\\s+फिर\\s+)\\s*", RegexOption.IGNORE_CASE)

    fun split(text: String): List<String> {
        val parts = text.trim().split(sep).map { it.trim().trimEnd('.', '!', '।') }.filter { it.isNotEmpty() }
        if (parts.size !in 2..5) return listOf(text)
        return if (parts.all { IntentParser.parse(it) !is Intent.Chat }) parts else listOf(text)
    }
}

/** Remaining steps of an interrupted multi-step request, saved on the phone so "continue" works even after the app restarts. */
object PlanState {
    private fun sp(ctx: android.content.Context) = ctx.getSharedPreferences("vision_plan", android.content.Context.MODE_PRIVATE)
    fun save(ctx: android.content.Context, remaining: List<String>) = sp(ctx).edit().putString("remaining", org.json.JSONArray(remaining).toString()).apply()
    fun load(ctx: android.content.Context): List<String> = try { org.json.JSONArray(sp(ctx).getString("remaining", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } } catch (_: Exception) { emptyList() }
    fun clear(ctx: android.content.Context) = sp(ctx).edit().remove("remaining").apply()
    val resumeWords = Regex("^(continue|resume|go on|carry on|next step|aage badho|aage badh|jaari rakho|जारी रखो|आगे बढ़ो)[.!]?$", RegexOption.IGNORE_CASE)
    val cancelWords = Regex("^(cancel (the )?plan|forget the rest|baaki rehne do|baaki chhod do)[.!]?$", RegexOption.IGNORE_CASE)
}
