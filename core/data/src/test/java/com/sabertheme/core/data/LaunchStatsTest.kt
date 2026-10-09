package com.sabertheme.core.data

import com.google.common.truth.Truth.assertThat
import com.sabertheme.core.model.AppKey
import org.junit.Test

class LaunchStatsTest {
    private fun key(pkg: String, user: Long = 0) = AppKey(pkg, "$pkg.Main", user)

    @Test
    fun roundTrips() {
        val counts = mapOf(key("a") to 3, key("b", user = 10) to 1)
        assertThat(LaunchStats.decode(LaunchStats.encode(counts))).isEqualTo(counts)
    }

    @Test
    fun decodeSkipsBrokenLines() {
        val good = "${key("a").encode()}\t2"
        assertThat(LaunchStats.decode("$good\nnot a key\t4\n${key("b").encode()}\tx\n${key("c").encode()}\t0"))
            .containsExactly(key("a"), 2)
        assertThat(LaunchStats.decode(null)).isEmpty()
    }

    @Test
    fun encodeKeepsTheMostLaunched() {
        val counts = (1..LaunchStats.MAX_ENTRIES + 5).associate { key("p$it") to it }
        val kept = LaunchStats.decode(LaunchStats.encode(counts))
        assertThat(kept).hasSize(LaunchStats.MAX_ENTRIES)
        assertThat(kept).doesNotContainKey(key("p1"))
    }

    @Test
    fun topOrdersByCountThenKey() {
        val counts = mapOf(key("b") to 5, key("a") to 5, key("c") to 9, key("d") to 1)
        assertThat(LaunchStats.top(counts, 3)).containsExactly(key("c"), key("a"), key("b")).inOrder()
    }
}
