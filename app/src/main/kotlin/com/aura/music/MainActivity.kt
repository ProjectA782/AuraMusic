/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music

import com.aura.music.ui.theme.isAuraDarkTheme
import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.aura.music.ui.component.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.aura.music.ui.component.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.aura.innertube.YouTube
import com.aura.innertube.models.SongItem
import com.aura.innertube.models.WatchEndpoint
import com.aura.music.constants.AppBarHeight
import com.aura.music.constants.AppLanguageKey
import com.aura.music.constants.CheckForUpdatesKey
import com.aura.music.constants.DarkModeKey
import com.aura.music.constants.DefaultOpenTabKey
import com.aura.music.constants.DismissedKmpUpdateKey
import com.aura.music.constants.DismissedStandaloneUpdateKey
import com.aura.music.constants.DensityScaleKey
import com.aura.music.constants.DisableScreenshotKey
import com.aura.music.constants.EnableHighRefreshRateKey
import com.aura.music.constants.EnableLandscapeScalingKey
import com.aura.music.constants.ExperimentalLyricsKey
import com.aura.music.constants.LastSeenVersionKey
import com.aura.music.constants.ListenTogetherUsernameKey
import com.aura.music.constants.LyricsProviderOrderKey
import com.aura.music.constants.SpotifyAccessTokenKey
import com.aura.music.constants.MiniPlayerBottomSpacing
import com.aura.music.constants.MiniPlayerHeight
import com.aura.music.constants.NavigationBarAnimationSpec
import com.aura.music.constants.NavigationBarHeight
import com.aura.music.constants.PauseListenHistoryKey
import com.aura.music.constants.PauseSearchHistoryKey
import com.aura.music.constants.PreferredLyricsProvider
import com.aura.music.constants.PreferredLyricsProviderKey
import com.aura.music.constants.PureBlackKey
import com.aura.music.constants.SYSTEM_DEFAULT
import com.aura.music.constants.SelectedThemeColorKey
import com.aura.music.constants.SimpMusicMigrationDoneKey
import com.aura.music.constants.SlimNavBarHeight
import com.aura.music.constants.SlimNavBarKey
import com.aura.music.constants.StopMusicOnTaskClearKey
import com.aura.music.constants.UpdateNotificationsEnabledKey
import com.aura.music.constants.UseNewMiniPlayerDesignKey
import com.aura.music.constants.VideoThumbnailMigrationDoneKey
import com.aura.music.db.MusicDatabase
import com.aura.music.db.entities.SearchHistory
import com.aura.music.extensions.toEnum
import com.aura.music.lyrics.LyricsProviderRegistry
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.DownloadUtil
import com.aura.music.playback.MusicService
import com.aura.music.playback.MusicService.MusicBinder
import com.aura.music.playback.PlayerConnection
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.component.AccountSettingsDialog
import com.aura.music.ui.component.AppNavigationBar
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.AuraRail
import com.aura.music.ui.aura.AuraSplash
import com.aura.music.ui.aura.AuraSplashSeen
import com.aura.music.ui.aura.AuraPageAnchors
import com.aura.music.ui.aura.AuraRailAnchor
import com.aura.music.ui.aura.AuraNowPlayingBanner
import com.aura.music.ui.aura.LocalAuraPageContent
import com.aura.music.ui.aura.LocalAuraPageFocus
import com.aura.music.ui.aura.LocalAuraRailAnchor
import com.aura.music.ui.aura.LocalAuraPageHoldsFocus
import com.aura.music.ui.aura.LocalAuraPlayerHoldsFocus
import com.aura.music.ui.aura.LocalAuraScale
import com.aura.music.ui.aura.LocalAuraTransportHoldsFocus
import com.aura.music.ui.component.LocalAuraSpotlight
import com.aura.music.ui.component.rememberAuraSpotlight
import com.aura.music.ui.component.BottomSheetMenu
import com.aura.music.ui.component.BottomSheetPage
import com.aura.music.ui.component.LocalBottomSheetPageState
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.rememberBottomSheetState
import com.aura.music.ui.component.shimmer.ShimmerTheme
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.ui.player.BottomSheetPlayer
import com.aura.music.ui.screens.Screens
import com.aura.music.ui.screens.navigationBuilder
import com.aura.music.ui.screens.settings.ChangelogScreen
import com.aura.music.ui.screens.settings.DarkMode
import com.aura.music.ui.screens.settings.NavigationTab
import com.aura.music.ui.theme.DefaultThemeColor
import com.aura.music.ui.theme.AuraTheme
import com.aura.music.ui.utils.appBarScrollBehavior
import com.aura.music.ui.utils.resetHeightOffset
import com.aura.music.ui.component.ApkUpdateAction
import com.aura.music.ui.component.ApkUpdateStatus
import com.aura.music.ui.component.rememberApkUpdateState
import com.aura.music.utils.ApkUpdateInstaller
import com.aura.music.utils.ReleaseAsset
import com.aura.music.utils.ReleaseInfo
import com.aura.music.utils.SearchRoutes
import com.aura.music.utils.SyncUtils
import com.aura.music.utils.ArtistNameAliases
import com.aura.music.utils.Updater
import com.aura.music.utils.dataStore
import com.aura.music.utils.safeDataStoreEdit
import com.aura.music.utils.get
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.utils.rememberPreference
import com.aura.music.utils.reportException
import com.aura.spotify.Spotify

import com.aura.music.utils.setAppLocale
import com.aura.music.viewmodels.HomeViewModel
import com.aura.music.widget.PlaylistWidgetReceiver
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * The shell's semantics, switched off for the duration of the splash reveal.
 *
 * One instance, on purpose. Written inline it is a new element every recomposition of the shell, and
 * an element that compares unequal makes the root node rebuild its semantics subtree - the same
 * re-apply storm the focus rings were causing, at the worst possible moment.
 */
private val AuraSplashHiddenSemantics = Modifier.clearAndSetSemantics { }

private data class AvailableUpdate(
    val release: ReleaseInfo,
    val asset: ReleaseAsset?,
    val isKmp: Boolean,
) {
    val dismissalKey = if (isKmp) DismissedKmpUpdateKey else DismissedStandaloneUpdateKey
}

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    companion object {
        private const val ACTION_SEARCH = "com.aura.music.action.SEARCH"
        private const val ACTION_LIBRARY = "com.aura.music.action.LIBRARY"
        const val ACTION_RECOGNITION = "com.aura.music.action.RECOGNITION"
        const val ACTION_OPEN_WIDGET_TARGET = "com.aura.music.action.OPEN_WIDGET_TARGET"
        const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"
        const val EXTRA_WIDGET_TARGET_TYPE = "widget_target_type"
        const val EXTRA_WIDGET_TARGET_ID = "widget_target_id"

        /** How long the shell waits for a page's enter transition before landing the selector. */
        private const val ReAnchorSettleMs = 420L

        /** A page still loading publishes its landing spots late, so the landing is retried. */
        private const val ReAnchorTries = 5
        private const val ReAnchorIntervalMs = 400L
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var listenTogetherManager: com.aura.music.listentogether.ListenTogetherManager

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var latestVersionName by mutableStateOf(BuildConfig.BASE_VERSION_NAME)

    // Keep PlayerConnection as regular property - NOT mutableStateOf to prevent UI recomposition
    // when it becomes null during onStop. Only update the snapshot for Compose when needed.
    private var playerConnection: PlayerConnection? = null

    // This is the snapshot we pass to Compose - changes here trigger recomposition
    private var playerConnectionSnapshot by mutableStateOf<PlayerConnection?>(null)

    private var isServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                if (service is MusicBinder) {
                    playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playerConnectionSnapshot = playerConnection
                    listenTogetherManager.setPlayerConnection(playerConnection)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                // Disconnect Listen Together manager
                listenTogetherManager.setPlayerConnection(null)
                playerConnection?.dispose()
                // DO NOT null out playerConnection here - keep it for when service reconnects
                // DO NOT update playerConnectionSnapshot - this is the key to preventing recomposition
            }
        }

    private fun safeUnbindService(source: String) {
        if (!isServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
            Timber.tag("MainActivity").w(e, "Service was not bound when attempting to unbind in $source")
        } finally {
            isServiceBound = false
            listenTogetherManager.setPlayerConnection(null)
            playerConnection?.dispose()
            // DO NOT null out playerConnection here - keep it for reconnection
            // DO NOT update playerConnectionSnapshot - this prevents UI recomposition
        }
    }

    override fun onStart() {
        super.onStart()
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1000)
            }
        }

        // Start the playback service explicitly once so it can outlive binding.
        // Re-issuing startForegroundService() while an existing service instance is already
        // running can trigger "did not then call startForeground" on some Android 9 devices
        // when the framework expects a fresh foreground promotion for that start request.
        if (!MusicService.isRunning) {
            val serviceIntent = Intent(this, MusicService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.startForegroundService(this, serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.w(e, "Cannot start foreground service from background")
            } catch (e: IllegalStateException) {
                Timber.w(e, "Failed to start foreground service")
            }
        }

        // Bind to service - if already bound, this is a no-op but ensures we stay connected
        if (!isServiceBound) {
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE,
            )
            isServiceBound = true
        }
    }

    override fun onStop() {
        // Keep the service binding, PlayerConnection and Listen Together wiring alive while
        // the Activity is backgrounded. The MusicService is a foreground service and keeps
        // running, so the host must keep reporting playback state to the LT server; detaching
        // the player listener here used to break LT for any host that wasn't staring at the
        // app the whole session. Full teardown happens in onDestroy() via safeUnbindService().
        super.onStop()
    }

    override fun onDestroy() {
        if (isFinishing) {
            listenTogetherManager.disconnect()
        }
        super.onDestroy()
        val stopServiceOnClear =
            dataStore.get(StopMusicOnTaskClearKey, false) &&
                playerConnection?.isPlaying?.value == true &&
                isFinishing

        // Full cleanup - only on actual destroy
        playerConnection?.dispose()
        playerConnection = null
        playerConnectionSnapshot = null

        // Unbind before stopService: a started+bound service does not stop until all clients unbind.
        safeUnbindService("onDestroy()")

        if (stopServiceOnClear) {
            stopService(Intent(this, MusicService::class.java))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (::navController.isInitialized) {
            handleWidgetTargetIntent(intent, navController)
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize Listen Together manager
        listenTogetherManager.initialize()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val locale =
                dataStore[AppLanguageKey]
                    ?.takeUnless { it == SYSTEM_DEFAULT }
                    ?.let { Locale.forLanguageTag(it) }
                    ?: Locale.getDefault()
            setAppLocale(this, locale)
        }

        lifecycleScope.launch {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    if (it) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE,
                        )
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
        }

        // Defer migration and version tracking to avoid blocking first frame
        lifecycleScope.launch(Dispatchers.IO) {
            val preferences = dataStore.data.first()
            val currentVersion = BuildConfig.BASE_VERSION_NAME

            // SimpMusic Removal Migration
            if (preferences[SimpMusicMigrationDoneKey] != true) {
                safeDataStoreEdit { settings ->
                    val currentOrder = settings[LyricsProviderOrderKey] ?: ""
                    if (currentOrder.contains("SimpMusic")) {
                        val orderList =
                            currentOrder
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() && it != "SimpMusic" }
                                .toMutableList()
                        if (orderList.isEmpty()) {
                            settings[LyricsProviderOrderKey] = ""
                        } else {
                            settings[LyricsProviderOrderKey] = orderList.joinToString(",")
                        }
                    }
                    if (settings[PreferredLyricsProviderKey] == "SIMPMUSIC") {
                        settings[PreferredLyricsProviderKey] = PreferredLyricsProvider.LRCLIB.name
                    }
                    settings[SimpMusicMigrationDoneKey] = true
                    settings[LastSeenVersionKey] = currentVersion
                }
            }

            if (preferences[VideoThumbnailMigrationDoneKey] != true) {
                database.repairMissingVideoThumbnails()
                safeDataStoreEdit { settings ->
                    settings[VideoThumbnailMigrationDoneKey] = true
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            safeDataStoreEdit { settings ->
                settings[LastSeenVersionKey] = BuildConfig.BASE_VERSION_NAME
            }
        }

        setContent {
            AuraApp(
                latestVersionName = latestVersionName,
                onLatestVersionNameChange = { latestVersionName = it },
                playerConnection = playerConnectionSnapshot,
                database = database,
                downloadUtil = downloadUtil,
                syncUtils = syncUtils,
            )
        }
        suppressPlatformFocusHighlight()
    }

    /**
     * Android paints its own focus highlight — a grey plate over the focused view's bounds — behind
     * every view it considers focusable. Aura draws its own selector, so on a transparent transport
     * button the plate is the only cue left, and it is square where every Aura element is round.
     * Switched off on the compose root only; the legacy XML screens keep the platform highlight.
     */
    private fun suppressPlatformFocusHighlight() {
        val root = findViewById<View>(android.R.id.content) as? ViewGroup ?: return
        val pending = arrayListOf<View>(root)
        while (pending.isNotEmpty()) {
            val view = pending.removeAt(pending.lastIndex)
            view.defaultFocusHighlightEnabled = false
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
            }
        }
    }

    /**
     * The updates channel. Its content intent opens the app rather than the asset's download URL:
     * this TV has no browser, and only the in-app prompt can carry a download through to install.
     */
    private fun notifyUpdateAvailable(update: AvailableUpdate) {
        val pending =
            PendingIntent.getActivity(
                this,
                1001,
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notificationText =
            if (update.isKmp) {
                getString(R.string.kmp_upgrade_warning)
            } else {
                update.release.versionName
            }
        val notification =
            NotificationCompat
                .Builder(this, "updates")
                .setSmallIcon(R.drawable.update)
                .setContentTitle(
                    if (update.isKmp) {
                        getString(R.string.kmp_upgrade_title, update.release.versionName)
                    } else {
                        getString(R.string.update_available_title)
                    },
                ).setContentText(notificationText)
                .apply {
                    if (update.isKmp) {
                        setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
                    }
                }.setContentIntent(pending)
                .setAutoCancel(true)
                .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(1001, notification)
        }
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AuraApp(
        latestVersionName: String,
        onLatestVersionNameChange: (String) -> Unit,
        playerConnection: PlayerConnection?,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        syncUtils: SyncUtils,
    ) {
        val checkForUpdates by rememberPreference(CheckForUpdatesKey, defaultValue = true)
        var availableUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
        val updateState = rememberApkUpdateState()

        if (BuildConfig.UPDATER_AVAILABLE) {
            LaunchedEffect(checkForUpdates) {
                // A TV is often left on for days, and a process that only checked at launch would go
                // on offering the release it started against. Re-arm on the interval the release
                // cache is valid for, so every pass after the first actually refetches.
                while (true) {
                    if (checkForUpdates) {
                        val preferences = dataStore.data.first()
                        val (releaseInfo, hasUpdate) = Updater.checkForUpdate().getOrNull() ?: (null to false)
                        releaseInfo?.let { onLatestVersionNameChange(it.versionName) }

                        val standaloneUpdate =
                            releaseInfo
                                ?.takeIf { hasUpdate }
                                ?.let { release ->
                                    AvailableUpdate(
                                        release = release,
                                        // A release whose assets name no APK for this build still
                                        // counts as newer: the prompt says so rather than pretending
                                        // that nothing was published.
                                        asset = Updater.getAssetForCurrentVariant(release),
                                        isKmp = false,
                                    )
                                }
                        // Upstream offers its Aura-KMP rewrite here in preference to its own
                        // release. Aura has no KMP build and must not point users at another app,
                        // so only Aura's own releases are offered. The isKmp plumbing below is left
                        // in place to keep future upstream merges small.
                        val offered =
                            standaloneUpdate?.takeUnless {
                                it.release.tagName == preferences[it.dismissalKey]
                            }
                        availableUpdate = offered
                        updateState.prepare(offered?.asset)

                        if (offered == null) {
                            // 27 MB of APK has no business sitting in the cache once the app is current.
                            ApkUpdateInstaller.deleteDownloaded(this@MainActivity)
                        } else if (preferences[UpdateNotificationsEnabledKey] ?: true) {
                            notifyUpdateAvailable(offered)
                        }
                    } else {
                        onLatestVersionNameChange(BuildConfig.BASE_VERSION_NAME)
                        availableUpdate = null
                        updateState.prepare(null)
                    }
                    delay(Updater.CHECK_INTERVAL_MILLIS)
                }
            }
        }

        // The TV shell is designed against the fixed Material You purple baseline. Sampling the
        // playing artwork as the colour seed recoloured every ring, chip and switch per track, so
        // the seed is no longer read at all.
        val enableHighRefreshRate by rememberPreference(EnableHighRefreshRateKey, defaultValue = true)

        LaunchedEffect(enableHighRefreshRate) {
            val window = this@MainActivity.window
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val layoutParams = window.attributes
                if (enableHighRefreshRate) {
                    layoutParams.preferredDisplayModeId = 0
                } else {
                    val modes = window.windowManager.defaultDisplay.supportedModes
                    val mode60 =
                        modes.firstOrNull { kotlin.math.abs(it.refreshRate - 60f) < 1f }
                            ?: modes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }

                    if (mode60 != null) {
                        layoutParams.preferredDisplayModeId = mode60.modeId
                    }
                }
                window.attributes = layoutParams
            } else {
                val params = window.attributes
                if (enableHighRefreshRate) {
                    params.preferredRefreshRate = 0f
                } else {
                    params.preferredRefreshRate = 60f
                }
                window.attributes = params
            }
        }

        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isAuraDarkTheme()
        val useDarkTheme =
            remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }

        LaunchedEffect(useDarkTheme) {
            setSystemBarAppearance(useDarkTheme)
        }

        val enableLandscapeScaling by rememberPreference(EnableLandscapeScalingKey, defaultValue = false)
        val userDensityScale by rememberPreference(DensityScaleKey, defaultValue = 1f)
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val pureBlack =
            remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            }

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        val showChangelog = rememberSaveable { mutableStateOf(false) }

        AuraTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = selectedThemeColor,
        ) {
            val currentDensity = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val containerSize = windowInfo.containerDpSize
            val smallestDimensionDp = minOf(containerSize.width, containerSize.height)

            val landscapeDensityScale =
                remember(smallestDimensionDp, enableLandscapeScaling) {
                    if (enableLandscapeScaling) {
                        when {
                            smallestDimensionDp >= 840.dp -> 1.15f
                            smallestDimensionDp >= 720.dp -> 1.1f
                            smallestDimensionDp >= 600.dp -> 1.05f
                            else -> 1.0f
                        }
                    } else {
                        1.0f
                    }
                }
            val scaledDensity: Density = remember(currentDensity, landscapeDensityScale, userDensityScale) {
                Density(
                    density = currentDensity.density * landscapeDensityScale * userDensityScale,
                    fontScale = currentDensity.fontScale,
                )
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface),
            ) {
                val density = LocalDensity.current
                val configuration = LocalWindowInfo.current
                val cutoutInsets = WindowInsets.displayCutout
                val windowsInsets = WindowInsets.systemBars
                val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    val lastSeenVersion = dataStore.data.first()[LastSeenVersionKey] ?: ""
                    val currentVersion = BuildConfig.BASE_VERSION_NAME
                    if (lastSeenVersion != currentVersion) {
                        showChangelog.value = true
                    }
                }

                val homeViewModel: HomeViewModel = hiltViewModel()
                val accountImageUrl by homeViewModel.accountImageUrl.collectAsStateWithLifecycle()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val (previousTab, setPreviousTab) = rememberSaveable { mutableStateOf("home") }

                val navigationItems = Screens.MainScreens
                val routeIndexMap = remember(navigationItems) {
                    navigationItems.mapIndexed { i, s -> s.route to i }.toMap()
                }
                val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                val (defaultOpenTabInt) = rememberPreference(DefaultOpenTabKey, defaultValue = NavigationTab.HOME.name)
                val defaultOpenTab = remember(defaultOpenTabInt) {
                    try {
                        NavigationTab.valueOf(defaultOpenTabInt)
                    } catch (_: IllegalArgumentException) {
                        NavigationTab.HOME
                    }
                }
                val tabOpenedFromShortcut =
                    remember {
                        when (intent?.action) {
                            ACTION_SEARCH -> NavigationTab.SEARCH
                            ACTION_LIBRARY -> NavigationTab.LIBRARY
                            else -> null
                        }
                    }

                val topLevelScreens =
                    remember {
                        listOf(
                            Screens.Home.route,
                            Screens.Library.route,
                            "settings",
                        )
                    }

                val (query, onQueryChange) =
                    rememberSaveable(stateSaver = TextFieldValue.Saver) {
                        mutableStateOf(TextFieldValue())
                    }

                val onSearch: (String) -> Unit =
                    remember {
                        { searchQuery ->
                            if (searchQuery.isNotEmpty()) {
                                navController.navigate(SearchRoutes.resultRoute(searchQuery))

                                if (dataStore[PauseSearchHistoryKey] != true) {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        runCatching {
                                            database.insert(SearchHistory(query = searchQuery))
                                        }.onFailure { throwable ->
                                            Timber
                                                .tag("MainActivity")
                                                .w(throwable, "Failed to save search history for query: %s", searchQuery)
                                        }
                                    }
                                }
                            }
                        }
                    }

                val currentRoute by remember {
                    derivedStateOf { navBackStackEntry?.destination?.route }
                }

                val inSearchScreen by remember {
                    derivedStateOf { currentRoute?.startsWith("search/") == true }
                }
                val navigationItemRoutes =
                    remember(navigationItems) {
                        navigationItems.map { it.route }.toSet()
                    }

                val shouldShowNavigationBar =
                    remember(currentRoute, navigationItemRoutes) {
                        currentRoute == null ||
                            navigationItemRoutes.contains(currentRoute) ||
                            currentRoute!!.startsWith("search/")
                    }

                val isLandscape = configuration.containerDpSize.width > configuration.containerDpSize.height
                val isTablet = configuration.containerDpSize.width >= 600.dp

                // `showRail` doubles as the Aura shell flag (it is provided as LocalAuraTvShell), so
                // dropping it on the search results page handed that page the phone bottom nav and
                // took the rail's player rules with it. The shell is the layout, not the route.
                val showRail = isLandscape || isTablet

                // The reference is authored against a 1080px-tall viewport; this TV reports 540dp of
                // that height, so one mock pixel is half a dp.
                val auraScale = LocalConfiguration.current.screenHeightDp / 1080f

                // The rail shell's transport is the banner at the head of the page, but the sheet it
                // expands still needs a collapsed anchor to rest on, so this reserves that strip.
                // One value drives both the reserved insets and the anchor so they can't drift.
                val useAuraFooter = showRail && useNewMiniPlayerDesign
                val miniPlayerHeight =
                    if (useAuraFooter) {
                        ((AuraPx.MiniPlayerHeight + AuraPx.MiniPlayerBottomMargin) * auraScale).dp
                    } else {
                        MiniPlayerHeight
                    }

                val navPadding =
                    if (shouldShowNavigationBar && !showRail) {
                        if (slimNav) SlimNavBarHeight else NavigationBarHeight
                    } else {
                        0.dp
                    }

                val navigationBarHeight by animateDpAsState(
                    targetValue = if (shouldShowNavigationBar && !showRail) NavigationBarHeight else 0.dp,
                    animationSpec = NavigationBarAnimationSpec,
                    label = "navBarHeight",
                )

                val playerBottomSheetState =
                    rememberBottomSheetState(
                        dismissedBound = 0.dp,
                        collapsedBound =
                            bottomInset +
                                (if (!showRail && shouldShowNavigationBar) navPadding else 0.dp) +
                                (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                                miniPlayerHeight,
                        expandedBound = maxHeight,
                    )

                val playerReadyState =
                    playerConnection?.service?.isPlayerReady?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(false) }
                val playerReady by playerReadyState
                val activePlayerConnection = if (playerReady) playerConnection else null
                // Whether the session owes the listener a transport. The banner at the head of every
                // page reads this; the rule that sets it lives with the sheet's own visibility pass.
                val auraTransportOwed = remember { mutableStateOf(false) }
                val expandingForSelection = remember { mutableStateOf(false) }
                val auraPlaybackStarted = remember { mutableStateOf(false) }

                val playerAwareWindowInsets =
                    remember(
                        bottomInset,
                        shouldShowNavigationBar,
                        playerBottomSheetState.isDismissed,
                        showRail,
                        auraScale,
                    ) {
                        var bottom = bottomInset
                        if (shouldShowNavigationBar && !showRail) {
                            bottom += NavigationBarHeight
                        }
                        if (!playerBottomSheetState.isDismissed) bottom += miniPlayerHeight
                        // The Aura header is taller than the 64dp Material bar it replaced once the mock
                        // is scaled to this viewport, and the legacy pages reserve this inset instead of
                        // measuring the bar, so the two have to agree or the header paints over row one.
                        val headerTop = if (showRail) (AuraPx.PageHeader * auraScale).dp else AppBarHeight
                        windowsInsets
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                            .add(WindowInsets(top = headerTop, bottom = bottom))
                    }
                appBarScrollBehavior(
                    canScroll = {
                        !inSearchScreen &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

                val topAppBarScrollBehavior =
                    appBarScrollBehavior(
                        canScroll = {
                            !inSearchScreen &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        },
                    )

                // Navigation tracking
                LaunchedEffect(navBackStackEntry) {
                    if (inSearchScreen) {
                        val searchQuery =
                            SearchRoutes.decodeQuery(
                                navBackStackEntry?.arguments?.getString("query").orEmpty(),
                            )
                        onQueryChange(
                            TextFieldValue(
                                searchQuery,
                                TextRange(searchQuery.length),
                            ),
                        )
                    } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }

                    // Reset scroll behavior for main navigation items
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }
                    }

                    topAppBarScrollBehavior.state.resetHeightOffset()

                    // Collapse player when navigating to equalizer
                    if (navBackStackEntry?.destination?.route == "equalizer" &&
                        playerBottomSheetState.isExpanded
                    ) {
                        playerBottomSheetState.collapseSoft()
                    }

                    // Track previous tab for animations
                    navController.currentBackStackEntry?.destination?.route?.let {
                        setPreviousTab(it)
                    }
                }

                LaunchedEffect(activePlayerConnection, showRail) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                    if (player?.currentMediaItem == null) {
                        if (!playerBottomSheetState.isDismissed) {
                            playerBottomSheetState.dismiss()
                        }
                        return@LaunchedEffect
                    }

                    // The Aura shell decides for itself when the bar is owed, so a player instance
                    // being re-attached must not push it back on under a paused track.
                    if (!showRail && playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.collapseSoft()
                    }
                }

                /*
                 * The reference's floating bar is a transport control, not a shelf: it earns its
                 * place while there is something to transport, and the full-screen player owns the
                 * screen while it is open.
                 *
                 * "Something to transport" is deliberately narrower than "a track is loaded". Paused
                 * has to keep the bar — the pause button is the only way back to play from a page,
                 * so hiding it on pause strands the listener with no transport at all. Ended and
                 * idle have nothing left to resume, so they clear the page.
                 *
                 * A session the service restored on its own is not one the listener started, so the
                 * bar waits for the first real play before it claims that a paused track is theirs.
                 *
                 * The playing flag is a StateFlow, so it has to be collected as one; reading its
                 * .value inside snapshotFlow would only re-sample it when a sheet anchor changed.
                 * Anchors are the snapshot half, and only a sheet at rest is managed — dismissing
                 * one that is still opening would cancel the full-screen player on song select.
                 */
                LaunchedEffect(showRail, activePlayerConnection) {
                    if (!showRail) return@LaunchedEffect
                    val connection = activePlayerConnection ?: return@LaunchedEffect
                    combine(
                        connection.isPlaying,
                        connection.playbackState,
                        connection.mediaMetadata,
                        snapshotFlow {
                            playerBottomSheetState.isCollapsed to playerBottomSheetState.isDismissed
                        },
                    ) { playing, state, metadata, anchors ->
                        val resumable =
                            metadata != null &&
                                state != Player.STATE_IDLE &&
                                state != Player.STATE_ENDED
                        Triple(playing to resumable, anchors.first, anchors.second)
                    }.collect { (signal, collapsed, dismissed) ->
                        val (playing, resumable) = signal
                        if (playing) auraPlaybackStarted.value = true
                        val owed = playing || (resumable && auraPlaybackStarted.value)
                        auraTransportOwed.value = owed
                        when {
                            dismissed ->
                                if (owed && !expandingForSelection.value) {
                                    playerBottomSheetState.collapseSoft()
                                }

                            collapsed -> if (!owed) playerBottomSheetState.dismiss()
                        }
                    }
                }

                /*
                 * The reference opens the full-screen now-playing view the moment you choose a song.
                 * Only a queue the listener asked for counts, so the service's session restore and
                 * automix replays leave the page they are on alone.
                 */
                DisposableEffect(activePlayerConnection, showRail, playerBottomSheetState) {
                    val expandPlayer: () -> Unit = {
                        if (!playerBottomSheetState.isExpanded) {
                            // The sheet's anchors follow its animated value, so for the frames between
                            // the tap and the spring settling the sheet still reads as dismissed —
                            // long enough for the visibility rule above to pull it back to the mini
                            // bar and cancel the very expansion the selection asked for.
                            expandingForSelection.value = true
                            playerBottomSheetState.expandSoft()
                        }
                    }
                    activePlayerConnection?.onQueueSelected = if (showRail) expandPlayer else null
                    onDispose {
                        activePlayerConnection?.onQueueSelected = null
                    }
                }

                LaunchedEffect(showRail, playerBottomSheetState) {
                    if (!showRail) return@LaunchedEffect
                    snapshotFlow {
                        playerBottomSheetState.isExpanded to playerBottomSheetState.isCollapsed
                    }.collect { (expanded, collapsed) ->
                        if (expanded || collapsed || playerBottomSheetState.isDismissed) {
                            expandingForSelection.value = false
                        }
                    }
                }

                DisposableEffect(activePlayerConnection, showRail, playerBottomSheetState) {
                    val player = runCatching { activePlayerConnection?.player }.getOrNull()
                        ?: return@DisposableEffect onDispose { }
                    val listener =
                        object : Player.Listener {
                            override fun onMediaItemTransition(
                                mediaItem: MediaItem?,
                                reason: Int,
                            ) {
                                if (!showRail &&
                                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                    mediaItem != null &&
                                    playerBottomSheetState.isDismissed
                                ) {
                                    playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                    }
                }

                var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(navBackStackEntry) {
                    val currentRoute = navBackStackEntry?.destination?.route
                    shouldShowTopBar = currentRoute in topLevelScreens &&
                        currentRoute != "settings"
                }

                val coroutineScope = rememberCoroutineScope()
                var sharedSong: SongItem? by remember {
                    mutableStateOf(null)
                }
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    if (pendingIntent != null) {
                        handleWidgetTargetIntent(pendingIntent!!, navController)
                        handleRecognitionIntent(pendingIntent!!, navController)
                        handleDeepLinkIntent(pendingIntent!!, navController)
                        pendingIntent = null
                    } else {
                        handleWidgetTargetIntent(intent, navController)
                        handleRecognitionIntent(intent, navController)
                        handleDeepLinkIntent(intent, navController)
                    }
                }

                DisposableEffect(Unit) {
                    val listener =
                        Consumer<Intent> { intent ->
                            handleWidgetTargetIntent(intent, navController)
                            handleRecognitionIntent(intent, navController)
                            handleDeepLinkIntent(intent, navController)
                        }

                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                val currentTitleRes =
                    remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Search.route -> R.string.search
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }

                var showAccountDialog by remember { mutableStateOf(false) }

                // One spotlight holder for the whole shell: tiles write to it on focus, the hero
                // and the ambient wash read from it.
                val auraSpotlight = rememberAuraSpotlight()

                // The rail writes its animated width here and the mini player insets itself by it,
                // which keeps the bar inside `main` the way the reference lays it out.
                val auraRailWidth = remember { mutableStateOf(0.dp) }

                // The page a rail item lands on publishes the focusables worth landing on, best first.
                val auraPageFocus = remember { AuraPageAnchors() }

                // The rail publishes back the row the page in view sits under, so BACK has somewhere
                // to put the selector that is not the nav stack.
                val auraRailAnchor = remember { AuraRailAnchor() }

                // Whether the BACK press about to be answered was taken by the rail. Its release has
                // to be taken too, and by then the selector is already in the rail.
                val auraBackTaken = remember { mutableStateOf(false) }

                // Which of the regions outside the rail holds the selector: the page, the floating
                // mini player, or the full-screen player. The rail re-anchors itself when none of
                // these nor it has the selector, because anything else that could hold it draws no
                // focus ring and leaves the remote looking dead.
                val auraPageHoldsFocus = remember { mutableStateOf(false) }
                val auraTransportHoldsFocus = remember { mutableStateOf(false) }
                val auraPlayerHoldsFocus = remember { mutableStateOf(false) }

                // Popping a screen drops focus on the floor: the removed view had it, and nothing
                // underneath claims it back, so the D-pad is dead until the user finds the rail.
                // Opening one drops it just as badly — the tile the user was on disappears, and the
                // selector falls onto whatever the new page composes first, which is its header's back
                // arrow. Either way, hand it to the page's best landing spot once the enter transition
                // lands, unless a real landing spot, the floating bar or the full-screen player already
                // holds it. "The page has focus" is not the test: the back arrow is part of the page,
                // and leaving the selector there means the next ENTER undoes the navigation. A page fed
                // by the network has published nothing yet at that point, so keep asking until either it
                // has a landing spot or the retries run out.
                LaunchedEffect(navBackStackEntry, showRail) {
                    if (!showRail) return@LaunchedEffect
                    delay(ReAnchorSettleMs)
                    var tries = 0
                    while (tries < ReAnchorTries) {
                        val landedElsewhere =
                            auraTransportHoldsFocus.value || auraPlayerHoldsFocus.value
                        if (
                            landedElsewhere ||
                            auraPageFocus.anchorHoldsFocus ||
                            auraPageFocus.requestFirstFocus()
                        ) {
                            break
                        }
                        tries++
                        delay(ReAnchorIntervalMs)
                    }
                }

                val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
                val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
                val showHistoryButton =
                    remember(pauseListenHistory, eventCount) {
                        !(pauseListenHistory && eventCount == 0)
                    }

                val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
                val artistNameAliases by ArtistNameAliases.aliases.collectAsStateWithLifecycle()

                CompositionLocalProvider(
                    LocalAuraScale provides auraScale,
                    LocalDatabase provides database,
                    LocalNavController provides navController,
                    LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                    LocalPlayerConnection provides playerConnection,
                    LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                    LocalAuraTvShell provides showRail,
                    LocalAuraPageHoldsFocus provides auraPageHoldsFocus,
                    LocalAuraTransportHoldsFocus provides auraTransportHoldsFocus,
                    LocalAuraPlayerHoldsFocus provides auraPlayerHoldsFocus,
                    LocalAuraRailWidth provides auraRailWidth,
                    LocalAuraPageFocus provides auraPageFocus,
                    LocalAuraRailAnchor provides auraRailAnchor,
                    // Everything below is the page the rail points at until a region opts itself out,
                    // so a focused element can offer itself as the target of DPAD_RIGHT without every
                    // screen being taught the convention by hand.
                    LocalAuraPageContent provides true,
                    LocalAuraSpotlight provides auraSpotlight,
                    LocalDownloadUtil provides downloadUtil,
                    LocalShimmerTheme provides ShimmerTheme,
                    LocalSyncUtils provides syncUtils,
                    LocalListenTogetherManager provides listenTogetherManager,
                    LocalChangelogState provides showChangelog,
                    LocalArtistNameAliases provides artistNameAliases,
                ) {
                    if (showChangelog.value) {
                        ChangelogScreen(onDismiss = { showChangelog.value = false })
                    }

                    // The reference's home view is a flat #141218 page; only its now-playing modal
                    // puts blurred artwork behind the content, and AuraNowPlaying paints that itself.

                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        containerColor =
                            if (pureBlack) {
                                Color.Black
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                        topBar = {
                            // The reference's rail shell has no app bar: every view starts 48px from the
                            // top of the viewport and the History/account entry points live on the rail.
                            AnimatedVisibility(
                                visible = shouldShowTopBar && !showRail,
                                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                exit = fadeOut(animationSpec = tween(durationMillis = 200)),
                            ) {
                                Row {
                                    TopAppBar(
                                        title = {
                                            Text(
                                                text = currentTitleRes?.let { stringResource(it) } ?: "",
                                                style = MaterialTheme.typography.titleLarge,
                                            )
                                        },
                                        actions = {
                                            if (showHistoryButton) {
                                                IconButton(onClick = { navController.navigate("history") }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.history),
                                                        contentDescription = stringResource(R.string.history),
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { navController.navigate("stats") }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.stats),
                                                    contentDescription = stringResource(R.string.stats),
                                                )
                                            }
                                            IconButton(onClick = { showAccountDialog = true }) {
                                                BadgedBox(badge = {
                                                    if (latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                                                        Badge()
                                                    }
                                                }) {
                                                    if (accountImageUrl != null) {
                                                        AsyncImage(
                                                            model = accountImageUrl,
                                                            contentDescription = stringResource(R.string.account),
                                                            modifier =
                                                                Modifier
                                                                    .size(24.dp)
                                                                    .clip(CircleShape),
                                                        )
                                                    } else {
                                                        Icon(
                                                            painter = painterResource(R.drawable.account),
                                                            contentDescription = stringResource(R.string.account),
                                                            modifier = Modifier.size(24.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        scrollBehavior = topAppBarScrollBehavior,
                                        colors =
                                            TopAppBarDefaults.topAppBarColors(
                                                containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                scrolledContainerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                        modifier =
                                            Modifier.windowInsetsPadding(
                                                cutoutInsets.only(WindowInsetsSides.Start + WindowInsetsSides.End),
                                            ),
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            val currentBackStackEntry = navController.currentBackStackEntry // reads reactively outside remember

                            val onNavItemClick: (Screens, Boolean) -> Unit =
                                remember(
                                    navController,
                                    coroutineScope,
                                    topAppBarScrollBehavior,
                                    playerBottomSheetState,
                                    currentBackStackEntry,
                                ) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }
                                        if (isSelected) {
                                            val targetEntry =
                                                try {
                                                    val route = navController.currentBackStackEntry?.destination?.route
                                                    if (route == SearchRoutes.ROUTE || route == "search_input") {
                                                        // For search screens, use search_input entry
                                                        navController.getBackStackEntry("search_input")
                                                    } else {
                                                        // For other screens, use current entry
                                                        navController.currentBackStackEntry
                                                    }
                                                } catch (e: Exception) {
                                                    null
                                                }

                                            // Use appropriate key based on screen type
                                            if (screen == Screens.Search) {
                                                val current = targetEntry?.savedStateHandle?.get<Int>("scrollToTopCount") ?: 0
                                                targetEntry?.savedStateHandle?.set("scrollToTopCount", current + 1)
                                            } else {
                                                targetEntry?.savedStateHandle?.set("scrollToTop", true)
                                            }

                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                }

                            val onSearchLongClick: () -> Unit =
                                remember(navController) {
                                    {
                                        navController.navigate("recognition") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                            // Pre-calculate values for graphicsLayer to avoid reading state during composition
                            val navBarTotalHeight = bottomInset + NavigationBarHeight

                            if (!showRail) {
                                Box {
                                    if (activePlayerConnection != null) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }

                                    AppNavigationBar(
                                        navigationItems = navigationItems,
                                        currentRoute = currentRoute,
                                        onItemClick = onNavItemClick,
                                        pureBlack = pureBlack,
                                        slimNav = slimNav,
                                        onSearchLongClick = onSearchLongClick,
                                        onHomeLongHold = { showAccountDialog = true },
                                        modifier =
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInset + navPadding)
                                                // Use graphicsLayer instead of offset to avoid recomposition
                                                // graphicsLayer runs during draw phase, not composition phase
                                                .graphicsLayer {
                                                    val navBarHeightPx = navigationBarHeight.toPx()
                                                    val totalHeightPx = navBarTotalHeight.toPx()

                                                    translationY =
                                                        if (navBarHeightPx == 0f) {
                                                            totalHeightPx
                                                        } else {
                                                            // Read progress only during draw phase
                                                            val progress = playerBottomSheetState.progress.coerceIn(0f, 1f)
                                                            val slideOffset = totalHeightPx * progress
                                                            val hideOffset =
                                                                totalHeightPx * (1 - navBarHeightPx / NavigationBarHeight.toPx())
                                                            slideOffset + hideOffset
                                                        }
                                                },
                                    )

                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .height(bottomInsetDp)
                                                // Use graphicsLayer for background color changes
                                                .graphicsLayer {
                                                    val progress = playerBottomSheetState.progress
                                                    alpha =
                                                        if (progress > 0f ||
                                                            (useNewMiniPlayerDesign && !shouldShowNavigationBar)
                                                        ) {
                                                            0f
                                                        } else {
                                                            1f
                                                        }
                                                }.background(baseBg),
                                    )
                                }
                            } else {
                                if (activePlayerConnection != null) {
                                    // The floating bar and the full-screen player are not the page:
                                    // the rail hands them its DOWN and the player keeps its own
                                    // focus trap, so neither may offer itself as a RIGHT landing.
                                    CompositionLocalProvider(LocalAuraPageContent provides false) {
                                        BottomSheetPlayer(
                                            state = playerBottomSheetState,
                                            navController = navController,
                                            pureBlack = pureBlack,
                                        )
                                    }
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(bottomInsetDp)
                                            // Use graphicsLayer for background color changes
                                            .graphicsLayer {
                                                val progress = playerBottomSheetState.progress
                                                alpha =
                                                    if (progress > 0f || (useNewMiniPlayerDesign && !shouldShowNavigationBar)) 0f else 1f
                                            }.background(baseBg),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                                // While the reveal owns the screen, the shell has no semantics
                                // subtree at all. With any accessibility service bound - this TV
                                // runs TVRelay, Button Mapper and TV Home - Compose re-walks the
                                // whole tree for geometry on the main thread, and the vendor's own
                                // frame monitor timed those passes at 0.6s to 2.4s each. That walk
                                // is why the launch never went idle, so the panel waited out its
                                // whole cap showing nothing. Focus is not semantics, so the D-pad
                                // is unaffected while this is on.
                                .then(
                                    if (showRail && !AuraSplashSeen.value) {
                                        AuraSplashHiddenSemantics
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                // BACK walks the nav stack one screen at a time and only reaches for
                                // the rail once there is nothing left to pop: three screens deep costs
                                // three presses home and the fourth lights the pill. Answered on the
                                // shell rather than on the page because the release arrives with the
                                // selector already moved into the rail, and Activity.onKeyUp answers an
                                // unconsumed BACK release with onBackPressed() — one press would light
                                // the pill and quit the app in the same stroke.
                                .onPreviewKeyEvent { event ->
                                    when {
                                        event.key != Key.Back -> false

                                        event.type == KeyEventType.KeyDown -> {
                                            // Letting the press fall through here is not a no-op: the
                                            // dispatcher answers it, so a sheet, a selection mode or
                                            // the open player still takes it before the pop does.
                                            val atTheRoot =
                                                navController.previousBackStackEntry == null
                                            auraBackTaken.value = atTheRoot &&
                                                auraPageHoldsFocus.value &&
                                                auraRailAnchor.claim()
                                            auraBackTaken.value
                                        }

                                        event.type == KeyEventType.KeyUp -> {
                                            val taken = auraBackTaken.value
                                            auraBackTaken.value = false
                                            taken
                                        }

                                        else -> false
                                    }
                                },
                        ) {
                            val onRailItemClick: (Screens, Boolean) -> Unit =
                                remember(navController, coroutineScope, topAppBarScrollBehavior, playerBottomSheetState) {
                                    { screen: Screens, isSelected: Boolean ->
                                        if (playerBottomSheetState.isExpanded) {
                                            playerBottomSheetState.collapseSoft()
                                        }

                                        if (isSelected) {
                                            navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                            coroutineScope.launch {
                                                topAppBarScrollBehavior.state.resetHeightOffset()
                                            }
                                        } else {
                                            val startId = navController.graph.startDestinationId
                                            if (navController.graph.startDestinationRoute == screen.route) {
                                                // The start tab is also the popUpTo target, and that pair makes
                                                // the navigator drop the new entry then re-add whatever stack it
                                                // just saved under the same slot — the page being left. So the
                                                // press lands back where it started. Pop the stack directly, and
                                                // keep the other tabs from saving into that slot at all.
                                                navController.popBackStack(startId, inclusive = false)
                                            } else {
                                                navController.navigate(screen.route) {
                                                    popUpTo(startId)
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    }
                                }

                            val onRailSearchLongClick: () -> Unit =
                                remember(navController) {
                                    {
                                        navController.navigate("recognition") {
                                            launchSingleTop = true
                                        }
                                    }
                                }

                            if (showRail) {
                                AuraRail(
                                    navigationItems = navigationItems,
                                    currentRoute = currentRoute,
                                    onItemClick = onRailItemClick,
                                    pureBlack = pureBlack,
                                    onSearchLongClick = onRailSearchLongClick,
                                    onHomeLongHold = { showAccountDialog = true },
                                    onSecondaryClick = { route ->
                                        navController.navigate(route) { launchSingleTop = true }
                                    },
                                    accountImageUrl = accountImageUrl,
                                    onAccountClick = { showAccountDialog = true },
                                    showUpdateBadge = latestVersionName != BuildConfig.BASE_VERSION_NAME,
                                )
                            }
                            val auraTvShell = LocalAuraTvShell.current
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    // Rail and page are two containers over one row, and a directional
                                    // press searches the row they share: without a group boundary here,
                                    // DOWN from the bottom row of a page walked sideways into the rail.
                                    // As its own focus group, the page answers the press first and the
                                    // rail only gets keys the page has no use for.
                                    .focusGroup()
                                    .onFocusEvent { auraPageHoldsFocus.value = it.hasFocus },
                            ) {
                                // The transport heads the page instead of floating under it: a bar at
                                // the foot sits below the page's last row, so DOWN answered for the
                                // transport before the list still waiting to be scrolled saw the press.
                                if (auraTvShell &&
                                    activePlayerConnection != null &&
                                    auraTransportOwed.value
                                ) {
                                    AuraNowPlayingBanner(
                                        onExpand = {
                                            if (!playerBottomSheetState.isExpanded) {
                                                playerBottomSheetState.expandSoft()
                                            }
                                        },
                                    )
                                }
                                // The reference swaps a view with `fadeIn .3s ease-out` while the block
                                // rises 10px into place; capture that rise in px while still composing.
                                val viewRisePx =
                                    with(LocalDensity.current) {
                                        (10f * LocalAuraScale.current).dp.roundToPx()
                                    }
                                // NavHost with animations (Material 3 Expressive style)
                                NavHost(
                                    navController = navController,
                                    startDestination =
                                        when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                            NavigationTab.HOME -> Screens.Home
                                            NavigationTab.SEARCH -> Screens.Search
                                            NavigationTab.LIBRARY -> Screens.Library
                                        }.route,
                                    // A screen that emits its page header as a second root (History,
                                    // Stats, the settings pages) gets laid out here, and the default
                                    // alignment centres that wrap-height bar in the middle of the
                                    // viewport — over the list it belongs above.
                                    contentAlignment = Alignment.TopStart,
                                    enterTransition = {
                                        if (auraTvShell) {
                                            slideInVertically(tween(300, easing = LinearOutSlowInEasing)) { viewRisePx } +
                                                fadeIn(tween(300, easing = LinearOutSlowInEasing))
                                        } else {
                                            val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                                            val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                                            if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex) {
                                                slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                            } else {
                                                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                            }
                                        }
                                    },
                                    exitTransition = {
                                        if (auraTvShell) {
                                            fadeOut(tween(300, easing = LinearOutSlowInEasing))
                                        } else {
                                            val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                                            val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                                            if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex) {
                                                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                            } else {
                                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                            }
                                        }
                                    },
                                    popEnterTransition = {
                                        if (auraTvShell) {
                                            slideInVertically(tween(300, easing = LinearOutSlowInEasing)) { viewRisePx } +
                                                fadeIn(tween(300, easing = LinearOutSlowInEasing))
                                        } else {
                                            val currentRouteIndex = routeIndexMap[targetState.destination.route] ?: -1
                                            val previousRouteIndex = routeIndexMap[initialState.destination.route] ?: -1

                                            if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex) {
                                                slideInHorizontally { it / 8 } + fadeIn(tween(200))
                                            } else {
                                                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
                                            }
                                        }
                                    },
                                    popExitTransition = {
                                        if (auraTvShell) {
                                            fadeOut(tween(300, easing = LinearOutSlowInEasing))
                                        } else {
                                            val currentRouteIndex = routeIndexMap[initialState.destination.route] ?: -1
                                            val targetRouteIndex = routeIndexMap[targetState.destination.route] ?: -1

                                            if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex) {
                                                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
                                            } else {
                                                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
                                ) {
                                    navigationBuilder(
                                        navController = navController,
                                        scrollBehavior = topAppBarScrollBehavior,
                                        latestVersionName = latestVersionName,
                                        activity = this@MainActivity,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                            }
                        }
                    }

                    BottomSheetMenu(
                        state = LocalMenuState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    BottomSheetPage(
                        state = LocalBottomSheetPageState.current,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    if (showAccountDialog) {
                        AccountSettingsDialog(
                            onDismiss = {
                                showAccountDialog = false
                                homeViewModel.refresh()
                            },
                            latestVersionName = latestVersionName,
                        )
                    }

                    // One reveal per process, painted over the shell that is already composing
                    // underneath. It is last in this Box so nothing can sit on top of it.
                    if (showRail && !AuraSplashSeen.value) {
                        AuraSplash(onFinished = { AuraSplashSeen.value = true })
                    }

                    sharedSong?.let { song ->
                        playerConnection?.let {
                            Dialog(
                                onDismissRequest = { sharedSong = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                            ) {
                                Surface(
                                    modifier = Modifier.padding(24.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = AlertDialogDefaults.containerColor,
                                    tonalElevation = AlertDialogDefaults.TonalElevation,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        YouTubeSongMenu(
                                            song = song,
                                            onDismiss = { sharedSong = null },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!showChangelog.value) {
                        availableUpdate?.let { update ->
                            val dismissUpdate: () -> Unit = {
                                availableUpdate = null
                                lifecycleScope.launch {
                                    safeDataStoreEdit {
                                        it[update.dismissalKey] = update.release.tagName
                                    }
                                }
                            }
                            AlertDialog(
                                onDismissRequest = dismissUpdate,
                                title = { Text(stringResource(R.string.update_available_title)) },
                                text = {
                                    Column(
                                        modifier =
                                            Modifier
                                                .heightIn(max = 480.dp)
                                                .verticalScroll(rememberScrollState()),
                                    ) {
                                        Text(
                                            text =
                                                stringResource(
                                                    if (update.isKmp) {
                                                        R.string.kmp_upgrade_title
                                                    } else {
                                                        R.string.update_available_message
                                                    },
                                                    update.release.versionName,
                                                ),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        if (update.isKmp) {
                                            Text(
                                                text = stringResource(R.string.kmp_upgrade_warning),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 12.dp),
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.changelog),
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                        )
                                        Text(
                                            text = update.release.description.ifBlank { stringResource(R.string.changelog_empty) },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        ApkUpdateStatus(updateState)
                                    }
                                },
                                confirmButton = {
                                    ApkUpdateAction(updateState)
                                },
                                dismissButton = {
                                    TextButton(onClick = dismissUpdate) {
                                        Text(stringResource(R.string.update_not_now))
                                    }
                                },
                            )
                        }
                    }
                }
            }
            }
        }
    }

    /**
     * Handles the ACTION_RECOGNITION intent sent from the Music Recognizer Widget.
     * Always navigates to the recognition screen to show the result.
     */
    private fun handleRecognitionIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_RECOGNITION) return
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)
        intent.action = null
        intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
        navController.navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
            launchSingleTop = true
        }
    }

    private sealed class WidgetTargetRoute(val route: String) {
        data class LocalPlaylist(val id: String) : WidgetTargetRoute("local_playlist/$id")
        data class OnlinePlaylist(val id: String) : WidgetTargetRoute("online_playlist/$id")
        data object LikedSongs : WidgetTargetRoute("auto_playlist/liked")
        data object DownloadedSongs : WidgetTargetRoute("auto_playlist/downloaded")
        data class TopSongs(val limit: String) : WidgetTargetRoute("top_playlist/$limit")
    }

    private fun handleWidgetTargetIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        if (intent.action != ACTION_OPEN_WIDGET_TARGET) return

        val targetType = intent.getStringExtra(EXTRA_WIDGET_TARGET_TYPE)
        val targetId = intent.getStringExtra(EXTRA_WIDGET_TARGET_ID)
        intent.action = null
        intent.removeExtra(EXTRA_WIDGET_TARGET_TYPE)
        intent.removeExtra(EXTRA_WIDGET_TARGET_ID)

        val normalizedTargetId = targetId?.takeIf { it.isNotBlank() }

        val targetRoute = when (targetType) {
            PlaylistWidgetReceiver.TARGET_TYPE_LOCAL ->
                normalizedTargetId?.let { WidgetTargetRoute.LocalPlaylist(it) }

            PlaylistWidgetReceiver.TARGET_TYPE_ONLINE ->
                normalizedTargetId?.let { WidgetTargetRoute.OnlinePlaylist(it) }

            PlaylistWidgetReceiver.TARGET_TYPE_LIKED ->
                WidgetTargetRoute.LikedSongs

            PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED ->
                WidgetTargetRoute.DownloadedSongs

            PlaylistWidgetReceiver.TARGET_TYPE_TOP ->
                WidgetTargetRoute.TopSongs(normalizedTargetId ?: "50")

            else -> null
        } ?: return

        navController.navigate(targetRoute.route)
    }

    private fun handleDeepLinkIntent(
        intent: Intent,
        navController: NavHostController,
    ) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        val coroutineScope = lifecycle.coroutineScope

        val listenCode =
            uri.getQueryParameter("code")
                ?: uri.getQueryParameter("room")
                ?: uri.pathSegments.getOrNull(1)
        val isListenLink = uri.pathSegments.firstOrNull() == "listen" || uri.host?.equals("listen", ignoreCase = true) == true
        if (!listenCode.isNullOrBlank() && isListenLink) {
            val username = dataStore.get(ListenTogetherUsernameKey, "").ifBlank { "Guest" }
            listenTogetherManager.joinRoom(listenCode, username)
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> {
                val playlistId = uri.getQueryParameter("list")
                if (playlistId.isNullOrBlank() || playlistId.equals("null", ignoreCase = true)) {
                    Toast.makeText(this, R.string.playlist_unavailable, Toast.LENGTH_SHORT).show()
                    return
                }

                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .albumSongs(playlistId)
                            .onSuccess { songs ->
                                songs.firstOrNull()?.album?.id?.let { browseId ->
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("album/$browseId")
                                    }
                                }
                            }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> {
                uri.lastPathSegment?.let { browseId ->
                    navController.navigate("album/$browseId")
                }
            }

            "channel", "c" -> {
                uri.lastPathSegment?.let { artistId ->
                    navController.navigate("artist/$artistId")
                }
            }

            "search" -> {
                uri.getQueryParameter("q")?.let {
                    navController.navigate(SearchRoutes.resultRoute(it))
                }
            }

            else -> {
                val videoId =
                    when {
                        path == "watch" -> uri.getQueryParameter("v")
                        uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                        else -> null
                    }

                val playlistId = uri.getQueryParameter("list")

                if (videoId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(listOf(videoId), playlistId)
                            .onSuccess { queue ->
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = queue.firstOrNull()?.id, playlistId = playlistId),
                                            queue.firstOrNull()?.toMediaMetadata(),
                                        ),
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                } else if (playlistId != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        YouTube
                            .queue(null, playlistId)
                            .onSuccess { queue ->
                                val firstItem = queue.firstOrNull()
                                withContext(Dispatchers.Main) {
                                    playerConnection?.playQueue(
                                        YouTubeQueue(
                                            WatchEndpoint(videoId = firstItem?.id, playlistId = playlistId),
                                            firstItem?.toMediaMetadata(),
                                        ),
                                    )
                                }
                            }.onFailure {
                                reportException(it)
                            }
                    }
                }
            }
        }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor = (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalNavController = staticCompositionLocalOf<NavController> { error("No NavController provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<com.aura.music.listentogether.ListenTogetherManager?> { null }
val LocalChangelogState = staticCompositionLocalOf<MutableState<Boolean>> { error("No LocalChangelogState provided") }
val LocalArtistNameAliases = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
val LocalIsPlayerExpanded = compositionLocalOf { false }

/** True in the rail shell, where popups take the mock's centered-modal shape instead of a sheet. */
val LocalAuraTvShell = compositionLocalOf { false }

/** The rail's live width, so `main`-scoped overlays can start at its edge instead of the screen's. */
val LocalAuraRailWidth = compositionLocalOf { mutableStateOf(0.dp) }
