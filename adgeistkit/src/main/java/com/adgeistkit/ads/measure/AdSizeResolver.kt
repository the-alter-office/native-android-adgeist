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

    fun undeterminedAxisWarning(
        adUnitId: String,
        widthUndetermined: Boolean,
        heightUndetermined: Boolean
    ): String? {
        if (!widthUndetermined && !heightUndetermined) return null

        val axis = when {
            widthUndetermined && heightUndetermined -> "width or height"
            widthUndetermined -> "width"
            else -> "height"
        }

        val remedy = when {
            widthUndetermined && heightUndetermined ->
                "Give the AdView a fixed width and height in your layout, or set " +
                    "adIsResponsive = false and call setAdDimension(AdSize(width, height))."

            widthUndetermined ->
                "Call setAdDimension(AdSize.width(...)) or give the AdView a fixed width " +
                    "in your layout."

            else ->
                "Call setAdDimension(AdSize.height(...)) or give the AdView a fixed height " +
                    "in your layout."
        }

        return "Ad unit '$adUnitId': this responsive AdView cannot determine its $axis. Its " +
            "parent supplies no fixed $axis and no AdSize supplies one, so it measures " +
            "0 and the ad will not be visible. $remedy"
    }

    private fun axisSize(measureSpec: Int, declaredPx: Int): Int {
        if (MeasureSpec.getMode(measureSpec) == MeasureSpec.EXACTLY) {
            return MeasureSpec.getSize(measureSpec)
        }

        return declaredPx
    }

    private fun isAxisUndetermined(measureSpec: Int, declaredPx: Int): Boolean =
        MeasureSpec.getMode(measureSpec) != MeasureSpec.EXACTLY && declaredPx <= 0
}
