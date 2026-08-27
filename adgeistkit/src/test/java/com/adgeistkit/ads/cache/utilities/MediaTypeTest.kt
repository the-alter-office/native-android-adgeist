package com.adgeistkit.ads.cache.utilities

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun `extension comes from the url path, not its query string`() {
        assertEquals("jpg", MediaType.extensionOf("https://cdn.test/a/book.jpg"))
        assertEquals("mp4", MediaType.extensionOf("https://cdn.test/a/clip.MP4?sig=xyz&v=2"))
        assertEquals("jpeg", MediaType.extensionOf("https://cdn.test/a/thumb-book.jpeg#frag"))
        assertEquals("", MediaType.extensionOf("https://cdn.test/a/no-extension"))
        assertEquals("", MediaType.extensionOf("https://cdn.test/a.very-long-suffix"))
    }
}
