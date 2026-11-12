package com.seedlessds.app.emu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProfilesTest {
    /** A minimal cartridge header with [code] at 0x0C and a correct checksum at 0x15E. */
    private fun header(code: String, title: String = "MARIOKARTDS", fixCrc: Boolean = true): ByteArray {
        val h = ByteArray(0x160)
        title.toByteArray(Charsets.US_ASCII).copyInto(h, 0)
        code.toByteArray(Charsets.US_ASCII).copyInto(h, 0x0C)
        h[0x10] = '0'.code.toByte(); h[0x11] = '1'.code.toByte()
        if (fixCrc) {
            val crc = GameProfiles.crc16(h, 0, 0x15E)
            h[0x15E] = (crc and 0xFF).toByte(); h[0x15F] = (crc ushr 8).toByte()
        }
        return h
    }

    @Test fun crcMatchesModbusCheckValue() {
        // CRC-16/MODBUS check value for "123456789".
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        assertEquals(0x4B37, GameProfiles.crc16(data, 0, data.size))
    }

    @Test fun marioKartIsRecognisedInEveryRegion() {
        // Retail codes listed in the bundled game database: Americas, Europe, Japan, Korea.
        val profiles = listOf("AMCE", "AMCP", "AMCJ", "AMCK").map { GameProfiles.forCode(it) }
        profiles.forEach { assertNotNull(it) }
        profiles.forEach { assertSame(profiles[0], it) }
        // Any other region letter is the same game too.
        assertSame(profiles[0], GameProfiles.forCode("AMCC"))
        assertTrue(profiles[0]!!.apply(DsSettings()).focusMode)
    }

    @Test fun otherGamesAndBadCodesHaveNoProfile() {
        listOf("ASME", "AMKE", "XMCE", "AMC", "AMCEE", "amce", "AMC ", "", "####").forEach {
            assertNull(it, GameProfiles.forCode(it))
        }
        assertNull(GameProfiles.forCode(null))
    }

    @Test fun headerCodeIsReadForEachRegion() {
        listOf("AMCE", "AMCP", "AMCJ", "AMCK").forEach { code ->
            assertEquals(code, GameProfiles.codeFromHeader(header(code), requireCrc = true))
        }
    }

    @Test fun headerWithBadChecksumIsRejectedOnlyWhenRequired() {
        val h = header("AMCP", fixCrc = false)
        assertNull(GameProfiles.codeFromHeader(h, requireCrc = true))
        assertEquals("AMCP", GameProfiles.codeFromHeader(h, requireCrc = false))
    }

    @Test fun nonHeaderDataIsRejected() {
        // Zip local file header: the bytes at 0x0C are part of a CRC-32, not a game code.
        val zip = ByteArray(0x160).also { byteArrayOf(0x50, 0x4B, 0x03, 0x04).copyInto(it, 0) }
        assertNull(GameProfiles.codeFromHeader(zip, requireCrc = true))
        assertNull(GameProfiles.codeFromHeader(ByteArray(0x160), requireCrc = false))
        assertNull(GameProfiles.codeFromHeader(ByteArray(8), requireCrc = false))
        assertNull(GameProfiles.codeFromHeader(header("AMCE").copyOf(0x100), requireCrc = true))
    }
}
