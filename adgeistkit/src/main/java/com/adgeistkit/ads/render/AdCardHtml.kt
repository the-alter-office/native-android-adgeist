package com.adgeistkit.ads.render

import android.content.res.AssetManager
import android.util.Log

/**
 * Builds the ad page from the bundled templates with the creative injected.
 * [escapeForJsString] is separate so it can be tested without an AssetManager.
 */
internal object AdCardHtml {

    private const val TAG = "AdCardHtml"

    private const val TEMPLATE_ASSET = "ad_view.html"
    private const val ADCARD_JS_ASSET = "adcard-beta.js"

    private const val JS_PLACEHOLDER = "{{ADCARD_JS}}"
    private const val CREATIVE_PLACEHOLDER = "{{CREATIVE_DATA}}"

    /**
     * Makes server-supplied [json] safe inside a JS string literal in the page.
     *
     * Order matters: the backslash is escaped first, because escaping the quote
     * before it would turn `\"` into `\\"` and reopen the literal. `</script` and
     * `<!--` are handled separately - they break out of the element rather than the
     * string, so no amount of quote escaping stops them.
     */
    fun escapeForJsString(json: String): String = json
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
        .replace("`", "\\`")
        .replace(Regex("(?i)</script")) { "<\\/script" }
        .replace(Regex("<!--")) { "<\\!--" }

    /** @return the full page, or an empty string when the assets cannot be read. */
    fun build(assets: AssetManager, creativeJson: String): String {
        return try {
            val template = assets.open(TEMPLATE_ASSET).bufferedReader().use { it.readText() }
            val adCardJs = assets.open(ADCARD_JS_ASSET).bufferedReader().use { it.readText() }
            template
                .replace(JS_PLACEHOLDER, adCardJs)
                .replace(CREATIVE_PLACEHOLDER, escapeForJsString(creativeJson))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ad view from assets", e)
            ""
        }
    }
}
