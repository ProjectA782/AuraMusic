/**
 * Aura Music
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The full-screen player, reproduced from the reference mock: a blurred cover wash, the art and
 * title block, an optional lyrics panel that shrinks the art, and the two-row transport bar.
 */

package com.aura.music.ui.aura

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.aura.music.LocalDatabase
import com.aura.music.LocalListenTogetherManager
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.LyricsRomanizeCyrillicByLineKey
import com.aura.music.constants.LyricsRomanizeList
import com.aura.music.constants.SimilarContent
import com.aura.music.constants.SleepTimerFadeOutKey
import com.aura.music.constants.SleepTimerStopAfterCurrentSongKey
import com.aura.music.db.entities.LyricsEntity
import com.aura.music.di.LyricsHelperEntryPoint
import com.aura.music.extensions.metadata
import com.aura.music.extensions.move
import com.aura.music.listentogether.RoomRole
import com.aura.music.lyrics.LyricsEntry
import com.aura.music.models.MediaMetadata
import com.aura.music.playback.PlayerConnection
import com.aura.music.ui.player.ProgressState
import com.aura.music.ui.screens.settings.defaultList
import com.aura.music.ui.component.LocalBottomSheetPageState
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.menu.LyricsMenu
import com.aura.music.ui.utils.ShowOffsetDialog
import com.aura.music.ui.utils.resize
import com.aura.music.ui.theme.AuraStopChain
import com.aura.music.ui.theme.rememberAuraStopChain
import com.aura.music.utils.joinToArtistString
import com.aura.music.utils.makeTimeString
import com.aura.music.utils.rememberPreference
import com.aura.music.viewmodels.LyricsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MockViewportHeight = 1080
private const val ArtColumnMaxWidthLyrics = 896
private const val ProgressRowMaxWidth = 1152
private const val SeekStepMillis = 10_000L

/** How long the player keeps offering the selector to its control chain while the sheet slides in. */
private const val ChainAnchorChecks = 12
private const val ChainAnchorIntervalMs = 150L
private const val BackdropBlur = 80
private const val BackdropAlpha = 0.30f
private const val BackdropScale = 1.10f
private const val LyricLineHeightPx = 54

/** The mock sizes the panel in viewport units; the shell maps mock px to dp, so 65vh is 702 of them. */
private val lyricsPanelHeightPx = (MockViewportHeight * 0.65f).roundToInt()

@Composable
fun AuraNowPlaying(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    // The sheet composes this player even while only the mini bar is on screen, so an unconditional
    // handler swallows BACK on every page: the key never reaches navigation and the sheet answers by
    // flashing the bar it was hiding. Only the open player may take the key.
    BackHandler(enabled = open) {
        when {
            showQueue -> showQueue = false
            showSleepTimer -> showSleepTimer = false
            showLyrics -> showLyrics = false
            else -> onClose()
        }
    }

    val playbackState by playerConnection.playbackState.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val automix by playerConnection.service.automixItems.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val listenTogetherManager = LocalListenTogetherManager.current
    val roleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = roleState?.value == RoomRole.GUEST

    val positionState = remember {
        mutableLongStateOf(runCatching { playerConnection.player.currentPosition }.getOrDefault(0L))
    }
    val durationState = remember {
        mutableLongStateOf(
            (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                ?: runCatching { playerConnection.player.duration }.getOrDefault(0L).coerceAtLeast(0L),
        )
    }
    val progressState = remember(positionState, durationState) { ProgressState(positionState, durationState) }
    // Position lives in a MutableLongState and is only read from the draw phase, the event handlers
    // and the two time labels; reading it here would recompose the bar every 100ms.
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                delay(100)
                positionState.longValue = playerConnection.player.currentPosition
                playerConnection.player.duration.takeIf { it > 0 }?.let { durationState.longValue = it }
            }
        }
    }
    LaunchedEffect(playbackState, mediaMetadata?.id) {
        positionState.longValue = playerConnection.player.currentPosition
        val fromMetadata = mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L)
        val fromPlayer = runCatching { playerConnection.player.duration }.getOrDefault(0L)
        durationState.longValue = fromMetadata ?: fromPlayer.takeIf { it > 0 } ?: durationState.longValue
    }
    val similarContentEnabled by rememberPreference(SimilarContent, defaultValue = true)
    if (similarContentEnabled && !canSkipNext && automix.isNotEmpty()) {
        playerConnection.service.addToQueueAutomix(automix[0], 0)
    } else if (!similarContentEnabled && automix.isNotEmpty()) {
        playerConnection.service.clearAutomix()
    }

    val andStr = stringResource(R.string.and)
    val closeStr = stringResource(R.string.close)
    val titleStr = mediaMetadata?.title.orEmpty()
    val artistStr = mediaMetadata
        ?.artists
        ?.takeIf { artists -> artists.any { it.name.isNotBlank() } }
        ?.joinToArtistString(" $andStr ") { it.name }
        .orEmpty()
    val sourceStr = queueTitle ?: mediaMetadata?.album?.title
    val thumbnailUrl = mediaMetadata?.thumbnailUrl
    val isLiked = currentSong?.let { song ->
        if (song.song.isEpisode) song.song.inLibrary != null else song.song.liked == true
    } == true

    // Toolbar, seek bar, transport: three rows the remote can walk, and nothing beyond them.
    val stopChain = rememberAuraStopChain()

    // The sheet composes while it is still collapsed, so an unanchored request would reach for the
    // selector from behind the home page and leave the remote moving a ring nobody can see. Ask only
    // once the sheet is up, and keep asking: while it slides the transport row is not attachable yet,
    // so a single attempt lands nowhere and focus stays on the page this sheet is covering.
    // The rail treats this sheet as one of the places the selector may live, so say whether it does.
    val playFocusRequester = remember { FocusRequester() }
    val playerHolds = LocalAuraPlayerHoldsFocus.current
    LaunchedEffect(open) {
        if (!open) {
            playerHolds?.value = false
            return@LaunchedEffect
        }
        var tries = 0
        while (tries < ChainAnchorChecks && !stopChain.holdsFocus.value) {
            delay(ChainAnchorIntervalMs)
            tries++
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    // A closed panel takes its rows out of the tree with the selector still on one of them, which
    // leaves the transport row unreachable until something re-anchors it.
    LaunchedEffect(showQueue, showSleepTimer) {
        if (open && !showQueue && !showSleepTimer) {
            delay(ChainAnchorIntervalMs)
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuraMock.Page)
            .onFocusEvent { playerHolds?.value = it.hasFocus && open }
            .then(stopChain.nav()),
    ) {
        AuraBackdrop(thumbnailUrl)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.apx()),
            ) {
                AuraPillButton(
                    icon = R.drawable.aura_ic_arrow_left,
                    contentDescription = closeStr,
                    buttonSize = 72,
                    iconSize = 32,
                    restColor = Color.White,
                    restBackground = AuraMock.Panel.copy(alpha = 0.8f),
                    modifier = stopChain.stop(0, 0),
                    onClick = onClose,
                )
                Spacer(modifier = Modifier.weight(1f))
                AuraOverflowRow(
                    chain = stopChain,
                    playerConnection = playerConnection,
                    mediaMetadata = mediaMetadata,
                    isLiked = isLiked,
                    showQueue = showQueue,
                    showSleepTimer = showSleepTimer,
                    onToggleQueue = {
                        showQueue = !showQueue
                        showSleepTimer = false
                    },
                    onToggleSleepTimer = {
                        showSleepTimer = !showSleepTimer
                        showQueue = false
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 64.apx()),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (showLyrics) Modifier.widthIn(max = ArtColumnMaxWidthLyrics.apx()) else Modifier,
                        ),
                ) {
                    AuraAlbumArt(
                        thumbnailUrl = thumbnailUrl,
                        sizePx = if (showLyrics) AuraPx.NowPlayingArtLyrics else AuraPx.NowPlayingArt,
                    )
                    Spacer(modifier = Modifier.height(48.apx()))
                    AuraText(
                        value = titleStr,
                        px = 60,
                        weight = FontWeight.W800,
                        color = Color.White,
                        modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                    )
                    Spacer(modifier = Modifier.height(16.apx()))
                    if (artistStr.isNotBlank()) {
                        AuraText(
                            value = artistStr,
                            px = 30,
                            weight = FontWeight.W500,
                            color = AuraMock.Primary,
                            modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                        )
                    }
                    if (!sourceStr.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.apx()))
                        AuraText(
                            value = stringResource(R.string.played_from, sourceStr),
                            px = 20,
                            weight = FontWeight.W500,
                            color = AuraMock.Muted,
                        )
                    }
                }

                if (showLyrics) {
                    Spacer(modifier = Modifier.width(64.apx()))
                    AuraLyricsPanel(
                        playerConnection = playerConnection,
                        progressState = progressState,
                        modifier = Modifier
                            .width(AuraPx.LyricsPanelWidth.apx())
                            .height(lyricsPanelHeightPx.apx()),
                    )
                }
            }

            AuraControlBar(
                chain = stopChain,
                playerConnection = playerConnection,
                progressState = progressState,
                positionState = positionState,
                playbackState = playbackState,
                isPlaying = isPlaying,
                isListenTogetherGuest = isListenTogetherGuest,
                isMuted = isMuted,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                showLyrics = showLyrics,
                playFocusRequester = playFocusRequester,
                onToggleLyrics = { showLyrics = !showLyrics },
                onOpenLyricsMenu = {
                    val metadata = mediaMetadata
                    if (metadata != null) {
                        menuState.show {
                            LyricsMenu(
                                lyricsProvider = { currentLyrics },
                                songProvider = { currentSong?.song },
                                mediaMetadataProvider = { metadata },
                                onDismiss = menuState::dismiss,
                                onShowOffsetDialog = {
                                    bottomSheetPageState.show {
                                        ShowOffsetDialog(songProvider = { currentSong?.song })
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }

        if (showQueue) {
            AuraQueuePanel(
                playerConnection = playerConnection,
                isListenTogetherGuest = isListenTogetherGuest,
                onDismiss = { showQueue = false },
            )
        }
        if (showSleepTimer) {
            AuraSleepTimerPanel(
                playerConnection = playerConnection,
                onDismiss = { showSleepTimer = false },
            )
        }
    }
}

@Composable
private fun AuraBackdrop(thumbnailUrl: String?) {
    if (thumbnailUrl == null) return
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest
            .Builder(context)
            .data(thumbnailUrl.resize(400, 400))
            .size(400, 400)
            .allowHardware(false)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .scale(BackdropScale)
            .blur(BackdropBlur.apx())
            .alpha(BackdropAlpha),
    )
}

@Composable
private fun AuraAlbumArt(
    thumbnailUrl: String?,
    sizePx: Int,
) {
    val shape = RoundedCornerShape(AuraPx.NowPlayingArtRadius.apx())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(sizePx.apx())
            .shadow(
                elevation = 32.apx(),
                shape = shape,
                ambientColor = AuraMock.Scrim,
                spotColor = AuraMock.Scrim,
            )
            .clip(shape)
            .background(AuraMock.Panel)
            .border(2.apx(), Color.White.copy(alpha = 0.1f), shape),
    ) {
        if (thumbnailUrl != null) {
            val url = remember(thumbnailUrl) { thumbnailUrl.resize(1200, 1200) }
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.aura_ic_disc_3),
                contentDescription = null,
                tint = AuraMock.Muted.copy(alpha = 0.4f),
                modifier = Modifier.size((sizePx / 3).apx()),
            )
        }
    }
}

@Composable
private fun AuraOverflowRow(
    chain: AuraStopChain,
    playerConnection: PlayerConnection,
    mediaMetadata: MediaMetadata?,
    isLiked: Boolean,
    showQueue: Boolean,
    showSleepTimer: Boolean,
    onToggleQueue: () -> Unit,
    onToggleSleepTimer: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val shareScope = rememberCoroutineScope()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.apx()),
    ) {
        AuraPillButton(
            icon = R.drawable.aura_ic_heart,
            contentDescription = stringResource(R.string.liked),
            buttonSize = 64,
            iconSize = 28,
            restColor = AuraMock.Muted,
            active = isLiked,
            modifier = chain.stop(0, 1),
            onClick = playerConnection::toggleLike,
        )
        AuraPillButton(
            icon = R.drawable.aura_ic_share_2,
            contentDescription = stringResource(R.string.share),
            buttonSize = 64,
            iconSize = 28,
            restColor = AuraMock.Muted,
            enabled = mediaMetadata != null,
            modifier = chain.stop(0, 2),
            onClick = {
                val id = mediaMetadata?.id ?: return@AuraPillButton
                // The queue is built from Spotify tracks mapped onto YouTube ids, so the id in the
                // metadata is not what the listener was listening to. Share the Spotify side when
                // this player has a mapping for the track, and only fall back to YouTube otherwise.
                shareScope.launch(Dispatchers.IO) {
                    val spotifyId = runCatching { database.getSpotifyMatchByYouTubeId(id)?.spotifyId }.getOrNull()
                    val link = spotifyId?.let { sid -> "https://open.spotify.com/track/$sid" }
                        ?: "https://music.youtube.com/watch?v=$id"
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }
            },
        )
        AuraPillButton(
            icon = R.drawable.aura_ic_list_music,
            contentDescription = stringResource(R.string.queue),
            buttonSize = 64,
            iconSize = 28,
            restColor = AuraMock.Muted,
            active = showQueue,
            modifier = chain.stop(0, 3),
            onClick = onToggleQueue,
        )
        AuraPillButton(
            icon = R.drawable.aura_ic_timer,
            contentDescription = stringResource(R.string.sleep_timer),
            buttonSize = 64,
            iconSize = 28,
            restColor = AuraMock.Muted,
            active = showSleepTimer,
            modifier = chain.stop(0, 4),
            onClick = onToggleSleepTimer,
        )
    }
}

@Composable
private fun AuraControlBar(
    chain: AuraStopChain,
    playerConnection: PlayerConnection,
    progressState: ProgressState,
    positionState: MutableLongState,
    playbackState: Int,
    isPlaying: Boolean,
    isListenTogetherGuest: Boolean,
    isMuted: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    showLyrics: Boolean,
    playFocusRequester: FocusRequester,
    onToggleLyrics: () -> Unit,
    onOpenLyricsMenu: () -> Unit,
) {
    val ended = playbackState == Player.STATE_ENDED

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(AuraPx.ControlBarHeight.apx())
            // No panel and no hairline here: the transport sits directly on the blurred artwork so
            // the backdrop reads as one surface. The glyphs are White/Muted on a 0.30-alpha image
            // over AuraMock.Page, which stays legible without a plate under it.
            .padding(bottom = 24.apx()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.apx()),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = ProgressRowMaxWidth.apx()),
        ) {
            AuraPlayedTime(progressState)
            AuraSeekBar(
                progressState = progressState,
                enabled = !isListenTogetherGuest,
                onSeek = { millis ->
                    playerConnection.seekTo(millis)
                    positionState.longValue = millis
                },
                modifier = Modifier
                    .weight(1f)
                    .then(chain.stop(1, 0, ownsHorizontal = true)),
            )
            AuraTotalTime(progressState)
        }

        Spacer(modifier = Modifier.height(8.apx()))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.apx()),
        ) {
            AuraPillButton(
                icon = R.drawable.aura_ic_shuffle,
                contentDescription = stringResource(
                    if (shuffleEnabled) R.string.action_shuffle_on else R.string.action_shuffle_off,
                ),
                buttonSize = 72,
                iconSize = 32,
                restColor = AuraMock.Muted,
                active = shuffleEnabled,
                enabled = !isListenTogetherGuest,
                modifier = chain.stop(2, 0),
                onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
            )
            AuraPillButton(
                icon = R.drawable.aura_ic_skip_back,
                contentDescription = null,
                buttonSize = 80,
                iconSize = 40,
                restColor = Color.White,
                enabled = canSkipPrevious && !isListenTogetherGuest,
                modifier = chain.stop(2, 1),
                onClick = playerConnection::seekToPrevious,
            )
            AuraPillButton(
                icon = when {
                    isListenTogetherGuest -> if (isMuted) R.drawable.aura_ic_volume_x else R.drawable.aura_ic_volume_2
                    ended -> R.drawable.aura_ic_refresh_cw
                    isPlaying -> R.drawable.aura_ic_pause
                    else -> R.drawable.aura_ic_play
                },
                contentDescription = stringResource(
                    when {
                        isListenTogetherGuest -> if (isMuted) R.string.unmute else R.string.mute
                        isPlaying -> R.string.pause
                        else -> R.string.play
                    },
                ),
                buttonSize = AuraPx.PlayButton,
                iconSize = 40,
                restColor = AuraMock.OnPrimary,
                restBackground = AuraMock.Primary,
                elevated = true,
                modifier = chain.stop(2, 2, requester = playFocusRequester),
                onClick = {
                    when {
                        isListenTogetherGuest -> playerConnection.toggleMute()
                        ended -> {
                            playerConnection.player.seekTo(0, 0)
                            playerConnection.player.playWhenReady = true
                        }

                        else -> playerConnection.togglePlayPause()
                    }
                },
            )
            AuraPillButton(
                icon = R.drawable.aura_ic_skip_forward,
                contentDescription = null,
                buttonSize = 80,
                iconSize = 40,
                restColor = Color.White,
                enabled = canSkipNext && !isListenTogetherGuest,
                modifier = chain.stop(2, 3),
                onClick = playerConnection::seekToNext,
            )
            AuraPillButton(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.aura_ic_repeat_1 else R.drawable.aura_ic_repeat,
                contentDescription = stringResource(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                        Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                        else -> R.string.repeat_mode_off
                    },
                ),
                buttonSize = 72,
                iconSize = 32,
                restColor = AuraMock.Muted,
                active = repeatMode != Player.REPEAT_MODE_OFF,
                enabled = !isListenTogetherGuest,
                modifier = chain.stop(2, 4),
                onClick = {
                    playerConnection.player.repeatMode = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                },
            )

            Box(
                modifier = Modifier
                    .size(width = 1.apx(), height = 40.apx())
                    .background(AuraMock.Border),
            )

            AuraPillButton(
                icon = R.drawable.aura_ic_mic_2,
                contentDescription = stringResource(R.string.lyrics),
                buttonSize = 72,
                iconSize = 32,
                restColor = AuraMock.Muted,
                active = showLyrics,
                modifier = chain.stop(2, 5),
                onClick = onToggleLyrics,
            )
            // The lyrics editor, refetch, search, copy, translation, romanisation and offset all live
            // in LyricsMenu; without this entry they were reachable only on the phone player.
            if (showLyrics) {
                AuraPillButton(
                    icon = R.drawable.aura_ic_settings,
                    contentDescription = stringResource(R.string.more_options),
                    buttonSize = 72,
                    iconSize = 32,
                    restColor = AuraMock.Muted,
                    modifier = chain.stop(2, 6),
                    onClick = onOpenLyricsMenu,
                )
            }
        }
    }
}

@Composable
private fun AuraPlayedTime(progressState: ProgressState) {
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier.width(64.apx()),
    ) {
        AuraText(
            value = makeTimeString(progressState.playedMillis),
            px = 20,
            weight = FontWeight.W700,
            color = AuraMock.Muted,
        )
    }
}

@Composable
private fun AuraTotalTime(progressState: ProgressState) {
    Box(modifier = Modifier.width(64.apx())) {
        AuraText(
            value = if (progressState.durationMillis > 0L) makeTimeString(progressState.durationMillis) else "",
            px = 20,
            weight = FontWeight.W700,
            color = AuraMock.Muted,
        )
    }
}

@Composable
private fun AuraPillButton(
    icon: Int,
    contentDescription: String?,
    buttonSize: Int,
    iconSize: Int,
    restColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    restBackground: Color = Color.Transparent,
    elevated: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .auraFocused(
                corner = FullRound,
                fill = false,
                ring = false,
                grow = false,
                shadow = false,
                onFocusChanged = { focused = it },
            )
            .size(buttonSize.apx())
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = 16.apx(),
                        shape = CircleShape,
                        ambientColor = AuraMock.Scrim,
                        spotColor = AuraMock.Scrim,
                    )
                } else {
                    Modifier
                },
            )
            // The focused button fills primary — the colour is the highlighter. No ring, no shadow.
            .auraPillFocused(focused, ring = false)
            .then(
                if (restBackground != Color.Transparent && !focused) {
                    Modifier.background(restBackground, CircleShape)
                } else {
                    Modifier
                },
            )
            // indication = null: Material's default focus state layer paints a grey square over the
            // whole button, behind and larger than our round fill. Every other Aura surface already
            // switches it off; this one still used the bare clickable, which is the plate on screen.
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier.alpha(0.35f)
                },
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = AuraPillGlyphColor(focused, if (active) AuraMock.Primary else restColor),
            modifier = Modifier.size(iconSize.apx()),
        )
    }
}

@Composable
private fun AuraSeekBar(
    progressState: ProgressState,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeight = AuraPx.ProgressTrack.apx()
    val thumbSize = AuraPx.ProgressThumb.apx()

    Box(
        modifier = modifier
            .height(thumbSize)
            // The mock scales every focusable by 1.05; a full-width bar would overflow the row it
            // sits in, so it only takes the ring and the fill. The ring stays here and nowhere else
            // in the transport: a slider has no other focus cue.
            .auraFocused(corner = FullRound, fill = false, grow = false, shadow = false)
            .focusable(enabled)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val step = when (event.key) {
                    Key.DirectionRight -> SeekStepMillis
                    Key.DirectionLeft -> -SeekStepMillis
                    else -> return@onPreviewKeyEvent false
                }
                if (!enabled) return@onPreviewKeyEvent false
                val duration = progressState.durationMillis
                if (duration <= 0L) return@onPreviewKeyEvent true
                onSeek((progressState.playedMillis + step).coerceIn(0L, duration))
                true
            }
            .pointerInput(enabled) {
                detectTapGestures { tap ->
                    val duration = progressState.durationMillis
                    if (enabled && duration > 0L) {
                        onSeek((duration * (tap.x / size.width).coerceIn(0f, 1f)).toLong())
                    }
                }
            }
            .drawBehind {
                val trackPx = trackHeight.toPx()
                val thumbPx = thumbSize.toPx()
                val trackTop = (size.height - trackPx) / 2f
                val radius = CornerRadius(trackPx / 2f, trackPx / 2f)
                drawRoundRect(
                    color = AuraMock.Border,
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackPx),
                    cornerRadius = radius,
                )
                val progress = progressState.progress
                if (progress > 0f) {
                    drawRoundRect(
                        color = AuraMock.Primary,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width * progress, trackPx),
                        cornerRadius = radius,
                    )
                }
                drawCircle(
                    color = Color.White,
                    radius = thumbPx / 2f,
                    center = Offset(
                        x = (size.width * progress).coerceIn(thumbPx / 2f, (size.width - thumbPx / 2f).coerceAtLeast(0f)),
                        y = size.height / 2f,
                    ),
                )
            },
    )
}

@Composable
private fun AuraLyricsPanel(
    playerConnection: PlayerConnection,
    progressState: ProgressState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AuraPx.LyricsPanelRadius.apx())
    Column(
        modifier = modifier
            .clip(shape)
            .background(AuraMock.Panel.copy(alpha = 0.6f))
            .border(1.apx(), Color.White.copy(alpha = 0.1f), shape)
            .padding(40.apx()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.apx()),
        ) {
            Icon(
                painter = painterResource(R.drawable.aura_ic_mic_2),
                contentDescription = null,
                tint = AuraMock.Primary,
                modifier = Modifier.size(24.apx()),
            )
            AuraText(
                value = stringResource(R.string.lyrics).uppercase(),
                px = 24,
                weight = FontWeight.W700,
                color = AuraMock.Primary,
                trackingEm = 0.1f,
            )
        }
        Spacer(modifier = Modifier.height(24.apx()))
        AuraLyricsBody(
            playerConnection = playerConnection,
            progressState = progressState,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AuraLyricsBody(
    playerConnection: PlayerConnection,
    progressState: ProgressState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val lyricsViewModel: LyricsViewModel = hiltViewModel()
    val notFoundStr = stringResource(R.string.lyrics_not_found)

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle(initialValue = -1)
    val lyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }

    val romanizeList = rememberPreference(LyricsRomanizeList, "")
    val romanizeCyrillicByLine by rememberPreference(LyricsRomanizeCyrillicByLineKey, false)
    val enabledLanguages = remember(romanizeList.value) {
        if (romanizeList.value.isEmpty()) {
            defaultList
        } else {
            romanizeList.value.split(",").mapNotNull { entry ->
                entry.split(":").takeIf { it.size == 2 }?.let { Pair(it[0], it[1].toBoolean()) }
            }
        }.filter { it.second }.map { it.first }
    }

    LaunchedEffect(mediaMetadata?.id, lyricsEntity) {
        val metadata = mediaMetadata ?: return@LaunchedEffect
        if (lyricsEntity == null) {
            delay(500)
            withContext(Dispatchers.IO) {
                runCatching {
                    val helper = EntryPointAccessors
                        .fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java)
                        .lyricsHelper()
                    val fetched = helper.getLyrics(metadata)
                    database.query { upsert(LyricsEntity(metadata.id, fetched.lyrics, fetched.provider)) }
                }
            }
        }
    }

    val nextMetadata = remember(queueWindows, currentWindowIndex) {
        queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata
    }
    LaunchedEffect(nextMetadata?.id, mediaMetadata?.id, lyricsEntity) {
        val next = nextMetadata ?: return@LaunchedEffect
        val currentId = mediaMetadata?.id ?: return@LaunchedEffect
        if (lyricsEntity?.id != currentId) return@LaunchedEffect
        delay(400)
        if (!isActive) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                if (database.lyrics(next.id).first() != null) return@withContext
                val helper = EntryPointAccessors
                    .fromApplication(context.applicationContext, LyricsHelperEntryPoint::class.java)
                    .lyricsHelper()
                val fetched = helper.getLyrics(next)
                database.query { upsert(LyricsEntity(next.id, fetched.lyrics, fetched.provider)) }
            }
        }
    }

    val lines by lyricsViewModel.lines.collectAsStateWithLifecycle()
    LaunchedEffect(lyrics, enabledLanguages, romanizeCyrillicByLine) {
        lyricsViewModel.processLyrics(lyrics, enabledLanguages, romanizeCyrillicByLine, false)
    }
    val visibleLines = remember(lines) { lines.filter { it.text.isNotBlank() } }

    val listState = rememberLazyListState()
    var manualIndex by remember { mutableIntStateOf(-1) }
    // Derived so the 100ms position poll only invalidates a line when the index actually moves.
    val activeIndex by remember(visibleLines, progressState) {
        derivedStateOf {
            visibleLines.indexOfLast { it.time <= progressState.playedMillis }.coerceAtLeast(0)
        }
    }
    val scrollIndex = if (manualIndex >= 0) manualIndex else activeIndex

    LaunchedEffect(mediaMetadata?.id) { manualIndex = -1 }
    LaunchedEffect(scrollIndex, visibleLines.size) {
        if (scrollIndex in 0 until visibleLines.size) listState.animateScrollToItem(scrollIndex)
    }

    when {
        lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = modifier,
            ) {
                AuraText(
                    value = notFoundStr,
                    px = 30,
                    weight = FontWeight.W700,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        visibleLines.isEmpty() -> Box(modifier = modifier)

        else -> {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(32.apx()),
                contentPadding = PaddingValues(16.apx(), 16.apx(), 16.apx(), 128.apx()),
                modifier = modifier
                    .auraFocused(corner = 24.apx())
                    .clip(RoundedCornerShape(24.apx()))
                    .focusable(true)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val step = when (event.key) {
                            Key.DirectionDown -> 1
                            Key.DirectionUp -> -1
                            else -> return@onPreviewKeyEvent false
                        }
                        val current = scrollIndex.coerceIn(0, visibleLines.lastIndex)
                        val target = (current + step).coerceIn(0, visibleLines.lastIndex)
                        if (target == current) return@onPreviewKeyEvent false
                        manualIndex = target
                        true
                    },
            ) {
                itemsIndexed(visibleLines) { index, entry ->
                    AuraLyricLine(entry = entry, active = index == activeIndex)
                }
            }
        }
    }
}

@Composable
private fun AuraLyricLine(
    entry: LyricsEntry,
    active: Boolean,
) {
    val romanized by entry.romanizedTextFlow.collectAsStateWithLifecycle(initialValue = null)
    // The mock gives lyric lines leading-[1.8], which AuraText has no slot for.
    Text(
        text = romanized?.takeIf { it.isNotBlank() } ?: entry.text,
        style = auraType(FontWeight.W700, 30).copy(lineHeight = LyricLineHeightPx.asp()),
        color = if (active) Color.White else Color.White.copy(alpha = 0.5f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Panels composed inside the player don't take the selector when they appear: the key stays on the
 * pill that opened them, which the scrim now covers, so the remote cannot reach the rows. Ask for the
 * first row until it actually holds the selector — a request answered while the window is still
 * coming up is cancelled rather than thrown, so the return value alone can't be trusted.
 */
@Composable
private fun auraTakePanelFocusOnOpen(
    focusRequester: FocusRequester,
    holdsFocus: () -> Boolean,
) {
    LaunchedEffect(Unit) {
        var tries = 0
        while (tries < ChainAnchorChecks && !holdsFocus()) {
            delay(ChainAnchorIntervalMs)
            tries++
            runCatching { focusRequester.requestFocus() }
        }
    }
}

@Composable
private fun AuraQueuePanel(
    playerConnection: PlayerConnection,
    isListenTogetherGuest: Boolean,
    onDismiss: () -> Unit,
) {
    val menuState = LocalMenuState.current
    val firstRowFocus = remember { FocusRequester() }
    var panelHoldsFocus by remember { mutableStateOf(false) }
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle(initialValue = -1)
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(AuraPx.LyricsPanelRadius.apx())

    // Drawn inside the player, this panel never becomes the key target: the transport row keeps the
    // selector and the player's focus trap will not hand it to rows it doesn't know. As its own
    // window it takes the keys, and BACK dismisses it the way the row menu already does.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        auraTakePanelFocusOnOpen(firstRowFocus) { panelHoldsFocus }

        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .fillMaxSize()
                .background(AuraMock.Scrim)
                // Tap-to-dismiss can't be a focus target: as a clickable it is the widest focusable
                // node in the window, so the selector lands on the wash instead of a row.
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(700.apx())
                    .padding(40.apx())
                    .clip(shape)
                    .background(AuraMock.Panel.copy(alpha = 0.95f))
                    .border(1.apx(), Color.White.copy(alpha = 0.1f), shape)
                    .padding(40.apx()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.apx()),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aura_ic_list_music),
                        contentDescription = null,
                        tint = AuraMock.Primary,
                        modifier = Modifier.size(24.apx()),
                    )
                    AuraText(
                        value = (queueTitle ?: stringResource(R.string.queue)).uppercase(),
                        px = 24,
                        weight = FontWeight.W700,
                        color = AuraMock.Primary,
                        trackingEm = 0.1f,
                    )
                }
                Spacer(modifier = Modifier.height(24.apx()))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.apx()),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(queueWindows) { index, window ->
                        val metadata = window.mediaItem.metadata
                        val isPlayingRow = index == currentWindowIndex
                        // Every Aura row opens its menu on a held ENTER, and the queue is no
                        // different: the trailing button is where a pointer finds the same command,
                        // but the remote only ever lights the row itself.
                        val openRowMenu: () -> Unit = {
                            menuState.show {
                                AuraQueueRowMenu(
                                    playerConnection = playerConnection,
                                    queueWindows = queueWindows,
                                    index = index,
                                    pinnedIndex = currentWindowIndex,
                                    shuffleEnabled = shuffleEnabled,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        }
                        AuraListRow(
                            title = metadata?.title
                                ?: window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
                            subtitle = metadata?.artists?.joinToString(", ") { it.name }.orEmpty(),
                            active = isPlayingRow,
                            onLongClick = if (!isListenTogetherGuest && !isPlayingRow) openRowMenu else null,
                            onClick = {
                                // The list is in playback order, so its position is only the real media
                                // item index while shuffle is off. firstPeriodIndex is the one on both.
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            },
                            modifier = if (index == 0) {
                                Modifier
                                    .focusGroup()
                                    .focusRequester(firstRowFocus)
                                    .onFocusEvent { panelHoldsFocus = it.hasFocus }
                            } else {
                                Modifier.focusGroup()
                            },
                            trailing = {
                                // The playing row has nothing to move, play next or remove, so it gets
                                // no button rather than one that opens an empty panel.
                                if (!isListenTogetherGuest && !isPlayingRow) {
                                    AuraGhostButton(
                                        icon = R.drawable.aura_ic_more_horizontal,
                                        boxPx = 52,
                                        glyphPx = 24,
                                        onClick = openRowMenu,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The phone queue reorders by drag and removes by swipe; a remote does neither, so the row menu
 * carries the same powers as explicit choices and reorder becomes move up / move down.
 */
@Composable
private fun AuraQueueRowMenu(
    playerConnection: PlayerConnection,
    queueWindows: List<Timeline.Window>,
    index: Int,
    pinnedIndex: Int,
    shuffleEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val window = queueWindows.getOrNull(index) ?: return
    val metadata = window.mediaItem.metadata
    val isPlayingRow = index == pinnedIndex

    Column(
        verticalArrangement = Arrangement.spacedBy(8.apx()),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AuraText(
            value = metadata?.title
                ?: window.mediaItem.mediaMetadata.title?.toString().orEmpty(),
            px = 30,
            weight = FontWeight.W700,
            color = AuraMock.Primary,
            maxLines = 2,
            modifier = Modifier.padding(bottom = 12.apx()),
        )
        if (!isPlayingRow) {
            AuraOptionRow(
                label = stringResource(R.string.play_next),
                onClick = {
                    onDismiss()
                    playerConnection.playNext(window.mediaItem)
                },
            )
        }
        if (index > 0 && !isPlayingRow) {
            AuraOptionRow(
                label = stringResource(R.string.move_up),
                onClick = {
                    onDismiss()
                    moveAuraQueueRow(playerConnection.player, queueWindows, index, index - 1, shuffleEnabled)
                },
            )
        }
        if (index < queueWindows.lastIndex && !isPlayingRow) {
            AuraOptionRow(
                label = stringResource(R.string.move_down),
                onClick = {
                    onDismiss()
                    moveAuraQueueRow(playerConnection.player, queueWindows, index, index + 1, shuffleEnabled)
                },
            )
        }
        if (!isPlayingRow) {
            AuraOptionRow(
                label = stringResource(R.string.remove_from_queue),
                onClick = {
                    onDismiss()
                    playerConnection.player.removeMediaItem(window.firstPeriodIndex)
                },
            )
        }
    }
}

/**
 * [windows] is the queue as the player intends to play it, which is shuffled order when shuffle is
 * on — and `moveMediaItem` only speaks the natural order, so under shuffle the visible reorder has
 * to go through the shuffle order instead.
 */
private fun moveAuraQueueRow(
    player: ExoPlayer,
    windows: List<Timeline.Window>,
    from: Int,
    to: Int,
    shuffleEnabled: Boolean,
) {
    val mediaItemOrder = windows.map { it.firstPeriodIndex }
    if (!shuffleEnabled) {
        player.moveMediaItem(mediaItemOrder[from], mediaItemOrder[to])
    } else {
        val reordered = mediaItemOrder.toMutableList().move(from, to)
        player.setShuffleOrder(DefaultShuffleOrder(reordered.toIntArray(), System.currentTimeMillis()))
    }
}

@Composable
private fun AuraSleepTimerPanel(
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
) {
    val sleepTimer = playerConnection.service.sleepTimer
    val stopAfterCurrentSong by rememberPreference(SleepTimerStopAfterCurrentSongKey, false)
    val fadeOut by rememberPreference(SleepTimerFadeOutKey, false)
    val timerActive = sleepTimer?.isActive == true
    var remaining by remember { mutableLongStateOf(0L) }

    LaunchedEffect(timerActive) {
        if (timerActive) {
            while (isActive) {
                remaining = if (sleepTimer?.pauseWhenSongEnd == true) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    (sleepTimer?.triggerTime ?: 0L) - System.currentTimeMillis()
                }
                delay(1000L)
            }
        }
    }

    val options = remember { listOf(5, 15, 30, 45, 60, 120) }
    val shape = RoundedCornerShape(32.apx())
    val disableStr = stringResource(R.string.disable)
    val firstRowFocus = remember { FocusRequester() }
    var panelHoldsFocus by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        auraTakePanelFocusOnOpen(firstRowFocus) { panelHoldsFocus }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(AuraMock.Scrim)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(700.apx())
                    .clip(shape)
                    .background(AuraMock.Panel.copy(alpha = 0.95f))
                    .border(1.apx(), Color.White.copy(alpha = 0.1f), shape)
                    .padding(40.apx()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.apx()),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.aura_ic_timer),
                        contentDescription = null,
                        tint = AuraMock.Primary,
                        modifier = Modifier.size(24.apx()),
                    )
                    AuraText(
                        value = stringResource(R.string.sleep_timer).uppercase(),
                        px = 24,
                        weight = FontWeight.W700,
                        color = AuraMock.Primary,
                        trackingEm = 0.1f,
                    )
                }
                if (timerActive) {
                    Spacer(modifier = Modifier.height(16.apx()))
                    AuraText(
                        value = makeTimeString(remaining.coerceAtLeast(0L)),
                        px = 60,
                        weight = FontWeight.W800,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(24.apx()))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.apx()),
                    modifier = Modifier.heightIn(max = 480.apx()),
                ) {
                    itemsIndexed(options) { index, minutes ->
                        AuraOptionRow(
                            label = "$minutes min",
                            modifier = if (index == 0) {
                            Modifier
                                .focusRequester(firstRowFocus)
                                .onFocusEvent { panelHoldsFocus = it.hasFocus }
                        } else {
                            Modifier
                        },
                        ) {
                            sleepTimer?.start(
                                minute = minutes,
                                stopAfterCurrentSong = stopAfterCurrentSong,
                                fadeOut = fadeOut,
                            )
                            onDismiss()
                        }
                    }
                    item {
                        AuraOptionRow(label = stringResource(R.string.end_of_song)) {
                            sleepTimer?.start(minute = -1)
                            onDismiss()
                        }
                    }
                    item {
                        AuraOptionRow(label = disableStr) {
                            sleepTimer?.clear()
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuraOptionRow(
    label: String,
    subtitle: String = "",
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val corner = 24.apx()
    Column(
        modifier = modifier
            .auraFocused(
                corner = corner,
                fill = false,
                ring = false,
                grow = false,
                shadow = false,
                onFocusChanged = { focused = it },
            )
            .fillMaxWidth()
            .auraPillFocused(focused, corner = corner, ring = false)
            .clip(RoundedCornerShape(corner))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.apx(), vertical = 16.apx()),
    ) {
        AuraText(
            value = label,
            px = 26,
            weight = FontWeight.W700,
            color = when {
                focused -> AuraMock.OnPrimary
                selected -> AuraMock.Primary
                else -> Color.White
            },
        )
        if (subtitle.isNotBlank()) {
            AuraText(
                value = subtitle,
                px = 20,
                weight = FontWeight.W500,
                color = if (focused) AuraMock.OnPrimary.copy(alpha = 0.8f) else AuraMock.Muted,
            )
        }
    }
}
