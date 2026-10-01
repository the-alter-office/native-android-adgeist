package com.examplecomposeapp.navigation

import androidx.annotation.StringRes
import com.examplecomposeapp.R

enum class AppDestination(val route: String, @StringRes val title: Int) {
    Home("home", R.string.destination_home),
    Fixed("fixed", R.string.destination_fixed),
    ResponsiveBoth("responsive_both", R.string.destination_responsive_both),
    ResponsiveVertical("responsive_vertical", R.string.destination_responsive_vertical),
    ResponsiveHorizontal("responsive_horizontal", R.string.destination_responsive_horizontal),
    ResponsiveScroll("responsive_scroll", R.string.destination_responsive_scroll);

    companion object {
        val start: AppDestination = Home

        fun fromRoute(route: String?): AppDestination = values().firstOrNull { it.route == route } ?: start
    }
}
