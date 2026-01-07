package com.adgeistkit.data.models

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
    val metaData: String,
    val id: String,
    val generatedAt: String?,
    val campaignId: String?,
    val advertiser: Advertiser?,
    val type: String?,
    val loadType: String?,
    val campaignValidity: CampaignValidity?,
    val creatives: List<Creative>?,
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
    val impressionType: String?,
    val minViewDurationSeconds: Int?
)
