/**
 * Aura Music — a saved, locally-stored playlist in the Material You TV reference's layout.
 *
 * The reference draws no playlist page, so this is composed from its own blocks. Every action the
 * touch screen had survives; the three gestures a remote cannot perform each got a control of their
 * own: swipe-to-remove became a trash glyph, drag-to-reorder became a pair of arrows, and the
 * floating edit button on the cover became a pill.
 */

package com.aura.music.ui.aura

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.aura.innertube.YouTube
import com.aura.music.LocalDatabase
import com.aura.music.LocalDownloadUtil
import com.aura.music.LocalPlayerConnection
import com.aura.music.LocalSyncUtils
import com.aura.music.R
import com.aura.music.constants.DarkModeKey
import com.aura.music.constants.PlaylistEditLockKey
import com.aura.music.constants.PlaylistSongSortDescendingKey
import com.aura.music.constants.PlaylistSongSortType
import com.aura.music.constants.PlaylistSongSortTypeKey
import com.aura.music.db.entities.Playlist
import com.aura.music.db.entities.PlaylistSong
import com.aura.music.extensions.toMediaItem
import com.aura.music.playback.ExoDownloadService
import com.aura.music.playback.queues.ListQueue
import com.aura.music.ui.component.DefaultDialog
import com.aura.music.ui.component.ExpandableText
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.TextFieldDialog
import com.aura.music.ui.menu.CustomThumbnailMenu
import com.aura.music.ui.menu.LocalPlaylistMenu
import com.aura.music.ui.menu.SelectionSongMenu
import com.aura.music.ui.menu.SongMenu
import com.aura.music.ui.screens.playlist.uriToByteArray
import com.aura.music.ui.screens.settings.DarkMode
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.utils.rememberPreference
import com.aura.music.utils.reportException
import com.aura.music.viewmodels.LocalPlaylistViewModel
import com.yalantis.ucrop.UCrop
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraLocalPlaylist(
    navController: NavController,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val syncUtils = LocalSyncUtils.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val onlinePlaylist by viewModel.onlinePlaylist.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSongSortTypeKey,
        PlaylistSongSortType.CUSTOM,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSongSortDescendingKey,
        true,
    )
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)

    val entity = playlist
    val editable = entity?.playlist?.isEditable == true
    val customOrder = sortType == PlaylistSongSortType.CUSTOM

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection: MutableList<Int> = remember { mutableStateListOf() }

    // The touch screen only lets a drag handle or a swipe through under these conditions; a remote
    // gets the same row controls, so the gate has to be the same.
    val canEditRows = editable && !locked && !inSelectMode && !isSearching && customOrder

    val syncedLabel = stringResource(R.string.playlist_synced)
    val visibleSongs = remember(songs, query) {
        val text = query.text
        if (text.isEmpty()) {
            songs
        } else {
            songs.filter {
                it.song.song.title.contains(text, ignoreCase = true) ||
                    it.song.artists.any { artist -> artist.name.contains(text, ignoreCase = true) }
            }
        }
    }
    val totalDuration = remember(songs) { songs.sumOf { it.song.song.duration } }

    LaunchedEffect(songs) {
        val alive = songs.mapTo(mutableSetOf()) { it.map.id }
        selection.removeAll { it !in alive }
        if (selection.isEmpty()) inSelectMode = false
    }
    val onExitSelection: () -> Unit = {
        inSelectMode = false
        selection.clear()
    }

    val queueAt: (List<PlaylistSong>, Int) -> Unit = { items, index ->
        val current = entity
        if (current != null && items.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = current.playlist.name,
                    items = items.map { it.song.toMediaItem() },
                    startIndex = index,
                ),
            )
        }
    }
    val playAll: () -> Unit = { queueAt(songs, 0) }
    val shuffleAll: () -> Unit = { queueAt(songs.shuffled(), 0) }

    var downloadState by remember { mutableIntStateOf(Download.STATE_STOPPED) }
    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = when {
                songs.all { downloads[it.song.id]?.state == Download.STATE_COMPLETED } ->
                    Download.STATE_COMPLETED

                songs.all {
                    val state = downloads[it.song.id]?.state
                    state == Download.STATE_QUEUED ||
                        state == Download.STATE_DOWNLOADING ||
                        state == Download.STATE_COMPLETED
                } -> Download.STATE_DOWNLOADING

                else -> Download.STATE_STOPPED
            }
        }
    }

    // Swipe-to-dismiss has no remote equivalent, so removal is a glyph on the row itself.
    val removeSong: (PlaylistSong) -> Unit = { item ->
        val browseId = entity?.playlist?.browseId
        database.transaction {
            move(item.map.playlistId, item.map.position, Int.MAX_VALUE)
            delete(item.map.copy(position = Int.MAX_VALUE))
        }
        if (browseId != null) {
            syncUtils.scheduleRemoveFromPlaylist(
                browseId,
                item.map.songId,
                item.map.playlistId,
            ) { item.map.setVideoId }
        }
    }

    // Drag handles become a pair of arrows; the transaction and the YTM order sync are unchanged.
    val moveSong: (Int, Int) -> Unit = { from, to ->
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            database.withTransaction { move(viewModel.playlistId, from, to) }
            val browseId = entity?.playlist?.browseId
            if (browseId != null) {
                val maps = database.playlistSongMaps(viewModel.playlistId, 0)
                val setVideoId = maps.getOrNull(to)?.setVideoId
                val successorSetVideoId = maps.getOrNull(to + 1)?.setVideoId
                if (setVideoId != null) {
                    YouTube.moveSongPlaylist(browseId, setVideoId, successorSetVideoId)
                }
            }
        }
    }

    var showEditDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveDownloadDialog by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        delay(4000)
        if (statusMessage == message) statusMessage = null
    }

    // The floating pencil over the touch screen's cover is a control a remote cannot reach, so the
    // same crop/upload pipeline hangs off a pill instead.
    var coverOverride by rememberSaveable { mutableStateOf<String?>(null) }
    val cropResult = remember { mutableStateOf<Uri?>(null) }
    var cropDestination by remember { mutableStateOf<Uri?>(null) }
    var showCoverNote by remember { mutableStateOf(false) }
    val shownCover = coverOverride ?: entity?.thumbnails?.firstOrNull()
    val isCustomCover = shownCover != null &&
        (shownCover.contains("studio_square_thumbnail") || shownCover.contains("content://com.aura.music"))

    // The crop activity is themed from the app's own palette, so these have to be read here rather
    // than inside the launcher callback, which is not a composition.
    val (darkMode, _) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
    val cropColors = MaterialTheme.colorScheme
    val cropDarkTheme = darkMode == DarkMode.ON || (darkMode == DarkMode.AUTO && isSystemInDarkTheme())
    val editCoverLabel = stringResource(R.string.edit_playlist_cover)

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            cropResult.value = res.data?.let { UCrop.getOutput(it) } ?: cropDestination
        }
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val source = uri ?: return@rememberLauncherForActivityResult
        val file = File(context.cacheDir, "playlist_cover_crop_${System.currentTimeMillis()}.jpg")
        val destination = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
        cropDestination = destination

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setHideBottomControls(true)
            setToolbarTitle(editCoverLabel)
            setStatusBarLight(!cropDarkTheme)
            setToolbarColor(cropColors.surface.toArgb())
            setToolbarWidgetColor(cropColors.inverseSurface.toArgb())
            setRootViewBackgroundColor(cropColors.surface.toArgb())
            setLogoColor(cropColors.surface.toArgb())
        }
        val intent = UCrop
            .of(source, destination)
            .withAspectRatio(1f, 1f)
            .withOptions(options)
            .getIntent(context)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cropLauncher.launch(intent)
    }

    LaunchedEffect(cropResult.value) {
        val uri = cropResult.value ?: return@LaunchedEffect
        val saved = entity ?: return@LaunchedEffect
        cropResult.value = null
        withContext(Dispatchers.IO) {
            val browseId = saved.playlist.browseId
            if (browseId == null) {
                coverOverride = uri.toString()
                database.query { update(saved.playlist.copy(thumbnailUrl = uri.toString())) }
            } else {
                val bytes = uriToByteArray(context, uri)
                if (bytes != null) {
                    YouTube
                        .uploadCustomThumbnailLink(browseId, bytes)
                        .onSuccess { url ->
                            coverOverride = url
                            database.query { update(saved.playlist.copy(thumbnailUrl = url)) }
                        }
                        .onFailure { error ->
                            if (error is ClientRequestException) {
                                statusMessage = "${error.response.status.value} ${error.response.status.description}"
                            }
                            reportException(error)
                        }
                }
            }
        }
    }

    val removeCustomCover: () -> Unit = {
        val saved = entity
        if (saved != null) {
            val browseId = saved.playlist.browseId
            if (browseId == null) {
                coverOverride = null
                database.query { update(saved.playlist.copy(thumbnailUrl = null)) }
            } else {
                val remoteId: String = browseId
                coroutineScope.launch(Dispatchers.IO) {
                    YouTube
                        .removeThumbnailPlaylist(remoteId)
                        .onSuccess { url ->
                            coverOverride = url
                            database.query { update(saved.playlist.copy(thumbnailUrl = url)) }
                        }
                }
            }
        }
    }
    val openCoverPicker: () -> Unit = {
        pickLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    var nextRow = 0
    val heroRow = nextRow++
    val description = onlinePlaylist?.description
    val aboutVisible = !description.isNullOrEmpty()
    val aboutRow = if (aboutVisible) nextRow++ else -1
    val actionsRow = nextRow++
    val searchRow = if (isSearching) nextRow++ else -1
    val tracksRowStart = nextRow
    nextRow += visibleSongs.size
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val heroFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    auraAnchorHero(heroFocus, entity != null)
    LaunchedEffect(isSearching) {
        if (isSearching) {
            delay(250)
            runCatching { searchFocus.requestFocus() }
        }
    }

    val pagePad = auraPagePadding()
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
        item(key = "aura_local_playlist_hero") {
            val current = entity
            if (current == null) {
                DetailPendingBlock(
                    failed = false,
                    onRetry = { },
                    modifier = tvRowNav.tile(heroRow, 0),
                )
            } else {
                AuraHeroBlock(
                    pick = AuraPick(
                        id = current.id,
                        title = current.playlist.name,
                        subtitle = listOfNotNull(
                            onlinePlaylist?.author?.name,
                            pluralStringResource(
                                R.plurals.n_song,
                                current.songCount,
                                current.songCount,
                            ),
                            makeTimeString(totalDuration * 1000L),
                        ).joinToString("  ·  "),
                        image = shownCover,
                        onClick = playAll,
                    ),
                    badge = stringResource(R.string.type_playlist).uppercase(),
                    modifier = tvRowNav.tile(heroRow, 0, best = true, anchor = heroFocus),
                )
            }
        }

        if (aboutVisible && aboutRow >= 0) {
            item(key = "aura_local_playlist_about") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = aboutRow,
                                itemKey = "aura_local_playlist_about",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup()
                        .then(tvRowNav.tile(aboutRow, 0))
                        .auraFocused(corner = AuraPx.PickCardRadius.apx(), grow = false)
                        .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                        .background(AuraMock.Panel)
                        .padding(28.apx()),
                ) {
                    ProvideTextStyle(auraType(FontWeight.Normal, 22)) {
                        CompositionLocalProvider(LocalContentColor provides AuraMock.Muted) {
                            ExpandableText(text = description.orEmpty(), collapsedMaxLines = 3)
                        }
                    }
                }
            }
        }

        item(key = "aura_local_playlist_actions") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.apx()),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        tvRowNav.row(
                            index = actionsRow,
                            itemKey = "aura_local_playlist_actions",
                            rowCount = rowCount,
                        ),
                    )
                    .focusGroup()
                    // Eight pills do not fit the page at the reference's own padding, and clipping
                    // them would hide controls a remote can never scroll to.
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.apx()),
            ) {
                var col = 0
                if (inSelectMode) {
                    AuraPillButton(
                        label = stringResource(R.string.select),
                        icon = R.drawable.aura_ic_check,
                        filled = true,
                        onClick = {
                            selection.clear()
                            selection.addAll(songs.map { it.map.id })
                        },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = "${selection.size}",
                        icon = R.drawable.aura_ic_more_horizontal,
                        filled = selection.isNotEmpty(),
                        onClick = {
                            if (selection.isNotEmpty()) {
                                val picked = songs.filter { it.map.id in selection }
                                menuState.show {
                                    SelectionSongMenu(
                                        songSelection = picked.map { it.song },
                                        songPosition = picked.map { it.map },
                                        onDismiss = menuState::dismiss,
                                        clearAction = onExitSelection,
                                    )
                                }
                            }
                        },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = stringResource(R.string.cancel),
                        onClick = onExitSelection,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                } else {
                    AuraPillButton(
                        label = stringResource(R.string.play),
                        icon = R.drawable.aura_ic_play,
                        filled = true,
                        onClick = playAll,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = stringResource(R.string.shuffle),
                        icon = R.drawable.aura_ic_shuffle,
                        onClick = shuffleAll,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = localSortLabel(sortType),
                        onClick = { onSortTypeChange(nextLocalSortType(sortType)) },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    if (!customOrder) {
                        AuraPillButton(
                            label = stringResource(
                                if (sortDescending) {
                                    R.string.sort_ascending
                                } else {
                                    R.string.sort_descending
                                },
                            ),
                            onClick = { onSortDescendingChange(!sortDescending) },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                    }
                    if (editable) {
                        AuraPillButton(
                            label = stringResource(
                                if (locked) {
                                    R.string.aura_playlist_locked
                                } else {
                                    R.string.aura_playlist_unlocked
                                },
                            ),
                            icon = if (locked) R.drawable.lock else R.drawable.lock_open,
                            filled = !locked,
                            onClick = { locked = !locked },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                    }
                    AuraPillButton(
                        label = stringResource(R.string.search),
                        icon = R.drawable.aura_ic_search,
                        filled = isSearching,
                        onClick = {
                            isSearching = !isSearching
                            if (!isSearching) query = TextFieldValue()
                        },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    AuraPillButton(
                        label = stringResource(R.string.select),
                        onClick = { inSelectMode = true },
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    if (editable) {
                        AuraPillButton(
                            label = editCoverLabel,
                            onClick = {
                                if (isCustomCover) {
                                    menuState.show {
                                        CustomThumbnailMenu(
                                            onEdit = openCoverPicker,
                                            onRemove = removeCustomCover,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                } else {
                                    showCoverNote = true
                                }
                            },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                    }
                    AuraGhostButton(
                        icon = R.drawable.aura_ic_more_horizontal,
                        boxPx = 68,
                        glyphPx = 30,
                        onClick = {
                            val current = entity ?: return@AuraGhostButton
                            menuState.show {
                                LocalPlaylistMenu(
                                    playlist = current,
                                    songs = songs,
                                    context = context,
                                    downloadState = downloadState,
                                    onEdit = { showEditDialog = true },
                                    onSync = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            current.playlist.browseId?.let {
                                                syncUtils.syncPlaylistSuspend(it, current.id)
                                            }
                                            statusMessage = syncedLabel
                                        }
                                    },
                                    onDelete = { showDeleteDialog = true },
                                    onDownload = {
                                        when (downloadState) {
                                            Download.STATE_COMPLETED ->
                                                showRemoveDownloadDialog = true

                                            Download.STATE_DOWNLOADING -> songs.forEach { song ->
                                                DownloadService.sendRemoveDownload(
                                                    context,
                                                    ExoDownloadService::class.java,
                                                    song.song.id,
                                                    false,
                                                )
                                            }

                                            else -> songs.forEach { song ->
                                                downloadUtil.download(song.song)
                                            }
                                        }
                                    },
                                    onQueue = {
                                        playerConnection.addToQueue(
                                            items = songs.map { it.song.toMediaItem() },
                                        )
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        modifier = tvRowNav.tile(actionsRow, col),
                    )
                }
            }
        }

        if (searchRow >= 0) {
            item(key = "aura_local_playlist_search") {
                AuraSearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSubmit = {
                        coroutineScope.launch { listState.animateScrollToItem(tracksRowStart) }
                    },
                    fieldFocus = searchFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = searchRow,
                                itemKey = "aura_local_playlist_search",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup()
                        .then(tvRowNav.tile(searchRow, 0)),
                )
            }
        }

        statusMessage?.let { message ->
            item(key = "aura_local_playlist_status") {
                AuraText(
                    value = message,
                    px = 22,
                    weight = FontWeight.Medium,
                    color = AuraMock.Primary,
                    modifier = Modifier.padding(horizontal = 8.apx()),
                )
            }
        }

        if (entity != null && visibleSongs.isEmpty()) {
            item(key = "aura_local_playlist_empty") {
                AuraText(
                    value = stringResource(R.string.playlist_is_empty),
                    px = 26,
                    color = AuraMock.Muted,
                    modifier = Modifier.padding(vertical = 32.apx()),
                )
            }
        }

        itemsIndexed(
            items = visibleSongs,
            key = { _, item -> "aura_local_track_${item.map.id}" },
        ) { index, item ->
            val rowKey = "aura_local_track_${item.map.id}"
            val rowIndex = tracksRowStart + index
            val selected = inSelectMode && item.map.id in selection
            AuraListRow(
                title = item.song.song.title,
                subtitle = item.song.artists.joinToString(", ") { artist -> artist.name },
                image = item.song.song.thumbnailUrl,
                label = if (customOrder) "${index + 1}" else null,
                active = if (inSelectMode) {
                    selected
                } else {
                    item.song.song.id == mediaMetadata?.id
                },
                onClick = {
                    if (inSelectMode) {
                        if (selected) selection.remove(item.map.id) else selection.add(item.map.id)
                    } else {
                        queueAt(visibleSongs, index)
                    }
                },
                onLongClick = {
                    menuState.show {
                        SongMenu(
                            originalSong = item.song,
                            playlistSong = item,
                            playlistBrowseId = entity?.playlist?.browseId,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        tvRowNav.row(
                            index = rowIndex,
                            itemKey = rowKey,
                            rowCount = rowCount,
                        ),
                    )
                    .focusGroup()
                    .then(tvRowNav.tile(rowIndex, 0)),
                trailing = {
                    if (item.song.song.explicit) {
                        Icon(
                            painter = painterResource(R.drawable.explicit),
                            contentDescription = null,
                            tint = AuraMock.Muted,
                            modifier = Modifier.size(24.apx()),
                        )
                        Spacer(modifier = Modifier.width(12.apx()))
                    }
                    AuraText(
                        value = makeTimeString(item.song.song.duration * 1000L),
                        px = 20,
                        color = AuraMock.Muted,
                    )
                    Spacer(modifier = Modifier.width(12.apx()))
                    if (canEditRows) {
                        if (index > 0) {
                            AuraGhostButton(
                                icon = R.drawable.arrow_upward,
                                boxPx = 52,
                                glyphPx = 24,
                                onClick = { moveSong(index, index - 1) },
                            )
                        }
                        if (index < visibleSongs.lastIndex) {
                            AuraGhostButton(
                                icon = R.drawable.arrow_downward,
                                boxPx = 52,
                                glyphPx = 24,
                                onClick = { moveSong(index, index + 1) },
                            )
                        }
                    }
                    if (editable && !locked && !inSelectMode && !isSearching) {
                        AuraGhostButton(
                            icon = R.drawable.delete,
                            boxPx = 52,
                            glyphPx = 24,
                            onClick = { removeSong(item) },
                        )
                    }
                    AuraGhostButton(
                        icon = R.drawable.aura_ic_more_horizontal,
                        boxPx = 52,
                        glyphPx = 24,
                        onClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = item.song,
                                    playlistSong = item,
                                    playlistBrowseId = entity?.playlist?.browseId,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                },
            )
        }
    }

    if (showCoverNote) {
        val browseId = entity?.playlist?.browseId
        val note = if (browseId != null) {
            "${stringResource(R.string.edit_playlist_cover_note)}\n\n" +
                stringResource(R.string.edit_playlist_cover_note_wait)
        } else {
            stringResource(R.string.edit_playlist_cover_note_wait)
        }
        AuraConfirmDialog(
            text = note,
            onDismiss = { showCoverNote = false },
            onConfirm = {
                showCoverNote = false
                openCoverPicker()
            },
        )
    }

    if (showEditDialog) {
        entity?.playlist?.let { playlistEntity ->
            TextFieldDialog(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.edit),
                        contentDescription = null,
                    )
                },
                title = { Text(stringResource(R.string.edit_playlist)) },
                initialTextFieldValue = TextFieldValue(
                    playlistEntity.name,
                    TextRange(playlistEntity.name.length),
                ),
                onDismiss = { showEditDialog = false },
                onDone = { name ->
                    database.query {
                        update(
                            playlistEntity.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistEntity.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    if (showDeleteDialog) {
        entity?.let { current ->
            AuraConfirmDialog(
                text = stringResource(R.string.delete_playlist_confirm, current.playlist.name),
                onDismiss = { showDeleteDialog = false },
                onConfirm = {
                    showDeleteDialog = false
                    database.query { delete(current.playlist) }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        current.playlist.browseId?.let { YouTube.deletePlaylist(it) }
                    }
                    navController.popBackStack()
                },
            )
        }
    }

    if (showRemoveDownloadDialog) {
        entity?.let { current ->
            AuraConfirmDialog(
                text = stringResource(
                    R.string.remove_download_playlist_confirm,
                    current.playlist.name,
                ),
                onDismiss = { showRemoveDownloadDialog = false },
                onConfirm = {
                    showRemoveDownloadDialog = false
                    if (!editable) {
                        database.transaction { clearPlaylist(current.id) }
                    }
                    songs.forEach { song ->
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.song.id,
                            false,
                        )
                    }
                },
            )
        }
    }
}

private val LocalSortTypes = listOf(
    PlaylistSongSortType.CUSTOM,
    PlaylistSongSortType.CREATE_DATE,
    PlaylistSongSortType.NAME,
    PlaylistSongSortType.ARTIST,
    PlaylistSongSortType.PLAY_TIME,
)

private fun nextLocalSortType(current: PlaylistSongSortType): PlaylistSongSortType {
    val index = LocalSortTypes.indexOf(current)
    return LocalSortTypes[(index + 1) % LocalSortTypes.size]
}

@Composable
private fun localSortLabel(type: PlaylistSongSortType): String = stringResource(
    when (type) {
        PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
        PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
        PlaylistSongSortType.NAME -> R.string.sort_by_name
        PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
        PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
    },
)

@Composable
private fun AuraConfirmDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

/** A D-pad-reachable search field; the reference has no text input, so it borrows the panel shape. */
@Composable
private fun AuraSearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    fieldFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .auraFocused(corner = AuraPx.PickCardRadius.apx(), grow = false)
            .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
            .background(AuraMock.Panel)
            .padding(horizontal = 28.apx(), vertical = 20.apx()),
    ) {
        AuraText(
            value = stringResource(R.string.search),
            px = 18,
            color = AuraMock.Muted,
            modifier = Modifier.padding(bottom = 8.apx()),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = auraType(FontWeight.Medium, 26).copy(color = Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus),
        )
    }
}
