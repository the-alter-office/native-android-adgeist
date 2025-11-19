package com.adgeistkit.ads;

import android.content.Context;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

public class JsBridge {
    private final static String TAG = "Javascript Bridge";
    private final BaseAdView baseAdView;
    private AdActivity adActivity;
    Context mContext;

    public JsBridge(BaseAdView baseAdView, Context c){
        this.baseAdView = baseAdView;
        mContext = c;
        initializeAdTracker();
    }

    private void initializeAdTracker(){
        adActivity = new AdActivity(baseAdView);
    }

    public void recordClickListener(){
        adActivity.captureClick();
    }

    public void destroyListeners(){
        adActivity.destroy();
    }

    @JavascriptInterface
    public void postMessage(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.optString("type");
            String msg  = obj.optString("message");

            if ("RENDER_STATUS".equals(type) && "Success".equals(msg)) {
                adActivity.captureImpression();
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid JSON: " + json);
        }
    }

    @JavascriptInterface
    public void postVideoStatus(String json){
        try {
            JSONObject obj = new JSONObject(json);
            String type = obj.optString("type");
            String msg  = obj.optString("message");

            if ("PLAY".equals(type)) {
                adActivity.onVideoPlay();
            }else if("PAUSE".equals(type)){
                adActivity.onVideoPause();
            } else if ("ENDED".equals(type)) {
                adActivity.onVideoEnd();
            }
        } catch (Exception e) {
            Log.e(TAG, "Invalid JSON: " + json);
        }
    }
}
