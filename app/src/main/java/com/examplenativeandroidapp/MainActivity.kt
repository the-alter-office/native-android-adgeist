package com.examplenativeandroidapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import com.adgeistkit.AdgeistCore
import com.adgeistkit.UserDetails
import com.adgeistkit.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adGeist: AdgeistCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adGeist = AdgeistCore.initialize(applicationContext, "bg-services-api.adgeist.ai")

        val fetchCreativeBtn = findViewById<Button>(R.id.triggerNetworkCall)
        val setUserDetailsBtn = findViewById<Button>(R.id.setUserDetailsBtn)
        val logEventBtn = findViewById<Button>(R.id.logEventBtn)

        // Fetch Creative
        fetchCreativeBtn.setOnClickListener {
            makeNetworkCall()
        }

        // Set User Details
        setUserDetailsBtn.setOnClickListener {
            val userDetails = UserDetails(
                userId = "1",
                email = "john@example.com",
                phone = "+911234567890",
                userName = "kishore"
            )
            adGeist.setUserDetails(userDetails)
        }

        // Log Event
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
    }

    private fun makeNetworkCall() {
        CoroutineScope(Dispatchers.IO).launch {
            val getAd = adGeist.getCreative()
            getAd.fetchCreative(
                "7f6b3361bd6d804edfb40cecf3f42e5ebc0b11bd88d96c8a6d64188b93447ad9",
                "https://beta.adgeist.ai",
                "686149fac1fd09fff371e53c",
                "67f8ad1350ff1e0870da3f5b"
            ) { adData ->
                if (adData != null) {
                    Log.d("MainActivity", "Ad Data: $adData")
                } else {
                    Log.e("MainActivity", "Failed to fetch creative")
                }
            }
        }
    }
}
