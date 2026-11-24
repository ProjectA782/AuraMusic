/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.ui.aura.AuraChip
import com.aura.music.ui.aura.AuraPick
import com.aura.music.ui.aura.AuraPickRow
import com.aura.music.ui.aura.AuraSectionHeader
import com.aura.music.ui.aura.AuraTrackRow
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraAnchorHero
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.aura.auraTrackRows
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.menu.YouTubeAlbumMenu
import com.aura.music.ui.menu.YouTubeArtistMenu
import com.aura.music.ui.menu.YouTubePlaylistMenu
import com.aura.music.ui.theme.TvRowNav
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.ui.utils.backToMain
import com.aura.music.viewmodels.AccountContentType
import com.aura.music.viewmodels.AccountViewModel

/**
 * The YouTube account page: one band of chips that picks what is listed, then either a shelf of
 * cover tiles (playlists, albums, artists) or the podcast shelves, which are rows rather than covers
 * because their titles are long. Everything was a stretch-mode grid whose page header was emitted
 * after the list, so it painted in the middle of the viewport; it is now the pinned header every
 * other Aura page uses, and every band is a stop in the vertical chain.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    navController: NavController,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val sePlaylist by viewModel.sePlaylist.collectAsStateWithLifecycle()
    val rdpnPlaylist by viewModel.rdpnPlaylist.collectAsStateWithLifecycle()
    val podcastPlaylists by viewModel.podcastPlaylists.collectAsStateWithLifecycle()
    val podcastChannels by viewModel.podcastChannels.collectAsStateWithLifecycle()
    val selectedContentType by viewModel.selectedContentType.collectAsStateWithLifecycle()

    val isPodcasts = selectedContentType == AccountContentType.PODCASTS

    // Covers for the three subscription tabs. A tile keeps the same long-press menu the grid item
    // had, so nothing about what a tile can do changed on the way to the shelf.
    val picks: List<AuraPick> =
        when (selectedContentType) {
            AccountContentType.PLAYLISTS ->
                playlists
                    ?.distinctBy { it.id }
                    ?.map { item ->
                        AuraPick(
                            id = item.id,
                            title = item.title,
                            subtitle = item.author?.name.orEmpty(),
                            image = item.thumbnail,
                            onClick = { navController.navigate("online_playlist/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubePlaylistMenu(
                                        playlist = item,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                    }
                    .orEmpty()

            AccountContentType.ALBUMS ->
                albums
                    ?.distinctBy { it.id }
                    ?.map { item ->
                        AuraPick(
                            id = item.id,
                            title = item.title,
                            subtitle = item.artists?.joinToString(", ") { it.name }.orEmpty(),
                            image = item.thumbnail,
                            onClick = { navController.navigate("album/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeAlbumMenu(
                                        albumItem = item,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                    }
                    .orEmpty()

            AccountContentType.ARTISTS ->
                artists
                    ?.distinctBy { it.id }
                    ?.map { item ->
                        AuraPick(
                            id = item.id,
                            title = item.title,
                            subtitle = "",
                            image = item.thumbnail,
                            onClick = { navController.navigate("artist/${item.id}") },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeArtistMenu(
                                        artist = item,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        )
                    }
                    .orEmpty()

            AccountContentType.PODCASTS -> emptyList()
        }

    // Podcasts keep the row shape: the two auto playlists have no heading of their own, then the
    // subscribed shows and the channels each get one. A null heading still occupies its rows, it just
    // does not emit a stop above them.
    val groups: List<AccountGroup> =
        if (isPodcasts) {
            val auto =
                listOfNotNull(
                    rdpnPlaylist?.let { playlist ->
                        AuraTrackRow(
                            id = "aura_account_rdpn",
                            title = stringResource(R.string.new_episodes),
                            subtitle = stringResource(R.string.auto_playlist),
                            image = playlist.thumbnail,
                            onClick = { navController.navigate("online_playlist/RDPN") },
                        )
                    },
                    sePlaylist?.let { playlist ->
                        AuraTrackRow(
                            id = "aura_account_se",
                            title = stringResource(R.string.episodes_for_later),
                            subtitle = stringResource(R.string.auto_playlist),
                            image = playlist.thumbnail,
                            onClick = { navController.navigate("online_playlist/SE") },
                        )
                    },
                )
            listOfNotNull(
                auto.takeIf { it.isNotEmpty() }?.let { AccountGroup(title = null, rows = it) },
                podcastPlaylists
                    .takeIf { it.isNotEmpty() }
                    ?.map { podcast ->
                        AuraTrackRow(
                            id = "aura_account_podcast_${podcast.id}",
                            title = podcast.title,
                            subtitle = podcast.author.orEmpty(),
                            image = podcast.thumbnailUrl,
                            onClick = { navController.navigate("online_podcast/${podcast.id}") },
                        )
                    }
                    ?.let {
                        AccountGroup(
                            title = stringResource(R.string.filter_podcasts),
                            rows = it,
                        )
                    },
                podcastChannels
                    .takeIf { it.isNotEmpty() }
                    ?.map { channel ->
                        AuraTrackRow(
                            id = "aura_account_channel_${channel.id}",
                            title = channel.title,
                            subtitle = "",
                            image = channel.thumbnail,
                            onClick = { navController.navigate("artist/${channel.id}") },
                        )
                    }
                    ?.let {
                        AccountGroup(
                            title = stringResource(R.string.filter_channels),
                            rows = it,
                        )
                    },
            )
        } else {
            emptyList()
        }

    var nextRow = 0
    val toggleRow = nextRow++
    val picksRow = if (picks.isNotEmpty()) nextRow++ else -1
    val spans =
        groups.map { group ->
            val titleRow = if (group.title == null) -1 else nextRow++
            val tracksRowStart = nextRow
            nextRow += group.rows.size
            AccountGroupRows(titleRow = titleRow, tracksRowStart = tracksRowStart)
        }
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val pageAnchor = remember { FocusRequester() }
    auraAnchorHero(pageAnchor)
    val pagePad = auraPagePadding()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
    ) {
        // First child and pinned, as on Stats and History: the header was the last sibling of the
        // grid, which put the Account title over the middle row of tiles.
        TopAppBar(
            title = {
                Text(stringResource(R.string.account))
            },
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

        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentPadding =
                PaddingValues(
                    start = pagePad,
                    end = pagePad,
                    bottom = 132.apx(),
                ),
            verticalArrangement = Arrangement.spacedBy(20.apx()),
        ) {
            item(key = "account_content_types") {
                AccountChipRow(
                    chips =
                        listOf(
                            AccountContentType.PLAYLISTS to stringResource(R.string.filter_playlists),
                            AccountContentType.ALBUMS to stringResource(R.string.filter_albums),
                            AccountContentType.ARTISTS to stringResource(R.string.filter_artists),
                            AccountContentType.PODCASTS to stringResource(R.string.filter_podcasts),
                        ),
                    selected = selectedContentType,
                    onSelect = viewModel::setSelectedContentType,
                    rowNav = tvRowNav,
                    rowIndex = toggleRow,
                    rowCount = rowCount,
                    itemKey = "account_content_types",
                    anchor = pageAnchor,
                )
            }

            if (picksRow >= 0) {
                item(key = "account_picks") {
                    AuraPickRow(
                        picks = picks,
                        rowNav = tvRowNav,
                        rowIndex = picksRow,
                        rowCount = rowCount,
                        itemKey = "account_picks",
                    )
                }
            }

            groups.forEachIndexed { groupIndex, group ->
                val span = spans[groupIndex]
                val title = group.title
                if (title != null && span.titleRow >= 0) {
                    item(key = "account_title_${span.titleRow}") {
                        AccountSectionHeader(
                            title = title,
                            rowNav = tvRowNav,
                            rowIndex = span.titleRow,
                            rowCount = rowCount,
                            itemKey = "account_title_${span.titleRow}",
                        )
                    }
                }
                auraTrackRows(
                    rows = group.rows,
                    rowNav = tvRowNav,
                    rowStart = span.tracksRowStart,
                    rowCount = rowCount,
                )
            }
        }
    }
}

private data class AccountGroup(
    val title: String?,
    val rows: List<AuraTrackRow>,
)

/** Which stop a shelf heading takes, and where that shelf's rows start. */
private data class AccountGroupRows(
    val titleRow: Int,
    val tracksRowStart: Int,
)

/**
 * Playlists, albums, artists, podcasts. The first chip carries the page anchor so the rail and BACK
 * both land on the band that decides what the rest of the page shows; a press inside the band goes to
 * the row nav rather than to Compose's tree-order search, which would land a row behind.
 */
@Composable
private fun AccountChipRow(
    chips: List<Pair<AccountContentType, String>>,
    selected: AccountContentType,
    onSelect: (AccountContentType) -> Unit,
    rowNav: TvRowNav,
    rowIndex: Int,
    rowCount: Int,
    itemKey: String,
    anchor: FocusRequester? = null,
) {
    val ownFocus = remember(chips.size, rowIndex) { List(chips.size) { FocusRequester() } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(20.apx()),
        contentPadding = PaddingValues(16.apx()),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(rowNav.row(index = rowIndex, itemKey = itemKey, rowCount = rowCount))
                .focusGroup(),
    ) {
        itemsIndexed(chips, key = { _, chip -> "${itemKey}_${chip.second}" }) { col, chip ->
            val requester = if (col == 0 && anchor != null) anchor else ownFocus[col]
            AuraChip(
                label = chip.second,
                selected = chip.first == selected,
                onClick = { onSelect(chip.first) },
                focusRequester = requester,
                modifier = rowNav.stop(rowIndex, col, requester, best = col == 0 && anchor != null),
            )
        }
    }
}

/**
 * A heading is a stop in its own right: with nothing registered on the heading row, a press aimed at
 * it finds no requester and the walk out of the rows below stops dead.
 */
@Composable
private fun AccountSectionHeader(
    title: String,
    rowNav: TvRowNav,
    rowIndex: Int,
    rowCount: Int,
    itemKey: String,
) {
    AuraSectionHeader(
        title = title,
        modifier =
            Modifier
                .padding(top = 28.apx())
                .then(rowNav.row(index = rowIndex, itemKey = itemKey, rowCount = rowCount))
                .focusGroup()
                .then(rowNav.tile(rowIndex, 0)),
    )
}
