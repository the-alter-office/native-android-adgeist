package com.adgeistkit.ads

/**
 * Defines the supported ad types for AdGeist SDK.
 * Publishers must use these predefined values when configuring ads.
 */
enum class AdType(internal val value: String) {
    /**
     * Banner ads - Small rectangular ads typically displayed at the top or bottom of the screen
     */
    BANNER("banner"),
    
    /**
     * Display ads - Standard display advertisements
     */
    DISPLAY("display"),
    
    /**
     * Companion ads - Ads displayed alongside other content
     * Requires minimum 320x320 dimensions
     */
    COMPANION("companion")
}
