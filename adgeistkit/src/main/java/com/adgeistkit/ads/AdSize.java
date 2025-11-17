package com.adgeistkit.ads;

import android.content.Context;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AdSize {
    private int width;
    private int height;
    
    /**
     * Standard banner size (320x50 dp)
     * Mobile phones
     */
    public static final AdSize BANNER = new AdSize(320, 50);
    
    /**
     * Large banner size (320x100 dp)
     * Mobile phones
     */
    public static final AdSize LARGE_BANNER = new AdSize(320, 100);
    
    /**
     * Medium rectangle size (300x250 dp)
     * Tablets and mobile phones
     */
    public static final AdSize MEDIUM_RECTANGLE = new AdSize(300, 250);
    
    /**
     * Full banner size (468x60 dp)
     * Tablets
     */
    public static final AdSize FULL_BANNER = new AdSize(468, 60);
    
    /**
     * Leaderboard size (728x90 dp)
     * Tablets
     */
    public static final AdSize LEADERBOARD = new AdSize(728, 90);
    
    /**
     * Wide skyscraper size (160x600 dp)
     * Tablets
     */
    public static final AdSize WIDE_SKYSCRAPER = new AdSize(160, 600);

    /**
     * Invalid ad size for error cases
     */
    public static final AdSize INVALID = new AdSize(0, 0);

    /**
     * Creates a custom ad size.
     * @param width Width in density-independent pixels (dp)
     * @param height Height in density-independent pixels (dp)
     */
    public AdSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Gets the width of the ad in device-independent pixels (dp).
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the height of the ad in device-independent pixels (dp).
     */
    public int getHeight() {
        return height;
    }

    /**
     * Gets the width of the ad in pixels for the given context.
     */
    public int getWidthInPixels(@NonNull Context context) {
        if (width == 0) return 0;
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (width * density);
    }

    /**
     * Gets the height of the ad in pixels for the given context.
     */
    public int getHeightInPixels(@NonNull Context context) {
        if (height == 0) return 0;
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (height * density);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (!(other instanceof AdSize)) {
            return false;
        }
        AdSize otherSize = (AdSize) other;
        return width == otherSize.width && height == otherSize.height;
    }

    @Override
    public int hashCode() {
        return width * 31 + height;
    }

    @Override
    @NonNull
    public String toString() {
        return width + "x" + height;
    }
}
