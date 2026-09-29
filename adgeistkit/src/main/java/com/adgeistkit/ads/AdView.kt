package com.adgeistkit.ads

import android.content.Context
import android.util.*

public open class AdView : BaseAdView {
    public constructor(context: Context) : super(context)

    public constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    public constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)
}
