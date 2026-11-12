package com.seedlessds.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import com.seedlessds.app.BuildConfig
import com.seedlessds.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Self-update from GitHub releases. Each release built by the CI workflow carries the APK and
 * an update.json describing it (versionCode, versionName, url, sha256). The app reads the
 * update.json of the latest release, and when it is newer, downloads the APK, checks its
 * SHA-256 and hands it to Android's package installer, which asks the user to confirm.
 *
 * Only release builds made by CI know their repository (BuildConfig.UPDATE_REPO); local and
 * debug builds never check.
 */
object Updater {
    private const val PREFS = "lessscreen_updates"
    private const val KEY_AUTO = "auto_check"
    private const val KEY_LAST_CHECK = "last_check"
    private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val sha256: String,
        val size: Long,
    )

    /** The release offered to the user, shown by [UpdatePrompt]. */
    val available = mutableStateOf<Release?>(null)

    /** Download progress 0..1 while an update downloads, otherwise null. */
    val progress = mutableStateOf<Float?>(null)

    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank() && BuildConfig.BUILD_TYPE == "release"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCheck(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO, true)

    fun setAutoCheck(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean(KEY_AUTO, on).apply() }

    /** Parses an update.json, accepting only APKs published on this repository's releases. */
    fun parse(json: String, repo: String): Release? = runCatching {
        val o = JSONObject(json)
        val url = o.getString("url")
        if (!url.startsWith("https://github.com/$repo/releases/download/")) return null
        val sha = o.getString("sha256").lowercase()
        if (sha.length != 64 || sha.any { it !in '0'..'9' && it !in 'a'..'f' }) return null
        Release(o.getInt("versionCode"), o.getString("versionName"), url, sha, o.optLong("size", -1L))
    }.getOrNull()

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.UPDATE_REPO
        val req = Request.Builder()
            .url("https://github.com/$repo/releases/latest/download/update.json")
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            parse(resp.body?.string() ?: return@withContext null, repo)
        }
    }

    /** Launch-time check: at most every few hours, silent when there is nothing new or no network. */
    suspend fun checkInBackground(ctx: Context) {
        if (!enabled || !autoCheck(ctx)) return
        val now = System.currentTimeMillis()
        if (now - prefs(ctx).getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        val latest = runCatching { fetchLatest() }.getOrNull() ?: return
        prefs(ctx).edit().putLong(KEY_LAST_CHECK, now).apply()
        if (latest.versionCode > BuildConfig.VERSION_CODE) available.value = latest
    }

    /** "Check now" from settings: always reports the outcome. */
    suspend fun checkNow(ctx: Context) {
        if (!enabled) return
        val latest = try {
            fetchLatest()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            toast(ctx, ctx.getString(R.string.update_failed, e.message ?: e.javaClass.simpleName))
            return
        }
        prefs(ctx).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        when {
            latest == null -> toast(ctx, ctx.getString(R.string.update_no_release))
            latest.versionCode > BuildConfig.VERSION_CODE -> {
                available.value = latest
                toast(ctx, ctx.getString(R.string.update_found, latest.versionName))
            }
            else -> toast(ctx, ctx.getString(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
        }
    }

    /** Downloads (or reuses an already verified download of) the release and opens the installer. */
    suspend fun downloadAndInstall(ctx: Context, release: Release) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            toast(ctx, ctx.getString(R.string.update_allow_installs))
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        try {
            progress.value = 0f
            val apk = download(ctx, release)
            progress.value = null
            install(ctx, apk)
        } catch (e: Exception) {
            progress.value = null
            if (e is kotlinx.coroutines.CancellationException) throw e
            toast(ctx, ctx.getString(R.string.update_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun updateDir(ctx: Context): File {
        // Before Android 7 the installer cannot read content:// URIs, so use app-specific external storage.
        val base = if (Build.VERSION.SDK_INT >= 24) ctx.cacheDir else (ctx.externalCacheDir ?: ctx.cacheDir)
        return File(base, "updates").apply { mkdirs() }
    }

    private suspend fun download(ctx: Context, release: Release): File = withContext(Dispatchers.IO) {
        val dir = updateDir(ctx)
        val target = File(dir, "LessScreenDS-${release.versionCode}.apk")
        if (target.isFile && sha256(target) == release.sha256) return@withContext target
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, target.name + ".part")
        client.newCall(Request.Builder().url(release.url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: release.size
            val digest = MessageDigest.getInstance("SHA-256")
            body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) progress.value = (done.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (hex != release.sha256) {
                part.delete()
                throw java.io.IOException(ctx.getString(R.string.update_checksum_mismatch))
            }
        }
        if (!part.renameTo(target)) throw java.io.IOException("could not save the download")
        target
    }

    private fun sha256(f: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun install(ctx: Context, apk: File) {
        val uri = if (Build.VERSION.SDK_INT >= 24)
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        else Uri.fromFile(apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    }

    private suspend fun toast(ctx: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()
    }
}
