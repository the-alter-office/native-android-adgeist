package com.adgeistkit.request

class AdRequest private constructor(builder: Builder) {
    class Builder {
        fun build(): AdRequest {
            return AdRequest(this)
        }
    }
}
