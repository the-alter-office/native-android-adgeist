package com.examplecomposeapp.ui.components.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

@Composable
fun AdSlot(
    configure: AdView.() -> Unit,
    listener: (AdView) -> AdListener,
    modifier: Modifier = Modifier,
    onCreated: (AdView) -> Unit = {},
) {
    val owner = LocalViewModelStoreOwner.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdView(context).apply {
                viewModelStoreOwner = owner
                configure()
                setAdListener(listener(this))
                onCreated(this)
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { },
    )
}
