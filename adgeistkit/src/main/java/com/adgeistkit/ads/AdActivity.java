package com.adgeistkit.ads;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

public class AdActivity {
    private static final String TAG = "Ad Activity";
    private static final double VISIBILITY_THRESHOLD = 0.5f;
    private static final long MIN_VIEW_TIME = 1000L;

    private final BaseAdView baseAdView;
    private final long renderStartTime;

    private final String mediaType;
    private float currentVisibilityRatio;
    private boolean isVisible;
    private long viewStartTime;
    private long totalViewTime;
    private boolean hasViewEvent = false;
    private boolean hasImpression = false;
    private long renderTime;

    private long playbackStartTime = 0;
    private long totalPlaybackTime = 0;
    private boolean hasEnded = false;
    private boolean hasSentPlaybackEvent = false;

    private ViewTreeObserver.OnScrollChangedListener scrollListener;
    private ViewTreeObserver.OnWindowFocusChangeListener focusListener;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable visibilityCheckRunnable;

    public AdActivity(BaseAdView baseAdView){
        this.baseAdView = baseAdView;
        this.renderStartTime = SystemClock.elapsedRealtime();
        this.mediaType = baseAdView.mediaType;
        initialize();
    }

    private void initialize(){
        setupVisibilityTracking();
    }

    private void setupVisibilityTracking(){
        ViewTreeObserver vto = baseAdView.getViewTreeObserver();

        scrollListener = this::checkVisibility;
        vto.addOnScrollChangedListener(scrollListener);

        focusListener = hasFocus -> onVisibilityChange(hasFocus);
        vto.addOnWindowFocusChangeListener(focusListener);

        checkVisibility();
    }

    private void checkVisibility() {
        Rect rect = new Rect();
        boolean isVisible = baseAdView.getGlobalVisibleRect(rect);

        if (!isVisible) {
            handleVisibilityChange(false);
            return;
        }

        int totalWidth = baseAdView.getWidth();
        int totalHeight = baseAdView.getHeight();

        if (totalWidth == 0 || totalHeight == 0) {
            return;
        }

        int visibleWidth = rect.width();
        int visibleHeight = rect.height();

        currentVisibilityRatio = (visibleWidth * visibleHeight) / (float) (totalWidth * totalHeight);
        boolean newVisible = currentVisibilityRatio >= VISIBILITY_THRESHOLD;

        handleVisibilityChange(newVisible);
    }

    private void handleVisibilityChange(boolean newVisible) {
        boolean wasVisible = isVisible;
        isVisible = newVisible;

        if (isVisible && !wasVisible) {
            if (viewStartTime == 0) {
                viewStartTime = SystemClock.elapsedRealtime();
                startVisibilityCheck();
            }
            if ("video".equals(mediaType) && !hasEnded) {
                getWebView().onResume();
                onVideoPlay();
            }
        } else if (!isVisible && wasVisible) {
            updateViewTime();
            stopVisibilityCheck();
            if ("video".equals(mediaType) && !hasEnded) {
                getWebView().onPause();
                onVideoPause();
            }
        }
    }

    private void startVisibilityCheck() {
        if (visibilityCheckRunnable != null) return;
        visibilityCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (isVisible && viewStartTime > 0 && !hasViewEvent) {
                    long timeInView = SystemClock.elapsedRealtime() - viewStartTime;
                    if (timeInView >= MIN_VIEW_TIME) {
                        hasViewEvent = true;
                        baseAdView.listener.onAdImpression();
                        float scrollDepth = getScrollDepth();
                        long timeToVisible = SystemClock.elapsedRealtime() - renderStartTime;
                        Log.e(TAG, " scrollDepth=" + scrollDepth + "timeToVisible =" +   timeToVisible + "currentVisiblityRatio =" + currentVisibilityRatio);
                        stopVisibilityCheck();
                    }
                }
                handler.postDelayed(this, 100);
            }
        };
        handler.post(visibilityCheckRunnable);
    }

    private View findRootScrollView(View view) {
        View scrollParent = null;

        while (view != null) {
            if (isScrollableView(view)) {
                scrollParent = view;
            }
            if (view.getParent() instanceof View) {
                view = (View) view.getParent();
            } else {
                break;
            }
        }
        return scrollParent;
    }

    private boolean isScrollableView(View view) {
        return (view instanceof ScrollView
                || view instanceof NestedScrollView
                || view instanceof HorizontalScrollView);
    }

    public float getScrollDepth() {
        View scrollView = findRootScrollView(baseAdView);

        // Get AdView absolute Y on screen
        int[] adLocation = new int[2];
        baseAdView.getLocationOnScreen(adLocation);
        int adTopOnScreen = adLocation[1];

        // Get ScrollView top on screen
        int[] scrollLocation = new int[2];
        scrollView.getLocationOnScreen(scrollLocation);
        int scrollTopOnScreen = scrollLocation[1];

        int requiredScroll = adTopOnScreen - scrollTopOnScreen;
        if (requiredScroll <= 0) {
            return 1f;
        }

        // 4. Current scroll amount
        int currentScroll = scrollView.getScrollY();
        float rawRatio = (float) currentScroll / (float) requiredScroll;
        if (rawRatio < 0f) rawRatio = 0f;
        if (rawRatio > 1f) rawRatio = 1f;

        return rawRatio;
    }

    private void updateViewTime() {
        if (viewStartTime > 0) {
            totalViewTime += SystemClock.elapsedRealtime() - viewStartTime;
            viewStartTime = 0;
            stopVisibilityCheck();
        }
    }

    private void stopVisibilityCheck() {
        if (visibilityCheckRunnable != null) {
            handler.removeCallbacks(visibilityCheckRunnable);
            visibilityCheckRunnable = null;
        }
    }

    public void onVisibilityChange(boolean hasFocus) {
        if (!hasFocus) {
            updateViewTime();
            stopVisibilityCheck();
            if ("video".equals(mediaType) && !hasEnded) {
                getWebView().onPause();
                onVideoPause();
            }
        } else if (isVisible) {
            if ("video".equals(mediaType) && !hasEnded) {
                getWebView().onResume();
                onVideoPlay();
            }
        }
    }

    public void onVideoPlay() {
        if (playbackStartTime == 0) {
            playbackStartTime = SystemClock.elapsedRealtime();
        }
    }

    public void onVideoPause() {
        updatePlaybackTime();
    }

    public void onVideoEnd() {
        if (!hasEnded && "video".equals(mediaType)) {
            hasEnded = true;
            updatePlaybackTime();
        }
    }

    private void updatePlaybackTime() {
        if (playbackStartTime > 0 && "video".equals(mediaType)) {
            totalPlaybackTime += SystemClock.elapsedRealtime() - playbackStartTime;
            playbackStartTime = 0;
        }
    }

    public void captureImpression() {
        if (!hasImpression) {
            renderTime = SystemClock.elapsedRealtime() - renderStartTime;
            Log.e(TAG, "renderTime= " + renderTime);
            baseAdView.listener.onAdLoaded();
            hasImpression = true;
        }
    }

    public void captureClick(){
        baseAdView.listener.onAdClicked();
    }

    public void captureTotalViewTime(){

    }

    public void captureTotalVideoPlaybackTime(){
        if (totalPlaybackTime > 0 && !hasSentPlaybackEvent && "video".equals(mediaType)) {
            hasSentPlaybackEvent = true;
        }
    }

    @Nullable
    private WebView getWebView(){
        if (baseAdView.getChildCount() > 0 && baseAdView.getChildAt(0) instanceof WebView) {
            return (WebView) baseAdView.getChildAt(0);
        }
        return null;
    }

    public void destroy(){
        ViewTreeObserver vto = baseAdView.getViewTreeObserver();
        if(vto.isAlive()){
            vto.removeOnScrollChangedListener(scrollListener);
        }

        captureTotalViewTime();
        captureTotalVideoPlaybackTime();
        updateViewTime();
        stopVisibilityCheck();
    }
}
