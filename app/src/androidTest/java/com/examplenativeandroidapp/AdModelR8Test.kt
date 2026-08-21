package com.examplenativeandroidapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adgeistkit.data.models.AdErrorResponse
import com.adgeistkit.data.models.FixedAdResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R8 regression guard.
 *
 * This test runs against the **minified release** variant (see `testBuildType =
 * "release"` and `isMinifyEnabled = true` in the app module's build.gradle.kts),
 * so Gson reflects over the R8-processed [FixedAdResponse] and its nested models.
 *
 * The AdGeist response models map JSON keys by field name with no @SerializedName.
 * If the SDK's consumer-rules.pro fails to keep those field names, R8 renames them,
 * Gson can no longer bind the JSON, and every field deserializes to null — the exact
 * production symptom (empty ad, no crash). The assertions below fail in that case and
 * pass once the keep rules are in place.
 */
@RunWith(AndroidJUnit4::class)
class AdModelR8Test {

    private fun loadSampleJson(): String {
        val ctx = InstrumentationRegistry.getInstrumentation().context
        return ctx.assets.open("sample_ad_response.json")
            .bufferedReader()
            .use { it.readText() }
    }

    @Test
    fun fixedAdResponse_deserializesUnderR8() {
        val response = Gson().fromJson(loadSampleJson(), FixedAdResponse::class.java)

        assertNotNull("FixedAdResponse itself must deserialize", response)

        // Top-level fields that isEmptyCreative() gates the ad on.
        assertEquals("creative_6789", response.id)
        assertEquals("campaign_42", response.campaignId)
        assertNotNull("advertiser must be bound (obfuscation would null it)", response.advertiser)
        assertEquals("Acme Corp", response.advertiser?.name)
        assertFalse("metaData must be populated", response.metaData.isBlank())

        // Nested list + object graph — these break first under field renaming.
        assertTrue("creativesV1 must be populated", response.creativesV1.isNotEmpty())
        val creative = response.creativesV1.first()
        assertEquals("Buy Acme Widgets", creative.title)
        assertNotNull("primary media item must be bound", creative.primary)
        assertEquals(
            "https://cdn.example.com/acme/banner.png",
            creative.primary?.fileUrl
        )

        assertNotNull("displayOptions must be bound", response.displayOptions)
        assertEquals(300, response.displayOptions?.dimensions?.width)
        assertEquals(250, response.displayOptions?.dimensions?.height)
    }

    @Test
    fun fixedAdResponse_bindsFullNestedGraphUnderR8() {
        val response = Gson().fromJson(loadSampleJson(), FixedAdResponse::class.java)

        assertNotNull("campaignValidity must be bound", response.campaignValidity)
        assertEquals("2026-07-01T00:00:00Z", response.campaignValidity?.startTime)
        assertEquals("2026-12-31T23:59:59Z", response.campaignValidity?.endTime)

        val style = response.displayOptions?.styleOptions
        assertNotNull("styleOptions must be bound", style)
        assertEquals("#000000", style?.fontColor)
        assertEquals("Roboto", style?.fontFamily)
        assertEquals(true, response.displayOptions?.isResponsive)
        assertEquals(listOf("banner", "display"), response.displayOptions?.allowedFormats)

        val impressions = response.impressionRequirements
        assertNotNull("impressionRequirements must be bound", impressions)
        assertEquals(listOf("viewable"), impressions?.impressionType)
        assertEquals(1, impressions?.minViewDurationSeconds)

        val companions = response.creativesV1.first().companions
        assertNotNull("companions must be bound", companions)
        assertEquals("https://cdn.example.com/acme/companion.png", companions?.first()?.fileUrl)
        assertEquals("companion.png", companions?.first()?.fileName)
    }

    @Test
    fun adErrorResponse_deserializesUnderR8() {
        // AdErrorResponse (FetchCreative.kt) uses capitalized field names that
        // must match the backend's "Error"/"Status" keys exactly — the fields
        // most likely to break if keep rules stop protecting names.
        val json = """{"Error":"No fill for this ad space","Status":"NO_FILL"}"""

        val response = Gson().fromJson(json, AdErrorResponse::class.java)

        assertNotNull("AdErrorResponse itself must deserialize", response)
        assertEquals("No fill for this ad space", response.Error)
        assertEquals("NO_FILL", response.Status)
    }
}
