package com.adgeistkit.ads;

import android.content.Context;
import android.util.AttributeSet;
import androidx.annotation.NonNull;

public final class AdView extends BaseAdView {
    /**
     * Creates a new AdView.
     * @param context The activity context.
     */
    public AdView(@NonNull Context context) {
        super(context, 0);
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
    }

    /**
     * Creates a new AdView with attributes from XML.
     * @param context The activity context.
     * @param attrs The XML attributes.
     */
    public AdView(@NonNull Context context, @NonNull AttributeSet attrs) {
        super(context, attrs, 0);
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
    }

    /**
     * Creates a new AdView with attributes and style from XML.
     * @param context The activity context.
     * @param attrs The XML attributes.
     * @param defStyle The default style resource ID.
     */
    public AdView(@NonNull Context context, @NonNull AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle, 0);
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
    }
}
