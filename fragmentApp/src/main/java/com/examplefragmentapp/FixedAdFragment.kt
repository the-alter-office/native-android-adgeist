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

class FixedAdFragment : Fragment() {

    companion object {
        private const val TAG = "FixedAdFragment"
        private const val FIXED_AD_UNIT_ID = "6aacd70d7935d686e83fb1aa"
        private const val INVALID_AD_UNIT_ID = "000000000000000000000000"
    }

    private lateinit var logPanel: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_fixed_ad, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        logPanel = view.findViewById(R.id.logPanel)

        loadFixedAd(view.findViewById(R.id.fixedAdContainer))
        loadFailingAd(view.findViewById(R.id.collapsingAdContainer), reserve = false, tag = "B")
        loadFailingAd(view.findViewById(R.id.reservedAdContainer), reserve = true, tag = "C")
    }

    private fun loadFixedAd(container: FrameLayout) {
        val adView = AdView(requireContext()).apply {
            adUnitId = FIXED_AD_UNIT_ID
            adIsResponsive = false
            reserveSpace = true
            setAdDimension(AdSize(250, 250))
        }

        adView.setAdListener(screenListener("A"))
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun loadFailingAd(container: FrameLayout, reserve: Boolean, tag: String) {
        val adView = AdView(requireContext()).apply {
            adUnitId = INVALID_AD_UNIT_ID
            adIsResponsive = false
            reserveSpace = reserve
            setAdDimension(AdSize(360, 360))
        }

        adView.setAdListener(screenListener(tag))
        container.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun screenListener(tag: String) = object : AdListener() {
        override fun onAdLoaded() {
            appendLog("[$tag] onAdLoaded")
        }

        override fun onAdWarning(message: String) {
            appendLog("[$tag] onAdWarning: $message")
        }

        override fun onAdFailedToLoad(error: String) {
            appendLog("[$tag] onAdFailedToLoad: $error")
        }
    }

    private fun appendLog(line: String) {
        if (!isAdded) return
        logPanel.append(if (logPanel.text.isEmpty()) line else "\n\n$line")
    }
}
