package com.sabertheme.core.widgetdata

/** Which app the Media widget shows. Pure Kotlin; see MediaPickerTest. */
object MediaPicker {
    data class Session(val packageName: String, val playing: Boolean)

    /** [shown] is the app the widget controls; [pick] is the user's choice that still holds. */
    data class Result(val shown: String?, val pick: String?)

    /**
     * [sessions] come in system order (most recent first). A user [pick] holds,
     * even when that app has no session (the widget then offers to resume it),
     * until another app starts playing that wasn't playing before ([wasPlaying]).
     * Without a pick the playing session wins, then the most recent one.
     */
    fun pick(sessions: List<Session>, pick: String?, wasPlaying: Set<String>): Result {
        val newcomer = sessions.any { it.playing && it.packageName != pick && it.packageName !in wasPlaying }
        val kept = pick.takeUnless { newcomer }
        val shown = kept ?: (sessions.firstOrNull { it.playing } ?: sessions.firstOrNull())?.packageName
        return Result(shown, kept)
    }
}
