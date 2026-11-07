/**
 * Aura Music — artist destination in the Material You TV reference's layout.
 *
 * The design file never draws an artist page, so this is composed from the primitives it does
 * define: the hero card, the pill button, the pick shelf and the rail row. Every control the old
 * screen offered survives — subscribe, radio, shuffle, play all, the local/library switch, per-item
 * menus, see-all links and the about panel — only the vocabulary used to draw them changed.
 */

package com.aura.music.ui.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.innertube.YouTube
import com.aura.innertube.models.AlbumItem
import com.aura.innertube.models.ArtistItem
import com.aura.innertube.models.EpisodeItem
import com.aura.innertube.models.PlaylistItem
import com.aura.innertube.models.PodcastItem
import com.aura.innertube.models.SongItem
import com.aura.innertube.models.WatchEndpoint
import com.aura.innertube.models.YTItem
import com.aura.music.LocalArtistNameAliases
import com.aura.music.LocalListenTogetherManager
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.HideExplicitKey
import com.aura.music.db.entities.Album
import com.aura.music.extensions.toMediaItem
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.PlayerConnection
import com.aura.music.playback.queues.ListQueue
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.component.ExpandableText
import com.aura.music.ui.component.LinkSegment
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.MenuState
import com.aura.music.ui.menu.AlbumMenu
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.menu.YouTubeArtistMenu
import com.aura.music.ui.menu.YouTubePlaylistMenu
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.ArtistNameAliases
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.ArtistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One shelf below the hero, normalised so a YouTube section and a database query render through the
 * same two blocks. A pick shelf is one stop in the vertical D-pad chain; a song shelf is one stop
 * per track, so the list can be walked without jumping past it.
 */
/**
 * Where a shelf sits in the vertical D-pad chain: [titleRow] is its heading, [contentRow] the first
 * of its [AuraArtistShelf.stops] content rows.
 */
private data class AuraShelfPlan(val titleRow: Int, val contentRow: Int)

private sealed class AuraArtistShelf {
    abstract val key: String
    abstract val title: String
    abstract val seeAll: (() -> Unit)?
    abstract val stops: Int

    data class Songs(
        override val key: String,
        override val title: String,
        override val seeAll: (() -> Unit)?,
        override val stops: Int,
        val rows: List<AuraTrackRow>,
    ) : AuraArtistShelf()

    data class Picks(
        override val key: String,
        override val title: String,
        override val seeAll: (() -> Unit)?,
        override val stops: Int = 1,
        val picks: List<AuraPick>,
    ) : AuraArtistShelf()
}

@Composable
fun AuraArtist(
    navController: NavController,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val listenTogetherManager = LocalListenTogetherManager.current
    val scope = rememberCoroutineScope()
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsStateWithLifecycle()
    val librarySongs by viewModel.librarySongs.collectAsStateWithLifecycle()
    val libraryAlbums by viewModel.libraryAlbums.collectAsStateWithLifecycle()
    val isChannelSubscribed by viewModel.isChannelSubscribed.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val artistName = ArtistNameAliases.resolve(
        LocalArtistNameAliases.current,
        viewModel.artistId,
        artistPage?.artist?.title ?: libraryArtist?.artist?.name.orEmpty(),
    ).ifEmpty { stringResource(R.string.type_artist) }

    var showLocal by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(libraryArtist, artistPage) {
        val isYouTube = libraryArtist?.artist?.isYouTubeArtist == true
        val isLocal = libraryArtist?.artist?.isLocal == true
        val hasSpotifyId = libraryArtist?.artist?.spotifyId != null
        showLocal = when {
            isLocal -> true
            hasSpotifyId && !isYouTube -> false
            libraryArtist != null && !isYouTube -> true
            else -> false
        }
    }

    val artistItem = artistPage?.artist
    val canSwitchSource = librarySongs.isNotEmpty() && libraryArtist?.artist?.isYouTubeArtist == true
    val songsLabel = stringResource(R.string.songs)
    val albumsLabel = stringResource(R.string.albums)

    val playSong: (SongItem) -> Unit = { song ->
        if (!isGuest) {
            if (song.id == mediaMetadata?.id) {
                playerConnection.togglePlayPause()
            } else {
                playerConnection.playQueue(
                    YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata()),
                )
            }
        }
    }

    val playAll: () -> Unit = {
        if (!isGuest) {
            if (showLocal) {
                if (librarySongs.isNotEmpty()) {
                    playerConnection.playQueue(
                        ListQueue(title = artistName, items = librarySongs.map { it.toMediaItem() }),
                    )
                }
            } else {
                val section = artistPage?.sections?.firstOrNull {
                    (it.items.firstOrNull() as? SongItem)?.album != null
                }
                val endpoint = section?.moreEndpoint
                when {
                    endpoint != null -> scope.launch(Dispatchers.IO) {
                        val result = YouTube.artistItems(endpoint).getOrNull()
                        val songs = result?.items?.filterIsInstance<SongItem>().orEmpty()
                        if (songs.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = artistName,
                                        items = songs.map { it.toMediaItem() },
                                    ),
                                )
                            }
                        }
                    }

                    section != null -> playerConnection.playQueue(
                        ListQueue(
                            title = artistName,
                            items = section.items.filterIsInstance<SongItem>().map { it.toMediaItem() },
                        ),
                    )

                    librarySongs.isNotEmpty() -> playerConnection.playQueue(
                        ListQueue(title = artistName, items = librarySongs.map { it.toMediaItem() }),
                    )
                }
            }
        }
    }

    val shelves: List<AuraArtistShelf> = buildList {
        if (showLocal) {
            val songs = librarySongs
                .filter { !hideExplicit || !it.song.explicit }
                .mapIndexed { index, song ->
                    AuraTrackRow(
                        id = song.id,
                        title = song.title,
                        subtitle = song.artists.joinToString(", ") { it.name },
                        image = song.thumbnailUrl,
                        durationText = makeTimeString(song.song.duration * 1000L),
                        active = song.id == mediaMetadata?.id,
                        onClick = {
                            if (!isGuest) {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = artistName,
                                            items = librarySongs.map { it.toMediaItem() },
                                            startIndex = index,
                                        ),
                                    )
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
            if (songs.isNotEmpty()) {
                add(
                    AuraArtistShelf.Songs(
                        key = "aura_local_songs",
                        title = songsLabel,
                        seeAll = { navController.navigate("artist/${viewModel.artistId}/songs") },
                        stops = songs.size,
                        rows = songs,
                    ),
                )
            }

            val albums = libraryAlbums
                .filter { !hideExplicit || !it.album.explicit }
                .map { album -> localAlbumPick(album, navController, menuState) }
            if (albums.isNotEmpty()) {
                add(
                    AuraArtistShelf.Picks(
                        key = "aura_local_albums",
                        title = albumsLabel,
                        seeAll = { navController.navigate("artist/${viewModel.artistId}/albums") },
                        picks = albums,
                    ),
                )
            }
        } else {
            artistPage?.sections?.forEachIndexed { index, section ->
                val items = section.items.distinctBy { it.id }
                if (items.isEmpty()) return@forEachIndexed
                val seeAll = section.moreEndpoint?.let { endpoint ->
                    {
                        navController.navigate(
                            "artist/${viewModel.artistId}/items?browseId=${endpoint.browseId}?params=${endpoint.params}",
                        )
                    }
                }
                if ((items.firstOrNull() as? SongItem)?.album != null) {
                    val rows = items.filterIsInstance<SongItem>().map { song ->
                        AuraTrackRow(
                            id = song.id,
                            title = song.title,
                            subtitle = song.artists.joinToString(", ") { it.name },
                            image = song.thumbnail,
                            durationText = song.duration?.let { makeTimeString(it * 1000L) },
                            active = song.id == mediaMetadata?.id,
                            onClick = { playSong(song) },
                            onLongClick = {
                                menuState.show {
                                    YouTubeSongMenu(song = song, onDismiss = menuState::dismiss)
                                }
                            },
                        )
                    }
                    add(
                        AuraArtistShelf.Songs(
                            key = "aura_section_$index",
                            title = section.title.ifEmpty { songsLabel },
                            seeAll = seeAll,
                            stops = rows.size,
                            rows = rows,
                        ),
                    )
                } else {
                    add(
                        AuraArtistShelf.Picks(
                            key = "aura_section_$index",
                            title = section.title.ifEmpty { songsLabel },
                            seeAll = seeAll,
                            picks = items.map { item ->
                                ytItemPick(item, navController, menuState, playerConnection, isGuest, scope)
                            },
                        ),
                    )
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val pagePad = auraPagePadding()
    // Every band of the page is one or more stops in the vertical chain: the hero, the action pills,
    // the about panel, then each shelf — a song shelf taking one stop per track so the D-pad can
    // walk the list. The bound lets the first and last stops hand their presses back to the rail.
    val aboutDescription = if (showLocal) null else artistPage?.description
    val aboutRuns = if (showLocal) null else artistPage?.descriptionRuns
    val aboutVisible = !aboutDescription.isNullOrEmpty() || !aboutRuns.isNullOrEmpty()
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    val shelfPlan = run {
        var next = if (aboutVisible) 3 else 2
        shelves.map { shelf ->
            val title = next++
            val content = next
            next += shelf.stops
            AuraShelfPlan(title, content)
        }
    }
    val rowCount = if (shelves.isEmpty()) {
        if (aboutVisible) 3 else 2
    } else {
        shelfPlan.last().contentRow + shelves.last().stops
    }

    // A D-pad press that opens this page leaves focus on the control that fired it, so the first
    // DOWN after arrival walks the rail instead of the new shelves. Anchor it to the hero.
    val heroFocus = remember { FocusRequester() }
    auraAnchorHero(heroFocus)

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
        item(key = "aura_artist_hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(tvRowNav.row(index = 0, itemKey = "aura_artist_hero", rowCount = rowCount))
                    .focusGroup(),
            ) {
                AuraHeroBlock(
                    pick = AuraPick(
                        id = viewModel.artistId,
                        title = artistName,
                        subtitle = listOfNotNull(
                            artistPage?.monthlyListenerCount,
                            artistPage?.subscriberCountText,
                        ).joinToString("  ·  "),
                        image = artistItem?.thumbnail ?: libraryArtist?.artist?.thumbnailUrl,
                        onClick = playAll,
                    ),
                    badge = stringResource(R.string.type_artist).uppercase(),
                    modifier = tvRowNav.tile(0, 0, best = true, anchor = heroFocus),
                )
            }
        }

        item(key = "aura_artist_actions") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.apx()),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        tvRowNav.row(
                            index = 1,
                            itemKey = "aura_artist_actions",
                            rowCount = rowCount,
                        ),
                    )
                    .focusGroup(),
            ) {
                var col = 0
                AuraPillButton(
                    label = stringResource(
                        if (isChannelSubscribed) R.string.subscribed else R.string.subscribe,
                    ),
                    icon = if (isChannelSubscribed) R.drawable.aura_ic_check else R.drawable.aura_ic_plus,
                    filled = !isChannelSubscribed,
                    onClick = { viewModel.toggleChannelSubscription() },
                    modifier = tvRowNav.tile(1, col++),
                )
                if (!showLocal && !isGuest) {
                    artistItem?.shuffleEndpoint?.let { endpoint ->
                        AuraPillButton(
                            label = stringResource(R.string.shuffle),
                            icon = R.drawable.aura_ic_shuffle,
                            onClick = { playerConnection.playQueue(YouTubeQueue(endpoint)) },
                            modifier = tvRowNav.tile(1, col++),
                        )
                    }
                    artistItem?.radioEndpoint?.let { endpoint ->
                        AuraPillButton(
                            label = stringResource(R.string.radio),
                            icon = R.drawable.aura_ic_radio,
                            onClick = { playerConnection.playQueue(YouTubeQueue(endpoint)) },
                            modifier = tvRowNav.tile(1, col++),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (canSwitchSource) {
                    // The old screen hid this behind a floating action button; a remote reaches for
                    // a control it can see.
                    AuraGhostButton(
                        icon = if (showLocal) R.drawable.aura_ic_globe else R.drawable.aura_ic_library,
                        boxPx = 72,
                        glyphPx = 32,
                        onClick = {
                            showLocal = !showLocal
                            if (!showLocal && artistPage == null) viewModel.fetchArtistsFromYTM()
                        },
                        modifier = tvRowNav.tile(1, col),
                    )
                }
            }
        }

        if (aboutVisible) {
            item(key = "aura_artist_about") {
                AuraAboutPanel(
                    title = stringResource(R.string.about_artist),
                    details = listOfNotNull(
                        artistPage?.monthlyListenerCount,
                        artistPage?.subscriberCountText,
                    ),
                    description = aboutDescription.orEmpty(),
                    runs = aboutRuns?.map {
                        LinkSegment(text = it.text, url = it.navigationEndpoint?.urlEndpoint?.url)
                    },
                    expanded = aboutExpanded,
                    onToggle = { aboutExpanded = !aboutExpanded },
                    modifier = Modifier
                        .then(
                            tvRowNav.row(
                                index = 2,
                                itemKey = "aura_artist_about",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup()
                        .then(tvRowNav.tile(2, 0)),
                )
            }
        }

        if (artistPage == null && !showLocal) {
            item(key = "aura_artist_loading") {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.apx()),
                        strokeWidth = 4.apx(),
                        color = AuraMock.Primary,
                    )
                }
            }
        }

        shelves.forEachIndexed { index, shelf ->
            val plan = shelfPlan[index]
            item(key = "aura_shelf_title_${shelf.key}") {
                AuraSectionHeader(
                    title = shelf.title,
                    onClick = shelf.seeAll,
                    modifier = Modifier
                        .padding(top = 28.apx())
                        .then(
                            tvRowNav.row(
                                index = plan.titleRow,
                                itemKey = "aura_shelf_title_${shelf.key}",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup()
                        .then(tvRowNav.tile(plan.titleRow, 0)),
                )
            }
            when (shelf) {
                is AuraArtistShelf.Songs -> auraTrackRows(
                    rows = shelf.rows,
                    rowNav = tvRowNav,
                    rowStart = plan.contentRow,
                    rowCount = rowCount,
                )

                is AuraArtistShelf.Picks -> item(key = shelf.key) {
                    AuraPickRow(
                        picks = shelf.picks,
                        rowNav = tvRowNav,
                        rowIndex = plan.contentRow,
                        rowCount = rowCount,
                        itemKey = shelf.key,
                    )
                }
            }
        }
    }
}

private fun localAlbumPick(
    album: Album,
    navController: NavController,
    menuState: MenuState,
): AuraPick = AuraPick(
    id = album.id,
    title = album.title,
    subtitle = album.artists.joinToString(", ") { it.name },
    image = album.thumbnailUrl,
    onClick = { navController.navigate("album/${album.id}") },
    onLongClick = {
        menuState.show {
            AlbumMenu(originalAlbum = album, onDismiss = menuState::dismiss)
        }
    },
)

/** A shelf tile: what the reference draws, plus the same tap and long-press behaviour as before. */
private fun ytItemPick(
    item: YTItem,
    navController: NavController,
    menuState: MenuState,
    playerConnection: PlayerConnection,
    isGuest: Boolean,
    scope: CoroutineScope,
): AuraPick = when (item) {
    is SongItem -> AuraPick(
        id = item.id,
        title = item.title,
        subtitle = item.artists.joinToString(", ") { it.name },
        image = item.thumbnail,
        onClick = {
            if (!isGuest) playerConnection.playQueue(
                YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )
        },
        onLongClick = {
            menuState.show { YouTubeSongMenu(song = item, onDismiss = menuState::dismiss) }
        },
    )

    is AlbumItem -> AuraPick(
        id = item.id,
        title = item.title,
        subtitle = item.artists?.joinToString(", ") { it.name }.orEmpty(),
        image = item.thumbnail,
        onClick = { navController.navigate("album/${item.browseId}") },
        onLongClick = {
            menuState.show { YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss) }
        },
    )

    is ArtistItem -> AuraPick(
        id = item.id,
        title = item.title,
        subtitle = "",
        image = item.thumbnail,
        onClick = { navController.navigate("artist/${item.id}") },
        onLongClick = {
            menuState.show { YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss) }
        },
    )

    is PlaylistItem -> AuraPick(
        id = item.id,
        title = item.title,
        subtitle = item.songCountText.orEmpty(),
        image = item.thumbnail,
        onClick = { navController.navigate("online_playlist/${item.id}") },
        onLongClick = {
            menuState.show {
                YouTubePlaylistMenu(
                    playlist = item,
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
    )

    is PodcastItem -> AuraPick(
        id = item.id,
        title = item.title,
        subtitle = item.episodeCountText.orEmpty(),
        image = item.thumbnail,
        onClick = { navController.navigate("online_podcast/${item.id}") },
        onLongClick = {
            menuState.show {
                YouTubePlaylistMenu(
                    playlist = item.asPlaylistItem(),
                    coroutineScope = scope,
                    onDismiss = menuState::dismiss,
                )
            }
        },
    )

    is EpisodeItem -> AuraPick(
        id = item.id,
        title = item.title,
        subtitle = item.podcast?.name.orEmpty(),
        image = item.thumbnail,
        onClick = {
            if (!isGuest) playerConnection.playQueue(
                YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )
        },
        onLongClick = {
            menuState.show {
                YouTubeSongMenu(song = item.asSongItem(), onDismiss = menuState::dismiss)
            }
        },
    )
}

/**
 * The reference's panel: `#211F26`, radius 28, `p-6`, with the text that sits under it. A remote has
 * no hover and no scroll wheel, so the panel is one focusable stop and CENTER asks it for more.
 */
@Composable
private fun AuraAboutPanel(
    title: String,
    details: List<String>,
    description: String,
    runs: List<LinkSegment>?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .auraFocused(corner = AuraPx.PickCardRadius.apx(), grow = false)
            .clickable(onClick = onToggle)
            .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
            .background(AuraMock.Panel)
            .padding(28.apx()),
        verticalArrangement = Arrangement.spacedBy(12.apx()),
    ) {
        AuraText(value = title, px = 30, weight = FontWeight.Bold, color = Color.White)
        details.forEach {
            AuraText(value = it, px = 20, color = AuraMock.Primary)
        }
        ProvideTextStyle(auraType(FontWeight.Normal, 22)) {
            CompositionLocalProvider(
                LocalContentColor provides AuraMock.Muted,
            ) {
                ExpandableText(
                    text = description,
                    runs = runs,
                    collapsedMaxLines = if (expanded) Int.MAX_VALUE else 3,
                )
            }
        }
    }
}
