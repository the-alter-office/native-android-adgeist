package com.examplenativeandroidapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.examplenativeandroidapp.ui.theme.NativeandroidadgeistTheme
import com.adgeistkit.AdgeistCore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize AdGeistSDK
        val adGeist = AdgeistCore.initialize(applicationContext)

        // Create LoadAd instance
        val getAd = adGeist.getCreative()

        getAd.fetchCreative("b1a3823498a258d8ed334145eb7153c1fec8150d67121139ef8681a4782d40dd","https://beta.adgeist.ai", "684a806cbaa6cffe4088eaf9", "67a056c63205fce2290d1cda") { adData ->
            if (adData != null) {
                Log.d("MyActivity of app module", "${adData}")
            } else {
            }
        }

        val postAnalytics = adGeist.postCreativeAnalytics()

        postAnalytics.sendTrackingData("685d4d85d589952479188259", "67f8af1850ff1e0870da3fbe", "67f8ad1350ff1e0870da3f5b" ,"CLICK", "https://beta.adgeist.ai", "1401f7740ea15573c05a39a4de72396d609ff931722c1b87aa6b98bdce2b2ba8", "d80b43ee-b85e-4de1-b81d-8a6a79c162d5") { adData ->
            if (adData != null) {
                Log.d("MyActivity of app module", "${adData}")
            } else {
            }
        }

        enableEdgeToEdge()
        setContent {
            NativeandroidadgeistTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NativeandroidadgeistTheme {
        Greeting("Android")
    }
}