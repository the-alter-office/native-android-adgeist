package com.adgeistkit.data.models

import com.adgeistkit.ads.AdgeistEventCode
import com.adgeistkit.benchmark.FetchTimings
import com.google.gson.annotations.SerializedName

public sealed interface AdResponseData

public data class FixedAdResponse(
    @SerializedName("expiresAt") val expiresAt: String?,
    @SerializedName("metaData") val metaData: String,
    @SerializedName("id") val id: String,
    @SerializedName("generatedAt") val generatedAt: String?,
    @SerializedName("campaignId") val campaignId: String?,
    @SerializedName("advertiser") val advertiser: Advertiser?,
    @SerializedName("type") val type: String?,
    @SerializedName("adSpaceType") val adSpaceType: AdSpaceType,
    @SerializedName("loadType") val loadType: String?,
    @SerializedName("campaignValidity") val campaignValidity: CampaignValidity?,
    @SerializedName("creativesV1") val creativesV1: List<CreativeV1>,
    @SerializedName("displayOptions") val displayOptions: DisplayOptions?,
    @SerializedName("frontendCacheDurationSeconds") val frontendCacheDurationSeconds: Int?
) : AdResponseData

public data class Advertiser(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("logoUrl") val logoUrl: String?
)

public data class CampaignValidity(
    @SerializedName("startTime") val startTime: String?,
    @SerializedName("endTime") val endTime: String?
)

public data class CreativeV1(
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("ctaUrl") val ctaUrl: String?,
    @SerializedName("deepLinkUrl") val deepLinkUrl: String?,
    @SerializedName("primary") val primary: MediaItem?,
    @SerializedName("companions") val companions: List<MediaItem>?
)

public data class MediaItem(
    @SerializedName("type") val type: String?,
    @SerializedName("fileName") val fileName: String?,
    @SerializedName("fileSize") val fileSize: Int?,
    @SerializedName("fileUrl") val fileUrl: String?,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String?
)

public data class DisplayOptions(
    @SerializedName("primaryFormats") val primaryFormats: List<String>?,
    @SerializedName("companionFormats") val companionFormats: List<String>?,
    @SerializedName("dimensions") val dimensions: Dimensions?,
    @SerializedName("isResponsive") val isResponsive: Boolean?,
    @SerializedName("responsiveType") val responsiveType: String?,
    @SerializedName("styleOptions") val styleOptions: StyleOptions?
)

public data class Dimensions(
    @SerializedName("height") val height: Int?,
    @SerializedName("width") val width: Int?
)

public data class StyleOptions(
    @SerializedName("fontColor") val fontColor: String?,
    @SerializedName("fontFamily") val fontFamily: String?
)

public data class AdErrorResponse(
    @SerializedName("Error") val Error: String,
    @SerializedName("Status") val Status: String
)

public data class AdData(
    val data: AdResponseData?,
    val error: AdgeistEventCode?,
    val statusCode: Int?,
    val timings: FetchTimings? = null
)
