package com.seedlessds.app.update

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seedlessds.app.BuildConfig
import com.seedlessds.app.R
import com.seedlessds.app.emu.SeedlessConfirmDialog
import com.seedlessds.app.emu.SeedlessDialog
import kotlinx.coroutines.launch

/** Offers the release found by [Updater], then shows download progress until the installer opens. */
@Composable
fun UpdatePrompt() {
    val release = Updater.available.value ?: return
    val progress = Updater.progress.value
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (progress != null) {
        SeedlessDialog(onDismiss = {}, dismissOnClickOutside = false) {
            Text(
                text = ctx.getString(R.string.update_downloading, (progress * 100).toInt()),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(22.dp),
            )
        }
        return
    }
    SeedlessConfirmDialog(
        title = ctx.getString(R.string.update_available_title),
        message = ctx.getString(R.string.update_available_message, release.versionName, BuildConfig.VERSION_NAME),
        confirmLabel = ctx.getString(R.string.update_install),
        cancelLabel = ctx.getString(R.string.update_later),
        onConfirm = { scope.launch { Updater.downloadAndInstall(ctx.applicationContext, release) } },
        onDismiss = { Updater.available.value = null },
        icon = Icons.Rounded.SystemUpdate,
        destructive = false,
    )
}
