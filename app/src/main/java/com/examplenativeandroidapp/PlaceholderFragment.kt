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

class PlaceholderFragment : Fragment() {

    companion object {
        private const val TAG = "PlaceholderFragment"
        private const val ARG_TITLE = "title"

        private const val SHARED_AD_UNIT_ID = "69ca2675576a0a20dd6c6cfb"

        fun newInstance(title: String): PlaceholderFragment {
            return PlaceholderFragment().apply {
                arguments = Bundle().apply { putString(ARG_TITLE, title) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_placeholder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = arguments?.getString(ARG_TITLE) ?: "Screen"
        view.findViewById<TextView>(R.id.screenTitle).text = title

        val adContainer = view.findViewById<FrameLayout>(R.id.placeholderAdContainer)

        val adView = AdView(requireContext()).apply {
            adUnitId = SHARED_AD_UNIT_ID
            adIsResponsive = true
        }
        adContainer.addView(
            adView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                Log.d(TAG, "[$title] ad loaded (adopted or fetched - see BaseAdView logs)")
            }

            override fun onAdFailedToLoad(error: String) {
                Log.e(TAG, "[$title] ad failed to load: $error")
            }
        })

        adView.loadAd(AdRequest.Builder().build())
    }
}
