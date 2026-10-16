package com.sabertheme.core.briefing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Missed calls from `CallLog` (`READ_CALL_LOG`, asked from Settings → Briefing). */
@Singleton
class CallLogSource @Inject constructor(@ApplicationContext private val context: Context) {

    fun granted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /** Missed calls since [sinceMs] the user hasn't seen in the dialer yet; empty without the grant. */
    suspend fun missedSince(sinceMs: Long): List<BriefingInput.MissedCall> = withContext(Dispatchers.IO) {
        if (!granted()) return@withContext emptyList()
        buildList {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1 AND ${CallLog.Calls.DATE} >= ?",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString(), sinceMs.toString()),
                "${CallLog.Calls.DATE} DESC LIMIT $MAX_CALLS",
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(1).orEmpty().ifBlank { "Unknown" }
                    val name = c.getString(2)?.takeIf { it.isNotBlank() }
                    add(BriefingInput.MissedCall("call:${c.getLong(0)}", name ?: number, number, c.getLong(3)))
                }
            }
        }
    }

    private companion object {
        const val MAX_CALLS = 30
    }
}
