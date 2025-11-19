package com.adgeistkit.ads.network;

import org.json.JSONObject;

public class AnalyticsRequest {
    //Required
    private final String metaData;
    private final boolean isTestMode;

    //Optional
    private final String type;
    private final long renderTime;
    private final float visibilityRatio;
    private final float scrollDepth;
    private final long viewTime;
    private final long timeToVisible;
    private final long totalViewTime;
    private final long totalPlaybackTime;

    private AnalyticsRequest(AnalyticsRequestBuilder analyticsRequest){
        this.metaData = analyticsRequest.metaData;
        this.isTestMode = analyticsRequest.isTestMode;
        this.type = analyticsRequest.type;
        this.renderTime = analyticsRequest.renderTime;
        this.visibilityRatio = analyticsRequest.visibilityRatio;
        this.scrollDepth = analyticsRequest.scrollDepth;
        this.viewTime = analyticsRequest.viewTime;
        this.timeToVisible = analyticsRequest.timeToVisible;
        this.totalViewTime = analyticsRequest.totalViewTime;
        this.totalPlaybackTime = analyticsRequest.totalPlaybackTime;
    }

    public static class AnalyticsRequestBuilder {
        //Required
        private final String metaData;
        private final boolean isTestMode;

        //Optional
        private String type;
        private long renderTime;
        private float visibilityRatio;
        private float scrollDepth;
        private long viewTime;
        private long timeToVisible;
        private long totalViewTime;
        private long totalPlaybackTime;

        public AnalyticsRequestBuilder(String metaData, boolean isTestMode){
            this.metaData = metaData;
            this.isTestMode = isTestMode;
        }

        public AnalyticsRequestBuilder trackImpression(long renderTime){
            this.type = "IMPRESSION";
            this.renderTime = renderTime;
            return this;
        }

        public AnalyticsRequestBuilder trackViewableImpression(long timeToVisible, float scrollDepth, float visibilityRatio, long viewTime){
            this.type = "VIEW";
            this.timeToVisible = timeToVisible;
            this.scrollDepth = scrollDepth;
            this.visibilityRatio = visibilityRatio;
            this.viewTime = viewTime;
            return this;
        }

        public AnalyticsRequestBuilder trackClick(){
            this.type = "CLICK";
            return this;
        }

        public AnalyticsRequestBuilder trackTotalViewTime(long totalViewTime){
            this.type = "TOTAL_VIEW_TIME";
            this.totalViewTime = totalViewTime;
            return this;
        }

        public AnalyticsRequestBuilder trackTotalPlaybackTime(long totalPlaybackTime){
            this.type = "TOTAL_PLAYBACK_TIME";
            this.totalPlaybackTime = totalPlaybackTime;
            return this;
        }

        public AnalyticsRequest build(){
            return new AnalyticsRequest(this);
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("metaData", metaData);
            json.put("isTestMode", isTestMode);
            json.put("type", type);

            switch (type) {
                case "IMPRESSION":
                    json.put("renderTime", renderTime);
                    break;

                case "VIEW":
                    json.put("timeToVisible", timeToVisible);
                    json.put("scrollDepth", scrollDepth);
                    json.put("visibilityRatio", visibilityRatio);
                    json.put("viewTime", viewTime);
                    break;

                case "TOTAL_VIEW_TIME":
                    json.put("totalViewTime", totalViewTime);
                    break;

                case "TOTAL_PLAYBACK_TIME":
                    json.put("totalPlaybackTime", totalPlaybackTime);
                    break;

                case "CLICK":

                default:
                    break;
            }
        } catch (Exception ignored) {}
        return json;
    }
}
