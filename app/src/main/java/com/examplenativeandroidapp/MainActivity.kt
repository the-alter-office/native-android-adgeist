package com.examplenativeandroidapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import com.adgeistkit.AdgeistCore
import com.adgeistkit.data.models.Event
import com.adgeistkit.data.models.UserDetails

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adGeist: AdgeistCore
    private var lastAdBidId: String? = null
    private var lastAdCampaignId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adGeist = AdgeistCore.initialize(applicationContext, "bg-services-qa-api.adgeist.ai")

        val fetchCreativeBtn = findViewById<Button>(R.id.triggerNetworkCall)
        val setUserDetailsBtn = findViewById<Button>(R.id.setUserDetailsBtn)
        val logEventBtn = findViewById<Button>(R.id.logEventBtn)
        val consentBtn = findViewById<Button>(R.id.consentBtn)
        val getStatusBtn = findViewById<Button>(R.id.statusBtn)
        val trackImpressionBtn = findViewById<Button>(R.id.trackImpressionBtn)
        val trackViewBtn = findViewById<Button>(R.id.trackViewBtn)
        val trackClickBtn = findViewById<Button>(R.id.trackClickBtn)

        // Fetch Creative
        fetchCreativeBtn.setOnClickListener {
            makeNetworkCall()
        }

        // Set User Details
        setUserDetailsBtn.setOnClickListener {
            adGeist.requestPhoneStatePermission(this)
            val userDetails = UserDetails(
                userId = "1",
                email = "john@example.com",
                phone = "+911234567890",
                userName = "kishore"
            )
            adGeist.setUserDetails(userDetails)
        }

        // Get Consent
        getStatusBtn.setOnClickListener {
             val status = adGeist.getConsentStatus()
            Log.d("MainActivity", "Consent: $status")
        }


        // Update Consent
        logEventBtn.setOnClickListener {
            val eventProps = mapOf(
                "screen" to "home",
                "search_query" to "Moto Edge 50 Pro"
            )
            val event = Event(
                eventType = "search",
                eventProperties = eventProps
            )
            adGeist.logEvent(event)
        }

        // Log Event
        consentBtn.setOnClickListener {
            adGeist.updateConsentStatus(true)
        }

        // Track Impression
        trackImpressionBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val analytics = adGeist.postCreativeAnalytics()
                analytics.trackImpression(
                    campaignId = lastAdCampaignId ?: "",
                    adSpaceId = "68e511714b6a95a8d4d1d1c6",
                    publisherId = "68e4baa14040394a656d5262",
                    apiKey = "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
                    bidId = lastAdBidId ?: "",
                    isTestEnvironment = true,
                    renderTime = 400f
                )
            }
        }

        // Track View
        trackViewBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val analytics = adGeist.postCreativeAnalytics()
                analytics.trackView(
                    campaignId = lastAdCampaignId ?: "",
                    adSpaceId = "68e511714b6a95a8d4d1d1c6",
                    publisherId = "68e4baa14040394a656d5262",
                    apiKey = "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
                    bidId = lastAdBidId ?: "",
                    isTestEnvironment = true,
                    viewTime = 2500f,
                    visibilityRatio = 0.8f,
                    scrollDepth = 0.5f,
                    timeToVisible = 1000f,
                )
            }
        }

        // Track Click
        trackClickBtn.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                val analytics = adGeist.postCreativeAnalytics()
                analytics.trackClick(
                    campaignId = lastAdCampaignId ?: "",
                    adSpaceId = "68e511714b6a95a8d4d1d1c6",
                    publisherId = "68e4baa14040394a656d5262",
                    apiKey = "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
                    bidId = lastAdBidId ?: "",
                    isTestEnvironment = true
                )
            }
        }
    }

    private fun makeNetworkCall() {
        CoroutineScope(Dispatchers.IO).launch {
            val getAd = adGeist.getCreative()
            getAd.fetchCreative(
                "48ad37bbe0c4091dee7c4500bc510e4fca6e7f7a1c293180708afa292820761c",
                "https://adgeist-ad-integration.d49kd6luw1c4m.amplifyapp.com",
                "68e511714b6a95a8d4d1d1c6",
                "68e4baa14040394a656d5262",
            ) { adData ->
                if (adData != null) {
                    lastAdBidId = adData?.data?.id
                    lastAdCampaignId = adData?.data?.seatBid?.getOrNull(0)?.bid?.getOrNull(0)?.id
                    Log.d("MainActivity", "Ad Data: $adData")
                } else {
                    Log.e("MainActivity", "Failed to fetch creative")
                }
            }
        }
    }
}
