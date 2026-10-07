package com.adgeistkit.ads.measure

import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import kotlin.math.max

internal object AdSizeResolver {

    data class Result(
        val widthPx: Int,
        val heightPx: Int,
        val widthUndetermined: Boolean,
        val heightUndetermined: Boolean
    )

    fun resolve(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
        adIsResponsive: Boolean,
        declaredWidthPx: Int,
        declaredHeightPx: Int,
        minWidthPx: Int,
        minHeightPx: Int
    ): Result {
        val desiredWidth: Int
        val desiredHeight: Int
        var widthUndetermined = false
        var heightUndetermined = false

        if (adIsResponsive) {
            desiredWidth = axisSize(widthMeasureSpec, declaredWidthPx)
            desiredHeight = axisSize(heightMeasureSpec, declaredHeightPx)
            widthUndetermined = isAxisUndetermined(widthMeasureSpec, declaredWidthPx)
            heightUndetermined = isAxisUndetermined(heightMeasureSpec, declaredHeightPx)
        } else {
            desiredWidth = declaredWidthPx
            desiredHeight = declaredHeightPx
        }

        return Result(
            widthPx = View.resolveSize(max(desiredWidth, minWidthPx), widthMeasureSpec),
            heightPx = View.resolveSize(max(desiredHeight, minHeightPx), heightMeasureSpec),
            widthUndetermined = widthUndetermined,
            heightUndetermined = heightUndetermined
        )
    }

    fun measureChild(parent: ViewGroup, widthPx: Int, heightPx: Int) {
        val child = parent.getChildAt(0)

        if (child == null || child.visibility == View.GONE) return

        child.measure(
            MeasureSpec.makeMeasureSpec(widthPx, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY)
        )
    }

    private fun axisSize(measureSpec: Int, declaredPx: Int): Int {
        if (MeasureSpec.getMode(measureSpec) == MeasureSpec.EXACTLY) {
            return MeasureSpec.getSize(measureSpec)
        }

        return declaredPx
    }

    private fun isAxisUndetermined(measureSpec: Int, declaredPx: Int): Boolean {
        if (declaredPx > 0) return false
        return MeasureSpec.getMode(measureSpec) != MeasureSpec.EXACTLY ||
                MeasureSpec.getSize(measureSpec) == 0
    }
}
