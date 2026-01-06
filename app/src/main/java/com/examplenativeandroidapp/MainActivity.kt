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
import com.adgeistkit.ads.network.AnalyticsRequestDEPRECATED

class MainActivity : AppCompatActivity() {
    private lateinit var adGeist: AdgeistCore

    // Configuration Section
    private lateinit var packageIdInput: EditText
    private lateinit var adgeistAppIdInput: EditText
    private lateinit var configureBtn: Button

    // Ad Loading Section
    private lateinit var adspaceIdInput: EditText
    private lateinit var adspaceTypeInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var generateAdBtn: Button
    private lateinit var cancelAdBtn: Button
    private lateinit var adContainer: LinearLayout
    private lateinit var testModeSwitch: SwitchCompat

    private var currentAdView: AdView? = null

    private val defaultPackageId = "com.examplenativeandroidapp"
    private val defaultAdgeistAppId = "6954e6859ab54390db01e3d7"
    private val defaultBidRequestBackendDomain = "https://beta.v2.bg-services.adgeist.ai"

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdgeistCore with default packageId from build.gradle.kts
        adGeist = AdgeistCore.initialize(applicationContext)

        // Configuration Section
        packageIdInput = findViewById(R.id.packageIdInput)
        adgeistAppIdInput = findViewById(R.id.adgeistAppIdInput)
        configureBtn = findViewById(R.id.configureBtn)

        // Ad Loading Section
        adspaceIdInput = findViewById(R.id.adspaceIdInput)
        adspaceTypeInput = findViewById(R.id.adspaceTypeInput)
        widthInput = findViewById(R.id.widthInput)
        heightInput = findViewById(R.id.heightInput)
        generateAdBtn = findViewById(R.id.generateAdBtn)
        cancelAdBtn = findViewById(R.id.cancelAdBtn)
        adContainer = findViewById(R.id.adContainer)
        testModeSwitch = findViewById(R.id.testModeSwitch)

        // Set default values in input fields
        packageIdInput.setText(defaultPackageId)
        adgeistAppIdInput.setText(defaultAdgeistAppId)

        generateAdBtn.isEnabled = true
        cancelAdBtn.isEnabled = false

        // Configuration button listener
        configureBtn.setOnClickListener {
            configureSDK()
        }

        // Generate Ad button listener
        generateAdBtn.setOnClickListener {
            loadNewAd()
        }

        // Cancel button listener
        cancelAdBtn.setOnClickListener {
            destroyCurrentAd()
            clearInputFields()
        }
    }

    private fun configureSDK() {
        val packageId = packageIdInput.text.toString().trim()
        val adgeistAppId = adgeistAppIdInput.text.toString().trim()

        if (packageId.isEmpty() || adgeistAppId.isEmpty()) {
            showAlertDialog("Invalid Configuration", "Please enter valid Package ID and Adgeist App ID")
            return
        }

        // Reinitialize AdgeistCore with new configuration
        adGeist = AdgeistCore.initialize(applicationContext, defaultBidRequestBackendDomain, packageId, adgeistAppId)
        
        showAlertDialog("Success", "SDK configured successfully with:\nPackage ID: $packageId\nApp ID: $adgeistAppId")
        Log.d("MainActivity", "SDK reinitialized with Package ID: $packageId, App ID: $adgeistAppId")
    }

    private fun loadNewAd() {
        destroyCurrentAd()

        val adspaceId = "695baa786c59cd9c0bd23ff0"
        val adSpaceType = "banner"
        val width = 320
        val height = 100

        // val adspaceId = adspaceIdInput.text.toString().trim()
        // val adSpaceType = adspaceTypeInput.text.toString().trim()
        // val width = widthInput.text.toString().toIntOrNull() ?: 0
        // val height = heightInput.text.toString().toIntOrNull() ?: 0

        val missingFields = mutableListOf<String>()

        if (adspaceId.isEmpty()) missingFields.add("Adspace ID")
        if (adSpaceType.isEmpty()) missingFields.add("Adspace Type")
        if (width <= 0) missingFields.add("Width")
        if (height <= 0) missingFields.add("Height")

        if (missingFields.isNotEmpty()) {
            val message = "Please enter valid values for: ${missingFields.joinToString(", ")}"
            showAlertDialog("Invalid Fields", message)
            return
        }

        val pxWidth = dpToPx(width)
        val pxHeight = dpToPx(height)
        adContainer.layoutParams.apply {
            this.width = pxWidth
            this.height = pxHeight
        }
        adContainer.visibility = View.VISIBLE
 
        // Create a new AdView instance
        val adView = AdView(this).apply {
            layoutParams = LinearLayout.LayoutParams(pxWidth, pxHeight)
        }

        // Add to container
        adContainer.removeAllViews()
        adContainer.addView(adView)

        adView.adUnitId = adspaceId
        adView.adType = adSpaceType

        adView.setAdDimension(AdSize(width, height))

        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdView", "Ad Loaded Successfully!")
                adView.visibility = View.VISIBLE
                runOnUiThread {
                    generateAdBtn.isEnabled = false
                    cancelAdBtn.isEnabled = true
                }
            }

            override fun onAdFailedToLoad(error: String) {
                Log.e("AdView", "Ad Failed to Load: $error")
                showAlertDialog("Ad Load Failed", "Reason: $error")
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
        adspaceIdInput.text.clear()
        adspaceTypeInput.text.clear()
        widthInput.text.clear()
        heightInput.text.clear()
    }

    override fun onDestroy() {
        adspaceIdInput.text.clear()
        adspaceTypeInput.text.clear()
        widthInput.text.clear()
        heightInput.text.clear()
        super.onDestroy()
    }

    private fun showAlertDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(this@MainActivity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(this@MainActivity, R.color.teal_700)
        )
    }
}