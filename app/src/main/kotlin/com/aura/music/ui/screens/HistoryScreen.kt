/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aura.innertube.utils.parseCookieString
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.HistorySource
import com.aura.music.constants.InnerTubeCookieKey
import com.aura.music.extensions.metadata
import com.aura.music.extensions.toMediaItem
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.queues.ListQueue
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.aura.AuraChip
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraSectionHeader
import com.aura.music.ui.aura.AuraTrackRow
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraAnchorHero
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.aura.auraTrackRows
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.menu.SelectionMediaMetadataMenu
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.ui.theme.TvRowNav
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.DateAgo
import com.aura.music.viewmodels.HistoryViewModel
import java.time.format.DateTimeFormatter

/**
 * The listening history, one shelf per day bucket (local) or per remote section: a heading row, then
 * its tracks, then the next heading. Stats is the shape this copies - the same pinned page header,
 * the same band of chips that picks local versus remote, and an actions row in place of the floating
 * button a remote could never reach.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver<MutableList<Long>, Long>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf() }
    val onExitSelectionMode: () -> Unit = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    val historySource by viewModel.historySource.collectAsStateWithLifecycle()
    val historyPage by viewModel.historyPage.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    val showRemote = historySource == HistorySource.REMOTE && isLoggedIn

    @Composable
    fun dateAgoToString(dateAgo: DateAgo): String =
        when (dateAgo) {
            DateAgo.Today -> stringResource(R.string.today)
            DateAgo.Yesterday -> stringResource(R.string.yesterday)
            DateAgo.ThisWeek -> stringResource(R.string.this_week)
            DateAgo.LastWeek -> stringResource(R.string.last_week)
            is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
        }

    val allEvents =
        remember(events) {
            events.values.flatten()
        }

    LaunchedEffect(allEvents) {
        selection.fastForEachReversed { eventId ->
            if (allEvents.find { it.event.id == eventId } == null) {
                selection.remove(eventId)
            }
        }
    }

    // Both sources end up in the same row shape, so the shelf loop below does not care which one is
    // showing. A remote id carries the section and the position because one song can appear twice in
    // one shelf, and a repeated key collapses the list.
    val groups: List<HistoryGroup> =
        if (showRemote) {
            historyPage
                ?.sections
                ?.filter { it.songs.isNotEmpty() }
                ?.map { section ->
                    HistoryGroup(
                        title = section.title,
                        rows =
                            section.songs.mapIndexed { index, song ->
                                AuraTrackRow(
                                    id = "${section.title}_${index}_${song.id}",
                                    title = song.title,
                                    subtitle = song.artists.joinToString(", ") { it.name },
                                    image = song.thumbnail,
                                    durationText = song.duration?.let { makeTimeString(it * 1000L) },
                                    badges = if (song.explicit) listOf(R.drawable.explicit) else emptyList(),
                                    active = song.id == mediaMetadata?.id,
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue.radio(song.toMediaMetadata()),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song,
                                                onDismiss = menuState::dismiss,
                                                onHistoryRemoved = viewModel::fetchRemoteHistory,
                                            )
                                        }
                                    },
                                )
                            },
                    )
                }
                .orEmpty()
        } else {
            events.map { (dateAgo, dateEvents) ->
                val dateTitle = dateAgoToString(dateAgo)
                HistoryGroup(
                    title = dateTitle,
                    rows =
                        dateEvents.mapIndexed { index, event ->
                            AuraTrackRow(
                                id = event.event.id.toString(),
                                title = event.song.title,
                                subtitle = event.song.artists.joinToString(", ") { it.name },
                                image = event.song.thumbnailUrl,
                                durationText = makeTimeString(event.song.song.duration * 1000L),
                                badges =
                                    if (event.song.song.explicit) {
                                        listOf(R.drawable.explicit)
                                    } else {
                                        emptyList()
                                    },
                                // Selection is keyed on the play event, which only the local table
                                // carries, so remote rows never light as selected.
                                active =
                                    if (inSelectMode) {
                                        event.event.id in selection
                                    } else {
                                        event.song.id == mediaMetadata?.id
                                    },
                                onClick = {
                                    if (inSelectMode) {
                                        if (event.event.id in selection) {
                                            selection.remove(event.event.id)
                                        } else {
                                            selection.add(event.event.id)
                                        }
                                    } else if (event.song.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = dateTitle,
                                                items = dateEvents.map { it.song.toMediaItem() },
                                                startIndex = index,
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        SongMenu(
                                            originalSong = event.song,
                                            event = event.event,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            )
                        },
                )
            }
        }

    var nextRow = 0
    val toggleRow = nextRow++
    val hasContent = groups.any { it.rows.isNotEmpty() }
    val actionsRow = if (hasContent) nextRow++ else -1
    val spans =
        groups.map { group ->
            val titleRow = nextRow++
            val tracksRowStart = nextRow
            nextRow += group.rows.size
            HistoryGroupRows(titleRow = titleRow, tracksRowStart = tracksRowStart)
        }
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val pageAnchor = remember { FocusRequester() }
    auraAnchorHero(pageAnchor)
    val pagePad = auraPagePadding()

    val historyTitle = stringResource(R.string.history)

    // The same queue the floating button built, now a stop on the chain. Remote shuffles the sections
    // already loaded; local shuffles every event on the page.
    val shuffleAll: () -> Unit = {
        val items =
            if (showRemote) {
                historyPage?.sections?.flatMap { it.songs }?.map { it.toMediaItem() }.orEmpty()
            } else {
                allEvents.map { it.song.toMediaItem() }
            }
        if (items.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = historyTitle,
                    items = items.shuffled(),
                ),
            )
        }
    }

    val selectedSongs =
        selection.mapNotNull { eventId ->
            allEvents
                .find { it.event.id == eventId }
                ?.song
                ?.toMediaItem()
                ?.metadata
        }

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
        // Same page box as Stats: a bar emitted after the list is a sibling of it and paints over the
        // rows, so the History title landed in the middle of the viewport. It goes first and stays
        // pinned, and the list carries no top inset of its own because the shell already moves the
        // page box below the now-playing banner.
        TopAppBar(
            title = {
                if (inSelectMode) {
                    Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
                } else {
                    Text(historyTitle)
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (inSelectMode) {
                    IconButton(
                        onClick = onExitSelectionMode,
                        onLongClick = onExitSelectionMode,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
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
            item(key = "history_sources") {
                HistoryChipRow(
                    chips =
                        if (isLoggedIn) {
                            listOf(
                                HistorySource.LOCAL to stringResource(R.string.local_history),
                                HistorySource.REMOTE to stringResource(R.string.remote_history),
                            )
                        } else {
                            listOf(HistorySource.LOCAL to stringResource(R.string.local_history))
                        },
                    selected = historySource,
                    onSelect = {
                        onExitSelectionMode()
                        viewModel.historySource.value = it
                        if (it == HistorySource.REMOTE) {
                            viewModel.fetchRemoteHistory()
                        }
                    },
                    rowNav = tvRowNav,
                    rowIndex = toggleRow,
                    rowCount = rowCount,
                    itemKey = "history_sources",
                    anchor = pageAnchor,
                )
            }

            if (actionsRow >= 0) {
                item(key = "history_actions") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.apx()),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    tvRowNav.row(
                                        index = actionsRow,
                                        itemKey = "history_actions",
                                        rowCount = rowCount,
                                    ),
                                )
                                .focusGroup(),
                    ) {
                        var col = 0
                        AuraPillButton(
                            label = stringResource(R.string.shuffle),
                            icon = R.drawable.aura_ic_shuffle,
                            filled = true,
                            onClick = shuffleAll,
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                        if (!showRemote) {
                            if (inSelectMode) {
                                AuraPillButton(
                                    label = stringResource(R.string.select),
                                    icon = R.drawable.aura_ic_check,
                                    filled = true,
                                    onClick = {
                                        selection.clear()
                                        selection.addAll(allEvents.map { it.event.id })
                                    },
                                    modifier = tvRowNav.tile(actionsRow, col++),
                                )
                                AuraPillButton(
                                    label = "${selection.size}",
                                    icon = R.drawable.aura_ic_more_horizontal,
                                    filled = selection.isNotEmpty(),
                                    onClick = {
                                        if (selectedSongs.isNotEmpty()) {
                                            menuState.show {
                                                SelectionMediaMetadataMenu(
                                                    songSelection = selectedSongs,
                                                    onDismiss = menuState::dismiss,
                                                    clearAction = onExitSelectionMode,
                                                    currentItems = emptyList(),
                                                )
                                            }
                                        }
                                    },
                                    modifier = tvRowNav.tile(actionsRow, col++),
                                )
                                AuraPillButton(
                                    label = stringResource(R.string.cancel),
                                    onClick = onExitSelectionMode,
                                    modifier = tvRowNav.tile(actionsRow, col++),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                                AuraPillButton(
                                    label = stringResource(R.string.select),
                                    onClick = { inSelectMode = true },
                                    modifier = tvRowNav.tile(actionsRow, col++),
                                )
                            }
                        }
                    }
                }
            }

            groups.forEachIndexed { groupIndex, group ->
                val span = spans[groupIndex]
                if (group.rows.isNotEmpty()) {
                    item(key = "history_title_${span.titleRow}") {
                        HistorySectionHeader(
                            title = group.title,
                            rowNav = tvRowNav,
                            rowIndex = span.titleRow,
                            rowCount = rowCount,
                            itemKey = "history_title_${span.titleRow}",
                        )
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
}

private data class HistoryGroup(
    val title: String,
    val rows: List<AuraTrackRow>,
)

/** Which stop a shelf heading takes, and where that shelf's tracks start. */
private data class HistoryGroupRows(
    val titleRow: Int,
    val tracksRowStart: Int,
)

/**
 * Local versus remote. The first chip carries the page anchor, so the rail and BACK both land on the
 * band that decides what the rest of the page shows; a press inside the band goes to the row nav
 * rather than to Compose's tree-order search, which would land a row behind.
 */
@Composable
private fun HistoryChipRow(
    chips: List<Pair<HistorySource, String>>,
    selected: HistorySource,
    onSelect: (HistorySource) -> Unit,
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
 * A day bucket heading is a stop in its own right: with nothing registered on the heading row, a
 * press aimed at it finds no requester and the walk out of the tracks below stops dead.
 */
@Composable
private fun HistorySectionHeader(
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
