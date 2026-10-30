package com.vision.assistant.research

import com.vision.assistant.VisionApp
import com.vision.assistant.core.NetworkMonitor
import com.vision.assistant.tools.Say
import com.vision.assistant.tools.ToolResult

/** Placeholder replaced by the full research engine in Phase 7 (same file path, same class). */
class ResearchTool(private val app: VisionApp) {
    suspend fun run(query: String): ToolResult {
        if (!NetworkMonitor.isOnline(app)) {
            return ToolResult.Failure(Say("You're offline, so I can't search the web right now.", "Aap offline hain, abhi web search nahi ho sakta.", "आप ऑफ़लाइन हैं, अभी वेब खोज नहीं हो सकती।"))
        }
        return ToolResult.Failure(Say.plain("Web research is not built yet."))
    }
}
