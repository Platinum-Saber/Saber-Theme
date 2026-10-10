package com.sabertheme.core.widgetdata

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads at most [limit] bytes, failing past it. Other apps' providers and the
 * network decide how much they send; reading it all could run Home out of
 * memory, and Home restarts into the same input.
 */
fun InputStream.readCapped(limit: Int): ByteArray {
    val out = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        total += n
        if (total > limit) throw IOException("Input larger than $limit bytes")
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

/**
 * Lets one event through per [windowMs] (wall clock); for work that exported
 * receivers trigger, which any app can broadcast at.
 */
class Coalescer(private val windowMs: Long) {
    private var last = Long.MIN_VALUE

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        if (last != Long.MIN_VALUE && nowMs - last in 0 until windowMs) return false
        last = nowMs
        return true
    }
}
