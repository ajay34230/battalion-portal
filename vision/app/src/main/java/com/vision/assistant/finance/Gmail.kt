package com.vision.assistant.finance

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.vision.assistant.VisionApp
import com.vision.assistant.data.PaymentRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Read-only Gmail access (scope gmail.readonly) to find payment mails. Sign-in goes through Google's Authorization API;
 * the token lives only in memory for this sync. Nothing is sent anywhere except to Google. Mail text is untrusted: only amount, party and date are extracted.
 * Requires an Android OAuth client in the user's own Google Cloud project (package + signing SHA-1, see docs/GMAIL_SETUP.md).
 */
object Gmail {
    const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"
    const val QUERY = "newer_than:35d (debited OR credited OR \"payment successful\" OR \"paid to\" OR received OR \"bill payment\" OR refund) -otp -\"verification code\""

    sealed interface Auth { data class Token(val value: String) : Auth; data class NeedsConsent(val sender: android.content.IntentSender) : Auth; data class Failed(val why: String) : Auth }

    suspend fun authorize(ctx: Context): Auth = suspendCancellableCoroutine { c ->
        val req = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(ctx).authorize(req)
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) c.resume(Auth.NeedsConsent(pi.intentSender))
                else c.resume(r.accessToken?.let { Auth.Token(it) } ?: Auth.Failed("Google returned no access token."))
            }
            .addOnFailureListener { c.resume(Auth.Failed(explain(it.message))) }
    }

    /** After the consent screen returns. */
    fun tokenFrom(ctx: Context, data: android.content.Intent?): Auth = try {
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data).accessToken?.let { Auth.Token(it) } ?: Auth.Failed("No access token after consent.")
    } catch (t: Throwable) { Auth.Failed(explain(t.message)) }

    fun explain(m: String?) = if (m != null && (m.contains("10:") || m.contains("DEVELOPER_ERROR") || m.contains("12500") || m.contains("API: "))) "Google rejected this app: the OAuth client for package com.vision.assistant and this build's signing SHA-1 is missing or not yet active in your Google Cloud project. See docs/GMAIL_SETUP.md. ($m)" else (m ?: "Sign-in failed.")

    data class Mail(val id: String, val subject: String, val snippet: String, val atMillis: Long)

    fun parseMessage(json: String): Mail? = try {
        val o = JSONObject(json)
        val headers = o.optJSONObject("payload")?.optJSONArray("headers")
        var subject = ""
        if (headers != null) for (i in 0 until headers.length()) { val h = headers.getJSONObject(i); if (h.optString("name").equals("Subject", true)) subject = h.optString("value") }
        Mail(o.getString("id"), subject, o.optString("snippet"), o.optString("internalDate").toLongOrNull() ?: System.currentTimeMillis())
    } catch (_: Exception) { null }

    private fun get(url: String, token: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $token"); c.connectTimeout = 15000; c.readTimeout = 20000
        if (c.responseCode !in 200..299) throw java.io.IOException("Gmail answered ${c.responseCode}")
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** Returns (new payments added, mails checked). */
    suspend fun sync(app: VisionApp, token: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val list = JSONObject(get("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=50&q=" + URLEncoder.encode(QUERY, "UTF-8"), token))
            val msgs = list.optJSONArray("messages")
            var added = 0; var seen = 0
            val dao = app.database.payments()
            if (msgs != null) for (i in 0 until msgs.length()) {
                val id = msgs.getJSONObject(i).getString("id"); seen++
                if (dao.bySource("gmail:$id") > 0) continue
                val mail = parseMessage(get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id?format=metadata&metadataHeaders=Subject", token)) ?: continue
                val p = PaymentParser.parse(mail.subject, mail.snippet) ?: continue
                dao.insert(PaymentRecord(direction = p.direction.name, paise = p.amountPaise, party = p.party, bill = p.bill, source = "gmail:$id", category = PaymentParser.suggestCategory(p.party, p.bill), at = mail.atMillis))
                added++
            }
            added to seen
        }
    }
}
