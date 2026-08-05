package com.adgeistkit.ads

/**
 * Host-tunable limits for ad session reuse. Main thread only; changes apply from
 * the next ad onwards. See AD_LIFECYCLE.md, "Ad reuse has a TTL".
 */
object AdSessionConfig {

    /** How long a rendered ad may be reused on return. 0 disables reuse; rotation always reuses. */
    @JvmStatic
    var sessionTtlMs: Long = 300_000L

    /** Cap on parked off-screen sessions, each holding a live WebView; excess is LRU-evicted. */
    @JvmStatic
    var maxParkedSessions: Int = 3
}
