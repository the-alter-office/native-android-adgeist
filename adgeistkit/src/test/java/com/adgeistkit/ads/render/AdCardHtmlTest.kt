package com.adgeistkit.ads.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the escaping applied to the creative payload before it is embedded in
 * the ad page. The payload is server-supplied and lands inside a JS string
 * literal, so anything able to terminate that literal - or the enclosing script
 * element or HTML comment - must not survive.
 */
class AdCardHtmlTest {

    private fun escape(input: String) = AdCardHtml.escapeForJsString(input)

    // ---------------------------------------------------------------------
    // Breaking out of the script element
    // ---------------------------------------------------------------------

    @Test
    fun `a closing script tag cannot survive`() {
        val escaped = escape("""{"title":"</script><img src=x onerror=alert(1)>"}""")

        assertFalse("raw </script must not reach the page", escaped.contains("</script"))
        assertTrue(escaped.contains("<\\/script"))
    }

    @Test
    fun `closing script tag detection is case insensitive`() {
        for (variant in listOf("</script", "</SCRIPT", "</Script", "</sCrIpT")) {
            val escaped = escape(variant)
            assertFalse(
                "variant '$variant' leaked through",
                escaped.contains("</script", ignoreCase = true) && !escaped.contains("<\\/")
            )
            assertTrue("variant '$variant' was not neutralised", escaped.contains("<\\/"))
        }
    }

    @Test
    fun `an html comment opener cannot survive`() {
        val escaped = escape("""{"title":"<!-- hiding"}""")

        assertFalse(escaped.contains("<!--"))
        assertTrue(escaped.contains("<\\!--"))
    }

    // ---------------------------------------------------------------------
    // Breaking out of the string literal
    // ---------------------------------------------------------------------

    @Test
    fun `double quotes are escaped`() {
        assertEquals("""\"quoted\"""", escape(""""quoted""""))
    }

    @Test
    fun `backslashes are escaped before anything else`() {
        // Escaping the backslash after the quote would produce \\" and reopen the
        // literal, so ordering here is load-bearing
        assertEquals("""\\\"""", escape("""\""""))
    }

    @Test
    fun `backticks are escaped so template literals cannot be opened`() {
        assertEquals("""\`${'$'}{payload}\`""", escape("""`${'$'}{payload}`"""))
    }

    @Test
    fun `newlines carriage returns and tabs are escaped`() {
        assertEquals("""a\nb\rc\td""", escape("a\nb\rc\td"))
    }

    @Test
    fun `no raw literal breakers remain in a hostile payload`() {
        val hostile = """{"t":"a\"b`c
</script><!--"}"""

        val escaped = escape(hostile)

        // Control characters are replaced outright, so none may remain
        assertFalse("raw newline remains", escaped.contains('\n'))
        assertFalse("raw carriage return remains", escaped.contains('\r'))
        assertFalse("raw </script remains", escaped.contains("</script", ignoreCase = true))
        assertFalse("raw <!-- remains", escaped.contains("<!--"))

        // Quotes and backticks survive as characters but must always be escaped,
        // so neither can terminate the literal it sits in
        assertAllEscaped(escaped, '"')
        assertAllEscaped(escaped, '`')
    }

    /** Asserts every occurrence of [char] in [escaped] is preceded by a backslash. */
    private fun assertAllEscaped(escaped: String, char: Char) {
        escaped.forEachIndexed { i, c ->
            if (c == char) {
                assertTrue(
                    "unescaped '$char' at index $i in: $escaped",
                    i > 0 && escaped[i - 1] == '\\'
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Ordinary payloads are left usable
    // ---------------------------------------------------------------------

    @Test
    fun `a benign payload keeps its structure`() {
        val escaped = escape("""{"title":"Hello","width":320}""")

        assertEquals("""{\"title\":\"Hello\",\"width\":320}""", escaped)
    }

    @Test
    fun `an empty payload is handled`() {
        assertEquals("", escape(""))
    }

    @Test
    fun `urls survive intact`() {
        val escaped = escape("https://cdn.example.com/a_b-c.png?x=1&y=2")

        assertEquals("https://cdn.example.com/a_b-c.png?x=1&y=2", escaped)
    }
}
