/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens

import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraAlbum
import com.aura.music.ui.aura.AuraArtist
import com.aura.music.ui.aura.AuraSpotifyAlbum
import com.aura.music.ui.aura.AuraHome
import com.aura.music.ui.aura.AuraLocalPlaylist
import com.aura.music.ui.aura.AuraOnlinePlaylist
import com.aura.music.ui.aura.AuraPodcast
import com.aura.music.ui.aura.AuraSpotifyPlaylist
import com.aura.music.ui.theme.isAuraDarkTheme
import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import com.aura.music.constants.DarkModeKey
import com.aura.music.constants.PureBlackKey
import com.aura.music.ui.screens.artist.ArtistAlbumsScreen
import com.aura.music.ui.screens.artist.ArtistItemsScreen
import com.aura.music.ui.screens.artist.ArtistScreen
import com.aura.music.ui.screens.artist.ArtistSongsScreen
import com.aura.music.ui.screens.equalizer.EqScreen
import com.aura.music.ui.screens.equalizer.wizard.WizardScreen
import com.aura.music.ui.screens.library.LibraryScreen
import com.aura.music.ui.screens.playlist.AutoPlaylistScreen
import com.aura.music.ui.screens.playlist.CachePlaylistScreen
import com.aura.music.ui.screens.playlist.LocalPlaylistScreen
import com.aura.music.ui.screens.playlist.OnlinePlaylistScreen
import com.aura.music.ui.screens.playlist.SpotifyLikedSongsScreen
import com.aura.music.ui.screens.album.SpotifyAlbumScreen
import com.aura.music.ui.screens.library.SpotifyFolderScreen
import com.aura.music.ui.screens.playlist.SpotifyPlaylistScreen
import com.aura.music.ui.screens.playlist.TopPlaylistScreen
import com.aura.music.ui.screens.podcast.OnlinePodcastScreen
import com.aura.music.ui.screens.recognition.RecognitionHistoryScreen
import com.aura.music.ui.screens.recognition.RecognitionScreen
import com.aura.music.ui.screens.search.OnlineSearchResult
import com.aura.music.ui.screens.search.SearchScreen
import com.aura.music.ui.screens.settings.AboutScreen
import com.aura.music.ui.screens.settings.AiSettings
import com.aura.music.ui.screens.settings.AndroidAutoSettings
import com.aura.music.ui.screens.settings.AppearanceSettings
import com.aura.music.ui.screens.settings.BackupAndRestore
import com.aura.music.ui.screens.settings.ContentSettings
import com.aura.music.ui.screens.settings.DarkMode
import com.aura.music.ui.screens.settings.PlayerSettings
import com.aura.music.ui.screens.settings.SponsorBlockSettings
import com.aura.music.ui.screens.settings.PrivacySettings
import com.aura.music.ui.screens.settings.RomanizationSettings
import com.aura.music.ui.screens.settings.SettingsScreen
import com.aura.music.ui.screens.settings.StorageSettings
import com.aura.music.ui.screens.settings.UpdaterScreen
import com.aura.music.ui.screens.settings.integrations.SpotifyPreloadScreen
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
) {
    composable(Screens.Home.route) {
        if (LocalAuraTvShell.current) {
            AuraHome(navController = navController)
        } else {
            HomeScreen(snackbarHostState = snackbarHostState)
        }
    }

    composable(Screens.Search.route) { backStackEntry ->
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemInDarkTheme = isAuraDarkTheme()
        val useDarkTheme =
            remember(darkTheme, isSystemInDarkTheme) {
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            }
        val pureBlack =
            remember(pureBlackEnabled, useDarkTheme) {
                pureBlackEnabled && useDarkTheme
            }
        SearchScreen(
            pureBlack = pureBlack,
            savedStateHandle = backStackEntry.savedStateHandle
        )
    }

    composable(Screens.Library.route) {
        LibraryScreen()
    }

    composable("history") {
        HistoryScreen(navController)
    }

    composable("stats") {
        StatsScreen(navController)
    }

    composable("mood_and_genres") {
        MoodAndGenresScreen(navController)
    }

    composable("account") {
        AccountScreen(navController)
    }

    composable("new_release") {
        NewReleaseScreen(navController)
    }

    composable(
        route = "browse/{browseId}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                },
            ),
    ) {
        BrowseScreen(
            navController,
            it.arguments?.getString("browseId"),
        )
    }

    composable(
        route = "search/{query}",
        arguments =
            listOf(
                navArgument("query") {
                    type = NavType.StringType
                },
            ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) { backStackEntry ->
        OnlineSearchResult(
            savedStateHandle = backStackEntry.savedStateHandle
        )

    }

    composable(
        route = "album/{albumId}",
        arguments =
            listOf(
                navArgument("albumId") {
                    type = NavType.StringType
                },
            ),
    ) { backStackEntry ->
        val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
        if (albumId.startsWith("spotify:")) {
            val spotifyId = albumId.removePrefix("spotify:")
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.navigate("spotify_album/$spotifyId") {
                    popUpTo("album/$albumId") { inclusive = true }
                }
            }
        } else {
            if (LocalAuraTvShell.current) AuraAlbum(navController) else AlbumScreen(navController)
        }
    }

    composable(
        route = "artist/{artistId}?isPodcastChannel={isPodcastChannel}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("isPodcastChannel") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        if (LocalAuraTvShell.current) {
            AuraArtist(navController = navController)
        } else {
            ArtistScreen(navController)
        }
    }

    composable(
        route = "artist/{artistId}/songs",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistSongsScreen(navController)
    }

    composable(
        route = "artist/{artistId}/albums",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    composable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments =
            listOf(
                navArgument("artistId") {
                    type = NavType.StringType
                },
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        ArtistItemsScreen(navController)
    }

    composable(
        route = "online_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        if (LocalAuraTvShell.current) {
            AuraOnlinePlaylist(navController)
        } else {
            OnlinePlaylistScreen(navController)
        }
    }

    composable(
        route = "online_podcast/{podcastId}",
        arguments =
            listOf(
                navArgument("podcastId") {
                    type = NavType.StringType
                },
            ),
    ) {
        if (LocalAuraTvShell.current) {
            AuraPodcast(navController)
        } else {
            OnlinePodcastScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "local_playlist/{playlistId}",
        arguments =
            listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                },
            ),
    ) {
        if (LocalAuraTvShell.current) {
            AuraLocalPlaylist(navController)
        } else {
            LocalPlaylistScreen(navController)
        }
    }

    composable(
        route = "auto_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        AutoPlaylistScreen(navController)
    }

    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
                },
            ),
    ) {
        CachePlaylistScreen(navController)
    }

    composable(
        route = "top_playlist/{top}",
        arguments =
            listOf(
                navArgument("top") {
                    type = NavType.StringType
                },
            ),
    ) {
        TopPlaylistScreen(navController)
    }

    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
            listOf(
                navArgument("browseId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("params") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    composable("settings") {
        SettingsScreen(navController, latestVersionName)
    }

    composable("settings/appearance") {
        AppearanceSettings(navController, snackbarHostState)
    }

    composable("settings/content") {
        ContentSettings(navController)
    }

    composable("settings/content/romanization") {
        RomanizationSettings(navController)
    }

    composable("settings/ai") {
        AiSettings(navController)
    }

    composable("settings/player") {
        PlayerSettings(navController)
    }

    composable("settings/player/sponsorblock") {
        SponsorBlockSettings(navController)
    }

    composable("settings/storage") {
        StorageSettings(navController)
    }

    composable("settings/privacy") {
        PrivacySettings(navController)
    }

    composable("settings/backup_restore") {
        BackupAndRestore(navController)
    }

    composable("settings/integrations/spotify/preload") {
        SpotifyPreloadScreen(navController, scrollBehavior)
    }

    composable("settings/spotify/login") {
        SpotifyLoginScreen(navController)
    }

    composable(
        route = "spotify_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        if (LocalAuraTvShell.current) {
            AuraSpotifyPlaylist(navController)
        } else {
            SpotifyPlaylistScreen(navController, scrollBehavior)
        }
    }

    composable(
        route = "spotify_album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),
    ) {
        if (LocalAuraTvShell.current) {
            AuraSpotifyAlbum(navController)
        } else {
            SpotifyAlbumScreen(navController, scrollBehavior)
        }
    }

    composable("spotify_liked_songs") {
        SpotifyLikedSongsScreen(navController, scrollBehavior)
    }

    composable(
        route = "spotify_folder/{folderUri}?name={name}",
        arguments = listOf(
            navArgument("folderUri") { type = NavType.StringType },
            navArgument("name") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStack ->
        val rawUri = backStack.arguments?.getString("folderUri").orEmpty()
        val rawName = backStack.arguments?.getString("name")
        // Both args were URL-encoded by the caller (URI contains ':' and '/'; name
        // can contain spaces or unicode). Decode here so we hand the unmodified
        // values to the API and the AppBar.
        val folderUri = java.net.URLDecoder.decode(rawUri, Charsets.UTF_8.name())
        val folderName = rawName?.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
        SpotifyFolderScreen(navController, folderUri, folderName)
    }

    // Upstream replaced the in-app Discord login screen with DiscordOAuthActivity.

    composable("settings/updater") {
        UpdaterScreen(navController)
    }

    composable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }

    composable("login") {
        LoginScreen(navController)
    }

    composable("switch_channel") {
        LoginScreen(
            navController = navController,
            isSwitchingChannel = true,
        )
    }

    composable("equalizer") {
        EqScreen()
    }

    composable("eq_wizard") {
        WizardScreen(onNavigateBack = {
            navController.popBackStack()
        })
    }

    composable(
        route = "recognition?autoStart={autoStart}",
        arguments =
            listOf(
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
    ) {
        RecognitionScreen(navController, it.arguments?.getBoolean("autoStart") ?: false)
    }

    composable("recognition_history") {
        RecognitionHistoryScreen(navController)
    }
    composable("settings/android_auto") {
        AndroidAutoSettings(navController)
    }
}
