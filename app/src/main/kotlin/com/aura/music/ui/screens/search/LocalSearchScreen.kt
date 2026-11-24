/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aura.music.LocalArtistNameAliases
import com.aura.music.LocalNavController
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.CONTENT_TYPE_HEADER
import com.aura.music.db.entities.Album
import com.aura.music.db.entities.Artist
import com.aura.music.db.entities.LocalItem
import com.aura.music.db.entities.Playlist
import com.aura.music.db.entities.Song
import com.aura.music.extensions.toMediaItem
import com.aura.music.playback.queues.ListQueue
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraSectionHeader
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.AuraTrackRow
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.aura.auraTrackRows
import com.aura.music.ui.component.ChipsRow
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.ArtistNameAliases
import com.aura.music.utils.joinByBullet
import com.aura.music.utils.joinToArtistString
import com.aura.music.utils.makeTimeString
import com.aura.music.viewmodels.LocalFilter
import com.aura.music.viewmodels.LocalSearchViewModel
import kotlinx.coroutines.flow.drop

@Composable
fun LocalSearchScreen(
    query: String,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val queueSearchedSongsStr = stringResource(R.string.queue_searched_songs)
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    val artistNameAliases = LocalArtistNameAliases.current
    val andSeparator = stringResource(R.string.and)

    val lazyListState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(lazyListState)

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    val configuration = LocalWindowInfo.current
    val isLandscape = configuration.containerSize.width > configuration.containerSize.height

    // Under a single filter the chips above already name the group, so the page goes straight to rows.
    val showTitles = result.filter == LocalFilter.ALL

    // The queue a pressed song joins is the same preview list the page shows, not the full table, so
    // what the remote walks down is what plays next.
    val searchedSongs = result.map
        .getOrDefault(LocalFilter.SONG, emptyList())
        .filterIsInstance<Song>()
        .map { it.toMediaItem() }

    val currentSongId = mediaMetadata?.id
    val currentAlbumId = mediaMetadata?.album?.id

    val rowOf = @Composable { item: LocalItem ->
        when (item) {
            is Song -> AuraTrackRow(
                id = item.id,
                title = item.song.title,
                subtitle = item.orderedArtists.joinToArtistString(" $andSeparator ") {
                    ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
                },
                image = item.song.thumbnailUrl,
                durationText = makeTimeString(item.song.duration * 1000L),
                badges = buildList {
                    if (item.song.liked) add(R.drawable.favorite)
                    if (item.song.explicit) add(R.drawable.explicit)
                },
                active = item.id == currentSongId,
                onClick = {
                    if (item.id == currentSongId) {
                        playerConnection.togglePlayPause()
                    } else {
                        playerConnection.playQueue(
                            ListQueue(
                                title = queueSearchedSongsStr,
                                items = searchedSongs,
                                startIndex = searchedSongs.indexOfFirst { it.mediaId == item.id },
                            ),
                        )
                    }
                },
                onLongClick = {
                    menuState.show {
                        SongMenu(
                            originalSong = item,
                            onDismiss = {
                                onDismiss()
                                menuState.dismiss()
                            },
                            isFromCache = isFromCache,
                        )
                    }
                },
            )

            is Album -> AuraTrackRow(
                id = item.id,
                title = item.album.title,
                subtitle = joinByBullet(
                    item.artists.joinToArtistString(" $andSeparator ") { it.name },
                    pluralStringResource(R.plurals.n_song, item.album.songCount, item.album.songCount),
                ),
                image = item.album.thumbnailUrl,
                active = item.id == currentAlbumId,
                onClick = {
                    onDismiss()
                    navController.navigate("album/${item.id}")
                },
            )

            is Artist -> AuraTrackRow(
                id = item.id,
                title = item.artist.name,
                subtitle = if (item.songCount > 0) {
                    pluralStringResource(R.plurals.n_song, item.songCount, item.songCount)
                } else {
                    null
                },
                image = item.artist.thumbnailUrl,
                onClick = {
                    onDismiss()
                    navController.navigate("artist/${item.id}")
                },
            )

            is Playlist -> AuraTrackRow(
                id = item.id,
                title = item.playlist.name,
                subtitle = pluralStringResource(R.plurals.n_song, item.songCount, item.songCount),
                image = item.playlist.thumbnailUrl ?: item.thumbnails.firstOrNull(),
                onClick = {
                    onDismiss()
                    navController.navigate("local_playlist/${item.id}")
                },
            )
        }
    }

    // A group holding nothing is not emitted: its title would then be a counted row whose only
    // content is a heading with no result under it, and the chain would aim a press at that gap.
    val groups = result.map
        .map { (filter, items) -> filter to items.distinctBy { it.id } }
        .filter { (_, items) -> items.isNotEmpty() }
        .map { (filter, items) -> filter to items.map { rowOf(it) } }

    // One counted row per title and per result, in the order the list below emits them, so a press
    // walks the page instead of jumping past the rows that have not scrolled into view. The chips
    // above are not part of this chain: they carry the shell's own page anchor.
    val plan = mutableListOf<Pair<Int, Int>>()
    var nextRow = 0
    groups.forEach { (_, rows) ->
        val titleRow = if (showTitles) nextRow++ else -1
        val contentRow = nextRow
        nextRow += rows.size
        plan += titleRow to contentRow
    }
    val rowCount = nextRow

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
                .let { base ->
                    if (isLandscape) {
                        base.windowInsetsPadding(
                            WindowInsets.systemBars.only(WindowInsetsSides.Horizontal),
                        )
                    } else {
                        base
                    }
                },
    ) {
        ChipsRow(
            chips =
                listOf(
                    LocalFilter.ALL to stringResource(R.string.filter_all),
                    LocalFilter.SONG to stringResource(R.string.filter_songs),
                    LocalFilter.ALBUM to stringResource(R.string.filter_albums),
                    LocalFilter.ARTIST to stringResource(R.string.filter_artists),
                    LocalFilter.PLAYLIST to stringResource(R.string.filter_playlists),
                ),
            currentValue = searchFilter,
            onValueUpdate = { viewModel.filter.value = it },
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding =
                PaddingValues(
                    start = auraPagePadding(),
                    end = auraPagePadding(),
                    bottom = 132.apx(),
                ),
            verticalArrangement = Arrangement.spacedBy(20.apx()),
        ) {
            groups.forEachIndexed { index, (filter, rows) ->
                val titleRow = plan[index].first
                val contentRow = plan[index].second
                val titleKey = "aura_local_search_title_$filter"

                if (titleRow >= 0) {
                    item(key = titleKey, contentType = CONTENT_TYPE_HEADER) {
                        AuraSectionHeader(
                            title = stringResource(filterTitleRes(filter)),
                            onClick = { viewModel.filter.value = filter },
                            modifier =
                                Modifier
                                    .padding(top = 28.apx())
                                    .then(
                                        tvRowNav.row(
                                            index = titleRow,
                                            itemKey = titleKey,
                                            rowCount = rowCount,
                                        ),
                                    )
                                    .focusGroup()
                                    .then(tvRowNav.tile(titleRow, 0)),
                        )
                    }
                }

                auraTrackRows(
                    rows = rows,
                    rowNav = tvRowNav,
                    rowStart = contentRow,
                    rowCount = rowCount,
                )
            }

            // Not a counted row: a message takes no focus, so aiming the chain at it would strand the
            // press on something a remote cannot select.
            if (result.query.isNotEmpty() && groups.isEmpty()) {
                item(key = "no_result") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.apx()),
                    ) {
                        AuraText(
                            value = stringResource(R.string.no_results_found),
                            px = 26,
                            color = AuraMock.Muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun filterTitleRes(filter: LocalFilter) =
    when (filter) {
        LocalFilter.SONG -> R.string.filter_songs
        LocalFilter.ALBUM -> R.string.filter_albums
        LocalFilter.ARTIST -> R.string.filter_artists
        LocalFilter.PLAYLIST -> R.string.filter_playlists
        LocalFilter.ALL -> R.string.filter_all
    }
