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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.adgeistkit.AdgeistCore
import com.adgeistkit.ads.AdListener
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdType
import com.adgeistkit.ads.AdView
import com.adgeistkit.request.AdRequest
import com.examplenativeandroidapp.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    // Configuration section
    private lateinit var packageIdInput: EditText
    private lateinit var adgeistAppIdInput: EditText
    private lateinit var configureBtn: Button

    // Ad loading section
    private lateinit var autoLoadSwitch: SwitchCompat
    private lateinit var manualLoadSection: LinearLayout
    private lateinit var adspaceIdInput: EditText
    private lateinit var adspaceTypeInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var generateAdBtn: Button
    private lateinit var cancelAdBtn: Button
    private lateinit var adContainer: LinearLayout
    private lateinit var responsiveContainer: FrameLayout
    private lateinit var responsiveAdSwitch: SwitchCompat
    private lateinit var responsiveSizeSection: LinearLayout
    private lateinit var containerWidthInput: EditText
    private lateinit var containerHeightInput: EditText

    // Ads currently on screen, stacked vertically inside adContainer
    private val activeAdViews = mutableListOf<AdView>()

    private val defaultPackageId = "com.leaguex.crm.beta"
    private val defaultAdgeistAppId = "69a6777707df2b1527e357f9"
    private val defaultBidRequestBackendDomain = "https://beta.v2.bg-services.adgeist.ai"


    private val viewModel: HomeViewModel by activityViewModels()
    private var handledRequestId = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)

        packageIdInput.setText(defaultPackageId)
        adgeistAppIdInput.setText(defaultAdgeistAppId)
        generateAdBtn.isEnabled = true
        cancelAdBtn.isEnabled = false

        responsiveAdSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                responsiveSizeSection.visibility = View.VISIBLE
                widthInput.isEnabled = false
                heightInput.isEnabled = false
                containerWidthInput.setText("300")
                containerHeightInput.setText("250")
            } else {
                responsiveSizeSection.visibility = View.GONE
                widthInput.isEnabled = true
                heightInput.isEnabled = true
            }
        }

        configureBtn.setOnClickListener {
            configureSDK()
        }

        val autoLoad = viewModel.isAutoLoadEnabled.value
        autoLoadSwitch.isChecked = autoLoad

        applyLoadMode(autoLoad)

        autoLoadSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAutoLoadEnabled(isChecked)
            applyLoadMode(isChecked)
            if (isChecked) {
                viewModel.generateAd()
            }
        }

        generateAdBtn.setOnClickListener {
            viewModel.generateAd()
        }

        cancelAdBtn.setOnClickListener {
            viewModel.cancelAd()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.adRequestId.collect { requestId ->
                    if (requestId == handledRequestId) return@collect
                    handledRequestId = requestId

                    if (requestId == 0) {
                        destroyAllAds()
                        return@collect
                    }
                    if (viewModel.isAutoLoadEnabled.value) {
                        loadAdWithDefaults()
                    } else {
                        loadAdFromInputs()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        // No destroy: sessions stay alive in the SDK and are adopted on return
        Log.d(TAG, "onDestroyView - ad sessions stay alive inside the SDK")
        activeAdViews.clear()
        handledRequestId = -1
        super.onDestroyView()
    }

    private fun bindViews(view: View) {
        packageIdInput = view.findViewById(R.id.packageIdInput)
        adgeistAppIdInput = view.findViewById(R.id.adgeistAppIdInput)
        configureBtn = view.findViewById(R.id.configureBtn)

        autoLoadSwitch = view.findViewById(R.id.autoLoadSwitch)
        manualLoadSection = view.findViewById(R.id.manualLoadSection)
        adspaceIdInput = view.findViewById(R.id.adspaceIdInput)
        adspaceTypeInput = view.findViewById(R.id.adspaceTypeInput)
        widthInput = view.findViewById(R.id.widthInput)
        heightInput = view.findViewById(R.id.heightInput)
        generateAdBtn = view.findViewById(R.id.generateAdBtn)
        cancelAdBtn = view.findViewById(R.id.cancelAdBtn)
        adContainer = view.findViewById(R.id.adContainer)
        responsiveContainer = view.findViewById(R.id.responsiveContainer)
        responsiveAdSwitch = view.findViewById(R.id.responsiveAdSwitch)
        responsiveSizeSection = view.findViewById(R.id.responsiveSizeSection)
        containerWidthInput = view.findViewById(R.id.containerWidthInput)
        containerHeightInput = view.findViewById(R.id.containerHeightInput)
    }

    private fun applyLoadMode(autoLoad: Boolean) {
        manualLoadSection.visibility = if (autoLoad) View.GONE else View.VISIBLE
        if (!autoLoad) {
            if (adspaceIdInput.text.isEmpty()) adspaceIdInput.setText("")
            if (adspaceTypeInput.text.isEmpty()) adspaceTypeInput.setText("BANNER")
            if (widthInput.text.isEmpty()) widthInput.setText("320")
            if (heightInput.text.isEmpty()) heightInput.setText("320")
        }
    }

    private fun configureSDK() {
        val packageId = packageIdInput.text.toString().trim()
        val adgeistAppId = adgeistAppIdInput.text.toString().trim()

        if (packageId.isEmpty() || adgeistAppId.isEmpty()) {
            showAlertDialog("Invalid Configuration", "Please enter valid Package ID and Adgeist App ID")
            return
        }

        AdgeistCore.destroy()
        AdgeistCore.initialize(requireContext().applicationContext, defaultBidRequestBackendDomain, packageId, adgeistAppId)

        showAlertDialog("Success", "SDK configured successfully with:\nPackage ID: $packageId\nApp ID: $adgeistAppId")
        Log.d(TAG, "SDK reinitialized with Package ID: $packageId, App ID: $adgeistAppId")
    }

    // ---------------------------------------------------------------------
    // Ad loading
    // ---------------------------------------------------------------------

    /** Auto mode: two fixed ads with default ids, stacked vertically. */
    private fun loadAdWithDefaults() {
        clearAdContainer()
        performAdLoad(
            adspaceId = "6a82e3bae0f53b7dac65f76a",
            adSpaceType = AdType.BANNER,
            isResponsive = false,
            width = 360,
            height = 360,
            containerWidth = 360,
            containerHeight = 360
        )
        performAdLoad(
            adspaceId = "6a4b7c9a50946c5aa2fda929",
            adSpaceType = AdType.BANNER,
            isResponsive = false,
            width = 360,
            height = 360,
            containerWidth = 360,
            containerHeight = 360
        )
    }

    /** Manual mode: everything comes from the input fields. */
    private fun loadAdFromInputs() {
        val adspaceId = adspaceIdInput.text.toString().trim()
        val typeText = adspaceTypeInput.text.toString().trim()
        val isResponsive = responsiveAdSwitch.isChecked
        val width = widthInput.text.toString().toIntOrNull() ?: 0
        val height = heightInput.text.toString().toIntOrNull() ?: 0
        val containerWidth = containerWidthInput.text.toString().toIntOrNull() ?: 0
        val containerHeight = containerHeightInput.text.toString().toIntOrNull() ?: 0

        val adSpaceType = if (typeText.equals("COMPANION", ignoreCase = true)) {
            AdType.COMPANION
        } else {
            AdType.BANNER
        }

        clearAdContainer()
        performAdLoad(adspaceId, adSpaceType, isResponsive, width, height, containerWidth, containerHeight)
    }

    private fun performAdLoad(
        adspaceId: String,
        adSpaceType: AdType,
        isResponsive: Boolean,
        width: Int,
        height: Int,
        containerWidth: Int,
        containerHeight: Int
    ) {
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
            showAlertDialog("Invalid Fields", "Please enter valid values for: ${missingFields.joinToString(", ")}")
            return
        }

        val adView = AdView(requireContext())
        adView.adUnitId = adspaceId
        adView.adType = adSpaceType

        val wrapperWidth: Int
        val wrapperHeight: Int
        if (isResponsive) {
            // Responsive: no AdSize - the SDK measures from the wrapper
            adView.adIsResponsive = true
            wrapperWidth = dpToPx(containerWidth)
            wrapperHeight = dpToPx(containerHeight)
            Log.d(TAG, "Loading RESPONSIVE ad '$adspaceId' in ${containerWidth}dp x ${containerHeight}dp")
        } else {
            adView.setAdDimension(AdSize(width, height))
            wrapperWidth = dpToPx(width)
            wrapperHeight = dpToPx(height)
            Log.d(TAG, "Loading FIXED ad '$adspaceId': ${width}dp x ${height}dp")
        }

        // Each ad gets its own fixed-size wrapper so multiple ads stack
        // vertically instead of replacing each other in a shared container
        val wrapper = FrameLayout(requireContext())
        wrapper.addView(adView)

        val wrapperParams = LinearLayout.LayoutParams(wrapperWidth, wrapperHeight)
        wrapperParams.topMargin = dpToPx(16)
        adContainer.addView(wrapper, wrapperParams)
        adContainer.visibility = View.VISIBLE

        setupAdListener(adView)
        loadAdRequest(adView)
    }

    private fun setupAdListener(adView: AdView) {
        adView.setAdListener(object : AdListener() {
            override fun onAdLoaded() {
                Log.d("AdView", "Ad Loaded Successfully!")
                adView.visibility = View.VISIBLE
            }

            override fun onAdFailedToLoad(error: String) {
                Log.e("AdView", "Ad Failed to Load ('${adView.adUnitId}'): $error")
                if (isAdded) {
                    showAlertDialog("Ad Load Failed", "Reason: $error")
                    removeAd(adView)
                }
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

        activeAdViews.add(adView)
        cancelAdBtn.isEnabled = true
    }

    private fun loadAdRequest(adView: AdView) {
        val adRequest = AdRequest.Builder()
            .build()
        adView.loadAd(adRequest)
    }

    // ---------------------------------------------------------------------
    // Ad teardown
    // ---------------------------------------------------------------------

    /** Empties the container WITHOUT destroying: live sessions get adopted. */
    private fun clearAdContainer() {
        adContainer.removeAllViews()
        activeAdViews.clear()
    }

    /** Destroys one ad and removes its wrapper from the stack. */
    private fun removeAd(adView: AdView) {
        adView.destroyAd()
        val wrapper = adView.parent as? ViewGroup
        (wrapper?.parent as? ViewGroup)?.removeView(wrapper)
        activeAdViews.remove(adView)
        if (activeAdViews.isEmpty()) {
            adContainer.visibility = View.GONE
        }
    }

    /** Explicit teardown of every ad (Cancel); the next load fetches fresh. */
    private fun destroyAllAds() {
        activeAdViews.toList().forEach { removeAd(it) }
        adContainer.removeAllViews()
        adContainer.visibility = View.GONE
        generateAdBtn.isEnabled = true
        cancelAdBtn.isEnabled = false
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun clearInputFields() {
        adspaceIdInput.text.clear()
        adspaceTypeInput.text.clear()
        widthInput.text.clear()
        heightInput.text.clear()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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
