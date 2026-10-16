package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MoodleMapperTest {

    // Shaped like core_calendar_get_action_events_by_timesort, trimmed to the fields read.
    private val events = """
        {"events":[
          {"id":41,"name":"Assignment 2 - Offline-first app is due","modulename":"assign","timesort":1792259940,
           "course":{"id":7,"shortname":"CS3042","fullname":"Mobile Computing"},
           "action":{"name":"Add submission","url":"https://moodle.example.ac.lk/mod/assign/view.php?id=9","itemcount":1,"actionable":true},
           "url":"https://moodle.example.ac.lk/mod/assign/view.php?id=9","isactionevent":true,"overdue":false},
          {"id":42,"name":"Quiz 4 closes","modulename":"quiz","timesort":1792429200,
           "course":{"id":8,"shortname":"","fullname":"Machine Learning"},
           "action":{"name":"Attempt quiz now","actionable":true},
           "viewurl":"https://moodle.example.ac.lk/calendar/view.php?view=day"},
          {"id":43,"name":"Quiz 5 opens","modulename":"quiz","timesort":1792900000,
           "course":{"id":8,"shortname":"CS3051"},
           "action":{"name":"Attempt quiz now","actionable":false}},
          {"id":44,"name":"Lab booking","timesort":1792900000}
        ],"firstid":41,"lastid":44}
    """.trimIndent()

    @Test
    fun `keeps actionable events, with course, task and due time`() {
        val deadlines = MoodleMapper.deadlines(events)
        assertThat(deadlines.map { it.key }).containsExactly("moodle:41", "moodle:42").inOrder()
        val first = deadlines.first()
        assertThat(first.course).isEqualTo("Mobile Computing")
        assertThat(first.code).isEqualTo("CS3042")
        assertThat(first.task).isEqualTo("Assignment 2 - Offline-first app")
        assertThat(first.dueMs).isEqualTo(1_792_259_940_000L)
        assertThat(first.url).isEqualTo("https://moodle.example.ac.lk/mod/assign/view.php?id=9")
    }

    @Test
    fun `falls back to the full course name and the view url`() {
        val quiz = MoodleMapper.deadlines(events)[1]
        assertThat(quiz.course).isEqualTo("Machine Learning")
        assertThat(quiz.task).isEqualTo("Quiz 4")
        assertThat(quiz.url).isEqualTo("https://moodle.example.ac.lk/calendar/view.php?view=day")
    }

    @Test
    fun `enrolled courses become the code to name index`() {
        val body = """[
          {"id":1,"shortname":"In22-S7-EN3330 (129934)","fullname":"In22-S7-EN3330 - Robotics and Automation"},
          {"id":2,"shortname":"In22-S7-CS4373 (129879)","fullname":"CS4373 Cloud Computing"},
          {"id":3,"shortname":"Eng-CSE","fullname":"Department of Computer Science and Engineering"}
        ]"""
        assertThat(MoodleMapper.courses(body)).containsExactly(
            Course("CS4373", "Cloud Computing"),
            Course("EN3330", "Robotics and Automation"),
        ).inOrder()
        assertThat(MoodleMapper.userId("""{"sitename":"UoM","userid":4242}""")).isEqualTo(4242)
    }

    @Test
    fun `an exception reply throws with its error code`() {
        val body = """{"exception":"moodle_exception","errorcode":"invalidtoken","message":"Invalid token - token not found"}"""
        val e = assertThrows(MoodleException::class.java) { MoodleMapper.deadlines(body) }
        assertThat(e.code).isEqualTo("invalidtoken")
    }

    @Test
    fun `token reply gives the token, a login error throws`() {
        assertThat(MoodleMapper.token("""{"token":"abc123","privatetoken":null}""")).isEqualTo("abc123")
        val e = assertThrows(MoodleException::class.java) {
            MoodleMapper.token("""{"error":"Invalid login, please try again","errorcode":"invalidlogin","stacktrace":null}""")
        }
        assertThat(e.code).isEqualTo("invalidlogin")
        assertThat(e.message).isEqualTo("Invalid login, please try again")
    }

    @Test
    fun `garbage is a bad reply, not a crash`() {
        val e = assertThrows(MoodleException::class.java) { MoodleMapper.token("<html>Maintenance</html>") }
        assertThat(e.code).isEqualTo("badreply")
    }

    @Test
    fun `site addresses become https, plain http is refused`() {
        assertThat(MoodleClient.normalise(" moodle.uni.ac.lk/ ")).isEqualTo("https://moodle.uni.ac.lk")
        assertThat(MoodleClient.normalise("https://lms.uni.ac.lk/moodle")).isEqualTo("https://lms.uni.ac.lk/moodle")
        assertThrows(IllegalArgumentException::class.java) { MoodleClient.normalise("http://moodle.uni.ac.lk") }
    }
}
