package com.examplenativeandroidapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

class ResponsiveBothFragment : Fragment() {

    companion object {
        private const val TAG = "ResponsiveBothFragment"
        private const val RESPONSIVE_AD_UNIT_ID = "6aacd3a4fff212e2a8031309"
    }

    private lateinit var logPanel: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_responsive_both, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logPanel = view.findViewById(R.id.logPanel)
        val container = view.findViewById<FrameLayout>(R.id.responsiveBothContainer)

        val adView = AdView(requireContext()).apply {
            adUnitId = RESPONSIVE_AD_UNIT_ID
            adIsResponsive = true
        }

        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                appendLog("onAdLoaded")
            }

            override fun onAdWarning(message: String) {
                appendLog("onAdWarning: $message")
            }

            override fun onAdFailedToLoad(error: String) {
                appendLog("onAdFailedToLoad: $error")
            }
        })

        container.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun appendLog(line: String) {
        if (!isAdded) return
        logPanel.append(if (logPanel.text.isEmpty()) line else "\n\n$line")
    }
}
