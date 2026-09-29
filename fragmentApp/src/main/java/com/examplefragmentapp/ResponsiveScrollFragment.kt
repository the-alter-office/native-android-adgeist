package com.examplefragmentapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

class ResponsiveScrollFragment : Fragment() {

    companion object {
        private const val TAG = "ResponsiveScrollFragment"
        private const val RESPONSIVE_AD_UNIT_ID = "6aacd3a4fff212e2a8031309"
    }

    private lateinit var logPanel: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_responsive_scroll, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logPanel = view.findViewById(R.id.logPanel)

        loadAd(view.findViewById(R.id.scrollAdGoodContainer), AdSize.height(250), "A")
        loadAd(view.findViewById(R.id.scrollAdBadContainer), null, "B")
    }

    private fun loadAd(container: FrameLayout, size: AdSize?, tag: String) {
        val adView = AdView(requireContext()).apply {
            adUnitId = RESPONSIVE_AD_UNIT_ID
            adIsResponsive = true
            size?.let { setAdDimension(it) }
        }

        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                appendLog("[$tag] onAdLoaded")
            }

            override fun onAdWarning(message: String) {
                appendLog("[$tag] onAdWarning: $message")
            }

            override fun onAdFailedToLoad(error: String) {
                appendLog("[$tag] onAdFailedToLoad: $error")
            }
        })

        container.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun appendLog(line: String) {
        if (!isAdded) return
        logPanel.append(if (logPanel.text.isEmpty()) line else "\n\n$line")
    }
}
