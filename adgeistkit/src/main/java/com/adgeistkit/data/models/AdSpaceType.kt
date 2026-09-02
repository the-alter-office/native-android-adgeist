package com.adgeistkit.data.models

import com.google.gson.annotations.SerializedName

enum class AdSpaceType(internal val value: String) {
    @SerializedName("banner")
    BANNER("banner"),

    @SerializedName("display")
    DISPLAY("display"),

    @SerializedName("companion")
    COMPANION("companion")
}
