package com.vision.assistant.comms

import com.vision.assistant.research.httpGet
import org.json.JSONObject

data class MailSummary(val from: String, val subject: String, val snippet: String)

fun interface TokenProvider { suspend fun accessToken(): String? }

/**
 * Gmail REST client (read-only scope). OAuth sign-in is NOT built in this app yet: it needs a Google Cloud OAuth client
 * owned by the developer. Until a [TokenProvider] is supplied, Gmail features report "not connected".
 */
class GmailApi(private val tokens: TokenProvider) {
    suspend fun unread(max: Int = 10): List<MailSummary>? {
        val token = tokens.accessToken() ?: return null
        val list = JSONObject(authGet("https://gmail.googleapis.com/gmail/v1/users/me/messages?q=is:unread&maxResults=$max", token))
        val msgs = list.optJSONArray("messages") ?: return emptyList()
        return (0 until msgs.length()).map {
            val id = msgs.getJSONObject(it).getString("id")
            parse(authGet("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=From&metadataHeaders=Subject", token))
        }
    }

    private fun authGet(url: String, token: String): String {
        val c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token"); connectTimeout = 10_000; readTimeout = 15_000
        }
        if (c.responseCode !in 200..299) error("Gmail HTTP ${c.responseCode}")
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    companion object {
        fun parse(json: String): MailSummary {
            val o = JSONObject(json)
            val headers = o.optJSONObject("payload")?.optJSONArray("headers")
            fun h(name: String): String {
                if (headers == null) return ""
                for (i in 0 until headers.length()) { val x = headers.getJSONObject(i); if (x.getString("name").equals(name, true)) return x.getString("value") }
                return ""
            }
            return MailSummary(h("From"), h("Subject"), o.optString("snippet"))
        }
    }
}
