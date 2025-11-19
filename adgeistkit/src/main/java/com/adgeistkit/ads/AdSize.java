package com.adgeistkit.ads;

import android.content.Context;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AdSize {
    private int width;
    private int height;
    
    public static final AdSize BANNER = new AdSize(320, 50);
    
    public static final AdSize LARGE_BANNER = new AdSize(320, 100);
    
    public static final AdSize MEDIUM_RECTANGLE = new AdSize(300, 250);
    
    public static final AdSize FULL_BANNER = new AdSize(468, 60);
    
    public static final AdSize LEADERBOARD = new AdSize(728, 90);
    
    public static final AdSize WIDE_SKYSCRAPER = new AdSize(160, 600);

    public static final AdSize INVALID = new AdSize(0, 0);

    public AdSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getWidthInPixels(@NonNull Context context) {
        if (width == 0) return 0;
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (width * density);
    }

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
