package com.adgeistkit.interop;

import android.content.Context;

import com.adgeistkit.AdgeistCore;

/**
 * Compile-time check that the SDK's public entry points stay callable from Java.
 *
 * Nothing here runs: there is no @Test method and the class is never instantiated. Its
 * only job is to break compilation of :adgeistkit:test the moment the Kotlin API stops
 * being Java-friendly. Dropping @JvmStatic or @JvmOverloads from AdgeistCore.initialize
 * is the case that matters - Kotlin default arguments do not exist in Java, so the one-
 * and two-argument calls below would stop resolving.
 *
 * Deliberately in its own package, not com.adgeistkit, so it can only reach the API a
 * real Java consumer can reach.
 */
final class JavaInteropSmokeTest {

    private JavaInteropSmokeTest() {}

    static void initializeFromJava(Context context) {
        AdgeistCore adGeist = AdgeistCore.initialize(context.getApplicationContext());
        AdgeistCore.initialize(context, "https://example.com");
        adGeist.getConsentStatus();
    }
}
