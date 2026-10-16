package com.sabertheme.core.briefing

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Moodle said no; [code] is its `errorcode`, e.g. `invalidlogin`, `invalidtoken`. */
class MoodleException(val code: String, message: String) : Exception(message)

/**
 * Parses Moodle's web service replies. Field names follow Moodle's own
 * exporters (`calendar/classes/external/event_exporter_base.php`,
 * `event_action_exporter.php`); everything optional is optional here too.
 */
internal object MoodleMapper {

    /** `login/token.php`: `{"token": …}` or `{"error": …, "errorcode": …}`. */
    fun token(body: String): String {
        val reply = decode<TokenReply>(body)
        reply.token?.takeIf { it.isNotBlank() }?.let { return it }
        throw MoodleException(reply.errorcode ?: "notoken", reply.error ?: "Moodle gave no token")
    }

    /**
     * `core_calendar_get_action_events_by_timesort`: action events still
     * waiting on the user. Submitted work has no action event left; ones the
     * user can't act on yet (a quiz not open) are dropped.
     */
    fun deadlines(body: String): List<BriefingInput.Deadline> {
        failure(body)?.let { throw it }
        return decode<EventsReply>(body).events
            .filter { it.action?.actionable == true }
            .map { e ->
                BriefingInput.Deadline(
                    key = "moodle:${e.id}",
                    course = CourseNames.spoken(e.course?.fullname, e.course?.shortname),
                    task = taskName(e.name),
                    dueMs = e.timesort * 1000,
                    url = e.action?.url ?: e.url ?: e.viewurl,
                    code = CourseNames.code(e.course?.shortname, e.course?.fullname),
                )
            }
    }

    /** `core_webservice_get_site_info`: the signed-in user's id. */
    fun userId(body: String): Long {
        failure(body)?.let { throw it }
        return decode<SiteInfo>(body).userid ?: throw MoodleException("badreply", "Moodle gave no user")
    }

    /** `core_enrol_get_users_courses`: the module index, one row per code. */
    fun courses(body: String): List<Course> {
        failure(body)?.let { throw it }
        return decode<List<CourseReply>>(body).mapNotNull { c ->
            val code = CourseNames.code(c.shortname, c.fullname) ?: return@mapNotNull null
            Course(code, CourseNames.spoken(c.fullname, c.shortname))
        }.distinctBy { it.code }.sortedBy { it.code }
    }

    /** "Assignment 2 is due" → "Assignment 2"; Moodle appends what happens on the date. */
    fun taskName(name: String): String = name.replace(Suffix, "").trim()

    private val Suffix = Regex("""\s+(is due|closes|should be completed|is due to be graded|opens)\s*$""", RegexOption.IGNORE_CASE)

    /** REST errors come back as HTTP 200 with an `exception` object. */
    private fun failure(body: String): MoodleException? {
        val obj = runCatching { BriefingJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if ("exception" !in obj && "errorcode" !in obj) return null
        val code = obj["errorcode"]?.jsonPrimitive?.contentOrNull ?: "error"
        return MoodleException(code, obj["message"]?.jsonPrimitive?.contentOrNull ?: code)
    }

    private inline fun <reified T> decode(body: String): T = try {
        BriefingJson.decodeFromString<T>(body)
    } catch (e: SerializationException) {
        throw MoodleException("badreply", "Moodle sent something unexpected")
    } catch (e: IllegalArgumentException) {
        throw MoodleException("badreply", "Moodle sent something unexpected")
    }

    @Serializable
    private class TokenReply(val token: String? = null, val error: String? = null, val errorcode: String? = null)

    @Serializable
    private class EventsReply(val events: List<Event> = emptyList())

    @Serializable
    private class Event(
        val id: Long,
        val name: String = "",
        val timesort: Long = 0,
        val course: EventCourse? = null,
        val action: Action? = null,
        val url: String? = null,
        val viewurl: String? = null,
    )

    @Serializable
    private class EventCourse(val shortname: String? = null, val fullname: String? = null)

    @Serializable
    private class SiteInfo(val userid: Long? = null)

    @Serializable
    private class CourseReply(val shortname: String? = null, val fullname: String? = null)

    @Serializable
    private class Action(val actionable: Boolean = false, val url: String? = null)
}
