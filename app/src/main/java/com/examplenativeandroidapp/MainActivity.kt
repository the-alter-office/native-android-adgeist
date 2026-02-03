package com.examplenativeandroidapp

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.FrameLayout
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
    private lateinit var responsiveContainer: FrameLayout
    private lateinit var testModeSwitch: SwitchCompat
    private lateinit var responsiveAdSwitch: SwitchCompat
    private lateinit var responsiveSizeSection: LinearLayout
    private lateinit var containerWidthInput: EditText
    private lateinit var containerHeightInput: EditText

    private var currentAdView: AdView? = null

    private val defaultPackageId = "com.kke.adid"
    private val defaultAdgeistAppId = "695e797d6fcfb14c38cfd1d6"
    private val defaultBidRequestBackendDomain = "https://qa.v2.bg-services.adgeist.ai"

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AdgeistCore with default packageId from build.gradle.kts
        adGeist = AdgeistCore.initialize(applicationContext)

        // Handle deeplink UTM parameters
        handleDeeplinkUtm(intent)

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
        responsiveContainer = findViewById(R.id.responsiveContainer)
        testModeSwitch = findViewById(R.id.testModeSwitch)
        responsiveAdSwitch = findViewById(R.id.responsiveAdSwitch)
        responsiveSizeSection = findViewById(R.id.responsiveSizeSection)
        containerWidthInput = findViewById(R.id.containerWidthInput)
        containerHeightInput = findViewById(R.id.containerHeightInput)

        // Set default values in input fields
        packageIdInput.setText(defaultPackageId)
        adgeistAppIdInput.setText(defaultAdgeistAppId)

        generateAdBtn.isEnabled = true
        cancelAdBtn.isEnabled = false

        // Responsive ad switch listener
        responsiveAdSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                responsiveSizeSection.visibility = View.VISIBLE
                widthInput.isEnabled = false
                heightInput.isEnabled = false
                // Set default container sizes
                containerWidthInput.setText("300")
                containerHeightInput.setText("250")
            } else {
                responsiveSizeSection.visibility = View.GONE
                widthInput.isEnabled = true
                heightInput.isEnabled = true
            }
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeeplinkUtm(intent)
    }

    /**
     * Handle deeplink UTM parameters from intent
     */
    private fun handleDeeplinkUtm(intent: Intent?) {
        intent?.data?.let { uri ->
            Log.d("MainActivity", "Deeplink received: $uri")
            
            // Track UTM parameters from deeplink
            adGeist.trackUtmFromDeeplink(uri)
            
            // Retrieve and log UTM parameters
            val utmParams = adGeist.getUtmParameters()
            utmParams?.let {
                Log.d("MainActivity", "UTM Parameters tracked:")
                Log.d("MainActivity", "  Source: ${it.source}")
                Log.d("MainActivity", "  Medium: ${it.medium}")
                Log.d("MainActivity", "  Campaign: ${it.campaign}")
                Log.d("MainActivity", "  Term: ${it.term}")
                Log.d("MainActivity", "  Content: ${it.content}")
                Log.d("MainActivity", "  Timestamp: ${it.timestamp}")
                Log.d("MainActivity", "  X Data: ${it.x_data}")
                
                showAlertDialog(
                    "UTM Parameters Tracked",
                    "Source: ${it.source ?: "N/A"}\n" +
                    "Medium: ${it.medium ?: "N/A"}\n" +
                    "Campaign: ${it.campaign ?: "N/A"}\n" +
                    "Term: ${it.term ?: "N/A"}\n" +
                    "Content: ${it.content ?: "N/A"}\n" +
                    "Timestamp: ${it.timestamp ?: "N/A"}\n" +
                    "X Data: ${it.x_data ?: "N/A"}" 
                )
            }
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
        AdgeistCore.destroy()
        adGeist = AdgeistCore.initialize(applicationContext, defaultBidRequestBackendDomain, packageId, adgeistAppId)
        
        showAlertDialog("Success", "SDK configured successfully with:\nPackage ID: $packageId\nApp ID: $adgeistAppId")
        Log.d("MainActivity", "SDK reinitialized with Package ID: $packageId, App ID: $adgeistAppId")
    }

    private fun loadNewAd() {
        destroyCurrentAd()

        val adspaceId = "695e828d6fcfb14c38cfd3b1"
        val adSpaceType = "banner"
        val width = 250
        val height = 250
        val containerWidth = 300
        val containerHeight = 250

        val isResponsive = responsiveAdSwitch.isChecked

        // val containerWidth = containerWidthInput.text.toString().toIntOrNull()
        // val containerHeight = containerHeightInput.text.toString().toIntOrNull()
        // val adspaceId = adspaceIdInput.text.toString().trim()
        // val adSpaceType = adspaceTypeInput.text.toString().trim()
        // val width = widthInput.text.toString().toIntOrNull() ?: 0
        // val height = heightInput.text.toString().toIntOrNull() ?: 0

        val missingFields = mutableListOf<String>()

        if (adspaceId.isEmpty()) missingFields.add("Adspace ID")
        if (adSpaceType.isEmpty()) missingFields.add("Adspace Type")
        
        if (!isResponsive) {
            if (width <= 0) missingFields.add("Width")
            if (height <= 0) missingFields.add("Height")
        } else {
            if (containerWidth <= 0) missingFields.add("Container Width")
            if (containerHeight <= 0) missingFields.add("Container Height")
        }

        if (missingFields.isNotEmpty()) {
            val message = "Please enter valid values for: ${missingFields.joinToString(", ")}"
            showAlertDialog("Invalid Fields", message)
            return
        }

        if (isResponsive) {
            // RESPONSIVE AD: AdView directly in responsiveContainer with MATCH_PARENT
            val pxContainerWidth = dpToPx(containerWidth)
            val pxContainerHeight = dpToPx(containerHeight)
            
            // Set responsive container dimensions
            responsiveContainer.layoutParams.apply {
                this.width = pxContainerWidth
                this.height = pxContainerHeight
            }
            responsiveContainer.visibility = View.VISIBLE
            
            // Create AdView that fills the responsive container
            val adView = AdView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            
            // Add directly to responsive container
            responsiveContainer.removeAllViews()
            responsiveContainer.addView(adView)
            
            adView.adUnitId = adspaceId
            adView.adType = adSpaceType
            adView.adIsResponsive = true

            // For responsive ads, don't set AdSize - let it measure from parent
            // The SDK will use measuredWidth and measuredHeight to set dimensions
            
            Log.d("MainActivity", "Loading RESPONSIVE ad in container: ${containerWidth}dp x ${containerHeight}dp")
            
            setupAdListener(adView)
            loadAdRequest(adView)
            
        } else {
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
            
            Log.d("MainActivity", "Loading FIXED ad: ${width}dp x ${height}dp")
            
            setupAdListener(adView)
            loadAdRequest(adView)
        }
    }
    
    private fun setupAdListener(adView: AdView) {
        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdView", "Ad Loaded Successfully!")
                adView.visibility = View.VISIBLE
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
        
        currentAdView = adView
    }
    
    private fun loadAdRequest(adView: AdView) {
        val adRequest = AdRequest.Builder()
            .setTestMode(testModeSwitch.isChecked)
            .build()
        adView.loadAd(adRequest)
    }

    private fun destroyCurrentAd() {
        currentAdView?.let { adView ->
            adView.destroy()
            (adView.parent as? ViewGroup)?.removeView(adView)
        }
        currentAdView = null

        responsiveContainer.visibility = View.GONE
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