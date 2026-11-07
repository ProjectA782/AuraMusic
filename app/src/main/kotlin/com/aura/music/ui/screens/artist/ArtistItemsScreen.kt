/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import com.aura.music.ui.component.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aura.innertube.models.AlbumItem
import com.aura.innertube.models.ArtistItem
import com.aura.innertube.models.EpisodeItem
import com.aura.innertube.models.PlaylistItem
import com.aura.innertube.models.PodcastItem
import com.aura.innertube.models.SongItem
import com.aura.innertube.models.WatchEndpoint
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.GridItemSize
import com.aura.music.constants.GridItemsSizeKey
import com.aura.music.constants.GridThumbnailHeight
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.YouTubeGridItem
import com.aura.music.ui.component.YouTubeListItem
import com.aura.music.ui.component.shimmer.GridItemPlaceHolder
import com.aura.music.ui.component.shimmer.ListItemPlaceHolder
import com.aura.music.ui.component.shimmer.ShimmerHost
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.menu.YouTubeArtistMenu
import com.aura.music.ui.menu.YouTubePlaylistMenu
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.viewmodels.ArtistItemsViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    navController: NavController,
    viewModel: ArtistItemsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val title by viewModel.title.collectAsStateWithLifecycle()
    val itemsPage by viewModel.itemsPage.collectAsStateWithLifecycle()

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
    ) {
        // The shell gives this destination an exact page box, so a bar emitted after the list
        // becomes a sibling of it and paints over the rows. It is the page heading: first child,
        // pinned, with the list scrolling under it.

        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
        )

        // One branch, not two: while the page is loading the list below has nothing to show, and
        // the empty grid used to compose alongside the shimmer and take the weight the skeleton
        // needed to fill the page box.
        if (itemsPage == null) {
            ShimmerHost(
                modifier = Modifier.weight(1f),
            ) {
                repeat(8) {
                    ListItemPlaceHolder()
                }
            }
        } else if (itemsPage?.items?.firstOrNull() is SongItem) {
            // No top inset here: the heading is pinned above and the shell already keeps the page
            // box clear of the now-playing banner, so reserving it again opened a void under the bar.
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = itemsPage?.items.orEmpty().distinctBy { it.id },
                    key = { "artist_items_list_${it.id}" },
                ) { item ->
                    YouTubeListItem(
                        item = item,
                        isActive =
                            when (item) {
                                is SongItem -> mediaMetadata?.id == item.id
                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                else -> false
                            },
                        isPlaying = isPlaying,
                        trailingContent = {
                            IconButton(
                                onClick = {
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
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    when (item) {
                                        is SongItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                        item.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        }

                                        is AlbumItem -> {
                                            navController.navigate("album/${item.id}")
                                        }

                                        is ArtistItem -> {
                                            navController.navigate("artist/${item.id}")
                                        }

                                        is PlaylistItem -> {
                                            navController.navigate("online_playlist/${item.id}")
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
                                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                        item.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                    )
                }

                if (itemsPage?.continuation != null) {
                    item(key = "loading") {
                        ShimmerHost {
                            repeat(3) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = lazyGridState,
                modifier = Modifier.weight(1f),
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(
                    items = itemsPage?.items.orEmpty().distinctBy { it.id },
                    key = { "artist_items_grid_${it.id}" },
                ) { item ->
                    YouTubeGridItem(
                        item = item,
                        isActive =
                            when (item) {
                                is SongItem -> mediaMetadata?.id == item.id
                                is AlbumItem -> mediaMetadata?.album?.id == item.id
                                else -> false
                            },
                        isPlaying = isPlaying,
                        fillMaxWidth = true,
                        coroutineScope = coroutineScope,
                        modifier =
                            Modifier
                                .combinedClickable(
                                    onClick = {
                                        when (item) {
                                            is SongItem -> {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                        item.toMediaMetadata(),
                                                    ),
                                                )
                                            }

                                            is AlbumItem -> {
                                                navController.navigate("album/${item.id}")
                                            }

                                            is ArtistItem -> {
                                                navController.navigate("artist/${item.id}")
                                            }

                                            is PlaylistItem -> {
                                                navController.navigate("online_playlist/${item.id}")
                                            }

                                            is PodcastItem -> {
                                                navController.navigate("online_podcast/${item.id}")
                                            }

                                            is EpisodeItem -> {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                        item.toMediaMetadata(),
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                    onLongClick = {
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
                                    },
                                ).animateItem(),
                    )
                }

                if (itemsPage?.continuation != null) {
                    item(key = "loading") {
                        ShimmerHost(Modifier.animateItem()) {
                            GridItemPlaceHolder(fillMaxWidth = true)
                        }
                    }
                }
            }
        }

    }
}
