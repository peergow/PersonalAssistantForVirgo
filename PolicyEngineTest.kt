package com.disiplinai

import com.disiplinai.data.AppRule
import com.disiplinai.data.Config
import com.disiplinai.data.Profile
import com.disiplinai.data.Progress
import com.disiplinai.logic.PolicyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyEngineTest {
    @Test fun sleepOvernight() {
        val p = Profile(wakeMin = 330, sleepMin = 1350) // 05:30 - 22:30
        assertTrue(PolicyEngine.inSleep(1400, p))
        assertTrue(PolicyEngine.inSleep(100, p))
        assertFalse(PolicyEngine.inSleep(600, p))
    }

    @Test fun sleepAfterMidnight() {
        val p = Profile(wakeMin = 480, sleepMin = 60) // 01:00 - 08:00
        assertTrue(PolicyEngine.inSleep(120, p))
        assertFalse(PolicyEngine.inSleep(30, p))
        assertFalse(PolicyEngine.inSleep(600, p))
    }

    @Test fun penaltyIsCapped() {
        val cfg = Config(penaltyPerStrikePct = 20, maxPenaltyPct = 60)
        assertEquals(0, PolicyEngine.penaltyPct(cfg, Progress(strikes = 0)))
        assertEquals(40, PolicyEngine.penaltyPct(cfg, Progress(strikes = 2)))
        assertEquals(60, PolicyEngine.penaltyPct(cfg, Progress(strikes = 9)))
    }

    @Test fun effectiveLimit() {
        val cfg = Config(penaltyPerStrikePct = 20, maxPenaltyPct = 60)
        val r = AppRule("a", "A", 30)
        assertEquals(24, PolicyEngine.effectiveLimit(r, cfg, Progress(strikes = 1)))
        assertEquals(0, PolicyEngine.effectiveLimit(AppRule("a", "A", 0), cfg, Progress()))
        assertEquals(5, PolicyEngine.effectiveLimit(AppRule("a", "A", 10), cfg, Progress(strikes = 3)))
    }
}
