package com.adgeistkit.ads

/**
 * Host-tunable limits for ad session reuse. Main thread only;
 */
object AdSessionConfig {

    /** How long a rendered ad may be reused on return. 0 disables reuse; rotation always reuses. */
    @JvmStatic
    var sessionTtlMs: Long = 300_000L

    /** Cap on parked off-screen sessions, each holding a live WebView; excess is LRU-evicted. */
    @JvmStatic
    var maxParkedSessions: Int = 3
}
