package com.sabertheme.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoFramingTest {
    // A 16:9 photo on a tall phone screen.
    private val pw = 1920f
    private val ph = 1080f
    private val sw = 1080f
    private val sh = 2340f

    @Test
    fun defaultFillsAndCentres() {
        val (l, t, r, b) = PhotoFraming().destination(pw, ph, sw, sh).toList()
        assertThat(b - t).isWithin(0.5f).of(sh)
        assertThat((l + r) / 2f).isWithin(0.5f).of(sw / 2f)
        assertThat(l).isLessThan(0f)
    }

    @Test
    fun panIsClampedSoTheEdgeNeverShowsAtFill() {
        val f = PhotoFraming(cx = 0f).clamp(pw, ph, sw, sh)
        val (l, _, r, _) = f.destination(pw, ph, sw, sh).toList()
        assertThat(l).isWithin(0.5f).of(0f)
        assertThat(r).isGreaterThan(sw)
    }

    @Test
    fun minZoomShowsTheWholePhoto() {
        val min = PhotoFraming.minZoom(pw, ph, sw, sh)
        val f = PhotoFraming(zoom = 0.01f).clamp(pw, ph, sw, sh)
        assertThat(f.zoom).isWithin(1e-4f).of(min)
        val (l, _, r, _) = f.destination(pw, ph, sw, sh).toList()
        assertThat(r - l).isWithin(0.5f).of(sw)
        assertThat(f.showsFill(pw, ph, sw, sh)).isTrue()
        assertThat(f.cy).isEqualTo(0.5f)
    }

    @Test
    fun zoomedOutPhotoSlidesButStaysFullyOnScreen() {
        val min = PhotoFraming.minZoom(pw, ph, sw, sh)
        val f = PhotoFraming(cy = -5f, zoom = min).clamp(pw, ph, sw, sh)
        val (_, t, _, b) = f.destination(pw, ph, sw, sh).toList()
        assertThat(b).isWithin(0.5f).of(sh) // pushed down to the bottom edge, not past it
        assertThat(t).isGreaterThan(0f)
    }

    @Test
    fun zoomIsCappedAndPanStaysInsideWhenZoomedIn() {
        val f = PhotoFraming(cx = 0.99f, cy = 0.01f, zoom = 10f).clamp(pw, ph, sw, sh)
        assertThat(f.zoom).isEqualTo(PhotoFraming.MAX_ZOOM)
        val (l, t, r, b) = f.destination(pw, ph, sw, sh).toList()
        assertThat(r).isWithin(0.5f).of(sw)
        assertThat(t).isWithin(0.5f).of(0f)
        assertThat(l).isLessThan(0f)
        assertThat(b).isGreaterThan(sh)
    }

    @Test
    fun codecRoundTripsAndRejectsJunk() {
        val f = PhotoFraming(0.25f, 0.75f, 0.6f)
        assertThat(PhotoFraming.decode(PhotoFraming.encode(f))).isEqualTo(f)
        assertThat(PhotoFraming.decode(null)).isNull()
        assertThat(PhotoFraming.decode("a,b")).isNull()
        assertThat(PhotoFraming.decode("1,2,NaN")).isNull()
    }
}
