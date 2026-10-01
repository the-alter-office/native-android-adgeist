package com.examplecomposeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.examplecomposeapp.feature.fixed.FixedAdScreen
import com.examplecomposeapp.feature.home.HomeRoute
import com.examplecomposeapp.feature.responsiveboth.ResponsiveBothScreen
import com.examplecomposeapp.feature.responsivehorizontal.ResponsiveHorizontalScreen
import com.examplecomposeapp.feature.responsivescroll.ResponsiveScrollScreen
import com.examplecomposeapp.feature.responsivevertical.ResponsiveVerticalScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.start.route,
        modifier = modifier,
    ) {
        composable(AppDestination.Home.route) { HomeRoute() }
        composable(AppDestination.Fixed.route) { FixedAdScreen() }
        composable(AppDestination.ResponsiveBoth.route) { ResponsiveBothScreen() }
        composable(AppDestination.ResponsiveVertical.route) { ResponsiveVerticalScreen() }
        composable(AppDestination.ResponsiveHorizontal.route) { ResponsiveHorizontalScreen() }
        composable(AppDestination.ResponsiveScroll.route) { ResponsiveScrollScreen() }
    }
}

fun NavHostController.navigateToDestination(destination: AppDestination) {
    val current = AppDestination.fromRoute(currentBackStackEntry?.destination?.route)
    if (destination == current) return

    if (destination == AppDestination.start) {
        popBackStack(AppDestination.start.route, inclusive = false)
        return
    }

    navigate(destination.route) {
        launchSingleTop = true
    }
}
