package com.seedlessds.app.emu

import android.content.Context
import androidx.annotation.StringRes
import com.seedlessds.app.R

/**
 * Built-in single-screen profiles for games known to play well with one screen.
 *
 * A profile is applied once per game (per user profile) as ordinary per-game settings, so the
 * player can change or reset it afterwards like any other per-game override.
 */
object GameProfiles {
    private const val PREFS = "lessscreen"

    /** Offset of the cartridge header copy in main RAM (0x027FFE00), game code at +0x0C. */
    private const val HEADER_GAME_CODE = 0x3FFE0CL

    data class Profile(
        @StringRes val name: Int,
        val apply: (DsSettings) -> DsSettings,
    )

    /**
     * Keyed by the first three letters of the game code; the fourth is the region.
     * AMC = Mario Kart DS. The race is on the top screen and the bottom screen has the map
     * and standings, so the top screen fills the display and the map floats bottom-right,
     * under the top screen's item slot (top-left) and lap counter (top-right).
     */
    private val profiles = mapOf(
        "AMC" to Profile(R.string.profile_mario_kart_ds) {
            it.copy(
                focusMode = true,
                swapScreens = false,
                focusInsetVisible = true,
                focusInsetSize = FocusLayout.INSET_DEFAULT,
                focusInsetCorner = FocusLayout.CORNER_BOTTOM_RIGHT,
            )
        },
    )

    fun forCode(code: String): Profile? = if (code.length == 4) profiles[code.substring(0, 3)] else null

    /** The running game's 4-letter code from the header copy in main RAM, or null if unreadable. */
    fun readGameCode(): String? {
        val buf = ByteArray(4)
        val n = runCatching { com.seedlessds.app.SeedlessBridge.readMemory(HEADER_GAME_CODE, buf, 4) }.getOrDefault(0)
        if (n != 4) return null
        if (buf.any { b -> val c = b.toInt().toChar(); !(c in 'A'..'Z' || c in '0'..'9') }) return null
        return String(buf, Charsets.US_ASCII)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS + Profiles.suffix(ctx), Context.MODE_PRIVATE)

    fun wasApplied(ctx: Context, code: String): Boolean = prefs(ctx).getBoolean("applied_$code", false)

    fun markApplied(ctx: Context, code: String) { prefs(ctx).edit().putBoolean("applied_$code", true).apply() }
}
