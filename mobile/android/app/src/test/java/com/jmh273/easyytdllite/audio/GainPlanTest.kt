package com.jmh273.easyytdllite.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GainPlanTest {
    @Test
    fun `調小聲照套,不受峰值影響`() {
        // spike 3 的 B:track_gain -7.89、peak 1.20
        val g = GainPlan.plan(-7.89, 1.201731)
        assertEquals(-7.89, g.appliedDb, 1e-9)
        assertFalse(g.limited)
    }

    @Test
    fun `調大聲受峰值 0_98 限制`() {
        val g = GainPlan.plan(6.0, 0.70)
        assertEquals(20 * Math.log10(0.98 / 0.70), g.appliedDb, 1e-9) // 約 +2.92
        assertTrue(g.limited)
    }

    @Test
    fun `調大聲但峰值有空間時照套`() {
        val g = GainPlan.plan(2.0, 0.5) // 0.5 * 10^(2/20) = 0.63 < 0.98
        assertEquals(2.0, g.appliedDb, 1e-9)
        assertFalse(g.limited)
    }

    @Test
    fun `峰值未知或為 0 時不調大聲`() {
        assertEquals(0.0, GainPlan.plan(4.0, null).appliedDb, 1e-9)
        assertEquals(0.0, GainPlan.plan(4.0, 0.0).appliedDb, 1e-9)
        assertEquals(-3.0, GainPlan.plan(-3.0, null).appliedDb, 1e-9)
    }

    @Test
    fun `目標 92 dB 時多調 3 dB`() {
        val g = GainPlan.plan(-7.89, 1.20, targetDb = 92.0)
        assertEquals(-4.89, g.appliedDb, 1e-9)
        assertFalse(g.limited)
    }

    @Test
    fun `目標 80 dB 時少調 9 dB`() {
        val g = GainPlan.plan(-7.89, 1.20, targetDb = 80.0)
        assertEquals(-16.89, g.appliedDb, 1e-9)
        assertFalse(g.limited)
    }

    @Test
    fun `目標 95 dB 仍受峰值 0_98 限制`() {
        val g = GainPlan.plan(6.0, 0.70, targetDb = 95.0)
        assertEquals(20 * Math.log10(0.98 / 0.70), g.appliedDb, 1e-9)
        assertTrue(g.limited)
    }

    @Test
    fun `track gain 為正但目標調低後變負時不受峰值限制`() {
        // +2.0 + (83 - 89) = -4.0;峰值未知也照套
        val g = GainPlan.plan(2.0, null, targetDb = 83.0)
        assertEquals(-4.0, g.appliedDb, 1e-9)
        assertFalse(g.limited)
    }

    @Test
    fun `目標 89 dB 和不給目標相同`() {
        assertEquals(GainPlan.plan(6.0, 0.70), GainPlan.plan(6.0, 0.70, targetDb = 89.0))
        assertEquals(GainPlan.plan(-7.89, 1.2), GainPlan.plan(-7.89, 1.2, targetDb = 89.0))
    }
}
