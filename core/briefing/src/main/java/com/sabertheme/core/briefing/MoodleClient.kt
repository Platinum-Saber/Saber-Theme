package com.sabertheme.core.briefing

import android.content.Context
import com.sabertheme.core.widgetdata.readCapped
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** The Moodle site she reads deadlines from; the password is never kept. */
@Serializable
data class MoodleAccount(val site: String, val username: String, val token: String)

@Serializable
internal data class BriefingSecrets(val moodle: MoodleAccount? = null)

/** Device-only secrets, shared with `:brain`. */
@Singleton
class SecretsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = jsonStore(context, "briefing_secrets", BriefingSecrets.serializer(), BriefingSecrets())

    val moodle: Flow<MoodleAccount?> = store.data.map { it.moodle }

    internal suspend fun setMoodle(account: MoodleAccount?) {
        store.updateData { it.copy(moodle = account) }
    }
}

/**
 * Moodle's mobile web service: the same token the official app uses, so it
 * works wherever the Moodle app does. Every call is an HTTPS POST, which keeps
 * the password and token out of URLs and logs.
 */
@Singleton
class MoodleClient @Inject constructor(private val secrets: SecretsStore) {

    /** Signs in once and keeps only the token. Throws [MoodleException] or [IOException]. */
    suspend fun connect(site: String, username: String, password: String) {
        val base = normalise(site)
        val body = post("$base/login/token.php", mapOf("username" to username, "password" to password, "service" to SERVICE))
        secrets.setMoodle(MoodleAccount(base, username.trim(), MoodleMapper.token(body)))
    }

    suspend fun disconnect() = secrets.setMoodle(null)

    /**
     * Action events due in the next [days]; null when not connected. An
     * expired token disconnects, so Settings shows it needs signing in again.
     */
    suspend fun deadlines(nowMs: Long, days: Int = 14): List<BriefingInput.Deadline>? {
        val account = secrets.moodle.first() ?: return null
        val body = post(
            "${account.site}/webservice/rest/server.php",
            mapOf(
                "wstoken" to account.token,
                "wsfunction" to "core_calendar_get_action_events_by_timesort",
                "moodlewsrestformat" to "json",
                "timesortfrom" to (nowMs / 1000).toString(),
                "timesortto" to (nowMs / 1000 + days * 86_400L).toString(),
                "limitnum" to "30",
            ),
        )
        return try {
            MoodleMapper.deadlines(body)
        } catch (e: MoodleException) {
            if (e.code == "invalidtoken" || e.code == "accessexception") disconnect()
            throw e
        }
    }

    /** Every module the user is enrolled in, for the code → name index; null when not connected. */
    suspend fun courses(): List<Course>? {
        val account = secrets.moodle.first() ?: return null
        val userId = MoodleMapper.userId(call(account, "core_webservice_get_site_info", emptyMap()))
        return MoodleMapper.courses(call(account, "core_enrol_get_users_courses", mapOf("userid" to userId.toString())))
    }

    private suspend fun call(account: MoodleAccount, function: String, args: Map<String, String>): String =
        post(
            "${account.site}/webservice/rest/server.php",
            mapOf("wstoken" to account.token, "wsfunction" to function, "moodlewsrestformat" to "json") + args,
        )

    private suspend fun post(url: String, form: Map<String, String>): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val payload = form.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
            connection.outputStream.use { it.write(payload.encodeToByteArray()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.use { String(it.readCapped(MAX_BODY_BYTES), Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val SERVICE = "moodle_mobile_app"
        private const val MAX_BODY_BYTES = 512 * 1024

        /** "moodle.uni.ac.lk/" → "https://moodle.uni.ac.lk"; plain http is refused. */
        internal fun normalise(site: String): String {
            val trimmed = site.trim().trimEnd('/')
            require(!trimmed.startsWith("http://", ignoreCase = true)) { "Moodle must use https" }
            val url = if (trimmed.startsWith("https://", ignoreCase = true)) trimmed else "https://$trimmed"
            require(url.length > "https://".length && ' ' !in url) { "Not a Moodle address" }
            return url
        }
    }
}
