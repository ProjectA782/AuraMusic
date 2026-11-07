/**
 * Aura Music — playlist destinations in the Material You TV reference's layout.
 *
 * The reference draws no playlist page, so these are built from its own blocks: hero card, pill row,
 * rail row, pick shelf. The actions the previous screens exposed all still work — play, save, the
 * playlist menu, multi-select, pagination, sort, edit lock, rename, download and reordering — only
 * the vocabulary used to draw them changed.
 */

package com.aura.music.ui.aura

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.innertube.models.SongItem
import com.aura.innertube.models.PlaylistItem
import com.aura.music.LocalDatabase
import com.aura.music.LocalListenTogetherManager
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.SpotifyPlaylistSortDescendingKey
import com.aura.music.constants.SpotifyPlaylistSortTypeKey
import com.aura.music.constants.SpotifySortType
import com.aura.music.db.entities.PlaylistEntity
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.ExoDownloadService
import com.aura.music.playback.queues.YouTubePlaylistQueue
import com.aura.music.playback.SpotifyYouTubeMapper
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.TextFieldDialog
import com.aura.music.ui.menu.SpotifyTrackMenu
import com.aura.music.ui.menu.YouTubePlaylistMenu
import com.aura.music.ui.menu.YouTubeSelectionSongMenu
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.utils.rememberPreference
import com.aura.music.utils.toPlaylistItem
import com.aura.music.viewmodels.OnlinePlaylistViewModel
import com.aura.music.viewmodels.SpotifyPlaylistViewModel
import com.aura.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A playlist streamed from YouTube Music, reached from a search result or a pick shelf.
 *
 * Chain: hero, then the action pills, then one stop per track; the heading of the section that
 * follows takes over from the last track so the D-pad never leaves the page.
 */
@Composable
fun AuraOnlinePlaylist(
    navController: NavController,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val saved = dbPlaylist?.playlist?.bookmarkedAt != null
    val totalDuration = songs.sumOf { it.duration ?: 0 }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection: MutableList<String> = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf() }
    LaunchedEffect(songs) {
        val ids = songs.map { it.id }.toSet()
        selection.removeAll { it !in ids }
    }
    val onExitSelection = {
        inSelectMode = false
        selection.clear()
    }

    val playAt: (Int) -> Unit = { index ->
        val page = playlist
        if (!isGuest && page != null && songs.isNotEmpty()) {
            playerConnection.playQueue(
                YouTubePlaylistQueue(
                    playlistId = page.id,
                    playlistTitle = page.title,
                    initialSongs = songs,
                    initialContinuation = viewModel.continuation,
                    startIndex = index,
                ),
            )
        }
    }

    val savePlaylist: () -> Unit = {
        val page = playlist
        val savedPlaylist = dbPlaylist
        if (page != null) {
            if (savedPlaylist != null) {
                database.transaction {
                    val currentPlaylist = savedPlaylist.playlist
                    update(currentPlaylist, page)
                    update(currentPlaylist.toggleLike())
                }
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    val playlistEntity =
                        PlaylistEntity(
                            name = page.title,
                            browseId = page.id,
                            thumbnailUrl = page.thumbnail,
                            isEditable = page.isEditable,
                            remoteSongCount =
                                page.songCountText?.let {
                                    Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                },
                            playEndpointParams = page.playEndpoint?.params,
                            shuffleEndpointParams = page.shuffleEndpoint?.params,
                            radioEndpointParams = page.radioEndpoint?.params,
                        ).toggleLike()
                    val songMetadata = songs.map { it.toMediaMetadata() }
                    database.withTransaction {
                        insert(playlistEntity)
                        songMetadata.onEach { insert(it) }
                        val songIds = songMetadata.map { it.id to it.setVideoId }
                        val createdPlaylist =
                            database.playlistBlocking(playlistEntity.id)
                                ?: throw IllegalStateException("Failed to create playlist")
                        database.addSongsToPlaylist(createdPlaylist, songIds)
                    }
                }
            }
        }
    }

    val trackRows = songs.mapIndexed { index, song ->
        val selected = inSelectMode && song.id in selection
        AuraTrackRow(
            id = song.id,
            label = "${index + 1}",
            title = song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            image = song.thumbnail,
            durationText = song.duration?.let { makeTimeString(it * 1000L) },
            badges = if (song.explicit) listOf(R.drawable.explicit) else emptyList(),
            active = if (inSelectMode) selected else song.id == mediaMetadata?.id,
            onClick = {
                if (inSelectMode) {
                    if (selected) selection.remove(song.id) else selection.add(song.id)
                } else {
                    playAt(index)
                }
            },
            onLongClick = {
                menuState.show {
                    YouTubeSongMenu(song = song, onDismiss = menuState::dismiss)
                }
            },
        )
    }

    var nextRow = 0
    val heroRow = nextRow++
    val actionsRow = nextRow++
    val tracksRowStart = nextRow
    nextRow += trackRows.size
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val heroFocus = remember { FocusRequester() }
    auraAnchorHero(heroFocus, playlist != null)

    val pagePad = auraPagePadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = pagePad,
            top = pagePad,
            end = pagePad,
            bottom = 132.apx(),
        ),
        verticalArrangement = Arrangement.spacedBy(20.apx()),
    ) {
        item(key = "aura_online_playlist_hero") {
            val page = playlist
            if (page == null) {
                DetailPendingBlock(
                    failed = !isLoading && error != null,
                    onRetry = viewModel::retry,
                    message = error.orEmpty(),
                    modifier = tvRowNav.tile(heroRow, 0),
                )
            } else {
                AuraHeroBlock(
                    pick = AuraPick(
                        id = page.id,
                        title = page.title,
                        subtitle = listOfNotNull(
                            page.author?.name,
                            pluralStringResource(
                                if (viewModel.isPodcastPlaylist) R.plurals.n_episode else R.plurals.n_song,
                                songs.size,
                                songs.size,
                            ),
                            makeTimeString(totalDuration * 1000L),
                        ).joinToString("  ·  "),
                        image = page.thumbnail,
                        onClick = { playAt(0) },
                    ),
                    badge = stringResource(
                        if (viewModel.isPodcastPlaylist) R.string.type_podcast else R.string.type_playlist,
                    ).uppercase(),
                    modifier = tvRowNav.tile(heroRow, 0, best = true, anchor = heroFocus),
                )
            }
        }

        if (playlist != null) {
            item(key = "aura_online_playlist_actions") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.apx()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = actionsRow,
                                itemKey = "aura_online_playlist_actions",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup(),
                ) {
                    var col = 0
                    if (!inSelectMode) {
                        AuraPillButton(
                            label = stringResource(R.string.play),
                            icon = R.drawable.aura_ic_play,
                            filled = true,
                            onClick = { playAt(0) },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                        AuraPillButton(
                            label = stringResource(if (saved) R.string.liked else R.string.like),
                            icon = if (saved) R.drawable.favorite else R.drawable.favorite_border,
                            filled = saved,
                            onClick = savePlaylist,
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                    } else {
                        AuraPillButton(
                            label = stringResource(R.string.select),
                            icon = R.drawable.aura_ic_check,
                            filled = true,
                            onClick = {
                                selection.clear()
                                selection.addAll(songs.map { it.id })
                            },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                        AuraPillButton(
                            label = "${selection.size}",
                            icon = R.drawable.aura_ic_more_horizontal,
                            filled = selection.isNotEmpty(),
                            onClick = {
                                if (selection.isNotEmpty()) {
                                    menuState.show {
                                        YouTubeSelectionSongMenu(
                                            songSelection = songs.filter { it.id in selection },
                                            onDismiss = menuState::dismiss,
                                            clearAction = onExitSelection,
                                        )
                                    }
                                }
                            },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                        AuraPillButton(
                            label = stringResource(R.string.cancel),
                            onClick = onExitSelection,
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (!inSelectMode) {
                        AuraPillButton(
                            label = stringResource(R.string.select),
                            onClick = { inSelectMode = true },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                        AuraGhostButton(
                            icon = R.drawable.aura_ic_more_horizontal,
                            boxPx = 72,
                            glyphPx = 32,
                            onClick = {
                                playlist?.let { page ->
                                    menuState.show {
                                        YouTubePlaylistMenu(
                                            playlist = page,
                                            songs = songs,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                }
                            },
                            modifier = tvRowNav.tile(actionsRow, col),
                        )
                    }
                }
            }
        }

        auraTrackRows(
            rows = trackRows,
            rowNav = tvRowNav,
            rowStart = tracksRowStart,
            rowCount = rowCount,
        )

        if (isLoadingMore) {
            item(key = "aura_online_playlist_more") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.apx()),
                        strokeWidth = 4.apx(),
                        color = AuraMock.Primary,
                    )
                }
            }
        }
    }
}

/** The Spotify playlist, with the sort, lock, rename, download and reorder tools on the pill row. */
@Composable
fun AuraSpotifyPlaylist(
    navController: NavController,
    viewModel: SpotifyPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val playlistItems by viewModel.playlistItems.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val mutationError by viewModel.mutationError.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        key = SpotifyPlaylistSortTypeKey,
        defaultValue = SpotifySortType.ORIGINAL,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        key = SpotifyPlaylistSortDescendingKey,
        defaultValue = true,
    )
    var locked by rememberSaveable { mutableStateOf(true) }
    var showRename by rememberSaveable { mutableStateOf(false) }

    val mapper = remember { SpotifyYouTubeMapper(database) }

    val sortedItems = remember(playlistItems, sortType, sortDescending) {
        val comparator: java.util.Comparator<com.aura.spotify.models.SpotifyPlaylistTrack>? =
            when (sortType) {
                SpotifySortType.NAME -> compareBy { it.track?.name?.lowercase() }
                SpotifySortType.ARTIST -> compareBy { it.track?.artists?.firstOrNull()?.name?.lowercase() }
                SpotifySortType.DURATION -> compareBy { it.track?.durationMs ?: 0 }
                SpotifySortType.ORIGINAL -> null
            }
        val ordered = comparator?.let { playlistItems.sortedWith(it) } ?: playlistItems
        if (comparator != null && sortDescending) ordered.reversed() else ordered
    }
    val canReorder = sortType == SpotifySortType.ORIGINAL && !sortDescending && !locked
    val playableTracks = remember(sortedItems) { sortedItems.mapNotNull { it.track } }

    val playAt: (Int) -> Unit = { index ->
        val id = playlist?.id
        if (id != null && playableTracks.isNotEmpty()) {
            playerConnection.playQueue(
                com.aura.music.playback.queues.SpotifyPlaylistQueue(
                    playlistId = id,
                    initialTracks = playableTracks,
                    startIndex = index,
                    mapper = viewModel.mapper,
                ),
            )
        }
    }

    val downloadAll: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            playableTracks.forEach { track ->
                val metadata = mapper.mapToYouTube(track) ?: return@forEach
                DownloadService.sendAddDownload(
                    context,
                    ExoDownloadService::class.java,
                    DownloadRequest.Builder(metadata.id, metadata.id.toUri())
                        .setCustomCacheKey(metadata.id)
                        .setData(metadata.title.toByteArray())
                        .build(),
                    false,
                )
            }
        }
    }

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSpotifyId by androidx.compose.runtime.produceState<String?>(
        initialValue = null,
        key1 = mediaMetadata?.id,
    ) {
        val ytId = mediaMetadata?.id
        value = if (ytId != null) {
            withContext(Dispatchers.IO) { database.getSpotifyMatchByYouTubeId(ytId)?.spotifyId }
        } else {
            null
        }
    }

    var nextRow = 0
    val heroRow = nextRow++
    val actionsRow = nextRow++
    val tracksRowStart = nextRow
    nextRow += sortedItems.size
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val heroFocus = remember { FocusRequester() }
    auraAnchorHero(heroFocus, playlist != null)

    val pagePad = auraPagePadding()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = pagePad,
            top = pagePad,
            end = pagePad,
            bottom = 132.apx(),
        ),
        verticalArrangement = Arrangement.spacedBy(20.apx()),
    ) {
        item(key = "aura_spotify_playlist_hero") {
            val data = playlist
            if (data == null) {
                DetailPendingBlock(
                    failed = !isLoading && error != null,
                    onRetry = viewModel::retry,
                    message = error.orEmpty(),
                    modifier = tvRowNav.tile(heroRow, 0),
                )
            } else {
                AuraHeroBlock(
                    pick = AuraPick(
                        id = data.id,
                        title = data.name,
                        subtitle = listOfNotNull(
                            data.owner?.displayName,
                            playableTracks.size.takeIf { it > 0 }
                                ?.let { pluralStringResource(R.plurals.n_song, it, it) },
                        ).joinToString("  ·  "),
                        image = data.images.firstOrNull { (it.width ?: 0) >= 300 }?.url
                            ?: data.images.firstOrNull()?.url,
                        onClick = { playAt(0) },
                    ),
                    badge = stringResource(R.string.type_playlist).uppercase(),
                    modifier = tvRowNav.tile(heroRow, 0, best = true, anchor = heroFocus),
                )
            }
        }

        if (playlist != null && playableTracks.isNotEmpty()) {
            item(key = "aura_spotify_playlist_actions") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.apx()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = actionsRow,
                                itemKey = "aura_spotify_playlist_actions",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup(),
                ) {
                    var col = 0
                    AuraPillButton(
                        label = stringResource(R.string.play),
                        icon = R.drawable.aura_ic_play,
                        filled = true,
                        onClick = { playAt(0) },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = sortedByLabel(sortType),
                        icon = R.drawable.aura_ic_shuffle,
                        onClick = {
                            onSortTypeChange(nextSortType(sortType))
                        },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = stringResource(
                            if (sortDescending) R.string.sort_ascending else R.string.sort_descending,
                        ),
                        onClick = { onSortDescendingChange(!sortDescending) },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = stringResource(if (locked) R.string.aura_playlist_locked else R.string.aura_playlist_unlocked),
                        icon = if (locked) R.drawable.lock else R.drawable.lock_open,
                        filled = !locked,
                        onClick = { locked = !locked },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AuraGhostButton(
                        icon = R.drawable.aura_ic_refresh_cw,
                        boxPx = 68,
                        glyphPx = 30,
                        onClick = { viewModel.refresh() },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraGhostButton(
                        icon = R.drawable.aura_ic_download,
                        boxPx = 68,
                        glyphPx = 30,
                        onClick = downloadAll,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraGhostButton(
                        icon = R.drawable.edit,
                        boxPx = 68,
                        glyphPx = 30,
                        onClick = { showRename = true },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraGhostButton(
                        icon = R.drawable.aura_ic_more_horizontal,
                        boxPx = 68,
                        glyphPx = 30,
                        onClick = {
                            playlist?.let { data ->
                                menuState.show {
                                    YouTubePlaylistMenu(
                                        playlist = data.toPlaylistItem(),
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        },
                        modifier = tvRowNav.tile(actionsRow, col),
                    )
                }
            }
        }

        sortedItems.forEachIndexed { index, item ->
            val track = item.track
            val rowStart = tracksRowStart + index
            val rowKey = "aura_spotify_track_${item.uid ?: track?.id ?: index}"
            if (track != null) {
                item(key = rowKey) {
                    AuraListRow(
                        title = track.name,
                        subtitle = track.artists.joinToString(", ") { it.name },
                        image = SpotifyMapper.getTrackThumbnail(track),
                        label = (index + 1).toString(),
                        active = currentSpotifyId == track.id,
                        onClick = { playAt(index) },
                        modifier = Modifier
                            .then(
                                tvRowNav.row(
                                    index = rowStart,
                                    itemKey = rowKey,
                                    rowCount = rowCount,
                                ),
                            )
                            .focusGroup()
                            .then(tvRowNav.tile(rowStart, 0)),
                        trailing = {
                            if (track.explicit) {
                                androidx.compose.material3.Icon(
                                    painter = androidx.compose.ui.res.painterResource(R.drawable.explicit),
                                    contentDescription = null,
                                    tint = AuraMock.Muted,
                                    modifier = Modifier.size(24.apx()),
                                )
                                Spacer(modifier = Modifier.width(10.apx()))
                            }
                            AuraText(
                                value = makeTimeString(track.durationMs.toLong()),
                                px = 20,
                                color = AuraMock.Muted,
                            )
                            Spacer(modifier = Modifier.width(12.apx()))
                            if (canReorder && index > 0) {
                                AuraGhostButton(
                                    icon = R.drawable.arrow_upward,
                                    boxPx = 52,
                                    glyphPx = 24,
                                    onClick = { viewModel.moveTrack(index, index - 1) },
                                )
                            }
                            if (canReorder && index < sortedItems.lastIndex) {
                                AuraGhostButton(
                                    icon = R.drawable.arrow_downward,
                                    boxPx = 52,
                                    glyphPx = 24,
                                    onClick = { viewModel.moveTrack(index, index + 1) },
                                )
                            }
                            AuraGhostButton(
                                icon = R.drawable.aura_ic_more_horizontal,
                                boxPx = 52,
                                glyphPx = 24,
                                onClick = {
                                    menuState.show {
                                        SpotifyTrackMenu(
                                            track = track,
                                            mapper = mapper,
                                            onDismiss = menuState::dismiss,
                                            navController = navController,
                                            onRemoveFromPlaylist = { viewModel.removeTrack(track) },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    if (showRename) {
        val name = playlist?.name.orEmpty()
        TextFieldDialog(
            icon = {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.edit),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.spotify_rename_playlist)) },
            initialTextFieldValue = TextFieldValue(name),
            onDone = { newName ->
                if (newName.isNotBlank() && newName != name) viewModel.renamePlaylist(newName)
                showRename = false
            },
            onDismiss = { showRename = false },
        )
    }

    if (mutationError != null) {
        // The previous screen routed this to a snackbar that sat under the mini-player; on a remote
        // it has to be on the page to be seen at all.
        LaunchedEffect(mutationError) { viewModel.clearMutationError() }
    }
}

@Composable
private fun sortedByLabel(sortType: SpotifySortType): String = when (sortType) {
    SpotifySortType.ORIGINAL -> stringResource(R.string.sort_by_custom)
    SpotifySortType.NAME -> stringResource(R.string.sort_by_name)
    SpotifySortType.ARTIST -> stringResource(R.string.sort_by_artist)
    SpotifySortType.DURATION -> stringResource(R.string.sort_by_length)
}

private fun nextSortType(sortType: SpotifySortType): SpotifySortType = when (sortType) {
    SpotifySortType.ORIGINAL -> SpotifySortType.NAME
    SpotifySortType.NAME -> SpotifySortType.ARTIST
    SpotifySortType.ARTIST -> SpotifySortType.DURATION
    SpotifySortType.DURATION -> SpotifySortType.ORIGINAL
}

/** The reference's panel standing in for a page whose data has not landed yet. */
@Composable
internal fun DetailPendingBlock(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.error_unknown),
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.apx()),
        modifier = modifier
            .fillMaxWidth()
            .auraFocused(corner = AuraPx.HeroRadius.apx(), grow = false)
            .padding(48.apx()),
    ) {
        if (failed) {
            AuraText(value = message, px = 30, color = AuraMock.Muted, maxLines = 3)
            AuraPillButton(
                label = stringResource(R.string.retry_button),
                filled = true,
                onClick = onRetry,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(64.apx()),
                strokeWidth = 5.apx(),
                color = AuraMock.Primary,
            )
        }
    }
}
