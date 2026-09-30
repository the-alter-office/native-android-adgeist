package com.examplecomposeapp.feature.responsiveboth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.examplecomposeapp.R
import com.examplecomposeapp.ui.components.AdPlaceholder
import com.examplecomposeapp.ui.components.ScreenDescription
import com.examplecomposeapp.ui.components.ScreenTitle
import com.examplecomposeapp.ui.components.ads.AdEventLogPanel
import com.examplecomposeapp.ui.components.ads.AdSlot
import com.examplecomposeapp.ui.components.ads.AdUnits
import com.examplecomposeapp.ui.components.ads.rememberAdEventLog

@Composable
fun ResponsiveBothScreen(modifier: Modifier = Modifier) {
    val log = rememberAdEventLog()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenTitle(stringResource(R.string.responsive_both_title))
        ScreenDescription(stringResource(R.string.responsive_both_description))

        AdPlaceholder(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
                .size(300.dp, 250.dp)
        ) {
            AdSlot(
                configure = {
                    adUnitId = AdUnits.RESPONSIVE
                    adIsResponsive = true
                },
                listener = log.listener(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        AdEventLogPanel(log)
    }
}
