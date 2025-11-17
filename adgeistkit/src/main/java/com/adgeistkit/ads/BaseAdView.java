package com.adgeistkit.ads;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;

import com.adgeistkit.AdgeistCore;
import com.adgeistkit.R;
import com.adgeistkit.data.models.Creative;
import com.adgeistkit.data.models.Dimensions;
import com.adgeistkit.data.models.DisplayOptions;
import com.adgeistkit.data.models.FixedAdResponse;
import com.adgeistkit.data.network.FetchCreative;
import com.google.gson.Gson;

import java.util.*;

public abstract class BaseAdView extends ViewGroup {
    private static final String TAG = "BaseAdView";
    
    @Nullable
    protected AdSize adSize;
    
    @NonNull
    protected String adUnitId = "";

    @Nullable
    private WebView webView;
    
    private boolean isLoading = false;
    private boolean isDestroyed = false;
    private Handler mainHandler;

    @Nullable
    public AdSize getAdSize() {
        return adSize;
    }

    @NonNull
    public String getAdUnitId() {
        return adUnitId;
    }

    protected BaseAdView(@NonNull Context context, int adViewType) {
        super(context);
        initialize(context, null);
    }

    protected BaseAdView(@NonNull Context context, @NonNull AttributeSet attrs, int adViewType) {
        super(context, attrs);
        initialize(context, attrs);
    }

    protected BaseAdView(@NonNull Context context, @NonNull AttributeSet attrs, int defStyle, int adViewType) {
        super(context, attrs, defStyle);
        initialize(context, attrs);
    }

    private void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
        mainHandler = new Handler(Looper.getMainLooper());
        
        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.AdView);
            try {
                // Parse adUnitId from XML
                String xmlAdUnitId = typedArray.getString(R.styleable.AdView_adUnitId);
                if (xmlAdUnitId != null && !xmlAdUnitId.isEmpty()) {
                    adUnitId = xmlAdUnitId;
                }
                
                // Parse adSize from XML
                int sizeIndex = typedArray.getInt(R.styleable.AdView_adSize, -1);
                adSize = getAdSizeFromIndex(sizeIndex);
            } finally {
                typedArray.recycle();
            }
        }
        
        // Default ad size if not set
        if (adSize == null) {
            adSize = AdSize.BANNER;
        }
    }

    private AdSize getAdSizeFromIndex(int index) {
        switch (index) {
            case 0: return AdSize.BANNER;
            case 1: return AdSize.LARGE_BANNER;
            case 2: return AdSize.MEDIUM_RECTANGLE;
            case 3: return AdSize.FULL_BANNER;
            case 4: return AdSize.LEADERBOARD;
            case 5: return AdSize.WIDE_SKYSCRAPER;
            default: return AdSize.BANNER;
        }
    }

    private String getMetaValue(Context context, String key) {
        try {
            ApplicationInfo ai = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

            Bundle bundle = ai.metaData;
            return bundle != null ? bundle.getString(key) : null;

        } catch (Exception e) {
            Log.e("BaseAdView", "Meta-data read failed for: " + key, e);
            return null;
        }
    }

    @RequiresPermission("android.permission.INTERNET")
    public void loadAd(@NonNull AdRequest adRequest) {
        if (isDestroyed) {
            Log.e(TAG, "Cannot load ad - AdView has been destroyed");
            return;
        }
        
        if (isLoading) {
            Log.w(TAG, "Ad is already loading");
            return;
        }
        
        if (adUnitId == null || adUnitId.isEmpty()) {
            Log.e(TAG, "Ad unit ID is null or empty");
            return;
        }
        
        isLoading = true;

        new Thread(() -> {
            try {
                AdgeistCore adgeist = AdgeistCore.getInstance();
                FetchCreative fetchCreative = adgeist.getCreative();

                String publisherId = getMetaValue(getContext(), "com.adgeistkit.ads.APP_ID");
                String apiKey = getMetaValue(getContext(), "com.adgeistkit.ads.API_KEY");

                fetchCreative.fetchCreative(
                    apiKey,"https://adgeist-ad-integration.d49kd6luw1c4m.amplifyapp.com/", adUnitId, publisherId, "FIXED", true,
                    creativeData -> {
                        mainHandler.post(() -> {
                            isLoading = false;
                            if (creativeData == null) {
                                return;
                            }
                            try {

                                FixedAdResponse fixed = (FixedAdResponse) creativeData;
                                if (fixed == null) {
                                    return;
                                }
                                Creative c = fixed.getCreatives().get(0);

                                Map<String, Object> simpleCreative = new HashMap<>();

                                simpleCreative.put("adElementId", "adgeist_ads_iframe_" + adUnitId);
                                simpleCreative.put("title", c.getTitle());
                                simpleCreative.put("description", c.getDescription());
                                simpleCreative.put("name", fixed.getAdvertiser() != null ? fixed.getAdvertiser().getName() : "-");
                                simpleCreative.put("ctaUrl", c.getCtaUrl());
                                simpleCreative.put("fileUrl", c.getFileUrl());
                                simpleCreative.put("type", c.getType());

                                DisplayOptions options = fixed.getDisplayOptions();
                                boolean isResponsive = options != null && options.isResponsive() != null ? options.isResponsive() : false;
                                String responsiveType = options != null && options.getResponsiveType() != null ? options.getResponsiveType() : "Square";

                                simpleCreative.put("isResponsive", isResponsive);
                                simpleCreative.put("responsiveType", responsiveType);

                                Dimensions dim = options != null ? options.getDimensions() : null;
                                int width = dim != null && dim.getWidth() != null ? dim.getWidth() : 300;
                                int height = dim != null && dim.getHeight() != null ? dim.getHeight() : 300;

                                simpleCreative.put("width", width);
                                simpleCreative.put("height", height);

                                simpleCreative.put("adspaceType", "banner");

                                List<Map<String, String>> mediaList = new ArrayList<>();

                                if (c.getFileUrl() != null) {
                                    Map<String, String> mediaObj = new HashMap<>();
                                    mediaObj.put("src", c.getFileUrl());
                                    mediaList.add(mediaObj);
                                }

                                simpleCreative.put("media", mediaList);

                                String mediaType = (c.getType() != null) ? c.getType() : "image";
                                simpleCreative.put("mediaType", mediaType);

                                String creativeJson = new Gson().toJson(simpleCreative);

                                if (creativeJson != null) {
                                    renderAdWithAdCard(creativeJson);
                                    notifyAdLoaded();
                                }
                            } catch (Exception err) {
                                Log.e(TAG, "Error parsing creativeData", err);
                            }

                        });

                        return null;
                    }
                );
            } catch (Exception e) {
                mainHandler.post(() -> {
                    isLoading = false;
                    Log.e(TAG, "Error loading ad", e);
                });
            }
        }).start();
    }

    private void renderAdWithAdCard(@NonNull String creativeJsonData) {
        removeAllViews();
        
        webView = new WebView(getContext());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);

        // Enable WebView debugging (you can inspect in Chrome DevTools)
        // chrome://inspect/#devices
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.i(TAG, "✅ WebView page finished loading: " + url);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
                Log.d(TAG, "📦 Loading resource: " + url);
            }
        });
        
        // Set WebChromeClient to capture console logs
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage consoleMessage) {
                String logLevel = consoleMessage.messageLevel().name();
                String message = consoleMessage.message();
                String source = consoleMessage.sourceId();
                int line = consoleMessage.lineNumber();
                
                // Format: [LEVEL] message (source:line)
                String fullLog = String.format("[%s] %s (%s:%d)", 
                    logLevel, message, source, line);
                Log.e(TAG, fullLog);
                // Log based on level
                switch (consoleMessage.messageLevel()) {
                    case ERROR:
                        Log.e(TAG, "🔴 JS Error: " + fullLog);
                        break;
                    case WARNING:
                        Log.w(TAG, "🟡 JS Warning: " + fullLog);
                        break;
                    case DEBUG:
                    case LOG:
                    case TIP:
                    default:
                        Log.d(TAG, "🔵 JS Log: " + fullLog);
                        break;
                }
                
                return true;
            }
        });
        
        // Set click listener
        webView.setOnClickListener(v -> {
        });
        
        // Build HTML that loads AdCard.js and renders the creative
        String htmlContent = buildAdCardHtml(creativeJsonData);

        webView.loadDataWithBaseURL(
            "https://adgeist.ai",  
            htmlContent,        
            "text/html",         
            "UTF-8",           
            null           
        );
        
        // Add to view hierarchy
        addView(webView, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        ));
    }

    private String buildAdCardHtml(@NonNull String creativeJsonData) {
        String escapedJson = creativeJsonData
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        Log.i(TAG, creativeJsonData);

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head>" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'>" +
                "    <style>" +
                "        * {" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            box-sizing: border-box;" +
                "        }" +
                "        html, body {" +
                "            width: 100%;" +
                "            height: 100%;" +
                "            margin: 0;" +
                "            padding: 0;" +
                "            overflow: hidden;" +
                "        }" +
                "        body {" +
                "            display: flex;" +
                "            justify-content: center;" +
                "            align-items: center;" +
                "        }" +
                "        #ad-content {" +
                "            width: 100%;" +
                "            height: 100%;" +
                "        }" +
                "        #ad-content > * {" +
                "            width: 100%;" +
                "            height: 100%;" +
                "            object-fit: inherit;" +
                "        }" +
                "    </style>" +
                "</head>" +
                "<body>" +
                "    <div id='ad-content'></div>" +
                "    <!-- Load AdCard.js library from S3 -->" +
                "    <script src='https://adserv-scripts.s3.ap-south-1.amazonaws.com/adcard.js'></script>" +
                "    <script>" +
                "        function initAd() {" +
                "            if (typeof AdCard === 'undefined') {" +
                "                console.error('AdCard library not loaded');" +
                "                return;" +
                "            }" +
                "            try {" +
                "                const creativeData = JSON.parse(\"" + escapedJson + "\");" +
                "                const adCard = new AdCard(creativeData);" +
                "                const html = adCard.renderHtml();" +
                "                document.getElementById('ad-content').innerHTML = html;" +
                "                document.getElementById('ad-content').addEventListener('click', function(e) {" +
                "                    console.log('Ad clicked');" +
                "                });" +
                "            } catch (error) {" +
                "                console.error('Error rendering ad:', error);" +
                "            }" +
                "        }" +
                "        if (document.readyState === 'loading') {" +
                "            document.addEventListener('DOMContentLoaded', function() {" +
                "                setTimeout(initAd, 100);" +
                "            });" +
                "        } else {" +
                "            setTimeout(initAd, 100);" +
                "        }" +
                "    </script>" +
                "</body>" +
                "</html>";
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        View child = getChildAt(0);
        if (child != null && child.getVisibility() != View.GONE) {
            int width = child.getMeasuredWidth();
            int height = child.getMeasuredHeight();
            
            int horizontalSpacing = (right - left - width) / 2;
            int verticalSpacing = (bottom - top - height) / 2;
            
            child.layout(
                horizontalSpacing,
                verticalSpacing,
                horizontalSpacing + width,
                verticalSpacing + height
            );
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View child = getChildAt(0);
        int width;
        int height;
        
        if (child != null && child.getVisibility() != View.GONE) {
            measureChild(child, widthMeasureSpec, heightMeasureSpec);
            width = child.getMeasuredWidth();
            height = child.getMeasuredHeight();
        } else {
            if (adSize != null) {
                width = adSize.getWidthInPixels(getContext());
                height = adSize.getHeightInPixels(getContext());
            } else {
                width = 0;
                height = 0;
            }
        }
        
        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        );
    }

    public void setAdSize(@NonNull AdSize adSize) {
        if (adSize == null) {
            throw new IllegalArgumentException("AdSize cannot be null");
        }
        this.adSize = adSize;
        requestLayout();
    }

    public void setAdUnitId(@NonNull String adUnitId) {
        if (adUnitId == null) {
            throw new IllegalArgumentException("Ad unit ID cannot be null");
        }
        this.adUnitId = adUnitId;
    }


    public boolean isCollapsible() {
        return false;
    }

    public boolean isLoading() {
        return isLoading;
    }

    private void notifyAdLoaded() {

    }
}