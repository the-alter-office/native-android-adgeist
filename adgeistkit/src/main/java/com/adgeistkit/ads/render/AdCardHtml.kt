package com.adgeistkit.ads.render

import android.content.res.AssetManager
import android.util.Log
import com.adgeistkit.constants.Logs

internal object AdCardHtml {

    private const val TAG = "AdCardHtml"

    private const val TEMPLATE_ASSET = "ad_view.html"
    private const val ADCARD_JS_ASSET = "adcard-beta.js"

    private const val JS_PLACEHOLDER = "{{ADCARD_JS}}"

    private const val FORM_FEED = 0x0C
    private const val LINE_SEPARATOR = 0x2028
    private const val PARAGRAPH_SEPARATOR = 0x2029

    @Volatile
    private var cachedShell: String? = null

    /**
     * Wraps [json] in a JavaScript string literal safe to splice into the source
     * handed to `WebView.evaluateJavascript`. U+2028 and U+2029 are line
     * terminators to a JS parser, so they have to go too.
     */
    fun quoteForJs(json: String): String {
        val out = StringBuilder(json.length + 16)
        out.append('"')
        for (ch in json) {
            val code = ch.code
            when {
                ch == '\\' -> out.append("\\\\")
                ch == '"' -> out.append("\\\"")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch == '\b' -> out.append("\\b")
                code == FORM_FEED -> out.append("\\f")
                code < 0x20 || code == LINE_SEPARATOR || code == PARAGRAPH_SEPARATOR ->
                    out.append(String.format("\\u%04x", code))
                else -> out.append(ch)
            }
        }
        out.append('"')
        return out.toString()
    }

    /**
     * @return the creative-independent shell page, or an empty string when the
     * assets cannot be read. Cached after the first successful read.
     */
    fun build(assets: AssetManager): String {
        cachedShell?.let { return it }

        return try {
            val template = assets.open(TEMPLATE_ASSET).bufferedReader().use { it.readText() }
            val adCardJs = assets.open(ADCARD_JS_ASSET).bufferedReader().use { it.readText() }
            template.replace(JS_PLACEHOLDER, adCardJs).also { cachedShell = it }
        } catch (e: Exception) {
            Log.e(TAG, Logs.Error.SHELL_ASSETS_LOAD_FAILED, e)
            ""
        }
    }
}
