package com.adgeistkit.ads.render

import com.adgeistkit.data.models.FixedAdResponse
import com.google.gson.Gson
import com.adgeistkit.ads.AdType
import com.adgeistkit.ads.AdSize

internal object AdCreativePayload {

    sealed interface Result {
        data class Success(val creativeJson: String, val metaData: String) : Result

        data class Failure(val message: String) : Result
    }

    private const val DEFAULT_ADVERTISER_NAME = "-"

    fun build(
        response: FixedAdResponse,
        adUnitId: String,
        adType: AdType,
        adIsResponsive: Boolean,
        adSize: AdSize?,
        measuredWidthDp: Int,
        measuredHeightDp: Int,
    ): Result {
        if (response.creativesV1.isEmpty()) {
            return Result.Failure("Empty creative")
        }

        val width: Int
        val height: Int
        if (adIsResponsive) {
            width = measuredWidthDp
            height = measuredHeightDp
        } else {
            if (adSize == null) {
                return Result.Failure(
                    "adSize not set. Call setAdDimension() or set adIsResponsive = true before loadAd()"
                )
            }
            width = adSize.width
            height = adSize.height
        }

        val creative = response.creativesV1[0]
        val options = response.displayOptions

        val media = mutableListOf<Map<String, String?>>()
        media.add(
            mapOf(
                "src" to creative.primary?.fileUrl,
                "thumbnailUrl" to creative.primary?.thumbnailUrl,
                "type" to creative.primary?.type,
            )
        )
        creative.companions?.forEach { companion ->
            media.add(
                mapOf(
                    "src" to companion.fileUrl,
                    "thumbnailUrl" to companion.thumbnailUrl,
                    "type" to companion.type,
                )
            )
        }

        val properties = mutableMapOf<String, Any?>(
            "adspaceType" to adType.value,
            "adElementId" to "adgeist_ads_iframe_$adUnitId",
            "name" to (response.advertiser?.name ?: DEFAULT_ADVERTISER_NAME),
            "isResponsive" to (options?.isResponsive ?: false),
            "title" to creative.title,
            "description" to creative.description,
            "ctaUrl" to creative.ctaUrl,
            "width" to width,
            "height" to height,
            "media" to media,
        )

        return Result.Success(Gson().toJson(properties), response.metaData)
    }
}
