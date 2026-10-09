package com.sabertheme.feature.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.SharingStarted

/** Emits once on registration (the sticky intent, if any) and then for every broadcast. */
internal fun Context.broadcasts(vararg actions: String): Flow<Intent?> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(intent)
        }
    }
    val filter = IntentFilter().apply { actions.forEach(::addAction) }
    val sticky = ContextCompat.registerReceiver(this@broadcasts, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    send(sticky)
    awaitClose { unregisterReceiver(receiver) }
}

/** Keep sources alive across short gaps (page swipes, config changes) without leaking receivers. */
internal val WhileShown = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0)
