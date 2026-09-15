package com.adgeistkit.request

public class AdRequest private constructor(builder: Builder) {
    public class Builder {
        public fun build(): AdRequest {
            return AdRequest(this)
        }
    }
}
