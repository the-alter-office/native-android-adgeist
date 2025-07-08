package com.examplenativeandroidapp

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.adgeistkit.AdgeistCore

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)  // Use XML layout instead of Compose

        // Initialize AdGeistSDK
        val adGeist = AdgeistCore.initialize(applicationContext, "bg-services-api.adgeist.ai")

        // Your existing AdGeist code...
        val getAd = adGeist.getCreative()
        getAd.fetchCreative("7f6b3361bd6d804edfb40cecf3f42e5ebc0b11bd88d96c8a6d64188b93447ad9","https://beta.adgeist.ai", "686149fac1fd09fff371e53c", "67f8ad1350ff1e0870da3f5b") { adData ->
            if (adData != null) {
                Log.d("MyActivity of app module", "${adData}")
            }
        }
    }
}