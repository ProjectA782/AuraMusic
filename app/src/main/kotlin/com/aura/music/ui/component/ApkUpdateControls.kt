/**
 * Aura Music — the download-and-install action shared by the update prompt and Settings ▸ Updater.
 *
 * One state machine, two hosts, so a single button always names the next real step: download, then
 * install, then - if the TV has not been told Aura may install apps - open that switch, then install
 * again once it is on.
 */

package com.aura.music.ui.component

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aura.music.R
import com.aura.music.utils.ApkUpdateInstaller
import com.aura.music.utils.ReleaseAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

sealed interface ApkUpdateStage {
    data object Idle : ApkUpdateStage

    data class Downloading(val done: Long, val total: Long) : ApkUpdateStage

    /** On disk and verified against the asset size; the installer has not been started. */
    data object Ready : ApkUpdateStage

    /** On disk, but Android will not let this app start an installer yet. */
    data object NeedPermission : ApkUpdateStage

    data class Failed(val reason: String) : ApkUpdateStage
}

class ApkUpdateState internal constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var stage by mutableStateOf<ApkUpdateStage>(ApkUpdateStage.Idle)
        private set

    private var pending: ReleaseAsset? = null
    private var downloaded: File? = null

    @get:StringRes
    val actionLabel: Int
        get() =
            when (stage) {
                ApkUpdateStage.Idle -> R.string.download_update
                is ApkUpdateStage.Downloading -> R.string.downloading
                ApkUpdateStage.Ready -> R.string.install_update
                ApkUpdateStage.NeedPermission -> R.string.grant_install_permission
                is ApkUpdateStage.Failed -> R.string.retry
            }

    val actionEnabled: Boolean
        get() = stage !is ApkUpdateStage.Downloading

    /**
     * Hand the matched asset over. A new release must not inherit the previous release's stage: a
     * Ready button would install an APK that is no longer the newest thing published, so the stale
     * file goes with the stage.
     */
    fun prepare(asset: ReleaseAsset?) {
        if (asset?.downloadUrl == pending?.downloadUrl) return
        if (downloaded != null) {
            ApkUpdateInstaller.deleteDownloaded(context)
            downloaded = null
        }
        pending = asset
        stage = ApkUpdateStage.Idle
    }

    /** The one button: advance whichever step we are on. */
    fun act() {
        when (stage) {
            is ApkUpdateStage.Downloading -> Unit
            ApkUpdateStage.NeedPermission -> askForPermissionOrInstall()
            ApkUpdateStage.Ready -> install()
            else -> download()
        }
    }

    private fun download() {
        val asset =
            pending
                ?: run {
                    stage = ApkUpdateStage.Failed(context.getString(R.string.update_asset_missing))
                    return
                }
        scope.launch {
            stage = ApkUpdateStage.Downloading(0, asset.size)
            ApkUpdateInstaller
                .download(context, asset) { done, total ->
                    stage = ApkUpdateStage.Downloading(done, total)
                }.onSuccess { file ->
                    downloaded = file
                    install()
                }.onFailure {
                    stage =
                        ApkUpdateStage.Failed(
                            context.getString(
                                R.string.update_download_failed,
                                it.message ?: it.javaClass.simpleName,
                            ),
                        )
                }
        }
    }

    private fun install() {
        val file =
            downloaded
                ?: run {
                    download()
                    return
                }
        if (!ApkUpdateInstaller.canInstall(context)) {
            stage = ApkUpdateStage.NeedPermission
            return
        }
        stage = ApkUpdateStage.Ready
        runCatching { context.startActivity(ApkUpdateInstaller.installIntent(context, file)) }
            .onFailure {
                stage = ApkUpdateStage.Failed(context.getString(R.string.update_install_unavailable))
            }
    }

    /** Pressing this again after granting must install, not open the settings page a second time. */
    private fun askForPermissionOrInstall() {
        if (ApkUpdateInstaller.canInstall(context)) {
            install()
            return
        }
        runCatching { context.startActivity(ApkUpdateInstaller.installPermissionIntent(context)) }
            .onFailure {
                stage = ApkUpdateStage.Failed(context.getString(R.string.update_install_unavailable))
            }
    }
}

/**
 * The one state machine, held outside the composition tree.
 *
 * A per-screen instance gave the update prompt and Settings ▸ Updater two different answers, and the
 * Updater screen's died with the screen: a BACK partway through a 26 MB download cancelled it, and
 * re-entering showed a button whose asset had never been handed over. The scope is the object's own
 * because a composition scope is cancelled the moment its screen leaves.
 */
@Volatile
private var sharedApkUpdateState: ApkUpdateState? = null

@Composable
fun rememberApkUpdateState(key: Any? = Unit): ApkUpdateState {
    val context = LocalContext.current
    return remember(context, key) {
        sharedApkUpdateState
            ?: ApkUpdateState(
                context.applicationContext,
                CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            ).also { sharedApkUpdateState = it }
    }
}

/** The primary action, as a text button: label and behaviour both come from the stage. */
@Composable
fun ApkUpdateAction(
    state: ApkUpdateState,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = state::act,
        enabled = state.actionEnabled,
        modifier = modifier,
    ) {
        Text(stringResource(state.actionLabel))
    }
}

/** Progress, the downloaded-and-ready hint, and failures - what the button face cannot say. */
@Composable
fun ApkUpdateStatus(
    state: ApkUpdateState,
    modifier: Modifier = Modifier,
) {
    when (val current = state.stage) {
        ApkUpdateStage.Idle -> Unit

        is ApkUpdateStage.Downloading ->
            Column(modifier = modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = {
                        if (current.total > 0) {
                            (current.done.toFloat() / current.total.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.update_download_progress,
                            megabytes(current.done),
                            megabytes(current.total),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

        ApkUpdateStage.Ready ->
            Text(
                text = stringResource(R.string.update_ready_to_install),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.padding(top = 8.dp),
            )

        ApkUpdateStage.NeedPermission ->
            Text(
                text = stringResource(R.string.install_permission_needed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(top = 8.dp),
            )

        is ApkUpdateStage.Failed ->
            Text(
                text = current.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(top = 8.dp),
            )
    }
}

private fun megabytes(bytes: Long) =
    if (bytes <= 0L) {
        "0 MB"
    } else {
        String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
    }
