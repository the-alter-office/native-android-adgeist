package com.examplenativeandroidapp;

import android.content.Context;

import com.adgeistkit.AdgeistCore;

/**
 * Compile-time check that the SDK's public entry points stay callable from Java.
 * Never instantiated at runtime.
 */
final class JavaInteropSmokeTest {

    private JavaInteropSmokeTest() {}

    static void initializeFromJava(Context context) {
        AdgeistCore adGeist = AdgeistCore.initialize(context.getApplicationContext());
        AdgeistCore.initialize(context, "https://example.com");
        adGeist.getConsentStatus();
    }
}
