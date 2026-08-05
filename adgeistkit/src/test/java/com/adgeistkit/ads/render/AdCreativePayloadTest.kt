package com.adgeistkit.ads.render

import com.adgeistkit.data.models.Advertiser
import com.adgeistkit.data.models.CreativeV1
import com.adgeistkit.data.models.DisplayOptions
import com.adgeistkit.data.models.FixedAdResponse
import com.adgeistkit.data.models.MediaItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.adgeistkit.ads.AdSize
import com.adgeistkit.ads.AdType

/**
 * Covers the ad-server response -> ad-page payload mapping. Pure JVM: the
 * mapping deliberately takes sizes already converted to dp so it needs no
 * Context.
 */
class AdCreativePayloadTest {

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    private fun media(url: String, type: String = "image") = MediaItem(
        type = type,
        fileName = "$url.png",
        fileSize = 1024,
        fileUrl = url,
        thumbnailUrl = "$url-thumb",
    )

    private fun response(
        creatives: List<CreativeV1> = listOf(
            CreativeV1(
                title = "Title",
                description = "Description",
                ctaUrl = "https://example.com",
                primary = media("primary"),
                companions = null,
            )
        ),
        displayOptions: DisplayOptions? = null,
        advertiser: Advertiser? = Advertiser(id = "a1", name = "Acme", logoUrl = null),
        metaData: String = "meta-token",
    ) = FixedAdResponse(
        expiresAt = null,
        metaData = metaData,
        id = "response-id",
        generatedAt = null,
        signature = null,
        campaignId = null,
        advertiser = advertiser,
        type = "FIXED",
        loadType = null,
        campaignValidity = null,
        creativesV1 = creatives,
        displayOptions = displayOptions,
        frontendCacheDurationSeconds = null,
        impressionRequirements = null,
    )

    private fun build(
        response: FixedAdResponse = response(),
        adUnitId: String = "unit-1",
        adType: AdType = AdType.BANNER,
        adIsResponsive: Boolean = false,
        adSize: AdSize? = AdSize(320, 250),
        measuredWidthDp: Int = 0,
        measuredHeightDp: Int = 0,
    ) = AdCreativePayload.build(
        response = response,
        adUnitId = adUnitId,
        adType = adType,
        adIsResponsive = adIsResponsive,
        adSize = adSize,
        measuredWidthDp = measuredWidthDp,
        measuredHeightDp = measuredHeightDp,
    )

    private fun successJson(result: AdCreativePayload.Result): Map<String, Any?> {
        assertTrue("expected Success but was $result", result is AdCreativePayload.Result.Success)
        val json = (result as AdCreativePayload.Result.Success).creativeJson
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        return Gson().fromJson(json, type)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mediaList(payload: Map<String, Any?>) =
        payload["media"] as List<Map<String, Any?>>

    // ---------------------------------------------------------------------
    // Sizing
    // ---------------------------------------------------------------------

    @Test
    fun `fixed ad takes its size from adSize`() {
        val payload = successJson(
            build(adIsResponsive = false, adSize = AdSize(300, 600), measuredWidthDp = 11, measuredHeightDp = 22)
        )

        assertEquals(300.0, payload["width"])
        assertEquals(600.0, payload["height"])
    }

    @Test
    fun `responsive ad takes its size from the measured dp values`() {
        val payload = successJson(
            build(adIsResponsive = true, adSize = AdSize(300, 600), measuredWidthDp = 411, measuredHeightDp = 200)
        )

        assertEquals(411.0, payload["width"])
        assertEquals(200.0, payload["height"])
    }

    @Test
    fun `responsive ad needs no adSize`() {
        val payload = successJson(build(adIsResponsive = true, adSize = null, measuredWidthDp = 360, measuredHeightDp = 120))

        assertEquals(360.0, payload["width"])
    }

    @Test
    fun `fixed ad without adSize fails instead of rendering`() {
        val result = build(adIsResponsive = false, adSize = null)

        assertTrue(result is AdCreativePayload.Result.Failure)
        assertEquals(
            "adSize not set. Call setAdDimension() or set adIsResponsive = true before loadAd()",
            (result as AdCreativePayload.Result.Failure).message
        )
    }

    // ---------------------------------------------------------------------
    // Failure paths
    // ---------------------------------------------------------------------

    @Test
    fun `empty creative list fails`() {
        val result = build(response = response(creatives = emptyList()))

        assertTrue(result is AdCreativePayload.Result.Failure)
        assertEquals("Empty creative", (result as AdCreativePayload.Result.Failure).message)
    }

    @Test
    fun `empty creative list is checked before the missing adSize`() {
        // Ordering matters: the caller surfaces whichever message comes back, and
        // "Empty creative" is the more actionable diagnosis of the two
        val result = build(response = response(creatives = emptyList()), adSize = null)

        assertEquals("Empty creative", (result as AdCreativePayload.Result.Failure).message)
    }

    // ---------------------------------------------------------------------
    // Media ordering
    // ---------------------------------------------------------------------

    @Test
    fun `primary creative leads and companions follow in order`() {
        val creative = CreativeV1(
            title = "T",
            description = "D",
            ctaUrl = "https://example.com",
            primary = media("primary"),
            companions = listOf(media("companion-1"), media("companion-2")),
        )

        val media = mediaList(successJson(build(response = response(creatives = listOf(creative)))))

        assertEquals(3, media.size)
        assertEquals("primary", media[0]["src"])
        assertEquals("companion-1", media[1]["src"])
        assertEquals("companion-2", media[2]["src"])
        assertEquals("primary-thumb", media[0]["thumbnailUrl"])
    }

    @Test
    fun `a creative with no companions still yields the primary`() {
        val media = mediaList(successJson(build()))

        assertEquals(1, media.size)
        assertEquals("primary", media[0]["src"])
    }

    @Test
    fun `only the first creative is used`() {
        val first = CreativeV1("first", "d", "https://a", media("first-primary"), null)
        val second = CreativeV1("second", "d", "https://b", media("second-primary"), null)

        val payload = successJson(build(response = response(creatives = listOf(first, second))))

        assertEquals("first", payload["title"])
        assertEquals("first-primary", mediaList(payload)[0]["src"])
    }

    // ---------------------------------------------------------------------
    // Field mapping and defaults
    // ---------------------------------------------------------------------

    @Test
    fun `adElementId keeps the shape the ad page expects`() {
        val payload = successJson(build(adUnitId = "abc123"))

        assertEquals("adgeist_ads_iframe_abc123", payload["adElementId"])
    }

    @Test
    fun `adspaceType carries the ad type wire value`() {
        assertEquals("companion", successJson(build(adType = AdType.COMPANION))["adspaceType"])
        assertEquals("banner", successJson(build(adType = AdType.BANNER))["adspaceType"])
    }

    @Test
    fun `metaData is returned alongside the payload rather than embedded in it`() {
        val result = build(response = response(metaData = "tracking-token"))

        assertEquals("tracking-token", (result as AdCreativePayload.Result.Success).metaData)
    }

    @Test
    fun `a missing advertiser name falls back to a placeholder`() {
        assertEquals("-", successJson(build(response = response(advertiser = null)))["name"])
        assertEquals(
            "-",
            successJson(build(response = response(advertiser = Advertiser("id", null, null))))["name"]
        )
    }

    @Test
    fun `display options default when the server omits them`() {
        val payload = successJson(build(response = response(displayOptions = null)))

        assertEquals(false, payload["isResponsive"])
        assertEquals("Square", payload["responsiveType"])
    }

    @Test
    fun `display options are carried through when present`() {
        val options = DisplayOptions(
            allowedFormats = null,
            dimensions = null,
            isResponsive = true,
            responsiveType = "Landscape",
            styleOptions = null,
        )

        val payload = successJson(build(response = response(displayOptions = options)))

        assertEquals(true, payload["isResponsive"])
        assertEquals("Landscape", payload["responsiveType"])
    }

    @Test
    fun `creative text fields are carried through`() {
        val payload = successJson(build())

        assertEquals("Title", payload["title"])
        assertEquals("Description", payload["description"])
        assertEquals("https://example.com", payload["ctaUrl"])
    }
}
