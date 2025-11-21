package com.adgeistkit.ads

import android.content.Context

class AdSize(val width: Int, val height: Int) {
    companion object {
        val BANNER: AdSize = AdSize(320, 50)

        val LARGE_BANNER: AdSize = AdSize(320, 100)

        val MEDIUM_RECTANGLE: AdSize = AdSize(300, 250)

        val FULL_BANNER: AdSize = AdSize(468, 60)

        val LEADERBOARD: AdSize = AdSize(728, 90)

        val WIDE_SKYSCRAPER: AdSize = AdSize(160, 600)

        val INVALID: AdSize = AdSize(0, 0)
    }

    fun getWidthInPixels(context: Context): Int {
        if (width == 0) return 0
        val density = context.resources.displayMetrics.density
        return (width * density).toInt()
    }

    fun getHeightInPixels(context: Context): Int {
        if (height == 0) return 0
        val density = context.resources.displayMetrics.density
        return (height * density).toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AdSize) {
            return false
        }
        val otherSize = other
        return width == otherSize.width && height == otherSize.height
    }

    override fun hashCode(): Int {
        return width * 31 + height
    }

    override fun toString(): String {
        return width.toString() + "x" + height
    }
}