package com.adgeistkit.ads

import android.content.Context
import android.util.*
import com.adgeistkit.constants.Messages

public open class AdView : BaseAdView {
    public constructor(context: Context) : super(context, 0){
        if (context == null) {
            throw IllegalArgumentException(Messages.Exceptions.CONTEXT_NULL)
        }
    }

    public constructor(context: Context, attrs: AttributeSet) : super(context, attrs, 0){
        if (context == null) {
            throw IllegalArgumentException(Messages.Exceptions.CONTEXT_NULL)
        }
    }

    public constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle, 0){
        if (context == null) {
            throw IllegalArgumentException(Messages.Exceptions.CONTEXT_NULL)
        }
    }
}