package com.adgeistkit.ads

import android.content.Context

class AdSize(val width: Int, val height: Int) {
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