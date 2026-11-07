/**
 * Aura Music — album destinations in the Material You TV reference's layout.
 *
 * The design file draws no album page, so these are assembled from the primitives it defines: the
 * hero card, the pill row, the rail row and the pick shelf. The features of the screens they replace
 * all survive here — play, save, the album menu, multi-select, other versions, artist links, the
 * Spotify queue and shuffle — only the vocabulary used to draw them changed.
 */

package com.aura.music.ui.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.music.LocalDatabase
import com.aura.music.LocalListenTogetherManager
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.HideExplicitKey
import com.aura.music.constants.HideVideoSongsKey
import com.aura.music.db.entities.Album
import com.aura.music.extensions.toMediaItem
import com.aura.music.playback.queues.LocalAlbumRadio
import com.aura.music.playback.queues.SpotifyPlaylistQueue
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.menu.AlbumMenu
import com.aura.music.ui.menu.SelectionSongMenu
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.menu.SpotifyTrackMenu
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.AlbumViewModel
import com.aura.music.viewmodels.SpotifyAlbumViewModel
import com.aura.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The album the app keeps in its own database, reached from an artist shelf or a search result.
 *
 * Rows follow the reference's stack: hero, then the action pills, then one stop per track, then the
 * other-versions shelf. Every index in that chain is derived from the same counter so a hidden band
 * — no artists, no other versions, an empty album — never leaves a gap the D-pad could fall into.
 */
@Composable
fun AuraAlbum(
    navController: NavController,
    viewModel: AlbumViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val collectedAlbum by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val playlistId by viewModel.playlistId.collectAsStateWithLifecycle()
    val otherVersions by viewModel.otherVersions.collectAsStateWithLifecycle()
    val fetchError by viewModel.fetchError.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val albumData = collectedAlbum
    val artists = albumData?.artists.orEmpty()
    val filteredSongs = remember(albumData, hideExplicit, hideVideoSongs) {
        (albumData?.songs ?: emptyList())
            .filter { !hideExplicit || !it.song.explicit }
            .filter { !hideVideoSongs || !it.song.isVideo }
    }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection: MutableList<String> = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) { mutableStateListOf() }
    LaunchedEffect(filteredSongs) {
        val ids = filteredSongs.map { it.id }.toSet()
        selection.removeAll { it !in ids }
    }
    val onExitSelection = {
        inSelectMode = false
        selection.clear()
    }

    val playAll: () -> Unit = {
        if (!isGuest && albumData != null) {
            playerConnection.service.getAutomix(playlistId)
            playerConnection.playQueue(LocalAlbumRadio(albumData))
        }
    }

    val liked = albumData?.album?.bookmarkedAt != null
    val toggleLike: () -> Unit = {
        albumData?.let { current ->
            database.query { update(current.album.toggleLike()) }
        }
    }

    val trackRows = filteredSongs.mapIndexed { index, song ->
        val selected = inSelectMode && song.id in selection
        AuraTrackRow(
            id = song.id,
            label = "${index + 1}",
            title = song.title,
            subtitle = song.artists.joinToString(", ") { it.name },
            image = song.thumbnailUrl,
            durationText = makeTimeString(song.song.duration * 1000L),
            badges = buildList {
                if (song.song.explicit) add(R.drawable.explicit)
                if (song.isDownloaded) add(R.drawable.aura_ic_download)
            },
            active = if (inSelectMode) selected else song.id == mediaMetadata?.id,
            onClick = {
                if (inSelectMode) {
                    if (selected) selection.remove(song.id) else selection.add(song.id)
                } else if (!isGuest) {
                    if (song.id == mediaMetadata?.id) {
                        playerConnection.togglePlayPause()
                    } else {
                        albumData?.let { current ->
                            playerConnection.service.getAutomix(playlistId)
                            playerConnection.playQueue(
                                LocalAlbumRadio(current, startIndex = index),
                            )
                        }
                    }
                }
            },
            onLongClick = {
                menuState.show {
                    SongMenu(originalSong = song, onDismiss = menuState::dismiss)
                }
            },
        )
    }

    val versionPicks = otherVersions.distinctBy { it.id }.map { item ->
        AuraPick(
            id = item.id,
            title = item.title,
            subtitle = item.artists?.joinToString(", ") { it.name }.orEmpty(),
            image = item.thumbnail,
            onClick = { navController.navigate("album/${item.id}") },
            onLongClick = {
                menuState.show {
                    YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss)
                }
            },
        )
    }

    var nextRow = 0
    val heroRow = nextRow++
    val actionsRow = nextRow++
    val artistsRow = if (artists.isNotEmpty()) nextRow++ else -1
    val tracksRowStart = nextRow
    nextRow += trackRows.size
    val versionsRow = if (versionPicks.isNotEmpty()) nextRow++ else -1
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val heroFocus = remember { FocusRequester() }
    auraAnchorHero(heroFocus, albumData != null)

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
        item(key = "aura_album_hero") {
            if (albumData == null) {
                AlbumPendingBlock(
                    failed = fetchError != null,
                    onRetry = viewModel::fetchAlbum,
                    modifier = tvRowNav.tile(heroRow, 0),
                )
            } else {
                val entity = albumData.album
                val totalDuration = filteredSongs.sumOf { it.song.duration }
                AuraHeroBlock(
                    pick = AuraPick(
                        id = entity.id,
                        title = entity.title,
                        subtitle = listOfNotNull(
                            entity.year?.toString(),
                            pluralStringResource(R.plurals.n_song, filteredSongs.size, filteredSongs.size),
                            makeTimeString(totalDuration * 1000L),
                        ).joinToString("  ·  "),
                        image = entity.thumbnailUrl,
                        onClick = playAll,
                    ),
                    badge = stringResource(R.string.type_album).uppercase(),
                    modifier = tvRowNav.tile(heroRow, 0, best = true, anchor = heroFocus),
                )
            }
        }

        item(key = "aura_album_actions") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.apx()),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(tvRowNav.row(index = actionsRow, itemKey = "aura_album_actions", rowCount = rowCount))
                    .focusGroup(),
            ) {
                var col = 0
                if (!inSelectMode) {
                    AuraPillButton(
                        label = stringResource(R.string.play),
                        icon = R.drawable.aura_ic_play,
                        filled = true,
                        onClick = playAll,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = stringResource(if (liked) R.string.liked else R.string.like),
                        icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
                        filled = liked,
                        onClick = toggleLike,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                } else {
                    AuraPillButton(
                        label = stringResource(R.string.select),
                        icon = R.drawable.aura_ic_check,
                        filled = true,
                        onClick = {
                            selection.clear()
                            selection.addAll(filteredSongs.map { it.id })
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
                                    SelectionSongMenu(
                                        songSelection = filteredSongs.filter { it.id in selection },
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
                            albumData?.let { current ->
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = Album(current.album, current.artists),
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

        if (artistsRow >= 0) {
            item(key = "aura_album_artists") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.apx()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = artistsRow,
                                itemKey = "aura_album_artists",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup(),
                ) {
                    artists.forEachIndexed { index, artist ->
                        AuraPillButton(
                            label = artist.name,
                            onClick = { navController.navigate("artist/${artist.id}") },
                            modifier = tvRowNav.tile(artistsRow, index),
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

        if (versionsRow >= 0) {
            item(key = "aura_album_versions_title") {
                AuraSectionHeader(
                    title = stringResource(R.string.other_versions),
                    modifier = Modifier.padding(top = 28.apx()),
                )
            }
            item(key = "aura_album_versions") {
                AuraPickRow(
                    picks = versionPicks,
                    rowNav = tvRowNav,
                    rowIndex = versionsRow,
                    rowCount = rowCount,
                    itemKey = "aura_album_versions",
                )
            }
        }
    }
}

/**
 * The Spotify album reached from a Spotify tile. Its queue is built by the same mapper the rest of
 * the Spotify pages use, so the play and shuffle buttons behave exactly as they did before.
 */
@Composable
fun AuraSpotifyAlbum(
    navController: NavController,
    viewModel: SpotifyAlbumViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val album by viewModel.album.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val currentSpotifyId by produceState<String?>(initialValue = null, key1 = mediaMetadata?.id) {
        val ytId = mediaMetadata?.id
        value = if (ytId != null) {
            withContext(Dispatchers.IO) { database.getSpotifyMatchByYouTubeId(ytId)?.spotifyId }
        } else {
            null
        }
    }

    val playAt: (Int, Boolean) -> Unit = { index, shuffled ->
        val albumId = album?.id
        val queue = if (albumId != null && tracks.isNotEmpty()) {
            SpotifyPlaylistQueue(
                playlistId = "album_$albumId",
                initialTracks = if (shuffled) tracks.shuffled() else tracks,
                startIndex = if (shuffled) 0 else index,
                mapper = viewModel.mapper,
            )
        } else {
            null
        }
        queue?.let(playerConnection::playQueue)
    }

    val menuState = LocalMenuState.current

    val trackRows = tracks.mapIndexed { index, track ->
        AuraTrackRow(
            id = track.id,
            label = track.trackNumber?.toString() ?: "${index + 1}",
            title = track.name,
            subtitle = track.artists.joinToString(", ") { it.name },
            image = SpotifyMapper.getTrackThumbnail(track),
            durationText = makeTimeString(track.durationMs.toLong()),
            badges = if (track.explicit) listOf(R.drawable.explicit) else emptyList(),
            active = currentSpotifyId == track.id,
            onClick = { playAt(index, false) },
            onLongClick = {
                menuState.show {
                    SpotifyTrackMenu(
                        track = track,
                        mapper = viewModel.mapper,
                        onDismiss = menuState::dismiss,
                        navController = navController,
                    )
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
    auraAnchorHero(heroFocus, album != null)
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
        item(key = "aura_spotify_album_hero") {
            val data = album
            if (data == null) {
                AlbumPendingBlock(
                    failed = !isLoading && error != null,
                    onRetry = viewModel::retry,
                    message = error.orEmpty(),
                    modifier = tvRowNav.tile(heroRow, 0),
                )
            } else {
                val meta = listOfNotNull(
                    data.albumType?.replaceFirstChar { it.uppercase() },
                    data.releaseDate?.take(4),
                    tracks.size.takeIf { it > 0 }
                        ?.let { pluralStringResource(R.plurals.n_song, it, it) },
                )
                AuraHeroBlock(
                    pick = AuraPick(
                        id = data.id,
                        title = data.name,
                        subtitle = meta.joinToString("  ·  "),
                        image = data.images.firstOrNull { (it.width ?: 0) >= 300 }?.url
                            ?: data.images.firstOrNull()?.url,
                        onClick = { playAt(0, false) },
                    ),
                    badge = (data.albumType ?: stringResource(R.string.type_album)).uppercase(),
                    modifier = tvRowNav.tile(heroRow, 0, best = true, anchor = heroFocus),
                )
            }
        }

        if (album != null && tracks.isNotEmpty()) {
            item(key = "aura_spotify_album_actions") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.apx()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = actionsRow,
                                itemKey = "aura_spotify_album_actions",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup(),
                ) {
                    AuraPillButton(
                        label = stringResource(R.string.play),
                        icon = R.drawable.aura_ic_play,
                        filled = true,
                        onClick = { playAt(0, false) },
                        modifier = tvRowNav.tile(actionsRow, 0),
                    )
                    AuraPillButton(
                        label = stringResource(R.string.shuffle),
                        icon = R.drawable.aura_ic_shuffle,
                        onClick = { playAt(0, true) },
                        modifier = tvRowNav.tile(actionsRow, 1),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AuraText(
                        value = album?.artists?.joinToString(", ") { it.name }.orEmpty(),
                        px = 22,
                        color = AuraMock.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        auraTrackRows(
            rows = trackRows,
            rowNav = tvRowNav,
            rowStart = tracksRowStart,
            rowCount = rowCount,
        )
    }
}

/** The reference's panel, standing in for an album or a track list that is not there yet. */
@Composable
private fun AlbumPendingBlock(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.error_unknown),
) {
    val shape = RoundedCornerShape(AuraPx.HeroRadius.apx())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.apx()),
        modifier = modifier
            .fillMaxWidth()
            .height(AuraPx.HeroHeight.apx())
            .auraFocused(corner = AuraPx.HeroRadius.apx(), grow = false)
            .clip(shape)
            .background(AuraMock.Panel)
            .padding(48.apx()),
    ) {
        if (failed) {
            AuraText(value = message, px = 30, color = AuraMock.Muted, maxLines = 3)
            AuraPillButton(
                label = stringResource(R.string.retry_button),
                icon = R.drawable.aura_ic_play,
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
