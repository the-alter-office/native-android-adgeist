package com.adgeistkit.data.models

import com.google.gson.annotations.SerializedName

sealed interface AdResponseData

data class CPMAdResponse(
    val success: Boolean,
    val message: String,
    val data: BidResponseData?
) : AdResponseData

data class BidResponseData(
    val id: String,
    val seatBid: List<SeatBid>,
    val bidId: String,
    val cur: String
)

data class SeatBid(
    val bidId: String,
    val bid: List<Bid>
)

data class Bid(
    val id: String,
    val impId: String,
    val price: Double,
    val ext: BidExtension
)

data class BidExtension(
    val creativeUrl: String,
    val ctaUrl: String,
    val creativeTitle: String,
    val creativeDescription: String,
)

data class FixedAdResponse(
    val isTest: Boolean?,
    val expiresAt: String?,
    val metaData: String,
    val id: String,
    val generatedAt: String?,
    val signature: String?,
    val campaignId: String?,
    val advertiser: Advertiser?,
    val type: String?,
    val loadType: String?,
    val campaignValidity: CampaignValidity?,
    val creativesV1: List<CreativeV1>,
    val displayOptions: DisplayOptions?,
    val frontendCacheDurationSeconds: Int?,
    val impressionRequirements: ImpressionRequirements?
) : AdResponseData

data class Advertiser(
    val id: String?,
    val name: String?,
    val logoUrl: String?
)

data class CampaignValidity(
    val startTime: String?,
    val endTime: String?
)

// New CreativeV1 structure
data class CreativeV1(
    val title: String?,
    val description: String?,
    val ctaUrl: String?,
    val primary: MediaItem?,
    val companions: List<MediaItem>?
)

data class MediaItem(
    val type: String?,
    val fileName: String?,
    val fileSize: Int?,
    val fileUrl: String?,
    val thumbnailUrl: String?
)

data class MongoIdWrapper(
    val `$oid`: String?
)

data class MongoDateWrapper(
    val `$date`: Long?
)

data class DisplayOptions(
    val allowedFormats: List<String>?,
    val dimensions: Dimensions?,
    val isResponsive: Boolean?,
    val responsiveType: String?,
    val styleOptions: StyleOptions?
)

data class Dimensions(
    val height: Int?,
    val width: Int?
)

data class StyleOptions(
    val fontColor: String?,
    val fontFamily: String?
)

data class ImpressionRequirements(
    val impressionType: List<String>?,
    val minViewDurationSeconds: Int?
)


data class AdErrorResponse(
    val Error: String,
    val Status: String
)

data class AdVisibilityError(
    val errorMessage: String
)

data class AdData(
    val data: AdResponseData?,
    val error: AdVisibilityError?,
    val statusCode: Int?
) {
    val isSuccess: Boolean
        get() = error == null && data != null
    
    val errorMessage: String
        get() = error?.errorMessage ?: "Unknown error occurred"
}
