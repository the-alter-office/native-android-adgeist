package com.adgeistkit.ads.network

import org.json.JSONObject

class AdRequest private constructor(builder: Builder) {
    val isTestMode: Boolean = builder.testMode

    class Builder {
        internal var testMode: Boolean = false

        fun setTestMode(testMode: Boolean): Builder {
            this.testMode = testMode
            return this
        }

        fun build(): AdRequest {
            return AdRequest(this)
        }
    }

    override fun toString(): String {
        return "AdRequest(testMode=" + isTestMode + ")"
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("testMode", isTestMode)
        } catch (ignored: Exception) {
        }
        return json
    }
}