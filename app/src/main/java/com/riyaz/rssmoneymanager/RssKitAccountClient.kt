package com.riyaz.rssmoneymanager

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** RSS KIT account adapter; RSS Core is the authoritative server behind it. */
class RssKitAccountClient(private val context: Context) {
    companion object {
        private const val BASE_URL = "https://rsscore.cv"
        private const val PROJECT_KEY = "rss-money-manager"
        private const val PREFS = "money_manager_account"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "display_name"
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_CUSTOMER_ID = "customer_id"
        private const val KEY_VERIFICATION_EXPIRES = "verification_expires_at"
    }

    data class Result(val ok: Boolean, val message: String, val verificationPending: Boolean = false, val verified: Boolean = false)

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    fun register(displayName: String, email: String, termsAccepted: Boolean): Result =
        postJson("/api/v1/license/register", JSONObject()
            .put("display_name", displayName.trim()).put("email", email.trim())
            .put("project_key", PROJECT_KEY).put("device_id", deviceId())
            .put("terms_accepted", termsAccepted)).let { response ->
            saveAccount(response)
            val pending = response.optBoolean("verification_required", false)
            Result(response.optBoolean("ok", false), if (pending) "RSS KIT verification email sent. Check your email to continue." else "RSS KIT account is ready.", pending, response.optBoolean("email_verified", false))
        }

    fun checkStatus(): Result {
        val email = prefs.getString(KEY_EMAIL, null).orEmpty()
        if (email.isBlank()) return Result(false, "Enter your RSS KIT account email.")
        val encoded = URLEncoder.encode(email, "UTF-8")
        val response = getJson("/api/v1/license/verification-status?email=$encoded&project_key=$PROJECT_KEY")
        saveAccount(response)
        val verified = response.optBoolean("email_verified", false)
        val pending = response.optBoolean("registered", false) && !verified
        val message = when {
            verified -> "RSS KIT account verified."
            pending -> "RSS KIT email verification is still pending."
            else -> "RSS KIT account was not found."
        }
        return Result(response.optBoolean("registered", false), message, pending, verified)
    }

    fun resendVerification(): Result = postJson("/api/v1/license/resend-verification", JSONObject()
        .put("display_name", prefs.getString(KEY_NAME, "").orEmpty()).put("email", prefs.getString(KEY_EMAIL, "").orEmpty())
        .put("project_key", PROJECT_KEY).put("device_id", deviceId())).let { response ->
        saveAccount(response); Result(response.optBoolean("ok", false), "RSS KIT verification email sent again.")
    }

    fun createSession(): Result {
        val email = prefs.getString(KEY_EMAIL, "").orEmpty()
        val appKey = prefs.getString(KEY_APP_KEY, "").orEmpty()
        if (email.isBlank() || appKey.isBlank()) return Result(false, "RSS KIT account details are incomplete. Register or verify first.")
        val response = postJson("/api/v1/license/session", JSONObject().put("email", email).put("project_key", PROJECT_KEY).put("device_id", deviceId()).put("app_key", appKey))
        saveAccount(response)
        val verified = response.optBoolean("email_verified", false)
        return Result(response.optBoolean("ok", false), if (verified) "RSS KIT sign-in successful." else "Verify your RSS KIT email first.", false, verified)
    }

    fun email() = prefs.getString(KEY_EMAIL, "").orEmpty()

    private fun saveAccount(json: JSONObject) {
        val editor = prefs.edit()
        json.optString("app_key").takeIf { it.isNotBlank() }?.let { editor.putString(KEY_APP_KEY, it) }
        json.optString("customer_id").takeIf { it.isNotBlank() }?.let { editor.putString(KEY_CUSTOMER_ID, it) }
        json.optLong("verification_expires_at", 0L).takeIf { it > 0L }?.let { editor.putLong(KEY_VERIFICATION_EXPIRES, it) }
        json.optString("email").takeIf { it.isNotBlank() }?.let { editor.putString(KEY_EMAIL, it) }
        json.optString("display_name").takeIf { it.isNotBlank() }?.let { editor.putString(KEY_NAME, it) }
        editor.apply()
    }

    private fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: ("android-" + android.os.Build.FINGERPRINT.hashCode())

    private fun getJson(path: String): JSONObject {
        val connection = open(path, "GET")
        return readResponse(connection)
    }
    private fun postJson(path: String, body: JSONObject): JSONObject {
        val connection = open(path, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use {
            it.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        return readResponse(connection)
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 15_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-RSS-App-Id", PROJECT_KEY)
        }

    private fun readResponse(connection: HttpURLConnection): JSONObject {
        connection.connect()
        val stream = if (connection.responseCode in 200..399) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        val json = runCatching { JSONObject(text) }.getOrElse { JSONObject().put("error", text.ifBlank { "RSS KIT account service unavailable" }) }
        if (connection.responseCode !in 200..399 && !json.has("error")) json.put("error", "RSS KIT account request failed (" + connection.responseCode + ")")
        connection.disconnect(); return json
    }
}