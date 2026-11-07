/**
 * Aura Music — Home destination in the Material You TV reference's layout:
 * page title, a hero card fed by the first Spotify shelf, then pick-card shelves.
 */

package com.aura.music.ui.aura


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.innertube.YouTube
import com.aura.innertube.models.ArtistItem
import com.aura.music.LocalAuraTvShell
import com.aura.music.LocalDatabase
import com.aura.music.LocalListenTogetherManager
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.models.SectionType
import com.aura.music.models.SpotifyHomeSection
import com.aura.music.models.toSpotifyAlbum
import com.aura.music.models.toSpotifyArtist
import com.aura.music.models.toSpotifyPlaylist
import com.aura.music.playback.SpotifyYouTubeMapper
import com.aura.music.playback.queues.SpotifyPlaylistQueue
import com.aura.music.playback.queues.SpotifyQueue
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.resolveSpotifySectionTitle
import com.aura.music.ui.menu.SpotifyPlaylistMenu
import com.aura.music.ui.menu.SpotifyTrackMenu
import com.aura.music.ui.theme.TvRowNav
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.toAlbumItem
import com.aura.music.utils.toArtistItem
import com.aura.music.utils.toPlaylistItem
import com.aura.music.utils.toSongItem
import com.aura.music.viewmodels.HomeViewModel
import com.aura.spotify.Spotify
import com.aura.spotify.models.SpotifyAlbum
import com.aura.spotify.models.SpotifyArtist
import com.aura.spotify.models.SpotifyHomeFeedItem
import com.aura.spotify.models.SpotifyPlaylist
import com.aura.spotify.models.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber


/** How many New Releases `getAlbum` round-trips a tile press may open at once. */
private const val ReleaseFetchConcurrency = 6

/** How many cards of the first shelf the hero carousel walks through. */
private const val HeroLeadCount = 10


@Composable
fun AuraHome(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val spotifyHomeSections by viewModel.spotifyHomeSections.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val spotifyMapper = remember { SpotifyYouTubeMapper(database) }
    val scope = rememberCoroutineScope()
    val menuState = LocalMenuState.current

    // A shelf tile is the only place a TV user can reach a track menu from the feed, and the
    // remote has no long-press-free gesture, so the pick carries the menu as its long-press.
    // Only entities with a Spotify-native menu are wired: an album or artist would open a
    // YouTube menu pointed at a browseId this catalog never had.
    val openMenu: (Any) -> Unit = { item ->
        when (item) {
            is SpotifyTrack ->
                menuState.show {
                    SpotifyTrackMenu(
                        track = item,
                        mapper = spotifyMapper,
                        onDismiss = menuState::dismiss,
                        navController = navController,
                    )
                }

            is SpotifyPlaylist ->
                menuState.show {
                    SpotifyPlaylistMenu(
                        playlist = item,
                        onNavigate = { navController.navigate("spotify_playlist/${item.id}") },
                        onDismiss = menuState::dismiss,
                    )
                }
        }
    }

    LaunchedEffect(Unit) { viewModel.loadHomeData() }

    val playTrack: (SpotifyTrack) -> Unit = { track ->
        if (!isListenTogetherGuest) {
            playerConnection.playQueue(
                SpotifyQueue(
                    initialTrack = track,
                    mapper = spotifyMapper,
                    context = viewModel.context,
                    database = database,
                )
            )
        }
    }

    val playAlbum: (SpotifyAlbum) -> Unit = { album ->
        scope.launch(Dispatchers.IO) {
            val tracks = Spotify.album(album.id)
                .getOrNull()
                ?.tracks
                ?.items
                ?.filter { !it.isLocal && it.id.isNotEmpty() }
                .orEmpty()
            if (tracks.isEmpty()) {
                Timber.w("AuraHome: no playable tracks for Spotify album ${album.id}")
                return@launch
            }
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    SpotifyPlaylistQueue(
                        playlistId = "album_${album.id}",
                        initialTracks = tracks,
                        startIndex = 0,
                        mapper = spotifyMapper,
                    ),
                )
            }
        }
    }

    // A New Releases tile queues the shelf from that release onward, so playback carries into the
    // next release instead of stopping at the end of the one that was pressed. Home warms these
    // track lists in the background after it lands; anything still missing when the tile is pressed
    // is fetched then, bounded so a cold shelf can't open twenty concurrent round-trips.
    val playReleaseShelf: (List<SpotifyAlbum>, SpotifyAlbum) -> Unit = { shelf, tapped ->
        val from = shelf.indexOfFirst { album -> album.id == tapped.id }.coerceAtLeast(0)
        val releases = shelf.drop(from)
        scope.launch(Dispatchers.IO) {
            val warmed = viewModel.newReleaseTracks.value
            val missing = releases.filter { album -> warmed[album.id] == null }
            val fetched: Map<String, List<SpotifyTrack>> = if (missing.isEmpty()) {
                emptyMap()
            } else {
                val gate = Semaphore(ReleaseFetchConcurrency)
                coroutineScope {
                    missing.map { album ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                album.id to (
                                    Spotify.album(album.id).getOrNull()?.tracks?.items
                                        ?.filter { track -> !track.isLocal && track.id.isNotEmpty() }
                                        ?: emptyList()
                                    )
                            }
                        }
                    }.awaitAll().toMap()
                }
            }
            val tracks = releases.flatMap { album -> warmed[album.id] ?: fetched[album.id].orEmpty() }
            if (tracks.isEmpty()) {
                Timber.w("AuraHome: no playable tracks in the Spotify New Releases shelf")
                return@launch
            }
            Timber.d("AuraHome: queueing ${tracks.size} tracks across ${releases.size} New Releases (from ${tapped.name})")
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    SpotifyPlaylistQueue(
                        playlistId = "spotify_new_releases",
                        initialTracks = tracks,
                        startIndex = 0,
                        mapper = spotifyMapper,
                    ),
                )
            }
        }
    }

    val playPlaylist: (SpotifyPlaylist) -> Unit = { playlist ->
        playerConnection.playQueue(
            SpotifyPlaylistQueue(
                playlistId = playlist.id,
                mapper = spotifyMapper,
            ),
        )
    }

    val openArtist: (SpotifyArtist) -> Unit = { artist ->
        scope.launch(Dispatchers.IO) {
            val ytResult = YouTube.search(
                artist.name,
                YouTube.SearchFilter.FILTER_ARTIST,
            ).getOrNull()
            val ytArtist = ytResult?.items
                ?.firstOrNull { it is ArtistItem }
            if (ytArtist != null) {
                withContext(Dispatchers.Main) {
                    navController.navigate("artist/${ytArtist.id}")
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val pagePad = auraPagePadding()
    val sections = spotifyHomeSections.orEmpty()
    val tvShell = LocalAuraTvShell.current
    // Which of the hero's recommended cards is showing. Saved so a scroll away and back, or a
    // navigation round-trip, does not restart the shelf at card one.
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = pagePad,
            top = pagePad,
            end = pagePad,
            bottom = 132.apx(),
        ),
    ) {
        item(key = "aura_home_title") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.apx()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AuraText(
                    value = stringResource(R.string.aura_recommended_for_you),
                    px = 48,
                    weight = FontWeight.Bold,
                    color = Color.White,
                )
                // The reference has no pull-to-refresh and a TV remote cannot perform one, so the
                // reload lives here as a focusable control.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .auraFocused()
                        .size(72.apx())
                        .clip(CircleShape)
                        .clickable { viewModel.refresh() },
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.apx()),
                            strokeWidth = 3.apx(),
                            color = AuraMock.Primary,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.aura_ic_refresh_cw),
                            contentDescription = null,
                            tint = AuraMock.Muted,
                            modifier = Modifier.size(36.apx()),
                        )
                    }
                }
            }
        }

        // The feed is a Spotify round-trip away, which on this TV is tens of seconds at cold start.
        // An empty page over that window reads as a hang, so the shelves are reserved while loading.
        if (sections.isEmpty() && (isLoading || isRefreshing)) {
            item(key = "aura_home_skeleton") {
                AuraHomeSkeleton()
            }
        }

        // The round-trip can also come back with nothing in it — expired anonymous token, a
        // rejected shelf call, an empty Spotify home for this region. That used to leave only the
        // page title on screen, which reads as a hang with no way out.
        if (sections.isEmpty() && !isLoading && !isRefreshing) {
            item(key = "aura_home_empty") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.apx()),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AuraPillButton(
                        label = stringResource(R.string.retry),
                        icon = R.drawable.aura_ic_refresh_cw,
                        filled = true,
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.auraFocused(),
                    )
                }
            }
        }

        sections.forEachIndexed { index, section ->
            if (index == 0) {
                item(key = "aura_hero") {
                    val leads = remember(section) { section.toAuraHeroLeads(openMenu) }
                    // A refresh can hand back a shorter shelf than the one the saved index was read
                    // from, so aim at what is actually here rather than the index that got us here.
                    val leadIndex = heroIndex.coerceIn(0, (leads.size - 1).coerceAtLeast(0))
                    val lead = leads.getOrNull(leadIndex) ?: return@item
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.apx())
                            .then(
                                tvRowNav.row(
                                    index = 0,
                                    itemKey = "aura_hero",
                                    rowCount = sections.size,
                                ),
                            )
                            // The hero is one card of a ten-card shelf, so the sideways press that
                            // would leave it walks the shelf instead. DOWN still goes to the row
                            // below; only LEFT and RIGHT are taken, and only while there is somewhere
                            // to go.
                            .onPreviewKeyEvent { event ->
                                if (!tvShell ||
                                    event.type != KeyEventType.KeyDown ||
                                    leads.size < 2
                                ) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.DirectionLeft -> {
                                        heroIndex = (leadIndex - 1 + leads.size) % leads.size
                                        true
                                    }

                                    Key.DirectionRight -> {
                                        heroIndex = (leadIndex + 1) % leads.size
                                        true
                                    }

                                    else -> false
                                }
                            }
                            .focusGroup(),
                    ) {
                        AuraHeroBlock(
                            pick = lead.pick.copy(onClick = {
                                when (val entity = lead.entity) {
                                    is SpotifyTrack -> playTrack(entity)
                                    is SpotifyAlbum ->
                                        if (section.title == "spotify_new_releases") {
                                            playReleaseShelf(section.albums, entity)
                                        } else {
                                            playAlbum(entity)
                                        }

                                    is SpotifyPlaylist -> playPlaylist(entity)
                                    is SpotifyArtist -> openArtist(entity)
                                }
                            }),
                            badge = stringResource(lead.typeRes).uppercase(),
                            modifier = tvRowNav.tile(0, 0, best = true),
                        )
                        // The count and the arrows sit opposite the play disc, over the faded art
                        // where the hero has no text: the listener has to know the card is one of ten.
                        if (leads.size > 1) {
                            AuraText(
                                value = "${leadIndex + 1} of ${leads.size}",
                                px = 22,
                                weight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 32.apx(), end = 32.apx())
                                    .background(AuraMock.Scrim, RoundedCornerShape(FullRound))
                                    .padding(horizontal = 20.apx(), vertical = 8.apx()),
                            )
                        }
                    }
                }
            } else {
                item(key = "aura_section_title_$index") {
                    AuraText(
                        value = resolveSpotifySectionTitle(section),
                        px = 30,
                        weight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 48.apx(), bottom = 32.apx()),
                    )
                }

                item(key = "aura_section_content_$index") {
                    AuraPickRow(
                        picks = section.toAuraPicks(
                            onMenu = openMenu,
                            onTrackPlay = playTrack,
                            // A New Releases tile is already the whole release, so pressing it plays
                            // rather than stopping on the detail screen to choose one track — and it
                            // plays the shelf, not just that release. Every other album shelf keeps
                            // opening, where the tile is a teaser.
                            onAlbumOpen = { album ->
                                if (section.title == "spotify_new_releases") {
                                    playReleaseShelf(section.albums, album)
                                } else {
                                    navController.navigate("spotify_album/${album.id}")
                                }
                            },
                            onPlaylistOpen = { navController.navigate("spotify_playlist/${it.id}") },
                            onArtistOpen = openArtist,
                        ),
                        rowNav = tvRowNav,
                        rowIndex = index,
                        rowCount = sections.size,
                        itemKey = "aura_section_content_$index",
                    )
                }
            }
        }
    }
}

/** The reference's hero and first two shelves, in the shapes they will take when the feed lands. */
@Composable
private fun AuraHomeSkeleton() {
    val transition = rememberInfiniteTransition(label = "auraHomeSkeleton")
    // A FloatState rather than `by`: reading the value in composition invalidates this whole group
    // every vsync, and the skeleton holds until the feed lands - 10 to 20s of recomposing eleven
    // clipped boxes under the splash, on the same thread the reveal needs. Recorded on the layer
    // instead, the pulse is a property update and the group composes once.
    val shade = transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.60f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700, easing = LinearEasing), RepeatMode.Reverse),
        label = "auraHomeSkeletonShade",
    )

    // Group alpha over opaque panels is the same compositing as translucent panels over the page,
    // and the boxes do not overlap, so the fade looks identical.
    Column(
        verticalArrangement = Arrangement.spacedBy(48.apx()),
        modifier = Modifier.graphicsLayer { alpha = shade.value },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AuraPx.HeroHeight.apx())
                .clip(RoundedCornerShape(AuraPx.HeroRadius.apx()))
                .background(AuraMock.Panel),
        )
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.apx())) {
                repeat(5) {
                    Box(
                        modifier = Modifier
                            .size(AuraPx.PickCard.apx())
                            .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                            .background(AuraMock.Panel),
                    )
                }
            }
        }
    }
}

private fun SpotifyHomeSection.toAuraPicks(
    onMenu: (Any) -> Unit,
    onTrackPlay: (SpotifyTrack) -> Unit,
    onAlbumOpen: (SpotifyAlbum) -> Unit,
    onPlaylistOpen: (SpotifyPlaylist) -> Unit,
    onArtistOpen: (SpotifyArtist) -> Unit,
): List<AuraPick> = when {
    // Spotify sends a shelf as one ordered run of mixed cards, so the row shows all of them instead
    // of keeping only the cards that match the shelf's dominant type.
    items.isNotEmpty() -> items.map { item ->
        when (item) {
            is SpotifyHomeFeedItem.Playlist -> {
                val playlist = item.toSpotifyPlaylist()
                AuraPick(
                    id = item.id,
                    title = item.name,
                    subtitle = item.sourceTitle ?: playlist.owner?.displayName.orEmpty(),
                    image = item.imageUrl,
                    onClick = { onPlaylistOpen(playlist) },
                    onLongClick = { onMenu(playlist) },
                )
            }

            is SpotifyHomeFeedItem.Album -> {
                val album = item.toSpotifyAlbum()
                AuraPick(
                    id = item.id,
                    title = item.name,
                    subtitle = item.sourceTitle ?: album.artists.joinToString(", ") { it.name },
                    image = item.imageUrl,
                    onClick = { onAlbumOpen(album) },
                )
            }

            is SpotifyHomeFeedItem.Artist -> {
                val artist = item.toSpotifyArtist()
                AuraPick(
                    id = item.id,
                    title = item.name,
                    subtitle = item.sourceTitle.orEmpty(),
                    image = item.imageUrl,
                    onClick = { onArtistOpen(artist) },
                )
            }
        }
    }

    else -> when (type) {
        SectionType.TRACKS -> tracks.map { track ->
            AuraPick(
                id = track.id,
                title = track.name,
                subtitle = track.artists.joinToString(", ") { it.name },
                image = track.toSongItem().thumbnail,
                onClick = { onTrackPlay(track) },
                onLongClick = { onMenu(track) },
            )
        }
        SectionType.ALBUMS -> albums.map { album ->
            AuraPick(
                id = album.id,
                title = album.name,
                subtitle = album.artists.joinToString(", ") { it.name },
                image = album.toAlbumItem().thumbnail,
                onClick = { onAlbumOpen(album) },
            )
        }
        SectionType.ARTISTS -> artists.map { artist ->
            AuraPick(
                id = artist.id,
                title = artist.name,
                subtitle = artist.genres.joinToString("  ·  "),
                image = artist.toArtistItem().thumbnail,
                onClick = { onArtistOpen(artist) },
            )
        }
        SectionType.PLAYLISTS -> playlists.map { playlist ->
            AuraPick(
                id = playlist.id,
                title = playlist.name,
                subtitle = playlist.owner?.displayName.orEmpty(),
                image = playlist.toPlaylistItem().thumbnail,
                onClick = { onPlaylistOpen(playlist) },
                onLongClick = { onMenu(playlist) },
            )
        }
    }
}

/**
 * One card of the hero carousel: the reference's hero shape plus the entity that plays when it is
 * chosen. The play action is set at the call site because a mixed shelf carries a different action
 * per card.
 */
private data class AuraHeroLead(val typeRes: Int, val pick: AuraPick, val entity: Any)

/**
 * The shelf's first [HeroLeadCount] cards, in the hero's own shape. The hero used to show card one
 * and nothing else, so the rest of a recommended shelf could only be reached by scrolling down into
 * the row beneath it.
 */
private fun SpotifyHomeSection.toAuraHeroLeads(onMenu: (Any) -> Unit): List<AuraHeroLead> {
    val noAction: () -> Unit = {}

    // A shelf straight out of Spotify's feed is an ordered run of mixed cards, and the typed lists
    // only carry the dominant type, so the feed order wins when it is there.
    if (items.isNotEmpty()) {
        return items.take(HeroLeadCount).map { item ->
            when (item) {
                is SpotifyHomeFeedItem.Playlist -> {
                    val playlist = item.toSpotifyPlaylist()
                    AuraHeroLead(
                        R.string.type_playlist,
                        AuraPick(
                            id = item.id,
                            title = item.name,
                            subtitle = item.sourceTitle ?: playlist.owner?.displayName.orEmpty(),
                            image = item.imageUrl,
                            onClick = noAction,
                            onLongClick = { onMenu(playlist) },
                        ),
                        entity = playlist,
                    )
                }

                is SpotifyHomeFeedItem.Album -> {
                    val album = item.toSpotifyAlbum()
                    AuraHeroLead(
                        R.string.type_album,
                        AuraPick(
                            id = item.id,
                            title = item.name,
                            subtitle = item.sourceTitle ?: album.artists.joinToString(", ") { a -> a.name },
                            image = item.imageUrl,
                            onClick = noAction,
                        ),
                        entity = album,
                    )
                }

                is SpotifyHomeFeedItem.Artist ->
                    AuraHeroLead(
                        R.string.type_artist,
                        AuraPick(
                            id = item.id,
                            title = item.name,
                            subtitle = item.sourceTitle.orEmpty(),
                            image = item.imageUrl,
                            onClick = noAction,
                        ),
                        entity = item.toSpotifyArtist(),
                    )
            }
        }
    }

    return when (type) {
        SectionType.TRACKS -> tracks.take(HeroLeadCount).map { track ->
            AuraHeroLead(
                R.string.type_song,
                AuraPick(
                    id = track.id,
                    title = track.name,
                    subtitle = track.artists.joinToString(", ") { a -> a.name },
                    image = track.toSongItem().thumbnail,
                    onClick = noAction,
                    onLongClick = { onMenu(track) },
                ),
                entity = track,
            )
        }

        SectionType.ALBUMS -> albums.take(HeroLeadCount).map { album ->
            AuraHeroLead(
                R.string.type_album,
                AuraPick(
                    id = album.id,
                    title = album.name,
                    subtitle = album.artists.joinToString(", ") { a -> a.name },
                    image = album.toAlbumItem().thumbnail,
                    onClick = noAction,
                ),
                entity = album,
            )
        }

        SectionType.PLAYLISTS -> playlists.take(HeroLeadCount).map { playlist ->
            AuraHeroLead(
                R.string.type_playlist,
                AuraPick(
                    id = playlist.id,
                    title = playlist.name,
                    subtitle = playlist.owner?.displayName.orEmpty(),
                    image = playlist.toPlaylistItem().thumbnail,
                    onClick = noAction,
                    onLongClick = { onMenu(playlist) },
                ),
                entity = playlist,
            )
        }

        SectionType.ARTISTS -> artists.take(HeroLeadCount).map { artist ->
            AuraHeroLead(
                R.string.type_artist,
                AuraPick(
                    id = artist.id,
                    title = artist.name,
                    subtitle = artist.genres.joinToString("  ·  "),
                    image = artist.toArtistItem().thumbnail,
                    onClick = noAction,
                ),
                entity = artist,
            )
        }
    }
}
