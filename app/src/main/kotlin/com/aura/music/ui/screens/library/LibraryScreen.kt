/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aura.music.LocalNavController
import com.aura.music.R
import com.aura.music.constants.AlbumViewTypeKey
import com.aura.music.constants.ChipSortTypeKey
import com.aura.music.constants.LibraryFilter
import com.aura.music.constants.LibraryViewType
import com.aura.music.constants.PlaylistViewTypeKey
import com.aura.music.ui.component.ChipsRow
import com.aura.music.ui.screens.library.local.LocalFilesScreen
import com.aura.music.utils.rememberEnumPreference
import androidx.navigation.NavController

@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    var libraryViewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips = listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                    LibraryFilter.PODCASTS to stringResource(R.string.filter_podcasts),
                    LibraryFilter.LOCAL_FILES to stringResource(R.string.filter_local_files),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType = if (filterType == it) LibraryFilter.LIBRARY else it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = libraryViewType,
                onViewTypeChange = { libraryViewType = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
            )
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.PODCASTS -> LibraryPodcastsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )

            LibraryFilter.LOCAL_FILES -> LocalFilesScreen(
                navController,
                filterContent,
            )
        }
    }
}
