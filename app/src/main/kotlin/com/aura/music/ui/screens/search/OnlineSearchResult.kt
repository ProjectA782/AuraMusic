/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.aura.music.ui.component.AuraTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import com.aura.music.LocalNavController
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_EPISODE
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_PROFILE
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.aura.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.aura.innertube.models.AlbumItem
import com.aura.innertube.models.ArtistItem
import com.aura.innertube.models.EpisodeItem
import com.aura.innertube.models.PlaylistItem
import com.aura.innertube.models.PodcastItem
import com.aura.innertube.models.SongItem
import com.aura.innertube.models.WatchEndpoint
import com.aura.innertube.models.YTItem
import com.aura.music.LocalAuraTvShell
import com.aura.music.LocalDatabase
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.AutoRadioQueueKey
import com.aura.music.constants.HideVideoSongsKey
import com.aura.music.constants.MiniPlayerBottomSpacing
import com.aura.music.constants.MiniPlayerHeight
import com.aura.music.constants.NavigationBarHeight
import com.aura.music.constants.PauseSearchHistoryKey
import com.aura.music.db.entities.SearchHistory
import com.aura.music.extensions.toMediaItem
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.queues.SpotifyQueue
import com.aura.music.playback.queues.ListQueue
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.aura.AuraGhostButton
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.component.ChipsRow
import com.aura.music.ui.component.EmptyPlaceholder
import com.aura.music.ui.component.HideOnScrollFAB
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.NavigationTitle
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.component.YouTubeListItem
import com.aura.music.ui.component.shimmer.ListItemPlaceHolder
import com.aura.music.ui.component.shimmer.ShimmerHost
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.menu.YouTubeArtistMenu
import com.aura.music.ui.menu.YouTubePlaylistMenu
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.utils.SearchRoutes
import com.aura.music.utils.rememberPreference
import com.aura.music.utils.isSpotifyId
import com.aura.music.utils.stripSpotifyPrefix
import com.aura.music.utils.toSpotifyTrackStub
import com.aura.music.viewmodels.OnlineSearchViewModel
import com.aura.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.navigation.NavController

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    viewModel: OnlineSearchViewModel = hiltViewModel(),
    pureBlack: Boolean = false,
    savedStateHandle: SavedStateHandle? = null
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val scrollToTopCount by savedStateHandle
        ?.getStateFlow("scrollToTopCount", 0)
        ?.collectAsStateWithLifecycle(initialValue = 0) ?: remember { mutableIntStateOf(0) }

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isSpotifySearch by viewModel.isSpotifySearch.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val tvShell = LocalAuraTvShell.current

    /*
     * The TV keyboard is a window of its own and takes the directional keys with it, so while it is
     * up nothing on this page can be reached. Dismissing it left the page with no focus owner at
     * all, which made every later press a no-op: hand focus back to the field so DOWN can walk out
     * of it into the chips and the suggestions below.
     */
    val imeUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var imeWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(imeUp) {
        if (tvShell && imeWasUp && !imeUp && isSearchFocused) {
            runCatching { focusRequester.requestFocus() }
        }
        imeWasUp = imeUp
    }

    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            kotlinx.coroutines.delay(100)
            if (tvShell) {
                // The header shows the query and the field lives on the input screen, so there is
                // no editor here to aim at: re-aiming at one would throw, throw the keyboard on
                // screen with nothing to type into, and drop the suggestion overlay over the
                // results. Walk the list back to its first row instead.
                runCatching { lazyListState.animateScrollToItem(0) }
            } else {
                try {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                } catch (e: Exception) {
                }
                // Set focused AFTER requesting focus, not before

                isSearchFocused = true
            }
        }
    }

    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(HideVideoSongsKey, defaultValue = false)
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    BackHandler(enabled = isSearchFocused) {
        isSearchFocused = false
        focusManager.clearFocus()
    }

    // Extract query from navigation arguments
    val encodedQuery = navController.currentBackStackEntry?.arguments?.getString("query") ?: ""
    val decodedQuery = remember(encodedQuery) { SearchRoutes.decodeQuery(encodedQuery) }

    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(decodedQuery, TextRange(decodedQuery.length)))
    }

    val onSearch: (String) -> Unit =
        remember {
            { searchQuery ->
                if (searchQuery.isNotEmpty()) {
                    isSearchFocused = false
                    focusManager.clearFocus()

                    navController.navigate(SearchRoutes.resultRoute(searchQuery)) {
                        popUpTo(SearchRoutes.ROUTE) {
                            inclusive = true
                        }

                        if (!pauseSearchHistory) {
                            coroutineScope.launch(Dispatchers.IO) {
                                database.query {
                                    insert(SearchHistory(query = searchQuery))
                                }
                            }
                        }
                    }
                }
            }
        }

    // Update query when decodedQuery changes
    LaunchedEffect(decodedQuery) {
        query = TextFieldValue(decodedQuery, TextRange(decodedQuery.length))
    }

    // Clear video filter if hideVideoSongs setting is enabled and filter is set to FILTER_VIDEO
    LaunchedEffect(hideVideoSongs) {
        if (hideVideoSongs && viewModel.filter.value == FILTER_VIDEO) {
            viewModel.filter.value = null
        }
    }

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val spotifyFilterValue by viewModel.spotifyFilter.collectAsStateWithLifecycle()
    val searchSummary = viewModel.summaryPage

    val itemsPage by remember(searchFilter, spotifyFilterValue, isSpotifySearch) {
        derivedStateOf {
            if (isSpotifySearch) {
                spotifyFilterValue?.let { viewModel.viewStateMap[it] }
            } else {
                searchFilter?.value?.let { viewModel.viewStateMap[it] }
            }
        }
    }

    // Unified "no active filter" check: YouTube uses `searchFilter`, Spotify uses `spotifyFilterValue`.
    // When null, the summary page is rendered; otherwise the filtered `itemsPage`.
    val isShowingSummary = if (isSpotifySearch) spotifyFilterValue == null else searchFilter == null

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                when (item) {
                    is SongItem -> {
                        YouTubeSongMenu(
                            song = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is AlbumItem -> {
                        YouTubeAlbumMenu(
                            albumItem = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is ArtistItem -> {
                        YouTubeArtistMenu(
                            artist = item,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is PlaylistItem -> {
                        YouTubePlaylistMenu(
                            playlist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is PodcastItem -> {
                        YouTubePlaylistMenu(
                            playlist = item.asPlaylistItem(),
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }

                    is EpisodeItem -> {
                        YouTubeSongMenu(
                            song = item.asSongItem(),
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            }
        }
        YouTubeListItem(
            item = item,
            isActive =
                when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    is EpisodeItem -> mediaMetadata?.id == item.id
                    else -> false
                },
            isPlaying = isPlaying,
            trailingContent = {
                if (tvShell) {
                    // The Material 3-dot button takes a long press there is no way to make on a
                    // remote. The ghost button is the same menu, opened by ENTER once the ring is
                    // on it, and it draws that ring.
                    AuraGhostButton(
                        icon = R.drawable.aura_ic_more_horizontal,
                        boxPx = 52,
                        glyphPx = 24,
                        onClick = longClick,
                    )
                } else {
                    IconButton(
                        onClick = longClick,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                }
            },
            modifier =
                (if (tvShell) Modifier.auraFocused(corner = 16.dp, grow = false) else Modifier)
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else if (item.id.isSpotifyId()) {
                                        // Spotify search result: create SpotifyQueue
                                        val spotifyTrack = item.toSpotifyTrackStub()
                                        if (spotifyTrack != null) {
                                            playerConnection.playQueue(
                                                SpotifyQueue(
                                                    initialTrack = spotifyTrack,
                                                    mapper = viewModel.spotifyYouTubeMapper,
                                                )
                                            )
                                        }
                                    } else {
                                        playerConnection.playQueue(
                                            if (autoRadioQueue) {
                                                YouTubeQueue(
                                                    WatchEndpoint(videoId = item.id),
                                                    item.toMediaMetadata(),
                                                )
                                            } else {
                                                ListQueue(
                                                    title = item.title,
                                                    items = listOf(item.toMediaItem())
                                                )
                                            }
                                        )
                                    }
                                }

                                is AlbumItem -> {
                                    if (item.id.isSpotifyId()) {
                                        coroutineScope.launch {
                                            val searchQuery = "${item.title} ${item.artists?.firstOrNull()?.name.orEmpty()}"
                                            val ytResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_ALBUM).getOrNull()
                                            val ytAlbum = ytResult?.items?.firstOrNull { it is AlbumItem }
                                            if (ytAlbum != null) {
                                                navController.navigate("album/${ytAlbum.id}")
                                            }
                                        }
                                    } else {
                                        navController.navigate("album/${item.id}")
                                    }
                                }

                                is ArtistItem -> {
                                    if (item.id.isSpotifyId()) {
                                        coroutineScope.launch {
                                            val ytResult = YouTube.search(item.title, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                                            val ytArtist = ytResult?.items?.firstOrNull { it is ArtistItem }
                                            if (ytArtist != null) {
                                                navController.navigate("artist/${ytArtist.id}")
                                            }
                                        }
                                    } else {
                                        navController.navigate("artist/${item.id}")
                                    }
                                }

                                is PlaylistItem -> {
                                    if (item.id.isSpotifyId()) {
                                        navController.navigate("spotify_playlist/${item.id.stripSpotifyPrefix()}")
                                    } else {
                                        navController.navigate("online_playlist/${item.id}")
                                    }
                                }

                                is PodcastItem -> {
                                    navController.navigate("online_podcast/${item.id}")
                                }

                                is EpisodeItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                }
                            }
                        },
                        onLongClick = longClick,
                    ).animateItem(),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
    ) {
        if (tvShell) {
            /*
             * The Aura page header. The results screen used to redraw the field the search input
             * screen already owns — a second bar with its own back arrow and clear button, painted
             * straight under the first, which read as two search screens stacked on one page. Here
             * the query is the heading instead, and the header component brings the back arrow and
             * its focus ring. The mic stays on the input screen's header, so nothing here re-offers
             * it either.
             */
            TopAppBar(
                title = {
                    Text(
                        text = query.text.ifEmpty {
                            stringResource(
                                if (isSpotifySearch) R.string.search_spotify else R.string.search_yt_music,
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                        )
                    }
                },
            )
        } else {
            // Google-style SearchBar with Material 3 design
            AuraTextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                },
                placeholder = {
                    Text(
                        text = if (isSpotifySearch) stringResource(R.string.search_spotify) else stringResource(R.string.search_yt_music),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingIcon = {
                    if (query.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = TextFieldValue("")
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = {
                            onSearch(query.text)
                        },
                    ),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor =
                            if (pureBlack) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        unfocusedContainerColor =
                            if (pureBlack) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                fieldFocus = focusRequester,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        // A single-line field answers DOWN by moving the caret, which swallows the key
                        // before focus can leave the input. On TV spend it on what sits below instead.
                        .onPreviewKeyEvent { event ->
                            tvShell &&
                                event.type == KeyEventType.KeyDown &&
                                event.key == Key.DirectionDown &&
                                focusManager.moveFocus(FocusDirection.Down)
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                isSearchFocused = true
                            }
                        },
            )
        }

        // Filter chips always on top of any overlay so they remain clickable while
        // the suggestion overlay is visible (see #69).
        if (isSpotifySearch) {
            ChipsRow(
                chips = listOf(
                    null to stringResource(R.string.filter_all),
                    "track" to stringResource(R.string.filter_songs),
                    "album" to stringResource(R.string.filter_albums),
                    "artist" to stringResource(R.string.filter_artists),
                    "playlist" to stringResource(R.string.filter_playlists),
                ),
                currentValue = spotifyFilterValue,
                onValueUpdate = { newFilter ->
                    viewModel.spotifyFilter.value = newFilter
                    if (isSearchFocused) {
                        isSearchFocused = false
                        focusManager.clearFocus()
                    }
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val visibleChips =
                listOf(
                    null to stringResource(R.string.filter_all),
                    FILTER_SONG to stringResource(R.string.filter_songs),
                ).let { baseChips ->
                    if (!hideVideoSongs) {
                        baseChips + (FILTER_VIDEO to stringResource(R.string.filter_videos))
                    } else {
                        baseChips
                    }
                } +
                listOf(
                    FILTER_ALBUM to stringResource(R.string.filter_albums),
                    FILTER_ARTIST to stringResource(R.string.filter_artists),
                    FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                    FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                    FILTER_PODCAST to stringResource(R.string.filter_podcasts),
                    FILTER_EPISODE to stringResource(R.string.filter_episodes),
                    FILTER_PROFILE to stringResource(R.string.filter_profiles),
                )

            ChipsRow(
                chips = visibleChips,
                currentValue = searchFilter,
                onValueUpdate = {
                    if (viewModel.filter.value != it) {
                        viewModel.filter.value = it
                    }
                    if (isSearchFocused) {
                        isSearchFocused = false
                        focusManager.clearFocus()
                    }
                    coroutineScope.launch {
                        lazyListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Main content area below search bar and filters
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isShowingSummary) {
                        searchSummary?.summaries?.forEach { summary ->
                            item {
                                NavigationTitle(summary.title)
                            }

                            itemsIndexed(
                                items = summary.items,
                                key = { index, item -> "${summary.title}/${item.id}/$index" },
                                itemContent = { index, item -> ytItemContent(item) },
                            )
                        }

                        if (searchSummary?.summaries?.isEmpty() == true) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    } else {
                        items(
                            items = itemsPage?.items.orEmpty().distinctBy { it.id },
                            key = { "filtered_${it.id}" },
                            itemContent = ytItemContent,
                        )

                        if (itemsPage?.continuation != null) {
                            item(key = "loading") {
                                ShimmerHost {
                                    repeat(3) {
                                        ListItemPlaceHolder()
                                    }
                                }
                            }
                        }

                        if (itemsPage?.items?.isEmpty() == true) {
                            item {
                                EmptyPlaceholder(
                                    icon = R.drawable.search,
                                    text = stringResource(R.string.no_results_found),
                                )
                            }
                        }
                    }

                    if (isShowingSummary && searchSummary == null || !isShowingSummary && itemsPage == null) {
                        item {
                            ShimmerHost {
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(MiniPlayerHeight + MiniPlayerBottomSpacing + NavigationBarHeight))
                    }
                }
            }
            if (isSearchFocused) {
                OnlineSearchScreen(
                    query = query.text,
                    onQueryChange = { query = it },
                    onSearch = onSearch,
                    onDismiss = {
                        isSearchFocused = false
                        focusManager.clearFocus()
                    },
                    pureBlack = pureBlack,
                )
            }
            // Recognition is reachable from the search input screen's header, so on TV this floating
            // disc was only a duplicate of it — and one the page header already replaced.
            if (!tvShell) {
                HideOnScrollFAB(
                    lazyListState = lazyListState,
                    icon = R.drawable.mic,
                    onClick = { navController.navigate("recognition") },
                )
            }
        }
    }
}
