package com.examplecomposeapp.feature.home

import androidx.annotation.StringRes
import com.examplecomposeapp.R

data class HomeForm(
    val packageId: String = "com.leaguex.crm.beta",
    val adgeistAppId: String = "69a6777707df2b1527e357f9",
    val adspaceId: String = "",
    val width: String = "320",
    val height: String = "320",
    val isResponsive: Boolean = false,
    val containerWidth: String = "",
    val containerHeight: String = "",
)

data class HomeAdRequest(
    val id: Int,
    val adUnitId: String,
    val isResponsive: Boolean,
    val widthDp: Int,
    val heightDp: Int,
)

enum class HomeField(@StringRes val label: Int) {
    AdspaceId(R.string.field_adspace_id),
    Width(R.string.field_width),
    Height(R.string.field_height),
    ContainerWidth(R.string.field_container_width),
    ContainerHeight(R.string.field_container_height),
}

sealed interface HomeDialog {
    object InvalidConfiguration : HomeDialog
    data class Configured(val packageId: String, val adgeistAppId: String) : HomeDialog
    data class AdLoadFailed(val reason: String) : HomeDialog
    data class InvalidFields(val fields: List<HomeField>) : HomeDialog
}

data class HomeUiState(
    val adRequest: HomeAdRequest? = null,
    val dialog: HomeDialog? = null,
)
