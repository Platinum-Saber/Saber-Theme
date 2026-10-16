package com.sabertheme.core.briefing

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CourseNamesTest {

    @Test
    fun `finds the module code in UoM short names`() {
        assertThat(CourseNames.code("In22-S7-EN3330 (129934)")).isEqualTo("EN3330")
        assertThat(CourseNames.code("Eng-CSE", "CS 4373 Cloud Computing")).isEqualTo("CS4373")
        assertThat(CourseNames.code("Eng-CSE")).isNull()
    }

    @Test
    fun `strips codes and ids from the spoken name`() {
        assertThat(CourseNames.spoken("In22-S7-EN3330 - Robotics and Automation", "In22-S7-EN3330 (129934)")).isEqualTo("Robotics and Automation")
        assertThat(CourseNames.spoken("CS4373: Cloud Computing (129879)", null)).isEqualTo("Cloud Computing")
        assertThat(CourseNames.spoken("Machine Vision - EN4554", null)).isEqualTo("Machine Vision")
        assertThat(CourseNames.spoken("In2022 Field Familiarization Program", "IN2022")).isEqualTo("Field Familiarization Program")
    }

    @Test
    fun `a full name that is only the code falls back to the code`() {
        assertThat(CourseNames.spoken("In22-S7-EN3330 (129934)", "In22-S7-EN3330 (129934)")).isEqualTo("EN3330")
    }

    @Test
    fun `a name without a code is kept`() {
        assertThat(CourseNames.spoken("Department of Computer Science", "Eng-CSE")).isEqualTo("Department of Computer Science")
    }
}
