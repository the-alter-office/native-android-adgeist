package com.adgeistkit.ads;

import org.json.JSONObject;

public class AdRequest {
    private final boolean testMode;

    private AdRequest(Builder builder) {
        this.testMode = builder.testMode;
    }

    public static class Builder {
        private boolean testMode;

        public Builder setTestMode(boolean testMode) {
            this.testMode = testMode;
            return this;
        }

        public AdRequest build() {
            return new AdRequest(this);
        }
    }

    public boolean isTestMode() {
        return testMode;
    }

    @Override
    public String toString() {
        return "AdRequest(testMode=" + testMode + ")";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try { json.put("testMode", testMode); }
        catch (Exception ignored) {}
        return json;
    }
}

