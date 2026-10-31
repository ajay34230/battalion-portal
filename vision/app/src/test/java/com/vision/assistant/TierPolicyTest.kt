package com.vision.assistant

import com.vision.assistant.core.Tier
import com.vision.assistant.core.TierPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TierPolicyTest {
    @Test fun lowRamIsLite() = assertEquals(Tier.LITE, TierPolicy.pick(2_000, true))
    @Test fun thirtyTwoBitIsLite() = assertEquals(Tier.LITE, TierPolicy.pick(8_000, false))
    @Test fun midRamIsStandard() = assertEquals(Tier.STANDARD, TierPolicy.pick(4_000, true))
    @Test fun highRamIsPerformance() = assertEquals(Tier.PERFORMANCE, TierPolicy.pick(8_000, true))
    @Test fun modelTooBigForRam() = assertFalse(TierPolicy.modelFits(3_000, 6_000, 20_000))
    @Test fun modelFits() = assertTrue(TierPolicy.modelFits(1_000, 6_000, 20_000))
    @Test fun modelNeedsStorageReserve() = assertFalse(TierPolicy.modelFits(1_000, 6_000, 1_500))
}

class ManifestSanityTest {
    @org.junit.Test fun everyEntryHasHashAndHttpsUrl() {
        for (e in com.vision.assistant.core.ResourceManifest.entries) {
            org.junit.Assert.assertEquals(e.id, 64, e.sha256.length); org.junit.Assert.assertTrue(e.id, e.url.startsWith("https://")); org.junit.Assert.assertTrue(e.id, e.sizeBytes > 1_000_000)
        }
        val nine = com.vision.assistant.core.ResourceManifest.entries.first { it.id == "qwen3.5-9b-int4" }
        org.junit.Assert.assertTrue(nine.experimental); org.junit.Assert.assertEquals(6_060_357_536L, nine.sizeBytes)
        org.junit.Assert.assertEquals(1, com.vision.assistant.core.ResourceManifest.entries.count { it.experimental })
    }
}
