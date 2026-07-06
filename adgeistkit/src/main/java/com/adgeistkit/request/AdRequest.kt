package com.adgeistkit.request

import org.json.JSONObject

class AdRequest private constructor(builder: Builder) {

    class Builder {
        fun build(): AdRequest {
            return AdRequest(this)
        }
    }

    override fun toString(): String {
        return "AdRequest()"
    }

    fun toJson(): JSONObject {
        return JSONObject()
    }
}
