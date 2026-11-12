package com.seedlessds.app.emu

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusLayoutTest {
    private val eps = 0.01f

    private fun compute(w: Float, h: Float, pct: Int = 35, corner: Int = FocusLayout.CORNER_BOTTOM_RIGHT,
                        visible: Boolean = true, integer: Boolean = false) =
        FocusLayout.compute(w, h, pct, corner, visible, integer)

    @Test fun nearSquarePanelPutsInsetInSpareBand() {
        // Retroid Pocket Mini V2: 1240x1080.
        val r = compute(1240f, 1080f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1240f, 930f), r.primary, eps)
        assertArrayEquals(floatArrayOf(806f, 754.5f, 434f, 325.5f), r.inset!!, eps)
    }

    @Test fun fourByThreeDisplayIsFilledAndInsetOverlapsCorner() {
        // Common single-screen handhelds: 640x480, 1024x768, 1280x960.
        for ((w, h) in listOf(640f to 480f, 1024f to 768f, 1280f to 960f)) {
            val r = compute(w, h)
            assertArrayEquals(floatArrayOf(0f, 0f, w, h), r.primary, eps)
            val iw = w * 0.35f
            assertArrayEquals(floatArrayOf(w - iw, h - iw * 0.75f, iw, iw * 0.75f), r.inset!!, eps)
        }
    }

    @Test fun fourByThreeEveryCornerStaysOnScreen() {
        for (corner in FocusLayout.CORNER_TOP_LEFT..FocusLayout.CORNER_BOTTOM_RIGHT) {
            val r = compute(640f, 480f, corner = corner)
            assertArrayEquals(floatArrayOf(0f, 0f, 640f, 480f), r.primary, eps)
            val i = r.inset!!
            assertTrue(i[0] >= 0f && i[1] >= 0f && i[0] + i[2] <= 640f + eps && i[1] + i[3] <= 480f + eps)
        }
    }

    @Test fun hiddenInsetCentresPrimary() {
        val r = compute(1240f, 1080f, visible = false)
        assertArrayEquals(floatArrayOf(0f, 75f, 1240f, 930f), r.primary, eps)
        assertNull(r.inset)
    }

    @Test fun topCornerPushesPrimaryDown() {
        val r = compute(1240f, 1080f, corner = FocusLayout.CORNER_TOP_LEFT)
        assertArrayEquals(floatArrayOf(0f, 150f, 1240f, 930f), r.primary, eps)
        assertEquals(0f, r.inset!![0], eps)
        assertEquals(0f, r.inset[1], eps)
    }

    @Test fun widePanelPillarboxesPrimary() {
        val r = compute(1920f, 1080f, corner = FocusLayout.CORNER_TOP_RIGHT)
        assertArrayEquals(floatArrayOf(240f, 0f, 1440f, 1080f), r.primary, eps)
        assertArrayEquals(floatArrayOf(1416f, 0f, 504f, 378f), r.inset!!, eps)
    }

    @Test fun insetSizeIsClamped() {
        val big = compute(1240f, 1080f, pct = 95)
        assertEquals(1240f * FocusLayout.INSET_MAX / 100f, big.inset!![2], eps)
        val small = compute(1240f, 1080f, pct = 1)
        assertEquals(1240f * FocusLayout.INSET_MIN / 100f, small.inset!![2], eps)
    }

    @Test fun integerScaleUsesWholeMultiples() {
        val r = compute(1240f, 1080f, integer = true)
        assertArrayEquals(floatArrayOf(108f, 0f, 1024f, 768f), r.primary, eps)
    }

    @Test fun emptyViewHasNoArea() {
        val r = compute(0f, 0f)
        assertEquals(0f, r.primary[2], eps)
        assertNull(r.inset)
    }

    @Test fun containsChecksBounds() {
        val rect = floatArrayOf(10f, 20f, 100f, 50f)
        assertTrue(FocusLayout.contains(rect, 10f, 20f))
        assertTrue(FocusLayout.contains(rect, 109.9f, 69.9f))
        assertFalse(FocusLayout.contains(rect, 110f, 30f))
        assertFalse(FocusLayout.contains(null, 50f, 50f))
        assertFalse(FocusLayout.contains(floatArrayOf(0f, 0f, 0f, 0f), 0f, 0f))
    }
}
