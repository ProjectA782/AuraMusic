/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import com.aura.music.ui.component.AuraSwitch
import androidx.compose.material3.Text
import com.aura.music.ui.component.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.music.BuildConfig
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.constants.CheckForUpdatesKey
import com.aura.music.constants.UpdateNotificationsEnabledKey
import com.aura.music.ui.component.ApkUpdateStatus
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.Material3SettingsGroup
import com.aura.music.ui.component.Material3SettingsItem
import com.aura.music.ui.component.auraSettingsGutter
import com.aura.music.ui.component.rememberApkUpdateState
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.ReleaseInfo
import com.aura.music.utils.Updater
import com.aura.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterScreen(
    navController: NavController
) {
    val (checkForUpdates, onCheckForUpdatesChange) = rememberPreference(CheckForUpdatesKey, true)
    val (updateNotifications, onUpdateNotificationsChange) = rememberPreference(UpdateNotificationsEnabledKey, true)

    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var showChangelog by remember { mutableStateOf(false) }
    var changelogContent by remember { mutableStateOf<String?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }
    val failedToCheckUpdatesTemplate = stringResource(R.string.failed_to_check_updates)
    val updateState = rememberApkUpdateState()

    val coroutineScope = rememberCoroutineScope()

    fun showRelease(releaseInfo: ReleaseInfo, hasUpdate: Boolean) {
        latestVersion = releaseInfo.versionName
        updateAvailable = hasUpdate
        changelogContent = releaseInfo.description
        updateState.prepare(
            if (hasUpdate) Updater.getAssetForCurrentVariant(releaseInfo) else null,
        )
    }

    // The check's result lived only in this screen, so leaving it and coming back showed a page that
    // had never heard of the update it offered seconds ago, and a download that is still running
    // lost its button. The release cache is process-wide, so answer from it first: no round-trip.
    LaunchedEffect(Unit) {
        Updater.getCachedLatestRelease()?.let {
            showRelease(it, Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, it.versionName))
        }
    }

    fun performManualCheck() {
        coroutineScope.launch {
            isChecking = true
            checkError = null
            withContext(Dispatchers.IO) {
                Updater
                    .checkForUpdate(forceRefresh = true)
                    .onSuccess { (releaseInfo, hasUpdate) ->
                        if (releaseInfo != null) showRelease(releaseInfo, hasUpdate)
                    }.onFailure {
                        checkError = String.format(failedToCheckUpdatesTemplate, it.message ?: "Unknown error")
                    }
            }
            isChecking = false
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
    ) {
        // The shell gives this destination an exact page box, so a bar emitted after the list
        // becomes a sibling of it and paints over the rows. It is the page heading: first child,
        // pinned, with the list scrolling under it.
        TopAppBar(
            title = { Text(stringResource(R.string.updater)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .auraSettingsGutter(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // No top-inset spacer here: the shell already moves the page box below the
            // now-playing banner, so the old one reserved the banner height a second
            // time and opened a 128px void between this header and the first group.

        Spacer(Modifier.height(4.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.current_version),
            items =
                listOf(
                    Material3SettingsItem(
                        title = {
                            Text(stringResource(R.string.version_format, BuildConfig.VERSION_NAME))
                        },
                        description = {
                            val arch = BuildConfig.ARCHITECTURE
                            val variant = if (BuildConfig.FLAVOR == "gms") "GMS" else "FOSS"
                            Text("$arch - $variant")
                        },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.update_settings),
            items =
                buildList {
                    add(
                        Material3SettingsItem(
                            title = { Text(stringResource(R.string.check_for_updates)) },
                            icon = painterResource(R.drawable.update),
                            trailingContent = {
                                AuraSwitch(
                                    checked = checkForUpdates,
                                    onCheckedChange = onCheckForUpdatesChange,
                                )
                            },
                            onClick = { onCheckForUpdatesChange(!checkForUpdates) },
                        ),
                    )

                    if (checkForUpdates) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.update_notifications)) },
                                icon = painterResource(R.drawable.notification),
                                trailingContent = {
                                    AuraSwitch(
                                        checked = updateNotifications,
                                        onCheckedChange = onUpdateNotificationsChange,
                                    )
                                },
                                onClick = { onUpdateNotificationsChange(!updateNotifications) },
                            ),
                        )
                    }
                },
        )

        Spacer(Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.check_for_updates_title),
            items =
                listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.refresh),
                        title = {
                            if (isChecking) {
                                Text(stringResource(R.string.checking_for_updates))
                            } else if (latestVersion != null) {
                                Text(stringResource(R.string.latest_version_format, latestVersion!!))
                            } else {
                                Text(stringResource(R.string.check_for_updates_button))
                            }
                        },
                        trailingContent = {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else if (updateAvailable) {
                                Icon(
                                    painter = painterResource(R.drawable.download),
                                    contentDescription = stringResource(R.string.update_available_title),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = { if (!isChecking) performManualCheck() },
                    ),
                ),
        )

        checkError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (updateAvailable && latestVersion != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showChangelog = !showChangelog },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) {
                Text(if (showChangelog) stringResource(R.string.hide_changelog) else stringResource(R.string.view_changelog))
            }

            if (showChangelog && changelogContent != null) {
                Spacer(Modifier.height(12.dp))
                // The release body is markdown; dumped as text it shows the # and - markers, which is
                // what the update prompt used to do too.
                MarkdownText(changelogContent!!)
            }

            // The old button handed the asset URL to ACTION_VIEW, which on a TV with no browser is
            // a dead end. This one is the next real step: download, then install, then open the
            // unknown-sources switch if the TV has not been told Aura may install apps.
            Spacer(Modifier.height(16.dp))
            Material3SettingsGroup(
                items =
                    listOf(
                        Material3SettingsItem(
                            title = { Text(stringResource(updateState.actionLabel)) },
                            description = { ApkUpdateStatus(updateState) },
                            icon = painterResource(R.drawable.download),
                            enabled = updateState.actionEnabled,
                            onClick = { updateState.act() },
                        ),
                    ),
                useLowContrast = true,
            )
        }

        Spacer(Modifier.height(32.dp))
        }
    }
}
