package com.examplenativeandroidapp

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdType
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest

/**
 * Home screen: hosts the ad rendering UI.
 *
 * Navigating to another screen destroys this fragment's view, which detaches
 * the AdView and should trigger the SDK's own cleanup (onDetachedFromWindow ->
 * safelyDestroyWebView). We intentionally do NOT call adView.destroy() in
 * onDestroyView so that the SDK's automatic cleanup path is what gets tested.
 */
class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

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

    private val defaultPackageId = "com.leaguex.crm.beta"
    private val defaultAdgeistAppId = "69a6777707df2b1527e357f9"
    private val defaultBidRequestBackendDomain = "https://beta.v2.bg-services.adgeist.ai"

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configuration Section
        packageIdInput = view.findViewById(R.id.packageIdInput)
        adgeistAppIdInput = view.findViewById(R.id.adgeistAppIdInput)
        configureBtn = view.findViewById(R.id.configureBtn)

        // Ad Loading Section
        adspaceIdInput = view.findViewById(R.id.adspaceIdInput)
        adspaceTypeInput = view.findViewById(R.id.adspaceTypeInput)
        widthInput = view.findViewById(R.id.widthInput)
        heightInput = view.findViewById(R.id.heightInput)
        generateAdBtn = view.findViewById(R.id.generateAdBtn)
        cancelAdBtn = view.findViewById(R.id.cancelAdBtn)
        adContainer = view.findViewById(R.id.adContainer)
        responsiveContainer = view.findViewById(R.id.responsiveContainer)
        testModeSwitch = view.findViewById(R.id.testModeSwitch)
        responsiveAdSwitch = view.findViewById(R.id.responsiveAdSwitch)
        responsiveSizeSection = view.findViewById(R.id.responsiveSizeSection)
        containerWidthInput = view.findViewById(R.id.containerWidthInput)
        containerHeightInput = view.findViewById(R.id.containerHeightInput)

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

    private fun configureSDK() {
        val packageId = packageIdInput.text.toString().trim()
        val adgeistAppId = adgeistAppIdInput.text.toString().trim()

        if (packageId.isEmpty() || adgeistAppId.isEmpty()) {
            showAlertDialog("Invalid Configuration", "Please enter valid Package ID and Adgeist App ID")
            return
        }

        // Reinitialize AdgeistCore with new configuration
        AdgeistCore.destroy()
        AdgeistCore.initialize(requireContext().applicationContext, defaultBidRequestBackendDomain, packageId, adgeistAppId)

        showAlertDialog("Success", "SDK configured successfully with:\nPackage ID: $packageId\nApp ID: $adgeistAppId")
        Log.d(TAG, "SDK reinitialized with Package ID: $packageId, App ID: $adgeistAppId")
    }

    private fun loadNewAd() {
        destroyCurrentAd()

        val adspaceId = "69ca2675576a0a20dd6c6cfb"
        val adSpaceType = AdType.BANNER
        val width = 320
        val height = 320
        val containerWidth = 320
        val containerHeight = 320

        val isResponsive = true

        val missingFields = mutableListOf<String>()

        if (adspaceId.isEmpty()) missingFields.add("Adspace ID")

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
            val adView = AdView(requireContext())

            // Add directly to responsive container
            responsiveContainer.removeAllViews()
            responsiveContainer.addView(adView)

            adView.adUnitId = adspaceId
            adView.adType = adSpaceType
            adView.adIsResponsive = true

            // For responsive ads, don't set AdSize - let it measure from parent
            // The SDK will use measuredWidth and measuredHeight to set dimensions

            Log.d(TAG, "Loading RESPONSIVE ad in container: ${containerWidth}dp x ${containerHeight}dp")

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
            val adView = AdView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(pxWidth, pxHeight)
            }

            // Add to container
            adContainer.removeAllViews()
            adContainer.addView(adView)

            adView.adUnitId = adspaceId
            adView.adType = adSpaceType

            adView.setAdDimension(AdSize(width, height))

            Log.d(TAG, "Loading FIXED ad: ${width}dp x ${height}dp")

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
                if (isAdded) {
                    showAlertDialog("Ad Load Failed", "Reason: $error")
                    destroyCurrentAd()
                    clearInputFields()
                }
            }

            override fun onAdClicked() {
                Log.d("AdView", "Ad Clicked")
            }

            override fun onAdOpened() {
                Log.d("AdView", "Ad Opened")
            }

            override fun onAdClosed() {
                // Fires from BaseAdView.onDetachedFromWindow -> onDestroyWebView,
                // i.e. when this fragment's view is destroyed on navigation.
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

    private fun clearInputFields() {
        adspaceIdInput.text.clear()
        adspaceTypeInput.text.clear()
        widthInput.text.clear()
        heightInput.text.clear()
    }

    override fun onDestroyView() {
        // Deliberately NOT calling currentAdView?.destroy() here: navigating away
        // detaches the AdView, and the SDK is expected to clean itself up via
        // onDetachedFromWindow. Watch Logcat for BaseAdView cleanup logs and the
        // "Ad Closed" callback right after this line.
        Log.d(TAG, "onDestroyView - AdView (if any) is being detached, SDK cleanup should follow")
        currentAdView = null
        super.onDestroyView()
    }

    private fun showAlertDialog(title: String, message: String) {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(requireContext(), R.color.teal_700)
        )
    }
}
