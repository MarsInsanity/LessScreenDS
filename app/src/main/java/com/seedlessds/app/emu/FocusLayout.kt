package com.seedlessds.app.emu

import com.seedlessds.app.R
import kotlin.math.floor

/**
 * Focus layout: one DS screen fills the display and the other floats as a small inset in a
 * corner. Built for single-display handhelds where showing both screens at full size leaves
 * each one too small (for example 1240x1080 panels).
 *
 * The primary screen is the top screen unless the layout is swapped, either by the persistent
 * swap setting or while the "peek" hotkey is held.
 */
object FocusLayout {
    const val CORNER_TOP_LEFT = 0
    const val CORNER_TOP_RIGHT = 1
    const val CORNER_BOTTOM_LEFT = 2
    const val CORNER_BOTTOM_RIGHT = 3

    const val INSET_MIN = 15
    const val INSET_MAX = 60
    const val INSET_DEFAULT = 35

    /** Thickness of the frame drawn around the inset, in pixels. */
    const val BORDER_PX = 3

    /** True while the "peek" hotkey is held: the screens trade places until it is released. */
    @Volatile var peek: Boolean = false

    fun cornerLabels(uiContext: android.content.Context) = listOf(
        uiContext.getString(R.string.focus_corner_top_left),
        uiContext.getString(R.string.focus_corner_top_right),
        uiContext.getString(R.string.focus_corner_bottom_left),
        uiContext.getString(R.string.focus_corner_bottom_right),
    )

    /** Focus layout is in charge of the built-in display unless a dual-display preset is live. */
    fun active(s: DsSettings): Boolean = s.focusMode && !DualScreenPresets.live(s)

    /** Whether top and bottom are exchanged right now (persistent swap, flipped while peeking). */
    fun swapped(s: DsSettings): Boolean = s.swapScreens != peek

    /** In focus layout, true when the bottom DS screen is the large one. */
    fun bottomPrimary(s: DsSettings): Boolean = swapped(s)

    /** Primary and inset rectangles as [x, y, w, h] in view pixels; inset is null when hidden. */
    data class Rects(val primary: FloatArray, val inset: FloatArray?)

    fun rects(viewW: Float, viewH: Float, s: DsSettings): Rects = compute(
        viewW, viewH,
        insetPercent = s.focusInsetSize,
        corner = s.focusInsetCorner,
        insetVisible = s.focusInsetVisible,
        integer = s.integerScale,
    )

    fun compute(
        viewW: Float,
        viewH: Float,
        insetPercent: Int,
        corner: Int,
        insetVisible: Boolean,
        integer: Boolean,
    ): Rects {
        if (viewW <= 0f || viewH <= 0f) return Rects(floatArrayOf(0f, 0f, 0f, 0f), null)
        val (pw, ph) = fit(viewW, viewH, integer)
        val cornerClamped = corner.coerceIn(CORNER_TOP_LEFT, CORNER_BOTTOM_RIGHT)
        val insetAtBottom = cornerClamped == CORNER_BOTTOM_LEFT || cornerClamped == CORNER_BOTTOM_RIGHT
        val insetAtRight = cornerClamped == CORNER_TOP_RIGHT || cornerClamped == CORNER_BOTTOM_RIGHT

        val px = (viewW - pw) / 2f
        // Push the primary screen away from the inset so the inset sits in the spare band
        // (if any) instead of covering the game picture.
        val py = when {
            !insetVisible -> (viewH - ph) / 2f
            insetAtBottom -> 0f
            else -> viewH - ph
        }
        val primary = floatArrayOf(px, py, pw, ph)
        if (!insetVisible) return Rects(primary, null)

        val pct = insetPercent.coerceIn(INSET_MIN, INSET_MAX) / 100f
        var iw = pw * pct
        var ih = iw * 3f / 4f
        if (ih > viewH) { ih = viewH; iw = ih * 4f / 3f }
        val ix = if (insetAtRight) viewW - iw else 0f
        val iy = if (insetAtBottom) viewH - ih else 0f
        return Rects(primary, floatArrayOf(ix, iy, iw, ih))
    }

    private fun fit(viewW: Float, viewH: Float, integer: Boolean): Pair<Float, Float> {
        val a = 256f / 192f
        var w: Float
        var h: Float
        if (viewW / viewH > a) { h = viewH; w = viewH * a } else { w = viewW; h = viewW / a }
        if (integer) {
            val factor = floor(w / 256f).coerceAtLeast(1f)
            w = 256f * factor
            h = 192f * factor
        }
        return Pair(w, h)
    }

    fun contains(r: FloatArray?, x: Float, y: Float): Boolean =
        r != null && r[2] > 0f && r[3] > 0f && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3]
}
