package com.examplenativeandroidapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdRequest
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.adgeistkit.data.models.Event
import com.adgeistkit.data.models.UserDetails

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.log

class MainActivity : AppCompatActivity() {

    private lateinit var adGeist: AdgeistCore
    private lateinit var adView: AdView
    private var lastAdBidId: String? = null
    private var lastAdCampaignId: String? = null
    private var lastAdMetaData: String = ""
    private var lastAdBuyType: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adGeist = AdgeistCore.initialize(applicationContext, "beta.v2.bg-services.adgeist.ai")
        
        setupAdView()

        val loadBannerAdBtn = findViewById<Button>(R.id.loadBannerAdBtn)
    //  val fetchCreativeBtn = findViewById<Button>(R.id.triggerNetworkCall)
    //  val setUserDetailsBtn = findViewById<Button>(R.id.setUserDetailsBtn)
    //  val logEventBtn = findViewById<Button>(R.id.logEventBtn)
    //  val consentBtn = findViewById<Button>(R.id.consentBtn)
    //  val getStatusBtn = findViewById<Button>(R.id.statusBtn)
    //  val trackImpressionBtn = findViewById<Button>(R.id.trackImpressionBtn)
    //  val trackViewBtn = findViewById<Button>(R.id.trackViewBtn)
    //  val trackClickBtn = findViewById<Button>(R.id.trackClickBtn)

        // Fetch Creative
    //  fetchCreativeBtn.setOnClickListener {
    //      makeNetworkCall()
    //  }

        // Set User Details
    //  setUserDetailsBtn.setOnClickListener {
    //      adGeist.requestPhoneStatePermission(this)
    //      val userDetails = UserDetails(
    //          userId = "1",
    //          email = "john@example.com",
    //          phone = "+911234567890",
    //          userName = "kishore"
    //      )
    //      adGeist.setUserDetails(userDetails)
    //  }

        // Get Consent
    //  getStatusBtn.setOnClickListener {
    //      val status = adGeist.getConsentStatus()
    //      Log.d("MainActivity", "Consent: $status")
    //  }


        // Update Consent
    //  logEventBtn.setOnClickListener {
    //      val eventProps = mapOf(
    //          "screen" to "home",
    //          "search_query" to "Moto Edge 50 Pro"
    //      )
    //      val event = Event(
    //          eventType = "search",
    //          eventProperties = eventProps
    //      )
    //      adGeist.logEvent(event)
    //  }

       // Log Event
    //  consentBtn.setOnClickListener {
    //      adGeist.updateConsentStatus(true)
    //  }

        // Track Impression
    //  trackImpressionBtn.setOnClickListener {
    //      CoroutineScope(Dispatchers.IO).launch {
    //          val analytics = adGeist.postCreativeAnalytics()
    //          analytics.trackImpression(
    //              campaignId = lastAdCampaignId ?: "",
    //              adSpaceId = "68e511714b6a95a8d4d1d1c6",
    //              publisherId = "68e4baa14040394a656d5262",
    //              apiKey = "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
    //              bidId = lastAdBidId ?: "",
    //              isTestEnvironment = true,
    //              renderTime = 400f,
    //              bidMeta = lastAdMetaData,
    //              buyType = lastAdBuyType
    //          )
    //      }
    //  }

        // Track View
    //  trackViewBtn.setOnClickListener {
    //      CoroutineScope(Dispatchers.IO).launch {
    //          val analytics = adGeist.postCreativeAnalytics()
    //          analytics.trackView(
    //              campaignId = lastAdCampaignId ?: "",
    //              adSpaceId = "68e511714b6a95a8d4d1d1c6",
    //              publisherId = "68e4baa14040394a656d5262",
    //              apiKey = "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
    //              bidId = lastAdBidId ?: "",
    //              isTestEnvironment = true,
    //              viewTime = 2500f,
    //              visibilityRatio = 0.8f,
    //              scrollDepth = 0.5f,
    //              timeToVisible = 1000f,
    //              bidMeta = lastAdMetaData,
    //              buyType = lastAdBuyType
    //          )
    //      }
    //  }

        // Track Click
    //  trackClickBtn.setOnClickListener {
    //      CoroutineScope(Dispatchers.IO).launch {
    //          val analytics = adGeist.postCreativeAnalytics()
    //          analytics.trackClick(
    //              campaignId = lastAdCampaignId ?: "",
    //              adSpaceId = "68e511714b6a95a8d4d1d1c6",
    //              publisherId = "68e4baa14040394a656d5262",
    //              apiKey = "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
    //              bidId = lastAdBidId ?: "",
    //              isTestEnvironment = true,
    //              bidMeta = lastAdMetaData,
    //              buyType = lastAdBuyType
    //          )
    //      }
    //  }

        loadBannerAdBtn.setOnClickListener {
            loadBannerAd()
        }
    }

    /**
     * Setup AdView with listener (Similar to AdMob pattern)
     */
    private fun setupAdView() {
        adView = findViewById(R.id.adView)
    }

    private fun loadBannerAd() {
        val adRequest = AdRequest.Builder()
            .setTestMode(true)
            .build()
        val dimensions = AdSize(360, 360)
        adView.setAdUnitId("691af20e4d10c63aa7ba7140")
        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                Log.d("Ads", "Ad Loaded!")
            }

            override fun onAdFailedToLoad(error: String) {
                Log.e("Ads", "Ad Failed: $error")
            }

            override fun onAdOpened() {
                Log.d("Ads", "Ad Opened")
            }

            override fun onAdClosed() {
                Log.d("Ads", "Ad Closed")
            }

            override fun onAdClicked() {
                Log.d("Ads", "Ad Clicked")
            }
        })
        adView.setAdSize(dimensions)
        adView.loadAd(adRequest)
    }

   private fun makeNetworkCall() {
       CoroutineScope(Dispatchers.IO).launch {
           val getAd = adGeist.getCreative()
           getAd.fetchCreative(
               "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
               "https://adgeist-ad-integration.d49kd6luw1c4m.amplifyapp.com",
               "68e511714b6a95a8d4d1d1c6",
               "68e4baa14040394a656d5262",
               "FIXED"
           ) { adData ->
               if (adData != null) {
                   when (adData) {
                       is com.adgeistkit.data.models.FixedAdResponse -> {
                           lastAdMetaData = adData.metaData
                           lastAdBuyType = "FIXED"
                       }

                       is com.adgeistkit.data.models.CPMAdResponse -> {
                           lastAdBidId = adData?.data?.id
                           lastAdCampaignId = adData?.data?.seatBid?.getOrNull(0)?.bid?.getOrNull(0)?.id
                           lastAdBuyType = "CPM"
                       }
                   }
               } else {
                   Log.e("MainActivity", "Failed to fetch creative")
               }
           }
       }
   }
}
