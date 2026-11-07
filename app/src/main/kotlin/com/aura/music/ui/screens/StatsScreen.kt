/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aura.innertube.models.WatchEndpoint
import com.aura.innertube.utils.parseCookieString
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.InnerTubeCookieKey
import com.aura.music.constants.StatPeriod
import com.aura.music.db.entities.Playlist
import com.aura.music.extensions.toMediaItem
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.queues.ListQueue
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.ui.aura.AuraChip
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPick
import com.aura.music.ui.aura.AuraPickRow
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraSectionHeader
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.AuraTrackRow
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraAnchorHero
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.aura.auraTrackRows
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.TimeTransfer
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.menu.AlbumMenu
import com.aura.music.ui.menu.ArtistMenu
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.theme.TvRowNav
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.joinByBullet
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.StatsViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Every band of this page is a shelf of the same shape as the ones on Home, Artist and Album: one
 * row that owns its up/down presses through [TvRowNav] and holds at least one focusable stop. A row
 * counted but not on the page strands the whole chain, so the empty bands are skipped rather than
 * emitted, and the song list is capped - uncapped it pushed the artist and album shelves so far
 * below the fold that no composed stop was left to aim at, and DOWN simply stopped answering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()
    val indexChips by viewModel.indexChips.collectAsStateWithLifecycle()
    val librarySongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val statsSongs by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val statsArtists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val statsAlbums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()
    val weeklyMostPlaylist by viewModel.weeklyMostPlaylist.collectAsStateWithLifecycle()
    val monthlyMostPlaylist by viewModel.monthlyMostPlaylist.collectAsStateWithLifecycle()
    val recapPlaylists by viewModel.recapPlaylists.collectAsStateWithLifecycle()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }
    val periodPlaylists = listOfNotNull(weeklyMostPlaylist, monthlyMostPlaylist)
    val visiblePlaylists: List<Playlist> =
        remember(periodPlaylists, recapPlaylists, isLoggedIn) {
            if (isLoggedIn) {
                (periodPlaylists + recapPlaylists).distinctBy { it.id }
            } else {
                periodPlaylists
            }
        }

    var showAllSongs by rememberSaveable { mutableStateOf(false) }
    val shownSongs =
        remember(statsSongs, showAllSongs) {
            if (showAllSongs) statsSongs else statsSongs.take(StatsSongLimit)
        }
    // The stats rows carry play counts but no entities: the queue and the long-press menu are built
    // from the songs behind them.
    val songsById = remember(librarySongs) { librarySongs.associateBy { it.song.id } }
    val shownQueue = remember(shownSongs, songsById) { shownSongs.mapNotNull { songsById[it.id] } }

    val trackRows =
        shownSongs.mapIndexed { index, statsSong ->
            val librarySong = songsById[statsSong.id]
            AuraTrackRow(
                id = statsSong.id,
                label = "${index + 1}",
                title = statsSong.title,
                subtitle =
                    joinByBullet(
                        context.resources.getQuantityString(
                            R.plurals.n_time,
                            statsSong.songCountListened,
                            statsSong.songCountListened,
                        ),
                        makeTimeString(statsSong.timeListened),
                    ),
                image = statsSong.thumbnailUrl,
                active = statsSong.id == mediaMetadata?.id,
                onClick = {
                    if (statsSong.id == mediaMetadata?.id) {
                        playerConnection.togglePlayPause()
                    } else if (librarySong != null) {
                        playerConnection.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(statsSong.id),
                                preloadItem = librarySong.toMediaMetadata(),
                            ),
                        )
                    }
                },
                onLongClick = librarySong?.let { target ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = target,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                },
            )
        }

    val playlistPicks =
        visiblePlaylists.map { playlist ->
            AuraPick(
                id = playlist.id,
                title = playlist.playlist.name,
                subtitle =
                    context.resources.getQuantityString(
                        R.plurals.n_song,
                        playlist.songCount,
                        playlist.songCount,
                    ),
                image = playlist.thumbnails.firstOrNull(),
                onClick = { navController.navigate("local_playlist/${playlist.id}") },
            )
        }

    val artistPicks =
        statsArtists.mapIndexed { index, artist ->
            AuraPick(
                id = artist.id,
                title = "${index + 1}. ${artist.artist.name}",
                subtitle =
                    joinByBullet(
                        context.resources.getQuantityString(
                            R.plurals.n_time,
                            artist.songCount,
                            artist.songCount,
                        ),
                        makeTimeString(artist.timeListened?.toLong()),
                    ),
                image = artist.artist.thumbnailUrl,
                onClick = { navController.navigate("artist/${artist.id}") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        ArtistMenu(
                            originalArtist = artist,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }

    val albumPicks =
        statsAlbums.mapIndexed { index, album ->
            AuraPick(
                id = album.id,
                title = "${index + 1}. ${album.album.title}",
                subtitle =
                    joinByBullet(
                        context.resources.getQuantityString(
                            R.plurals.n_time,
                            album.songCountListened ?: 0,
                            album.songCountListened ?: 0,
                        ),
                        makeTimeString(album.timeListened),
                    ),
                image = album.album.thumbnailUrl,
                onClick = { navController.navigate("album/${album.id}") },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        AlbumMenu(
                            originalAlbum = album,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
            )
        }

    val currentDate = LocalDateTime.now()
    val weeklyDates: List<Pair<Int, String>> =
        firstEvent?.event?.timestamp?.let { startedAt ->
            generateSequence(currentDate) { it.minusWeeks(1) }
                .takeWhile { it.isAfter(startedAt.minusWeeks(1)) }
                .mapIndexed { index, date ->
                    val endDate = date.plusWeeks(1).minusDays(1).coerceAtMost(currentDate)
                    val formatter = DateTimeFormatter.ofPattern("dd MMM")
                    val startDateFormatted = formatter.format(date)
                    val endDateFormatted = formatter.format(endDate)
                    val text =
                        when {
                            date.year != currentDate.year ->
                                "$startDateFormatted, ${date.year} - $endDateFormatted, ${endDate.year}"

                            date.month != endDate.month -> "$startDateFormatted - $endDateFormatted"
                            else -> "${date.dayOfMonth} - $endDateFormatted"
                        }
                    index to text
                }.toList()
        } ?: emptyList()

    val monthlyDates: List<Pair<Int, String>> =
        firstEvent?.event?.timestamp?.let { startedAt ->
            generateSequence(currentDate.plusMonths(1).withDayOfMonth(1).minusDays(1)) {
                it.minusMonths(1)
            }.takeWhile { it.isAfter(startedAt.withDayOfMonth(1)) }
                .mapIndexed { index, date ->
                    val formatted = DateTimeFormatter.ofPattern("MMM").format(date)
                    index to
                        if (date.year != currentDate.year) {
                            "$formatted ${date.year}"
                        } else {
                            formatted
                        }
                }.toList()
        } ?: emptyList()

    val yearlyDates: List<Pair<Int, String>> =
        firstEvent?.event?.timestamp?.let { startedAt ->
            generateSequence(currentDate.plusYears(1).withDayOfYear(1).minusDays(1)) {
                it.minusYears(1)
            }.takeWhile { it.isAfter(startedAt) }
                .mapIndexed { index, date -> index to "${date.year}" }
                .toList()
        } ?: emptyList()

    val modeChips =
        listOf(
            OptionStats.CONTINUOUS to stringResource(R.string.continuous),
            OptionStats.WEEKS to stringResource(R.string.weeks),
            OptionStats.MONTHS to stringResource(R.string.months),
            OptionStats.YEARS to stringResource(R.string.years),
        )
    val periodChips: List<Pair<Int, String>> =
        when (selectedOption) {
            OptionStats.WEEKS -> weeklyDates
            OptionStats.MONTHS -> monthlyDates
            OptionStats.YEARS -> yearlyDates
            OptionStats.CONTINUOUS ->
                listOf(
                    StatPeriod.WEEK_1.ordinal to
                        context.resources.getQuantityString(R.plurals.n_week, 1, 1),
                    StatPeriod.MONTH_1.ordinal to
                        context.resources.getQuantityString(R.plurals.n_month, 1, 1),
                    StatPeriod.MONTH_3.ordinal to
                        context.resources.getQuantityString(R.plurals.n_month, 3, 3),
                    StatPeriod.MONTH_6.ordinal to
                        context.resources.getQuantityString(R.plurals.n_month, 6, 6),
                    StatPeriod.YEAR_1.ordinal to
                        context.resources.getQuantityString(R.plurals.n_year, 1, 1),
                    StatPeriod.ALL.ordinal to stringResource(R.string.filter_all),
                )
        }

    var nextRow = 0
    val modeRow = nextRow++
    val periodRow = if (periodChips.isNotEmpty()) nextRow++ else -1
    val actionsRow = if (shownQueue.isNotEmpty()) nextRow++ else -1
    val playlistsTitleRow = if (playlistPicks.isNotEmpty()) nextRow++ else -1
    val playlistsRow = if (playlistPicks.isNotEmpty()) nextRow++ else -1
    val songsTitleRow = if (trackRows.isNotEmpty()) nextRow++ else -1
    // Claimed before the song rows so expanding cannot move it: the pill keeps one row index in both
    // states, which is what lets it hold the selector across its own press.
    val songsToggleRow = if (statsSongs.size > StatsSongLimit) nextRow++ else -1
    val songsRowStart = nextRow
    nextRow += trackRows.size
    val artistsTitleRow = if (artistPicks.isNotEmpty()) nextRow++ else -1
    val artistsRow = if (artistPicks.isNotEmpty()) nextRow++ else -1
    val albumsTitleRow = if (albumPicks.isNotEmpty()) nextRow++ else -1
    val albumsRow = if (albumPicks.isNotEmpty()) nextRow++ else -1
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val pageAnchor = remember { FocusRequester() }
    auraAnchorHero(pageAnchor)
    val pagePad = auraPagePadding()

    val playShown: (Boolean) -> Unit = { shuffled ->
        val items = shownQueue.map { it.toMediaMetadata().toMediaItem() }
        if (items.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = context.getString(R.string.most_played_songs),
                    items = if (shuffled) items.shuffled() else items,
                ),
            )
        }
    }

    var showTimeTransfer by rememberSaveable { mutableStateOf(false) }
    var prevOptionOrdinal by rememberSaveable { mutableStateOf<OptionStats?>(null) }
    var prevIndexChips by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(showTimeTransfer) {
        if (showTimeTransfer) {
            if (prevOptionOrdinal == null) prevOptionOrdinal = selectedOption
            if (prevIndexChips == null) prevIndexChips = indexChips
            viewModel.selectedOption.value = OptionStats.CONTINUOUS
            viewModel.indexChips.value = StatPeriod.ALL.ordinal
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncMostPlaylistsIfNeeded()
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
        // The shell gives this destination one page box, so a bar emitted after the list becomes a
        // sibling of it and paints over the rows - here it landed in the middle of the playlist grid
        // and cut the covers in half. It is the page heading: first child, pinned, list scrolling
        // under it. The list carries no top inset of its own either, because the shell already moves
        // the page box below the now-playing banner and padding it again opened a void under the bar.
        TopAppBar(
            title = {
                Text(stringResource(R.string.stats))
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
                IconButton(
                    onClick = { showTimeTransfer = true },
                    onLongClick = { showTimeTransfer = true },
                ) {
                    Icon(
                        painterResource(R.drawable.sync),
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
            item(key = "stats_modes") {
                StatsChipRow(
                    chips = modeChips,
                    selected = selectedOption,
                    onSelect = {
                        viewModel.selectedOption.value = it
                        viewModel.indexChips.value = 0
                    },
                    rowNav = tvRowNav,
                    rowIndex = modeRow,
                    rowCount = rowCount,
                    itemKey = "stats_modes",
                    anchor = pageAnchor,
                )
            }

            if (periodRow >= 0) {
                item(key = "stats_periods") {
                    StatsChipRow(
                        chips = periodChips,
                        selected = indexChips,
                        onSelect = { viewModel.indexChips.value = it },
                        rowNav = tvRowNav,
                        rowIndex = periodRow,
                        rowCount = rowCount,
                        itemKey = "stats_periods",
                    )
                }
            }

            if (actionsRow >= 0) {
                item(key = "stats_actions") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.apx()),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    tvRowNav.row(
                                        index = actionsRow,
                                        itemKey = "stats_actions",
                                        rowCount = rowCount,
                                    ),
                                )
                                .focusGroup(),
                    ) {
                        AuraPillButton(
                            label = stringResource(R.string.play),
                            icon = R.drawable.aura_ic_play,
                            filled = true,
                            onClick = { playShown(false) },
                            modifier = tvRowNav.tile(actionsRow, 0),
                        )
                        AuraPillButton(
                            label = stringResource(R.string.shuffle),
                            icon = R.drawable.aura_ic_shuffle,
                            onClick = { playShown(true) },
                            modifier = tvRowNav.tile(actionsRow, 1),
                        )
                    }
                }
            }

            if (playlistsRow >= 0) {
                item(key = "stats_playlists_title") {
                    StatsSectionHeader(
                        title = context.resources.getQuantityString(
                            R.plurals.n_playlist,
                            visiblePlaylists.size,
                            visiblePlaylists.size,
                        ),
                        rowNav = tvRowNav,
                        rowIndex = playlistsTitleRow,
                        rowCount = rowCount,
                        itemKey = "stats_playlists_title",
                    )
                }
                item(key = "stats_playlists") {
                    AuraPickRow(
                        picks = playlistPicks,
                        rowNav = tvRowNav,
                        rowIndex = playlistsRow,
                        rowCount = rowCount,
                        itemKey = "stats_playlists",
                    )
                }
            }

            if (songsTitleRow >= 0) {
                item(key = "stats_songs_title") {
                    StatsSectionHeader(
                        title = "${statsSongs.size} ${stringResource(R.string.songs)}",
                        rowNav = tvRowNav,
                        rowIndex = songsTitleRow,
                        rowCount = rowCount,
                        itemKey = "stats_songs_title",
                    )
                }
            }

            if (songsToggleRow >= 0) {
                item(key = "stats_songs_toggle") {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    tvRowNav.row(
                                        index = songsToggleRow,
                                        itemKey = "stats_songs_toggle",
                                        rowCount = rowCount,
                                    ),
                                )
                                .focusGroup(),
                    ) {
                        AuraPillButton(
                            label =
                                if (showAllSongs) {
                                    "${stringResource(R.string.stats_show_top)} ($StatsSongLimit)"
                                } else {
                                    "${stringResource(R.string.stats_show_all)} (${statsSongs.size})"
                                },
                            icon = if (showAllSongs) R.drawable.expand_less else R.drawable.expand_more,
                            onClick = { showAllSongs = !showAllSongs },
                            modifier = tvRowNav.tile(songsToggleRow, 0),
                        )
                    }
                }
            }

            auraTrackRows(
                rows = trackRows,
                rowNav = tvRowNav,
                rowStart = songsRowStart,
                rowCount = rowCount,
            )

            if (artistsRow >= 0) {
                item(key = "stats_artists_title") {
                    StatsSectionHeader(
                        title = "${statsArtists.size} ${stringResource(R.string.artists)}",
                        rowNav = tvRowNav,
                        rowIndex = artistsTitleRow,
                        rowCount = rowCount,
                        itemKey = "stats_artists_title",
                    )
                }
                item(key = "stats_artists") {
                    AuraPickRow(
                        picks = artistPicks,
                        rowNav = tvRowNav,
                        rowIndex = artistsRow,
                        rowCount = rowCount,
                        itemKey = "stats_artists",
                    )
                }
            }

            if (albumsRow >= 0) {
                item(key = "stats_albums_title") {
                    StatsSectionHeader(
                        title = "${statsAlbums.size} ${stringResource(R.string.albums)}",
                        rowNav = tvRowNav,
                        rowIndex = albumsTitleRow,
                        rowCount = rowCount,
                        itemKey = "stats_albums_title",
                    )
                }
                item(key = "stats_albums") {
                    AuraPickRow(
                        picks = albumPicks,
                        rowNav = tvRowNav,
                        rowIndex = albumsRow,
                        rowCount = rowCount,
                        itemKey = "stats_albums",
                    )
                }
            }

            if (trackRows.isEmpty() && artistPicks.isEmpty() && albumPicks.isEmpty() &&
                playlistPicks.isEmpty()
            ) {
                item(key = "stats_empty") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 80.apx()),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuraText(
                            value = stringResource(R.string.stats_no_history),
                            px = 24,
                            color = AuraMock.Muted,
                        )
                    }
                }
            }
        }
    }

    if (showTimeTransfer) {
        TimeTransfer(
            onDismiss = {
                showTimeTransfer = false
                prevOptionOrdinal?.let { viewModel.selectedOption.value = it }
                prevIndexChips?.let { viewModel.indexChips.value = it }
                prevOptionOrdinal = null
                prevIndexChips = null
            },
        )
    }
}

/**
 * The two chip bands, one row each: the mode band picks how the window is counted and the band under
 * it picks the window. Both walk left/right through their chips and hand a vertical press to the row
 * nav instead of letting Compose find the next focusable in tree order - which, on this page, is one
 * behind it.
 *
 * [anchor] is the requester the rail should land on, taken for the first chip of the top row; every
 * other chip mints its own.
 */
@Composable
private fun <T> StatsChipRow(
    chips: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
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
 * A shelf heading that is also a stop in the chain: with nothing registered on the title row, a
 * press aimed at it finds no requester and the walk out of the songs below stops dead.
 */
@Composable
private fun StatsSectionHeader(
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

private const val StatsSongLimit = 10

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
