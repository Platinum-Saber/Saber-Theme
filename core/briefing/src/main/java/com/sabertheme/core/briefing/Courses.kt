package com.sabertheme.core.briefing

import kotlinx.serialization.Serializable

/** One row of the module index: the code Moodle files it under, the name she says. */
@Serializable
data class Course(val code: String, val name: String)

/**
 * Turns Moodle's course names into a module code and a speakable name.
 * UoM short names look like "In22-S7-EN3330 (129934)"; full names carry the
 * same code plus the title. She says the title, never the code.
 */
internal object CourseNames {

    private val Code = Regex("""([A-Z]{2,4})\s?(\d{4})""")
    private val TrailingId = Regex("""\s*\(\d+\)\s*$""")
    // Case-insensitive: some full names start "In2022 …" for the IN2022 code.
    private val LeadingCode = Regex("""^[^\s]*[A-Z]{2,4}\s?\d{4}[^\s]*\s*[-–:|]?\s*""", RegexOption.IGNORE_CASE)
    private val TrailingCode = Regex("""\s*[-–:|]?\s*[^\s]*[A-Z]{2,4}\d{4}[^\s]*$""", RegexOption.IGNORE_CASE)
    private val Edges = charArrayOf(' ', '-', '–', ':', '|')

    /** "In22-S7-EN3330 (129934)" → "EN3330"; null when no code is in either name. */
    fun code(vararg names: String?): String? =
        names.firstNotNullOfOrNull { name -> name?.let { Code.find(it) }?.let { it.groupValues[1] + it.groupValues[2] } }

    /** "In22-S7-EN3330 - Robotics (129934)" → "Robotics"; falls back to the code, then the raw name. */
    fun spoken(fullname: String?, shortname: String?): String {
        for (raw in listOfNotNull(fullname, shortname)) {
            val name = raw.replace(TrailingId, "").replace(LeadingCode, "").replace(TrailingCode, "").trim(*Edges)
            if (name.isNotBlank() && Code.find(name) == null) return name
        }
        return code(shortname, fullname) ?: fullname?.ifBlank { null } ?: shortname.orEmpty()
    }
}
