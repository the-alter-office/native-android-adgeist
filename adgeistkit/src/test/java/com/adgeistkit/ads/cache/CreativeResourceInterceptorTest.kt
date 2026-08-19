package com.adgeistkit.ads.cache

import com.adgeistkit.ads.cache.CreativeResourceInterceptor.Range
import com.adgeistkit.ads.cache.CreativeResourceInterceptor.parseRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Range parsing decides whether a cached video plays and seeks, so every form a
 * player can send is pinned here.
 */
class CreativeResourceInterceptorTest {

    private val fileLength = 1000L

    @Test
    fun `no header serves the whole file`() {
        assertTrue(parseRange(null, fileLength) is Range.Absent)
    }

    @Test
    fun `open ended range runs to the last byte`() {
        val range = parseRange("bytes=0-", fileLength)
        assertEquals(Range.Slice(0L, 999L), range)
    }

    @Test
    fun `closed range is honoured exactly`() {
        assertEquals(Range.Slice(200L, 499L), parseRange("bytes=200-499", fileLength))
    }

    @Test
    fun `range past the end of the file is clamped to the last byte`() {
        assertEquals(Range.Slice(900L, 999L), parseRange("bytes=900-5000", fileLength))
    }

    @Test
    fun `suffix range returns the tail - how players find a trailing mp4 index`() {
        assertEquals(Range.Slice(800L, 999L), parseRange("bytes=-200", fileLength))
    }

    @Test
    fun `suffix range longer than the file starts at zero`() {
        assertEquals(Range.Slice(0L, 999L), parseRange("bytes=-5000", fileLength))
    }

    @Test
    fun `start at or past the end is unsatisfiable`() {
        assertTrue(parseRange("bytes=1000-", fileLength) is Range.Unsatisfiable)
        assertTrue(parseRange("bytes=1500-1600", fileLength) is Range.Unsatisfiable)
    }

    @Test
    fun `end before start is unsatisfiable`() {
        assertTrue(parseRange("bytes=500-200", fileLength) is Range.Unsatisfiable)
    }

    @Test
    fun `multi range requests honour the first range only`() {
        assertEquals(Range.Slice(0L, 99L), parseRange("bytes=0-99,200-299", fileLength))
    }

    @Test
    fun `header casing and padding are tolerated`() {
        assertEquals(Range.Slice(10L, 20L), parseRange("  BYTES=10-20 ", fileLength))
    }

    @Test
    fun `malformed headers fall back to the whole file rather than failing`() {
        assertTrue(parseRange("items=0-10", fileLength) is Range.Absent)
        assertTrue(parseRange("bytes=abc-def", fileLength) is Range.Absent)
        assertTrue(parseRange("bytes=", fileLength) is Range.Absent)
    }

    @Test
    fun `an empty file has no range to serve`() {
        assertTrue(parseRange("bytes=0-", 0L) is Range.Absent)
    }
}
