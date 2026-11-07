/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.settings

import android.webkit.CookieManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import com.aura.music.ui.component.AuraSwitch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import timber.log.Timber
import com.aura.music.utils.reportException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.aura.innertube.YouTube
import com.aura.innertube.utils.parseCookieString
import com.aura.music.BuildConfig
import com.aura.music.LocalAuraTvShell
import com.aura.music.R
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.theme.AuraMetrics
import com.aura.music.ui.theme.AuraPrimaryContainer
import com.aura.music.ui.theme.AuraTextPrimary
import androidx.compose.ui.text.style.TextOverflow
import com.aura.music.constants.EnableSpotifyKey
import com.aura.music.constants.InnerTubeCookieKey
import com.aura.music.constants.SpotifyAccessTokenKey
import com.aura.music.constants.SpotifySpDcKey
import com.aura.music.constants.SpotifySpKeyKey
import com.aura.music.constants.SpotifyTokenExpiryKey
import com.aura.music.constants.SpotifyUserIdKey
import com.aura.music.constants.SpotifyUsernameKey
import com.aura.music.constants.UseLoginForBrowse
import com.aura.music.constants.YtmSyncKey
import com.aura.music.playback.SpotifyMetadataRegistry
import com.aura.music.playback.SpotifyProfileCache
import com.aura.music.ui.component.DefaultDialog
import com.aura.music.ui.component.Material3SettingsGroup
import com.aura.music.ui.component.Material3SettingsItem
import com.aura.music.ui.screens.settings.integrations.SpotifyAccountContent
import com.aura.music.utils.Updater
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.AccountSettingsViewModel
import com.aura.music.viewmodels.HomeViewModel

@Composable
fun AccountSettings(
    navController: NavController,
    onClose: () -> Unit,
    latestVersionName: String
) {
    val context = LocalContext.current

    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")

    var spotifyUsername by rememberPreference(SpotifyUsernameKey, "")
    var spotifyAccessToken by rememberPreference(SpotifyAccessTokenKey, "")
    var spotifySpDc by rememberPreference(SpotifySpDcKey, "")
    var spotifySpKey by rememberPreference(SpotifySpKeyKey, "")
    var spotifyTokenExpiry by rememberPreference(SpotifyTokenExpiryKey, 0L)
    var spotifyUserId by rememberPreference(SpotifyUserIdKey, "")
    val (_, onEnableSpotifyChange) = rememberPreference(EnableSpotifyKey, true)

    // This sheet is the Spotify account; the YouTube cookie only gates the rows
    // that talk to YouTube Music directly.
    val isLoggedIn = remember(spotifyAccessToken, spotifySpDc) {
        spotifyAccessToken.isNotEmpty() && spotifySpDc.isNotEmpty()
    }
    val isYouTubeLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (useLoginForBrowse, onUseLoginForBrowseChange) = rememberPreference(UseLoginForBrowse, true)
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsStateWithLifecycle()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val spotifyLogout: () -> Unit = {
        spotifyAccessToken = ""
        spotifySpDc = ""
        spotifySpKey = ""
        spotifyTokenExpiry = 0L
        spotifyUsername = ""
        spotifyUserId = ""
        onEnableSpotifyChange(false)
        CookieManager.getInstance().removeAllCookies(null)
        // Without these the next login — possibly another account — shows stale data.
        SpotifyMetadataRegistry.clearAll()
        scope.launch { SpotifyProfileCache.clearAllPersisted(context) }
    }

    Column(
        modifier = Modifier
            .padding(AuraMetrics.ModalPadding)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(AuraMetrics.ProfileAvatarSize)
                    .clip(CircleShape)
                    .background(AuraPrimaryContainer, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                if (isYouTubeLoggedIn && accountImageUrl != null) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            if (isLoggedIn) R.drawable.spotify else R.drawable.account
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isLoggedIn && spotifyUsername.isNotEmpty() -> spotifyUsername
                        isLoggedIn -> stringResource(R.string.account)
                        isYouTubeLoggedIn -> accountName
                        else -> stringResource(R.string.app_name)
                    },
                    style = MaterialTheme.typography.displaySmall,
                    color = AuraTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        if (isLoggedIn) R.string.spotify_source_spotify else R.string.not_logged_in
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.close))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Logout confirmation dialog
        if (showLogoutDialog) {
            val clearAndLogout: () -> Unit = {
                Timber.d("[LOGOUT_CLEAR] User chose to clear data")
                scope.launch {
                    try {
                        Timber.d("[LOGOUT_CLEAR] Starting clear and logout process")
                        // Forget account first (stops all sync), then clear data.
                        // This prevents background syncs from re-adding songs.
                        accountSettingsViewModel.logoutAndClearLibraryData(context)
                        Timber.d("[LOGOUT_CLEAR] Library data cleared and account forgotten")
                    } catch (e: Exception) {
                        Timber.e(e, "[LOGOUT_CLEAR] Error clearing library data, proceeding with logout")
                        reportException(e)
                    }
                    onInnerTubeCookieChange("")
                    spotifyLogout()
                    Timber.d("[LOGOUT_CLEAR] Logout complete")
                    showLogoutDialog = false
                    onClose()
                }
            }
            val keepAndLogout: () -> Unit = {
                Timber.d("[LOGOUT_KEEP] User chose to keep data")
                scope.launch {
                    Timber.d("[LOGOUT_KEEP] Starting logout process (keeping data)")
                    accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                    spotifyLogout()
                    Timber.d("[LOGOUT_KEEP] Logout complete")
                    showLogoutDialog = false
                    onClose()
                }
            }
            DefaultDialog(
                onDismiss = { showLogoutDialog = false },
                title = { Text(stringResource(R.string.logout_dialog_title)) },
                content = {
                    Text(
                        text = stringResource(R.string.logout_dialog_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    )
                },
                buttons = {
                    if (LocalAuraTvShell.current) {
                        AuraPillButton(
                            label = stringResource(R.string.logout_clear),
                            filled = true,
                            onClick = clearAndLogout,
                        )
                        AuraPillButton(
                            label = stringResource(R.string.logout_keep),
                            onClick = keepAndLogout,
                        )
                    } else {
                        TextButton(onClick = clearAndLogout) {
                            Text(stringResource(R.string.logout_clear))
                        }
                        TextButton(onClick = keepAndLogout) {
                            Text(stringResource(R.string.logout_keep))
                        }
                    }
                }
            )
        }

        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    title = {
                        Text(
                            text = when {
                                isLoggedIn && spotifyUsername.isNotEmpty() -> spotifyUsername
                                isLoggedIn -> stringResource(R.string.account)
                                else -> stringResource(R.string.spotify_login)
                            }
                        )
                    },
                    icon = painterResource(if (isLoggedIn) R.drawable.account else R.drawable.spotify),
                    trailingContent = {
                        if (isLoggedIn) {
                            if (LocalAuraTvShell.current) {
                                AuraPillButton(
                                    label = stringResource(R.string.action_logout),
                                    onClick = {
                                        Timber.d("[LOGOUT] User clicked logout button")
                                        if (isYouTubeLoggedIn) {
                                            showLogoutDialog = true
                                        } else {
                                            spotifyLogout()
                                        }
                                    },
                                )
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        Timber.d("[LOGOUT] User clicked logout button")
                                        if (isYouTubeLoggedIn) {
                                            showLogoutDialog = true
                                        } else {
                                            spotifyLogout()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(stringResource(R.string.action_logout))
                                }
                            }
                        }
                    },
                    onClick = if (!isLoggedIn) {
                        {
                            onClose()
                            navController.navigate("settings/spotify/login")
                        }
                    } else if (isYouTubeLoggedIn) {
                        {
                            onClose()
                            navController.navigate("account")
                        }
                    } else {
                        null
                    }
                )
            ),
            useLowContrast = true
        )

        Spacer(Modifier.height(8.dp))

        SpotifyAccountContent(
            navController = navController,
            showBackendStatus = false,
            beforeNavigate = onClose,
        )

        if (isYouTubeLoggedIn) {
            Spacer(Modifier.height(8.dp))

            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.switch_youtube_channel)) },
                        icon = painterResource(R.drawable.account),
                        onClick = {
                            onClose()
                            navController.navigate("switch_channel")
                        },
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.more_content)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            AuraSwitch(
                                checked = useLoginForBrowse,
                                onCheckedChange = {
                                    YouTube.useLoginForBrowse = it
                                    onUseLoginForBrowseChange(it)
                                },
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (useLoginForBrowse) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    ),
                    Material3SettingsItem(
                        title = { Text(stringResource(R.string.yt_sync)) },
                        icon = painterResource(R.drawable.cached),
                        trailingContent = {
                            AuraSwitch(
                                checked = ytmSync,
                                onCheckedChange = onYtmSyncChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (ytmSync) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        }
                    )
                ),
                useLowContrast = true
            )
        }

        Spacer(Modifier.height(12.dp))

        Material3SettingsGroup(
            items = buildList {
                if (BuildConfig.UPDATER_AVAILABLE && Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, latestVersionName)) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val asset = releaseInfo?.let { Updater.getAssetForCurrentVariant(it) }
                    if (asset != null) {
                        add(
                            Material3SettingsItem(
                                title = { Text(stringResource(R.string.new_version_available)) },
                                description = { Text(latestVersionName) },
                                icon = painterResource(R.drawable.update),
                                showBadge = true,
                                onClick = { navController.navigate("settings/updater") }
                            )
                        )
                    }
                }
            },
            useLowContrast = true
        )
    }
}
