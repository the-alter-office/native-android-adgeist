package com.adgeistkit.data.models

import com.google.gson.annotations.SerializedName

data class CPMAdResponse(
    val success: Boolean,
    val message: String,
    val data: BidResponseData?
)

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
    val creatives: List<Creative>,
    val creativesV1: List<CreativeV1>,
    val displayOptions: DisplayOptions?,
    val frontendCacheDurationSeconds: Int?,
    val impressionRequirements: ImpressionRequirements?
)

data class Advertiser(
    val id: String?,
    val name: String?,
    val logoUrl: String?
)

data class CampaignValidity(
    val startTime: String?,
    val endTime: String?
)

data class Creative(
    val contentModerationResult: MongoIdWrapper?,
    val createdAt: MongoDateWrapper?,
    val ctaUrl: String?,
    val description: String?,
    val fileName: String?,
    val fileSize: Int?,
    val fileUrl: String?,
    val thumbnailUrl: String?,
    val title: String?,
    val type: String?,
    val updatedAt: MongoDateWrapper?
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
    @SerializedName("Status")
    val status: String?,
    @SerializedName("Error")
    val errorMessage: String?,
    @SerializedName("success")
    val success: Boolean?,
    @SerializedName("error")
    val error: String?,
    @SerializedName("message")
    val message: String?,
    val statusCode: Int?
) {
    override fun toString(): String {
        return "AdErrorResponse(status='$status', errorMessage='$errorMessage', success=$success, error='$error', message='$message', statusCode=$statusCode)"
    }
}

data class AdResult(
    val data: Any?,
    val error: AdErrorResponse?
) {

    val isSuccess: Boolean
        get() = error == null && data != null
    
    val errorMessage: String
        get() {
            return when {
                // API format: {"Status":"error","Error":"message"}
                error?.errorMessage != null && error.errorMessage!!.isNotEmpty() -> error.errorMessage!!
                // Alternative format: {"message":"..."}
                error?.message != null && error.message!!.isNotEmpty() -> error.message!!
                // Alternative format: {"error":"..."}
                error?.error != null && error.error!!.isNotEmpty() -> error.error!!
                else -> "Unknown error occurred"
            }
        }
}
