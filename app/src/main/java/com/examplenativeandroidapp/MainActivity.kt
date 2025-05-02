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

        getAd.fetchCreative("67c99c7a34929568f405e7ff", "67a056c63205fce2290d1cda") { adData ->
            if (adData != null) {
                Log.d("MyActivity of app module", "${adData}")
            } else {
            }
        }

        val postAnalytics = adGeist.postCreativeAnalytics()

        postAnalytics.sendTrackingData("67dd0dca83189049e16b02f6", "67c99c7a34929568f405e7ff", "67a056c63205fce2290d1cda", "click") { adData ->
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