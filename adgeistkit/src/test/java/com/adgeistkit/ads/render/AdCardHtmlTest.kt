package com.adgeistkit.ads.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the quoting applied to the creative payload before it is spliced into
 * the JS source handed to evaluateJavascript. The payload is server-supplied
 * and lands inside a JS string literal, so nothing able to terminate that
 * literal may survive.
 */
class AdCardHtmlTest {

    private companion object {
        /** The two characters a JS unicode escape opens with. */
        const val ESC = "\\" + "u"

        const val NUL = 0x00
        const val BACKSPACE = 0x08
        const val FORM_FEED = 0x0C
        const val UNIT_SEPARATOR = 0x1F
        const val LINE_SEPARATOR = 0x2028
        const val PARAGRAPH_SEPARATOR = 0x2029
    }

    private fun quote(input: String) = AdCardHtml.quoteForJs(input)

    /** The literal body, without the quotes quoteForJs wraps around it. */
    private fun body(input: String) = quote(input).let { it.substring(1, it.length - 1) }

    // ---------------------------------------------------------------------
    // Breaking out of the string literal
    // ---------------------------------------------------------------------

    @Test
    fun `the result is wrapped in double quotes`() {
        assertEquals("\"plain\"", quote("plain"))
    }

    @Test
    fun `double quotes are escaped`() {
        assertEquals("""\"quoted\"""", body(""""quoted""""))
    }

    @Test
    fun `backslashes are escaped before anything else`() {
        // Escaping the backslash after the quote would produce \\" and reopen the
        // literal, so ordering here is load-bearing
        assertEquals("""\\\"""", body("""\""""))
    }

    @Test
    fun `newlines carriage returns and tabs are escaped`() {
        assertEquals("""a\nb\rc\td""", body("a\nb\rc\td"))
    }

    @Test
    fun `backspace and form feed are escaped`() {
        assertEquals(
            """a\bb\fc""",
            body("a${BACKSPACE.toChar()}b${FORM_FEED.toChar()}c")
        )
    }

    @Test
    fun `line and paragraph separators are escaped`() {
        // U+2028 and U+2029 terminate a line to a JS parser but not to a JSON
        // parser, so a raw one would break the literal open
        assertEquals("a${ESC}2028b", body("a${LINE_SEPARATOR.toChar()}b"))
        assertEquals("a${ESC}2029b", body("a${PARAGRAPH_SEPARATOR.toChar()}b"))
    }

    @Test
    fun `other control characters are escaped`() {
        assertEquals(
            "a${ESC}0000b${ESC}001fc",
            body("a${NUL.toChar()}b${UNIT_SEPARATOR.toChar()}c")
        )
    }

    @Test
    fun `no raw literal breakers remain in a hostile payload`() {
        val hostile = "{\"t\":\"a\\\"b`c\n${LINE_SEPARATOR.toChar()}\"}"

        val quoted = quote(hostile)

        // Control characters are replaced outright, so none may remain
        assertFalse("raw newline remains", quoted.contains('\n'))
        assertFalse("raw carriage return remains", quoted.contains('\r'))
        assertFalse(
            "raw line separator remains",
            quoted.contains(LINE_SEPARATOR.toChar())
        )

        // The only unescaped quotes may be the wrapping pair
        assertAllEscaped(quoted.substring(1, quoted.length - 1), '"')
    }

    /** Asserts every occurrence of [char] in [quoted] is preceded by a backslash. */
    private fun assertAllEscaped(quoted: String, char: Char) {
        quoted.forEachIndexed { i, c ->
            if (c == char) {
                assertTrue(
                    "unescaped '$char' at index $i in: $quoted",
                    i > 0 && quoted[i - 1] == '\\'
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Ordinary payloads are left usable
    // ---------------------------------------------------------------------

    @Test
    fun `a benign payload keeps its structure`() {
        assertEquals(
            """{\"title\":\"Hello\",\"width\":320}""",
            body("""{"title":"Hello","width":320}""")
        )
    }

    @Test
    fun `an empty payload is handled`() {
        assertEquals("\"\"", quote(""))
    }

    @Test
    fun `urls survive intact`() {
        assertEquals(
            "https://cdn.example.com/a_b-c.png?x=1&y=2",
            body("https://cdn.example.com/a_b-c.png?x=1&y=2")
        )
    }

    @Test
    fun `backticks need no escaping outside a template literal`() {
        assertEquals("""`${'$'}{payload}`""", body("""`${'$'}{payload}`"""))
    }
}
