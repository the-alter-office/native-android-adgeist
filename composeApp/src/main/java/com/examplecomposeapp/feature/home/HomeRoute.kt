package com.examplecomposeapp.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adgeistkit.ads.AdView

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liveAdViews = remember { mutableListOf<AdView>() }

    HomeScreen(
        form = viewModel.form,
        uiState = uiState,
        onFormChange = viewModel::onFormChange,
        onResponsiveChange = viewModel::onResponsiveChange,
        onConfigureClick = viewModel::onConfigureClick,
        onGetAdClick = viewModel::onGetAdClick,
        onCancelClick = {
            liveAdViews.forEach { it.destroyAd() }
            liveAdViews.clear()
            viewModel.onCancelClick()
        },
        onAdCreated = { adView ->
            liveAdViews.clear()
            liveAdViews.add(adView)
        },
        onAdFailed = viewModel::onAdFailed,
        onDialogDismiss = viewModel::onDialogDismiss,
        modifier = modifier,
    )
}
