package com.adgeistkit

data class CreativeDataModel(
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