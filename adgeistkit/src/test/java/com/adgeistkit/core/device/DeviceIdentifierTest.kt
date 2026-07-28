package com.adgeistkit.core.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentifierTest {

    @Test
    fun `real advertising id is usable`() {
        assertTrue(
            DeviceIdentifier.isUsableAdId("38400000-8cf0-11bd-b23e-10b96e40000d", false)
        )
    }

    @Test
    fun `zeroed advertising id is rejected`() {
        assertFalse(
            DeviceIdentifier.isUsableAdId("00000000-0000-0000-0000-000000000000", false)
        )
    }

    @Test
    fun `limit ad tracking rejects an otherwise valid id`() {
        assertFalse(
            DeviceIdentifier.isUsableAdId("38400000-8cf0-11bd-b23e-10b96e40000d", true)
        )
    }

    @Test
    fun `null and blank advertising ids are rejected`() {
        assertFalse(DeviceIdentifier.isUsableAdId(null, false))
        assertFalse(DeviceIdentifier.isUsableAdId("", false))
        assertFalse(DeviceIdentifier.isUsableAdId("   ", false))
    }

    @Test
    fun `well formed check accepts a generated uuid`() {
        val generated = java.util.UUID.randomUUID().toString()
        assertTrue(DeviceIdentifier.isWellFormedId(generated))
    }

    @Test
    fun `a generated fallback id is accepted by the ad id validity check`() {
        // The fallback must be indistinguishable from a real GAID on the wire.
        val fallback = java.util.UUID.randomUUID().toString()
        assertTrue(DeviceIdentifier.isUsableAdId(fallback, false))
    }

    @Test
    fun `well formed check rejects corrupt stored values`() {
        assertFalse(DeviceIdentifier.isWellFormedId(null))
        assertFalse(DeviceIdentifier.isWellFormedId(""))
        assertFalse(DeviceIdentifier.isWellFormedId("not-a-uuid"))
        // UUID.fromString is lenient about short groups; the round-trip check catches them.
        assertFalse(DeviceIdentifier.isWellFormedId("1-1-1-1-1"))
    }
}
