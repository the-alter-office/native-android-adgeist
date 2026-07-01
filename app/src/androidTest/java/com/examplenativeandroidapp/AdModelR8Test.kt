package com.examplenativeandroidapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
}
