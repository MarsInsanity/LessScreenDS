package com.seedlessds.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {
    private val repo = "MarsInsanity/LessScreenDS"
    private val sha = "a".repeat(64)

    private fun json(url: String = "https://github.com/$repo/releases/download/v1.0.1-ls.12/LessScreenDS-1.0.1-ls.12.apk",
                     sha256: String = sha, code: Int = 1012) =
        """{"versionCode": $code, "versionName": "1.0.1-ls.12", "tag": "v1.0.1-ls.12",
            "apk": "LessScreenDS-1.0.1-ls.12.apk", "url": "$url", "sha256": "$sha256", "size": 39000000}"""

    @Test fun parsesReleaseFromThisRepository() {
        val r = Updater.parse(json(), repo)
        assertNotNull(r)
        assertEquals(1012, r!!.versionCode)
        assertEquals("1.0.1-ls.12", r.versionName)
        assertEquals(sha, r.sha256)
        assertEquals(39000000L, r.size)
    }

    @Test fun uppercaseChecksumIsNormalised() {
        assertEquals(sha, Updater.parse(json(sha256 = sha.uppercase()), repo)!!.sha256)
    }

    @Test fun rejectsApkHostedAnywhereElse() {
        assertNull(Updater.parse(json(url = "https://github.com/someone/else/releases/download/v1/x.apk"), repo))
        assertNull(Updater.parse(json(url = "http://github.com/$repo/releases/download/v1/x.apk"), repo))
        assertNull(Updater.parse(json(url = "https://example.com/$repo/releases/download/v1/x.apk"), repo))
        assertNull(Updater.parse(json(url = "https://github.com/$repo/raw/main/x.apk"), repo))
    }

    @Test fun rejectsBadChecksumOrJson() {
        assertNull(Updater.parse(json(sha256 = "abc"), repo))
        assertNull(Updater.parse(json(sha256 = "g".repeat(64)), repo))
        assertNull(Updater.parse("not json", repo))
        assertNull(Updater.parse("{}", repo))
    }
}
