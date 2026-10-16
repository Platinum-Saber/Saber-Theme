package com.sabertheme.launcher

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.net.toUri
import com.sabertheme.core.briefing.BriefingItem
import com.sabertheme.core.briefing.ItemKind

/** Where tapping a briefing row in her cloud goes. */
internal fun Context.openBriefingItem(item: BriefingItem) {
    val intent = when (item.kind) {
        ItemKind.Deadline -> item.link?.takeIf { it.startsWith("https://") }?.let { Intent(Intent.ACTION_VIEW, it.toUri()) }
        ItemKind.MissedCall -> item.link?.let { Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(it)}".toUri()) }
        ItemKind.Event -> item.key.split(':').getOrNull(1)?.toLongOrNull()?.let {
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, it))
        }
        ItemKind.Mail, ItemKind.Delivery -> packageManager.getLaunchIntentForPackage(GMAIL)
    } ?: return
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Log.w("SaberBrain", "No app for a ${item.kind} row")
    }
}

/** Ringer on and no Do Not Disturb, for [com.sabertheme.core.briefing.SpeakGuard]. */
internal fun Context.ringerNormal(): Boolean =
    getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL

internal fun Context.interruptionsAll(): Boolean =
    getSystemService(NotificationManager::class.java).currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL

private const val GMAIL = "com.google.android.gm"
