package com.examplecomposeapp.feature.fixed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.adgeistkit.ads.AdSize
import com.examplecomposeapp.R
import com.examplecomposeapp.ui.components.AdPlaceholder
import com.examplecomposeapp.ui.components.ScreenDescription
import com.examplecomposeapp.ui.components.ScreenTitle
import com.examplecomposeapp.ui.components.SectionDivider
import com.examplecomposeapp.ui.components.SectionLabel
import com.examplecomposeapp.ui.components.ads.AdEventLog
import com.examplecomposeapp.ui.components.ads.AdEventLogPanel
import com.examplecomposeapp.ui.components.ads.AdSlot
import com.examplecomposeapp.ui.components.ads.AdUnits
import com.examplecomposeapp.ui.components.ads.rememberAdEventLog
import com.examplecomposeapp.ui.theme.PlaceholderBlue
import com.examplecomposeapp.ui.theme.PlaceholderRed

@Composable
fun FixedAdScreen(modifier: Modifier = Modifier) {
    val log = rememberAdEventLog()
    val side = Modifier.padding(horizontal = 16.dp)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        ScreenTitle(stringResource(R.string.fixed_title), side)
        ScreenDescription(stringResource(R.string.fixed_description), side)

        SectionLabel(stringResource(R.string.fixed_ad_a_label), side.padding(top = 16.dp))
        AdPlaceholder(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
        ) {
            AdSlot(
                configure = {
                    adUnitId = AdUnits.FIXED
                    adIsResponsive = false
                    reserveSpace = true
                    setAdDimension(AdSize(250, 250))
                },
                listener = log.listener("A"),
            )
        }

        SectionDivider(side)
        SectionLabel(stringResource(R.string.fixed_ad_b_label), side)
        ScreenDescription(stringResource(R.string.fixed_ad_b_description), side)
        AdPlaceholder(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
            color = PlaceholderRed,
        ) {
            FailingAd(log, reserve = false, tag = "B")
        }

        SectionDivider(side)
        SectionLabel(stringResource(R.string.fixed_ad_c_label), side)
        ScreenDescription(stringResource(R.string.fixed_ad_c_description), side)
        AdPlaceholder(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp),
            color = PlaceholderBlue,
        ) {
            FailingAd(log, reserve = true, tag = "C")
        }

        AdEventLogPanel(log, side)
    }
}

@Composable
private fun FailingAd(log: AdEventLog, reserve: Boolean, tag: String) {
    AdSlot(
        configure = {
            adUnitId = AdUnits.INVALID
            adIsResponsive = false
            reserveSpace = reserve
            setAdDimension(AdSize(360, 360))
        },
        listener = log.listener(tag),
    )
}
