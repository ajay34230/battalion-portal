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
