package com.examplecomposeapp.feature.responsivescroll

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adgeistkit.ads.AdSize
import com.examplecomposeapp.R
import com.examplecomposeapp.ui.components.AdPlaceholder
import com.examplecomposeapp.ui.components.ScreenDescription
import com.examplecomposeapp.ui.components.ScreenTitle
import com.examplecomposeapp.ui.components.SectionLabel
import com.examplecomposeapp.ui.components.ads.AdEventLog
import com.examplecomposeapp.ui.components.ads.AdEventLogPanel
import com.examplecomposeapp.ui.components.ads.AdSlot
import com.examplecomposeapp.ui.components.ads.AdUnits
import com.examplecomposeapp.ui.components.ads.rememberAdEventLog
import com.examplecomposeapp.ui.theme.PlaceholderGreen
import com.examplecomposeapp.ui.theme.PlaceholderRed

@Composable
fun ResponsiveScrollScreen(modifier: Modifier = Modifier) {
    val log = rememberAdEventLog()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenTitle(stringResource(R.string.responsive_scroll_title))
        ScreenDescription(stringResource(R.string.responsive_scroll_description))

        SectionLabel(stringResource(R.string.responsive_scroll_ad_a_label), Modifier.padding(top = 16.dp))
        ScrollAd(log, PlaceholderGreen, AdSize.height(250), "A")

        SectionLabel(stringResource(R.string.responsive_scroll_ad_b_label), Modifier.padding(top = 24.dp))
        ScreenDescription(stringResource(R.string.responsive_scroll_ad_b_description))
        ScrollAd(log, PlaceholderRed, null, "B")

        AdEventLogPanel(log)
    }
}

@Composable
private fun ScrollAd(log: AdEventLog, color: Color, size: AdSize?, tag: String) {
    AdPlaceholder(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth(),
        color = color,
    ) {
        AdSlot(
            configure = {
                adUnitId = AdUnits.RESPONSIVE
                adIsResponsive = true
                size?.let { setAdDimension(it) }
            },
            listener = log.listener(tag),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
