package com.examplecomposeapp.feature.responsivevertical

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adgeistkit.ads.AdSize
import com.examplecomposeapp.R
import com.examplecomposeapp.ui.components.AdPlaceholder
import com.examplecomposeapp.ui.components.ScreenDescription
import com.examplecomposeapp.ui.components.ScreenTitle
import com.examplecomposeapp.ui.components.ads.AdEventLogPanel
import com.examplecomposeapp.ui.components.ads.AdSlot
import com.examplecomposeapp.ui.components.ads.AdUnits
import com.examplecomposeapp.ui.components.ads.rememberAdEventLog

@Composable
fun ResponsiveVerticalScreen(modifier: Modifier = Modifier) {
    val log = rememberAdEventLog()

    Row(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ScreenTitle(stringResource(R.string.responsive_vertical_title))
            ScreenDescription(stringResource(R.string.responsive_vertical_description))
            AdEventLogPanel(log)
        }

        AdPlaceholder(Modifier.fillMaxHeight()) {
            AdSlot(
                configure = {
                    adUnitId = AdUnits.RESPONSIVE
                    adIsResponsive = true
                    setAdDimension(AdSize.width(120))
                },
                listener = log.listener(),
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}
