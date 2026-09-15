package com.adgeistkit.data.models

import com.google.gson.annotations.SerializedName

public enum class AdSpaceType(internal val value: String) {
    @SerializedName("banner")
    BANNER("banner"),

    @SerializedName("display")
    DISPLAY("display"),

    @SerializedName("companion")
    COMPANION("companion")
}
