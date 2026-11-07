/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.aura.music.ui.component.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.GridItemSize
import com.aura.music.constants.GridItemsSizeKey
import com.aura.music.constants.GridThumbnailHeight
import com.aura.music.models.ReleaseSource
import com.aura.music.ui.component.ChipsRow
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.YouTubeGridItem
import com.aura.music.ui.component.shimmer.GridItemPlaceHolder
import com.aura.music.ui.component.shimmer.ShimmerHost
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.viewmodels.NewReleaseTab
import com.aura.music.viewmodels.NewReleaseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NewReleaseScreen(
    navController: NavController,
    viewModel: NewReleaseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val displayedReleases by viewModel.displayedReleases.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val followingCount by viewModel.followingCount.collectAsState()
    val discoverCount by viewModel.discoverCount.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val forYouLabel = stringResource(R.string.new_releases_for_you)
    val followingLabel = stringResource(R.string.new_releases_following)
    val discoverLabel = stringResource(R.string.new_releases_discover)

    val chips = listOf(
        NewReleaseTab.FOR_YOU to forYouLabel,
        NewReleaseTab.FOLLOWING to "$followingLabel (${followingCount.size})",
        NewReleaseTab.DISCOVER to "$discoverLabel (${discoverCount.size})",
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item(
            key = "filter_chips",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            ChipsRow(
                chips = chips,
                currentValue = selectedTab,
                onValueUpdate = { viewModel.selectTab(it) },
            )
        }

        items(
            items = displayedReleases.distinctBy { it.albumItem.id },
            key = { "newrelease_${it.albumItem.id}" },
        ) { releaseItem ->
            val album = releaseItem.albumItem
            YouTubeGridItem(
                item = album,
                isActive = mediaMetadata?.album?.id == album.id,
                isPlaying = isPlaying,
                fillMaxWidth = true,
                coroutineScope = coroutineScope,
                modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            if (releaseItem.source == ReleaseSource.SPOTIFY &&
                                releaseItem.spotifyAlbumId != null
                            ) {
                                navController.navigate("spotify_album/${releaseItem.spotifyAlbumId}")
                            } else {
                                navController.navigate("album/${album.id}")
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                YouTubeAlbumMenu(
                                    albumItem = album,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    ),
            )
        }

        if (isLoading) {
            items(8) {
                ShimmerHost {
                    GridItemPlaceHolder(fillMaxWidth = true)
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.new_release_albums)) },
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
}
