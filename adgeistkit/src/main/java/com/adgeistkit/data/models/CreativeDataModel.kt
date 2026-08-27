package com.adgeistkit.data.models

import com.adgeistkit.benchmark.FetchTimings
import com.google.gson.annotations.SerializedName

sealed interface AdResponseData

data class FixedAdResponse(
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("metaData") val metaData: String,
    @SerializedName("id") val id: String,
    @SerializedName("generatedAt") val generatedAt: String?,
    @SerializedName("signature") val signature: String?,
    @SerializedName("campaignId") val campaignId: String?,
    @SerializedName("advertiser") val advertiser: Advertiser?,
    @SerializedName("type") val type: String?,
    @SerializedName("loadType") val loadType: String?,
    @SerializedName("campaignValidity") val campaignValidity: CampaignValidity?,
    @SerializedName("creativesV1") val creativesV1: List<CreativeV1>,
    @SerializedName("displayOptions") val displayOptions: DisplayOptions?,
    @SerializedName("frontendCacheDurationSeconds") val frontendCacheDurationSeconds: Int?,
    @SerializedName("impressionRequirements") val impressionRequirements: ImpressionRequirements?
) : AdResponseData

data class Advertiser(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("logoUrl") val logoUrl: String?
)

data class CampaignValidity(
    @SerializedName("startTime") val startTime: String?,
    @SerializedName("endTime") val endTime: String?
)

data class CreativeV1(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("ctaUrl") val ctaUrl: String?,
    @SerializedName("primary") val primary: MediaItem?,
    @SerializedName("companions") val companions: List<MediaItem>?
)

data class MediaItem(
    @SerializedName("type") val type: String?,
    @SerializedName("fileName") val fileName: String?,
    @SerializedName("fileSize") val fileSize: Int?,
    @SerializedName("fileUrl") val fileUrl: String?,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String?
)

data class MongoIdWrapper(
    @SerializedName("\$oid") val `$oid`: String?
)

data class MongoDateWrapper(
    @SerializedName("\$date") val `$date`: Long?
)

data class DisplayOptions(
    @SerializedName("allowedFormats") val allowedFormats: List<String>?,
    @SerializedName("dimensions") val dimensions: Dimensions?,
    @SerializedName("isResponsive") val isResponsive: Boolean?,
    @SerializedName("styleOptions") val styleOptions: StyleOptions?
)

data class Dimensions(
    @SerializedName("height") val height: Int?,
    @SerializedName("width") val width: Int?
)

data class StyleOptions(
    @SerializedName("fontColor") val fontColor: String?,
    @SerializedName("fontFamily") val fontFamily: String?
)

data class ImpressionRequirements(
    @SerializedName("impressionType") val impressionType: List<String>?,
    @SerializedName("minViewDurationSeconds") val minViewDurationSeconds: Int?
)

data class AdErrorResponse(
    @SerializedName("Error") val Error: String,
    @SerializedName("Status") val Status: String
)

data class AdVisibilityError(
    val errorMessage: String
)

data class AdData(
    val data: AdResponseData?,
    val error: AdVisibilityError?,
    val statusCode: Int?,
    val timings: FetchTimings? = null
) {
    val isSuccess: Boolean
        get() = error == null && data != null

    val errorMessage: String
        get() = error?.errorMessage ?: "Unknown error occurred"
}
