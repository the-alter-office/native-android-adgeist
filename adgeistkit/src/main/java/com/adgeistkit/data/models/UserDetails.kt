package com.adgeistkit.data.models

import com.google.gson.annotations.SerializedName

data class UserDetails(
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("user_name") val userName: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("custom_attributes") val customAttributes: Map<String, Any>? = null
) {
    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "user_id" to userId,
            "user_name" to userName,
            "email" to email,
            "phone" to phone
        )
        customAttributes?.let { map.putAll(it) }
        return map
    }
}