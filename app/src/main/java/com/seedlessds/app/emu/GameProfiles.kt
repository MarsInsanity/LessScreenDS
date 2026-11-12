package com.seedlessds.app.emu

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.seedlessds.app.R

/**
 * Built-in single-screen profiles for games known to play well with one screen.
 *
 * Games are identified by the 4-letter game code in the cartridge header. The first three
 * letters name the title and the fourth is the region (E = Americas, P = Europe, J = Japan,
 * K = Korea, ...), so a profile keyed by the first three letters covers every region.
 *
 * A profile is applied once per game code (per user profile) as ordinary per-game settings,
 * so the player can change or reset it afterwards like any other per-game override.
 */
object GameProfiles {
    private const val PREFS = "lessscreen"

    private const val HEADER_SIZE = 0x160
    private const val HEADER_CODE = 0x0C
    private const val HEADER_CRC = 0x15E

    /** Main RAM offset of the cartridge header copy at 0x027FFE00. */
    private const val RAM_HEADER = 0x3FFE00L

    private val ARCHIVES = setOf("zip", "7z", "rar")

    data class Profile(
        @StringRes val name: Int,
        val apply: (DsSettings) -> DsSettings,
    )

    /**
     * AMC = Mario Kart DS: AMCE (Americas), AMCP (Europe), AMCJ (Japan), AMCK (Korea).
     * The race is on the top screen and the bottom screen has the map and standings, so the
     * top screen is the big one and the map sits bottom-right, away from the item slot
     * (top-left) and lap counter (top-right). On a 4:3 display it covers that corner of
     * the race; on wider or taller displays it moves into the spare space where there is some.
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

    fun forCode(code: String?): Profile? =
        if (code != null && isCode(code)) profiles[code.substring(0, 3)] else null

    private fun isCode(code: String): Boolean =
        code.length == 4 && code.all { it in 'A'..'Z' || it in '0'..'9' }

    /** CRC-16/MODBUS, the checksum the DS uses for the cartridge header. */
    fun crc16(data: ByteArray, off: Int, len: Int): Int {
        var crc = 0xFFFF
        for (i in off until off + len) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }

    /**
     * The game code from a cartridge header. With [requireCrc] the header's own checksum must
     * match, which rules out reading a code from something that is not an NDS header.
     */
    fun codeFromHeader(h: ByteArray, requireCrc: Boolean): String? {
        if (h.size < HEADER_CODE + 4) return null
        if (requireCrc) {
            if (h.size < HEADER_SIZE) return null
            val stored = (h[HEADER_CRC].toInt() and 0xFF) or ((h[HEADER_CRC + 1].toInt() and 0xFF) shl 8)
            if (crc16(h, 0, HEADER_CRC) != stored) return null
        }
        val code = String(h, HEADER_CODE, 4, Charsets.US_ASCII)
        return if (isCode(code)) code else null
    }

    /**
     * The game code from the ROM file, before the game starts. Only plain ROM files are read;
     * archives return null and are identified from RAM once the game is running.
     */
    fun readGameCodeFromRom(ctx: Context, fileName: String, path: String?, uri: Uri?): String? = runCatching {
        if (fileName.substringAfterLast('.', "").lowercase() in ARCHIVES) return null
        val stream = when {
            path != null -> java.io.FileInputStream(path)
            uri != null -> ctx.contentResolver.openInputStream(uri)
            else -> null
        } ?: return null
        stream.use { input ->
            val h = ByteArray(HEADER_SIZE)
            var n = 0
            while (n < h.size) {
                val r = input.read(h, n, h.size - n)
                if (r < 0) break
                n += r
            }
            if (n < h.size) null else codeFromHeader(h, requireCrc = true)
        }
    }.getOrNull()

    /**
     * The running game's code from the header copy in main RAM, or null if it is not there yet.
     * Direct boot writes it before the first frame; a firmware boot writes it while booting.
     */
    fun readGameCodeFromRam(): String? {
        val h = ByteArray(HEADER_SIZE)
        val n = runCatching { com.seedlessds.app.SeedlessBridge.readMemory(RAM_HEADER, h, h.size) }.getOrDefault(0)
        if (n != h.size) return null
        return codeFromHeader(h, requireCrc = true) ?: codeFromHeader(h, requireCrc = false)
    }

    /** The profile to apply now, or null when there is none, it was applied before, or the player has their own settings. */
    fun pending(ctx: Context, gameKey: String, code: String?): Profile? {
        val p = forCode(code) ?: return null
        if (com.seedlessds.app.control.RuntimeControl.settings != null) return null
        if (SettingsRepo.hasPerGame(ctx, gameKey) || wasApplied(ctx, code!!)) return null
        return p
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS + Profiles.suffix(ctx), Context.MODE_PRIVATE)

    fun wasApplied(ctx: Context, code: String): Boolean = prefs(ctx).getBoolean("applied_$code", false)

    fun markApplied(ctx: Context, code: String) { prefs(ctx).edit().putBoolean("applied_$code", true).apply() }
}
