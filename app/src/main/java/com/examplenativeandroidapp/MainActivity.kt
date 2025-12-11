package com.examplenativeandroidapp

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdView
import com.adgeistkit.ads.network.AdRequest

class MainActivity : AppCompatActivity() {
    private lateinit var adGeist: AdgeistCore

    private lateinit var publisherIdInput: EditText
    private lateinit var adspaceIdInput: EditText
    private lateinit var adspaceTypeInput: EditText
    private lateinit var originInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var generateAdBtn: Button
    private lateinit var cancelAdBtn: Button
    private lateinit var adContainer: LinearLayout
    private lateinit var testModeSwitch: SwitchCompat

    private var currentAdView: AdView? = null

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adGeist = AdgeistCore.initialize(applicationContext, "beta.v2.bg-services.adgeist.ai")

        publisherIdInput = findViewById(R.id.publisherIdInput)
        adspaceIdInput = findViewById(R.id.adspaceIdInput)
        adspaceTypeInput = findViewById(R.id.adspaceTypeInput)
        originInput = findViewById(R.id.originInput)
        widthInput = findViewById(R.id.widthInput)
        heightInput = findViewById(R.id.heightInput)
        generateAdBtn = findViewById(R.id.generateAdBtn)
        cancelAdBtn = findViewById(R.id.cancelAdBtn)
        adContainer = findViewById(R.id.adContainer)
        testModeSwitch = findViewById(R.id.testModeSwitch)

        generateAdBtn.isEnabled = true
        cancelAdBtn.isEnabled = false

        generateAdBtn.setOnClickListener {
            loadNewAd()
        }

        cancelAdBtn.setOnClickListener {
            destroyCurrentAd()
            clearInputFields()
        }
    }

    private fun loadNewAd() {
        destroyCurrentAd()

//        val publisherId = "69326f9fbb280f9241cabc94"
//        val adspaceId = "6932a4c022f6786424ce3b84"
//        val adSpaceType = "display"
//        val origin = "https://adgeist-ad-integration.d49kd6luw1c4m.amplifyapp.com"
//        val width = 320
//        val height = 480
        
         val publisherId = publisherIdInput.text.toString().trim()
         val adspaceId = adspaceIdInput.text.toString().trim()
         val adSpaceType = adspaceTypeInput.text.toString().trim()
         val origin = originInput.text.toString().trim()
         val width = widthInput.text.toString().toIntOrNull() ?: 0
         val height = heightInput.text.toString().toIntOrNull() ?: 0

        val missingFields = mutableListOf<String>()

        if (publisherId.isEmpty()) missingFields.add("Publisher ID")
        if (adspaceId.isEmpty()) missingFields.add("Adspace ID")
        if (adSpaceType.isEmpty()) missingFields.add("Adspace Type")
        if (origin.isEmpty()) missingFields.add("Origin")
        if (width <= 0) missingFields.add("Width")
        if (height <= 0) missingFields.add("Height")

        if (missingFields.isNotEmpty()) {
            val message = "Please enter valid values for: ${missingFields.joinToString(", ")}"

            val dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle("Invalid fields")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()

            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(this@MainActivity, R.color.teal_700)
            )
            return
        }

        val pxWidth = dpToPx(width)
        val pxHeight = dpToPx(height)
        adContainer.layoutParams.apply {
            this.width = pxWidth
            this.height = pxHeight
        }
        adContainer.visibility = View.VISIBLE
 
        // Create a BRAND NEW AdView instance
        val adView = AdView(this).apply {
            layoutParams = LinearLayout.LayoutParams(pxWidth, pxHeight)
        }

        // Add to container
        adContainer.removeAllViews()
        adContainer.addView(adView)

        adView.adUnitId = adspaceId
        adView.adType = adSpaceType
        adView.appId = publisherId

        if(origin.isNotEmpty()){
            adView.customOrigin = origin
        }
        adView.setAdDimension(AdSize(width, height))

        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdView", "Ad Loaded Successfully!")
                adView.visibility = View.VISIBLE
//                runOnUiThread {
//                    generateAdBtn.isEnabled = false
//                    cancelAdBtn.isEnabled = true
//                }
            }

            override fun onAdFailedToLoad(error: String) {
                Log.e("AdView", "Ad Failed to Load: $error")
                val dialog = AlertDialog.Builder(this@MainActivity)
                            .setTitle("Ad Load Failed")
                            .setMessage("Reason: $error")
                            .setPositiveButton("OK", null)
                            .show()

                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.teal_700)
                )

                destroyCurrentAd()
                clearInputFields()
            }

            override fun onAdClicked() {
                Log.d("AdView", "Ad Clicked")
            }

            override fun onAdOpened() {
                Log.d("AdView", "Ad Opened")
            }

            override fun onAdClosed() {
                Log.d("AdView", "Ad Closed")
            }
        })

        val adRequest = AdRequest.Builder()
            .setTestMode(testModeSwitch.isChecked)
            .build()
        adView.loadAd(adRequest)

        currentAdView = adView
    }

    private fun destroyCurrentAd() {
        currentAdView?.let { adView ->
            adView.destroy()
            (adView.parent as? ViewGroup)?.removeView(adView)
        }
        currentAdView = null

        adContainer.visibility = View.GONE
        generateAdBtn.isEnabled = true
        cancelAdBtn.isEnabled = false
    }

    private fun clearInputFields(){
        publisherIdInput.text.clear()
        adspaceIdInput.text.clear()
        originInput.text.clear()
        adspaceTypeInput.text.clear()
        widthInput.text.clear()
        heightInput.text.clear()
    }

    override fun onDestroy() {
        destroyCurrentAd()
        clearInputFields()
        super.onDestroy()
    }
}