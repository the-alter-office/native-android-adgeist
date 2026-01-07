package com.adgeistkit.ads

import android.content.Context
import android.util.*

open class AdView : BaseAdView {
    constructor(context: Context) : super(context, 0){
        if (context == null) {
            throw IllegalArgumentException("Context cannot be null")
        }
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs, 0){
        if (context == null) {
            throw IllegalArgumentException("Context cannot be null")
        }
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle, 0){
        if (context == null) {
            throw IllegalArgumentException("Context cannot be null")
        }
    }
}