/**
 * Aura Music — applying an update without leaving the app.
 *
 * Every update action used to fire ACTION_VIEW on the asset's browser_download_url. On a TV with no
 * browser installed that is a dead end: the prompt appears, the remote clicks it, nothing happens.
 * This streams the APK into the app's own cache and hands it to the package installer through the
 * FileProvider the manifest already declares.
 *
 * Installing an APK from inside the app needs REQUEST_INSTALL_PACKAGES, which Android only grants a
 * sideloaded app by hand; [installPermissionIntent] is the way out to that switch.
 */

package com.aura.music.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ApkUpdateInstaller {
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val client = HttpClient {
        // A stall mid-download must surface as a failure the button can retry, not as a spinner that
        // never stops. Socket is the gap between packets, so a slow-but-moving CDN is never cut off.
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    /** Inside cacheDir, which provider_paths.xml already publishes through its cache-path root. */
    private fun targetFile(context: Context) = File(context.cacheDir, "update/AuraMusic.apk")

    /**
     * Stream [ReleaseAsset.downloadUrl] to disk, reporting every chunk. GitHub redirects an asset URL
     * to a CDN host and the engine follows that on its own, so the redirect must not be treated as
     * a failure.
     */
    suspend fun download(
        context: Context,
        asset: ReleaseAsset,
        onProgress: (done: Long, total: Long) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = targetFile(context)
                file.parentFile?.mkdirs()
                file.delete()

                client.prepareGet(asset.downloadUrl).execute { response ->
                    if (!response.status.isSuccess()) {
                        error("The download server returned ${response.status.value}")
                    }
                    val channel = response.bodyAsChannel()
                    val total = response.contentLength() ?: asset.size
                    val buffer = ByteArray(128 * 1024)

                    FileOutputStream(file).use { out ->
                        var done = 0L
                        while (true) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            when {
                                read > 0 -> {
                                    out.write(buffer, 0, read)
                                    done += read
                                    onProgress(done, total)
                                }
                                // Zero means the channel is open but empty; waiting on content keeps
                                // this off the CPU instead of spinning on an empty read.
                                channel.awaitContent() -> {}
                                else -> break
                            }
                        }
                    }

                    // A truncated APK installs as a corrupt package and the installer's message says
                    // nothing about where it came from. Fail here, with the byte counts.
                    if (asset.size > 0 && file.length() != asset.size) {
                        val written = file.length()
                        file.delete()
                        error("Got $written of ${asset.size} bytes")
                    }
                    file
                }
            }
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installIntent(
        context: Context,
        file: File,
    ): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun installPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    /** The cached APK is 27 MB on a 2 GB TV; drop it once the app is already on the newest build. */
    fun deleteDownloaded(context: Context) {
        runCatching { targetFile(context).delete() }
    }
}
