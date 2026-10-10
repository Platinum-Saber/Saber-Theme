package com.sabertheme.core.widgetdata

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class LimitsTest {
    private fun stream(size: Int) = ByteArrayInputStream(ByteArray(size) { it.toByte() })

    @Test
    fun readsUpToTheLimit() {
        assertThat(stream(0).readCapped(10)).isEmpty()
        assertThat(stream(9).readCapped(10)).hasLength(9)
        assertThat(stream(10).readCapped(10)).isEqualTo(ByteArray(10) { it.toByte() })
        assertThat(stream(20_000).readCapped(20_000)).hasLength(20_000)
    }

    @Test
    fun failsPastTheLimit() {
        assertThrows(IOException::class.java) { stream(11).readCapped(10) }
        assertThrows(IOException::class.java) { stream(100_000).readCapped(64 * 1024) }
    }

    @Test
    fun coalescerLetsOneThroughPerWindow() {
        val c = Coalescer(2_000)
        assertThat(c.tryAcquire(10_000)).isTrue()
        assertThat(c.tryAcquire(10_500)).isFalse()
        assertThat(c.tryAcquire(11_999)).isFalse()
        assertThat(c.tryAcquire(12_000)).isTrue()
        // A clock set backwards doesn't block refreshes.
        assertThat(c.tryAcquire(5_000)).isTrue()
    }
}
