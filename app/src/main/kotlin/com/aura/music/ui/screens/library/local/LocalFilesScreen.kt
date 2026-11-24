/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.library.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.aura.music.LocalArtistNameAliases
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.CONTENT_TYPE_HEADER
import com.aura.music.constants.SongSortDescendingKey
import com.aura.music.constants.SongSortType
import com.aura.music.constants.SongSortTypeKey
import com.aura.music.extensions.toMediaItem
import com.aura.music.playback.queues.ListQueue
import com.aura.music.ui.aura.AuraChip
import com.aura.music.ui.aura.AuraGhostButton
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.AuraTrackRow
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.aura.auraTrackRows
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.ArtistNameAliases
import com.aura.music.utils.joinByBullet
import com.aura.music.utils.joinToArtistString
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.LocalFilesViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalFilesScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LocalFilesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val artistNameAliases = LocalArtistNameAliases.current
    val andSeparator = stringResource(R.string.and)

    val songs by viewModel.songs.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)

    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.scanLocalFiles()
    }

    val sortTypeLabel: @Composable (SongSortType) -> String = { type ->
        when (type) {
            SongSortType.CREATE_DATE -> stringResource(R.string.sort_by_create_date)
            SongSortType.NAME -> stringResource(R.string.sort_by_name)
            SongSortType.ARTIST -> stringResource(R.string.sort_by_artist)
            SongSortType.PLAY_TIME -> stringResource(R.string.sort_by_play_time)
        }
    }

    val queueTitle = stringResource(R.string.local_files)
    val rows = songs.mapIndexed { index, song ->
        AuraTrackRow(
            id = song.song.id,
            title = song.song.title,
            subtitle = joinByBullet(
                song.orderedArtists.joinToArtistString(" $andSeparator ") {
                    ArtistNameAliases.resolve(artistNameAliases, it.id, it.name)
                },
                makeTimeString(song.song.duration * 1000L),
            ),
            image = song.song.thumbnailUrl,
            badges = buildList {
                if (song.song.liked) add(R.drawable.favorite)
                if (song.song.explicit) add(R.drawable.explicit)
            },
            active = song.id == mediaMetadata?.id,
            onClick = {
                if (song.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        ListQueue(
                            title = queueTitle,
                            items = songs.map { it.toMediaItem() },
                            startIndex = index,
                        ),
                    )
                }
            },
            onLongClick = {
                menuState.show {
                    SongMenu(
                        originalSong = song,
                        onDismiss = menuState::dismiss,
                    )
                }
            },
        )
    }

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)

    // Row 0 is the sort band and every track below it is its own stop. The chip band above it is the
    // parent's slot and already carries the shell's own page anchor, so it stays out of this chain:
    // UP from the sort band is the first row's press, which the nav hands back, and the search finds
    // the chips one item above rather than escaping into the rail.
    val bandRow = 0
    val tracksRowStart = 1
    val rowCount = tracksRowStart + rows.size
    val sortChipFocus = remember { FocusRequester() }

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
        // First child and pinned: emitted after the list this bar became a sibling of it and painted
        // across the middle of the rows, the same way it did on every page converted before this one.
        TopAppBar(
            title = { Text(stringResource(R.string.local_files)) },
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
                // A rescan is the only way to pick up files copied onto this TV after launch, and a
                // remote cannot pull-to-refresh, so it sits in the header as its own focusable stop.
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.apx()),
                        strokeWidth = 3.apx(),
                        color = AuraMock.Primary,
                    )
                } else {
                    AuraGhostButton(
                        icon = R.drawable.refresh,
                        contentDescription = stringResource(R.string.scan_local_files),
                        onClick = { viewModel.scanLocalFiles() },
                    )
                }
            },
        )

        if (!hasPermission) {
            // The chips stay so the remote can leave this tab; without the pill the page is a dead end,
            // because the grant prompt behind it can only be opened by a press.
            filterContent()
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = auraPagePadding()),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(28.apx()),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = AuraMock.Primary,
                        modifier = Modifier.size(96.apx()),
                    )
                    AuraText(
                        value = stringResource(R.string.local_files_permission_required),
                        px = 26,
                        color = AuraMock.Muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AuraPillButton(
                        label = stringResource(R.string.grant_permission),
                        filled = true,
                        onClick = { permissionLauncher.launch(permissionToRequest) },
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding =
                    PaddingValues(
                        start = auraPagePadding(),
                        end = auraPagePadding(),
                        bottom = 132.apx(),
                    ),
                verticalArrangement = Arrangement.spacedBy(20.apx()),
            ) {
                item(
                    key = "filter",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    filterContent()
                }

                item(
                    key = "local_sort_band",
                    contentType = CONTENT_TYPE_HEADER,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.apx()),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    tvRowNav.row(
                                        index = bandRow,
                                        itemKey = "local_sort_band",
                                        rowCount = rowCount,
                                    ),
                                )
                                .focusGroup()
                                .padding(vertical = 12.apx()),
                    ) {
                        // A dropdown is a touch affordance, so the chip cycles through the sorts and the
                        // arrow flips the direction, exactly as the shared sort header does elsewhere.
                        AuraChip(
                            label = sortTypeLabel(sortType),
                            selected = false,
                            onClick = {
                                val next = sortType.ordinal + 1
                                onSortTypeChange(SongSortType.entries[next % SongSortType.entries.size])
                            },
                            focusRequester = sortChipFocus,
                            modifier = tvRowNav.stop(bandRow, 0, sortChipFocus, best = true),
                        )
                        AuraGhostButton(
                            icon = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                            contentDescription = stringResource(R.string.sort_descending),
                            boxPx = 56,
                            glyphPx = 26,
                            glyph = AuraMock.Primary,
                            onClick = { onSortDescendingChange(!sortDescending) },
                        )
                        // The shuffle lived in a floating button that only a finger could reach; on a TV
                        // the whole folder is one press away, and this is the only way to start it that way.
                        if (rows.isNotEmpty()) {
                            AuraPillButton(
                                label = stringResource(R.string.shuffle),
                                icon = R.drawable.aura_ic_shuffle,
                                onClick = {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = queueTitle,
                                            items = songs.shuffled().map { it.toMediaItem() },
                                        ),
                                    )
                                },
                            )
                        }
                        Box(modifier = Modifier.weight(1f))
                        AuraText(
                            value = pluralStringResource(R.plurals.n_local_file, songs.size, songs.size),
                            px = 22,
                            weight = FontWeight.Bold,
                            color = AuraMock.Primary,
                        )
                    }
                }

                // Not a row of its own: nothing here takes focus, so counting it would leave the chain
                // aiming at a band with no landing spot.
                if (rows.isEmpty()) {
                    item(key = "local_status") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 80.apx()),
                        ) {
                            AuraText(
                                value =
                                stringResource(
                                    if (isScanning) R.string.scanning_local_files else R.string.local_files_empty,
                                ),
                                px = 26,
                                color = AuraMock.Muted,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                auraTrackRows(
                    rows = rows,
                    rowNav = tvRowNav,
                    rowStart = tracksRowStart,
                    rowCount = rowCount,
                )
            }
        }
    }
}
