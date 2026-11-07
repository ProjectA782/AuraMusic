/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.aura.music.LocalNavController
import com.aura.innertube.models.AlbumItem
import com.aura.innertube.models.ArtistItem
import com.aura.innertube.models.EpisodeItem
import com.aura.innertube.models.PlaylistItem
import com.aura.innertube.models.PodcastItem
import com.aura.innertube.models.SongItem
import com.aura.music.LocalDatabase
import com.aura.music.LocalAuraTvShell
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.ui.aura.AuraGhostButton
import com.aura.music.ui.aura.AuraListRow
import com.aura.music.ui.aura.auraFocused
import com.aura.music.constants.AutoRadioQueueKey
import com.aura.music.constants.SuggestionItemHeight
import com.aura.music.extensions.toMediaItem
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.queues.ListQueue
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.YouTubeListItem
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.menu.YouTubeArtistMenu
import com.aura.music.ui.menu.YouTubePlaylistMenu
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.OnlineSearchSuggestionViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import androidx.navigation.NavController

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val coroutineScope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        snapshotFlow { query }.debounce(300L).collectLatest {
            viewModel.query.value = it
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Bottom).asPaddingValues(),
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        // Show parsed URL item at the top if present
        if (viewState.isUrlQuery && viewState.parsedUrlItem != null) {
            item(key = "parsed_url_header") {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.parsed_from_link),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item(key = "parsed_url_item") {
                val item = viewState.parsedUrlItem!!
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
                        IconButton(
                            onClick = {
                                menuState.show {
                                    when (item) {
                                        is SongItem -> {
                                            YouTubeSongMenu(
                                                song = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is AlbumItem -> {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is ArtistItem -> {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PlaylistItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PodcastItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item.asPlaylistItem(),
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is EpisodeItem -> {
                                            YouTubeSongMenu(
                                                song = item.asSongItem(),
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
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
                            .combinedClickable(
                                onClick = {
                                    when (item) {
                                        is SongItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    if (autoRadioQueue) {
                                                        YouTubeQueue.radio(item.toMediaMetadata())
                                                    } else {
                                                        ListQueue(
                                                            title = item.title,
                                                            items = listOf(item.toMediaItem())
                                                        )
                                                    }
                                                )
                                                onDismiss()
                                            }
                                        }

                                        is AlbumItem -> {
                                            navController.navigate("album/${item.id}")
                                            onDismiss()
                                        }

                                        is ArtistItem -> {
                                            navController.navigate("artist/${item.id}")
                                            onDismiss()
                                        }

                                        is PlaylistItem -> {
                                            navController.navigate("online_playlist/${item.id}")
                                            onDismiss()
                                        }

                                        is PodcastItem -> {
                                            navController.navigate("online_podcast/${item.id}")
                                            onDismiss()
                                        }

                                        is EpisodeItem -> {
                                            if (item.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.radio(item.toMediaMetadata()),
                                                )
                                                onDismiss()
                                            }
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
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is AlbumItem -> {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is ArtistItem -> {
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is PlaylistItem -> {
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is PodcastItem -> {
                                                YouTubePlaylistMenu(
                                                    playlist = item.asPlaylistItem(),
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }

                                            is EpisodeItem -> {
                                                YouTubeSongMenu(
                                                    song = item.asSongItem(),
                                                    onDismiss = {
                                                        menuState.dismiss()
                                                        onDismiss()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                },
                            ).background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                            .animateItem(),
                )
            }

            item(key = "parsed_url_divider") {
                HorizontalDivider(
                    modifier =
                        Modifier
                            .padding(vertical = 8.dp)
                            .animateItem(),
                )
            }
        }

        itemsIndexed(viewState.history, key = { _, history -> "history_${history.query}" }) { index, history ->
            SuggestionItem(
                query = history.query,
                online = false,
                onClick = {
                    onSearch(history.query)
                    onDismiss()
                },
                onDelete = {
                    database.query {
                        delete(history)
                    }
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(history.query, TextRange(history.query.length)))
                },
                anchor = index == 0,
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }

        itemsIndexed(viewState.suggestions, key = { _, query -> "suggestion_$query" }) { index, query ->
            SuggestionItem(
                query = query,
                online = true,
                onClick = {
                    onSearch(query)
                    onDismiss()
                },
                onFillTextField = {
                    onQueryChange(TextFieldValue(query, TextRange(query.length)))
                },
                anchor = index == 0 && viewState.history.isEmpty(),
                modifier = Modifier.animateItem(),
                pureBlack = pureBlack,
            )
        }

        if (viewState.items.isNotEmpty() && viewState.history.size + viewState.suggestions.size > 0) {
            item(key = "search_divider") {
                HorizontalDivider(
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "search_divider_spacer") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        items(viewState.items, key = { "item_${it.id}" }) { item ->
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
                    IconButton(
                        onClick = {
                            menuState.show {
                                when (item) {
                                    is SongItem -> {
                                        YouTubeSongMenu(
                                            song = item,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is AlbumItem -> {
                                        YouTubeAlbumMenu(
                                            albumItem = item,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is ArtistItem -> {
                                        YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is PlaylistItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is PodcastItem -> {
                                        YouTubePlaylistMenu(
                                            playlist = item.asPlaylistItem(),
                                            coroutineScope = coroutineScope,
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
                                        )
                                    }

                                    is EpisodeItem -> {
                                        YouTubeSongMenu(
                                            song = item.asSongItem(),
                                            onDismiss = {
                                                menuState.dismiss()
                                                onDismiss()
                                            },
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
                        .auraFocused(corner = 16.dp, grow = false)
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is SongItem -> {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                if (autoRadioQueue) {
                                                    YouTubeQueue.radio(item.toMediaMetadata())
                                                } else {
                                                    ListQueue(
                                                        title = item.title,
                                                        items = listOf(item.toMediaItem())
                                                    )
                                                }
                                            )
                                            onDismiss()
                                        }
                                    }

                                    is AlbumItem -> {
                                        navController.navigate("album/${item.id}")
                                        onDismiss()
                                    }

                                    is ArtistItem -> {
                                        navController.navigate("artist/${item.id}")
                                        onDismiss()
                                    }

                                    is PlaylistItem -> {
                                        navController.navigate("online_playlist/${item.id}")
                                        onDismiss()
                                    }

                                    is PodcastItem -> {
                                        navController.navigate("online_podcast/${item.id}")
                                        onDismiss()
                                    }

                                    is EpisodeItem -> {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(item.toMediaMetadata()),
                                            )
                                            onDismiss()
                                        }
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
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is AlbumItem -> {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is ArtistItem -> {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PlaylistItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is PodcastItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item.asPlaylistItem(),
                                                coroutineScope = coroutineScope,
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }

                                        is EpisodeItem -> {
                                            YouTubeSongMenu(
                                                song = item.asSongItem(),
                                                onDismiss = {
                                                    menuState.dismiss()
                                                    onDismiss()
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        ).background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                        .animateItem(),
            )
        }
    }
}

@Composable
fun SuggestionItem(
    modifier: Modifier = Modifier,
    query: String,
    online: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    onFillTextField: () -> Unit,
    pureBlack: Boolean,
    anchor: Boolean = false,
) {
    if (LocalAuraTvShell.current) {
        /*
         * The reference has no icon-and-text list row: a pickable line is the same row the library
         * and the settings pages use, so the history reads as part of the shell. The two trailing
         * Material icon buttons drew no selector, which is why a walk off the field appeared to land
         * on nothing — they become ring-wearing ghost buttons here, and they stand out of the page's
         * landing spots so DOWN from the field still aims at the query, not at its delete.
         */
        AuraListRow(
            title = query,
            icon = if (online) R.drawable.search else R.drawable.history,
            best = anchor,
            onClick = onClick,
            trailing = {
                if (!online) {
                    AuraGhostButton(
                        icon = R.drawable.close,
                        contentDescription = null,
                        onClick = onDelete,
                        boxPx = 48,
                        glyphPx = 22,
                        claim = false,
                        shadow = false,
                    )
                }
                AuraGhostButton(
                    icon = R.drawable.arrow_top_left,
                    contentDescription = null,
                    onClick = onFillTextField,
                    boxPx = 48,
                    glyphPx = 22,
                    claim = false,
                    shadow = false,
                )
            },
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .height(SuggestionItemHeight)
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Icon(
            painterResource(if (online) R.drawable.search else R.drawable.history),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 16.dp).alpha(0.5f),
        )

        Text(
            text = query,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!online) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.alpha(0.5f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                )
            }
        }

        IconButton(
            onClick = onFillTextField,
            modifier = Modifier.alpha(0.5f),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_top_left),
                contentDescription = null,
            )
        }
    }
}
