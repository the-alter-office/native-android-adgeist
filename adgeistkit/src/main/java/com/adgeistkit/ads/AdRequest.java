package com.adgeistkit.ads;

import org.json.JSONObject;

public class AdRequest {
    private final boolean testMode;

    public AdRequest(boolean testMode) {
        this.testMode = testMode;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public static class Builder {
        private boolean testMode = false;

        public Builder setTestMode(boolean enabled) {
            this.testMode = enabled;
            return this;
        }

        public AdRequest build() {
            return new AdRequest(testMode);
        }
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {json.put("testMode", testMode);}
        catch (Exception ignored) {}
        return json;
    }

    @Override
    public String toString() {
        return "AdRequest( testMode=" + testMode + ")";
    }
}
