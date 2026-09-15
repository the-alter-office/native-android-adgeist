package com.examplenativeandroidapp

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdView
import com.adgeistkit.ads.BaseAdView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R8 regression guard for the WebView JS bridge.
 *
 * Creative JavaScript calls the bridge by hard-coded method name
 * (window.Android.postMessage etc. — see assets/ad_view.html and
 * adcard-beta.js), so the @JavascriptInterface method names on JsBridge are
 * a wire contract. Like AdModelR8Test, this runs against the minified release
 * variant: if consumer-rules.pro stops keeping these methods, R8 renames or
 * strips them and the creative's calls silently do nothing in production.
 *
 * JsBridge is internal to the SDK, so it is resolved by name here — which is
 * also how the WebView reaches it at runtime.
 */
@RunWith(AndroidJUnit4::class)
class JsBridgeR8Test {

    private val jsBridgeClass: Class<*> =
        Class.forName("com.adgeistkit.ads.JsBridge")

    /**
     * The four bridge methods must keep their exact names and their
     * @JavascriptInterface annotation (WebView only exposes annotated methods)
     * on the R8-processed class. getMethod throws if a name was renamed;
     * reflection strings are never rewritten by R8, so this probes the real
     * runtime contract.
     */
    @Test
    fun jsBridgeMethods_keepNamesAndAnnotationUnderR8() {
        val expected = mapOf<String, Array<Class<*>>>(
            "postMessage" to arrayOf(String::class.java),
            "postVideoStatus" to arrayOf(String::class.java),
            "reportOverflow" to arrayOf(
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ),
            "showAd" to emptyArray()
        )

        for ((name, params) in expected) {
            val method = try {
                jsBridgeClass.getMethod(name, *params)
            } catch (e: NoSuchMethodException) {
                throw AssertionError(
                    "JsBridge.$name was renamed or removed by R8 — " +
                        "creative JS can no longer reach the bridge", e
                )
            }
            assertTrue(
                "JsBridge.$name lost @JavascriptInterface (WebView won't expose it)",
                method.isAnnotationPresent(JavascriptInterface::class.java)
            )
        }
    }

    /**
     * End-to-end: a real WebView dispatches JS calls to a real [JsBridge] by
     * name, exactly like a creative does. Each call is wrapped in try/catch on
     * the JS side; a renamed/stripped method surfaces as "err: TypeError...".
     */
    @Test
    fun jsBridgeMethods_dispatchFromWebViewUnderR8() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        var webView: WebView? = null
        val pageLoaded = CountDownLatch(1)

        instrumentation.runOnMainSync {
            // JsBridge -> AdActivity requires the SDK singleton.
            AdgeistCore.initialize(context)
            val adView = AdView(context)
            val bridge = jsBridgeClass
                .getDeclaredConstructor(BaseAdView::class.java, Context::class.java)
                .apply { isAccessible = true }
                .newInstance(adView, context)

            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                addJavascriptInterface(bridge, "Android")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded.countDown()
                    }
                }
                loadDataWithBaseURL(null, "<html><body></body></html>", "text/html", "utf-8", null)
            }
        }
        assertTrue("WebView never finished loading", pageLoaded.await(15, TimeUnit.SECONDS))

        // Payload types are chosen so every Kotlin handler is a no-op
        // (postMessage/postVideoStatus ignore unknown types; showAd and
        // reportOverflow only post to a detached view).
        val calls = listOf(
            """Android.postMessage(JSON.stringify({type: 'R8_TEST'}))""",
            """Android.postVideoStatus(JSON.stringify({type: 'R8_TEST'}))""",
            """Android.reportOverflow(1, 2, 3, 4)""",
            """Android.showAd()"""
        )

        for (call in calls) {
            val done = CountDownLatch(1)
            var result: String? = null
            instrumentation.runOnMainSync {
                webView!!.evaluateJavascript(
                    "(function(){ try { $call; return 'ok'; } catch (e) { return 'err: ' + e; } })()"
                ) { value ->
                    result = value
                    done.countDown()
                }
            }
            assertTrue("evaluateJavascript timed out for: $call", done.await(15, TimeUnit.SECONDS))
            assertNotNull(result)
            // evaluateJavascript returns the value JSON-encoded, hence the quotes.
            assertEquals(
                "JS call failed — bridge method missing after R8: $call",
                "\"ok\"",
                result
            )
        }

        instrumentation.runOnMainSync { webView?.destroy() }
    }
}
