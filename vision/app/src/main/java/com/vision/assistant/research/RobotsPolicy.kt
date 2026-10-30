package com.vision.assistant.research

/** Minimal robots.txt reader: honours Disallow/Allow for the "*" group (longest match wins). */
class RobotsPolicy(robotsTxt: String?) {
    private val rules = mutableListOf<Pair<Boolean, String>>() // allow?, path prefix

    init {
        var inStar = false
        var sawAgentLine = false
        robotsTxt?.lineSequence()?.forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "user-agent" -> { if (!sawAgentLine) inStar = false; sawAgentLine = true; if (value == "*") inStar = true }
                "disallow" -> { sawAgentLine = false; if (inStar && value.isNotEmpty()) rules += false to value }
                "allow" -> { sawAgentLine = false; if (inStar && value.isNotEmpty()) rules += true to value }
                else -> sawAgentLine = false
            }
        }
    }

    fun isAllowed(path: String): Boolean {
        val best = rules.filter { path.startsWith(it.second) }.maxByOrNull { it.second.length } ?: return true
        return best.first
    }
}
