/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.recognition

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.aura.music.LocalDatabase
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.db.entities.RecognitionHistory
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.FullRound
import com.aura.music.ui.aura.LocalAuraPageFocus
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.SearchRoutes
import com.aura.shazamkit.models.RecognitionResult
import com.aura.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** How long the page keeps aiming at the control its state just swapped in, and how often. */
private const val StateAnchorChecks = 8
private const val StateAnchorIntervalMs = 140L

/**
 * Song recognition, told through the shell's page language.
 *
 * Upstream drew this as a phone screen: a Material scaffold whose only control was a tap target, so
 * a remote had nothing to stop on. The recogniser itself - the service, the permission request, the
 * history write, the six states - is untouched; what changed is that every state now ends in a pill
 * or a disc the D-pad can land on, sized and coloured like the rest of the television shell, and the
 * page re-aims at that control whenever a state swap disposes the one that held the selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    navController: NavController,
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    // Only reset in Ready state: Listening/Processing belong to a running widget-service
    // recognition that must not be cancelled; Success/NoMatch/Error are results pending
    // display and history saving.
    LaunchedEffect(Unit) {
        if (com.aura.music.recognition.MusicRecognitionService.recognitionStatus.value
                is RecognitionStatus.Ready
        ) {
            com.aura.music.recognition.MusicRecognitionService
                .reset()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.aura.music.recognition.MusicRecognitionService
                .reset()
        }
    }

    // Observe recognition status from service for real-time updates (Listening -> Processing -> Result)
    val recognitionStatus by com.aura.music.recognition.MusicRecognitionService.recognitionStatus
        .collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasPermission = isGranted
            if (isGranted) {
                coroutineScope.launch {
                    com.aura.music.recognition.MusicRecognitionService
                        .recognize(context)
                }
            }
        }

    fun startRecognition() {
        if (hasPermission) {
            coroutineScope.launch {
                com.aura.music.recognition.MusicRecognitionService
                    .recognize(context)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (autoStart &&
            com.aura.music.recognition.MusicRecognitionService.recognitionStatus.value
                is RecognitionStatus.Ready
        ) {
            startRecognition()
        }
    }

    fun resetToReady() {
        com.aura.music.recognition.MusicRecognitionService
            .reset()
    }

    fun saveToHistory(result: RecognitionResult) {
        // Skip if the widget service already persisted this result to avoid a duplicate entry
        if (com.aura.music.recognition.MusicRecognitionService.resultSavedExternally) return
        coroutineScope.launch(Dispatchers.IO) {
            database.query {
                insert(
                    RecognitionHistory(
                        trackId = result.trackId,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl,
                        coverArtHqUrl = result.coverArtHqUrl,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        label = result.label,
                        shazamUrl = result.shazamUrl,
                        appleMusicUrl = result.appleMusicUrl,
                        spotifyUrl = result.spotifyUrl,
                        isrc = result.isrc,
                        youtubeVideoId = result.youtubeVideoId,
                        recognizedAt = LocalDateTime.now(),
                    ),
                )
            }
        }
    }

    // Each state's control offers itself to the rail in composition order, so the offer list always
    // holds exactly what the current state can act on. The shell re-arms on navigation but not on a
    // state swap inside one screen, and by then the control that carried the selector is gone - so
    // ask the page for its first offer again, and stop as soon as one reports holding it.
    val pageAnchors = LocalAuraPageFocus.current
    LaunchedEffect(recognitionStatus) {
        val anchors = pageAnchors ?: return@LaunchedEffect
        var tries = 0
        while (tries < StateAnchorChecks && !anchors.anchorHoldsFocus) {
            anchors.requestFirstFocus()
            delay(StateAnchorIntervalMs)
            tries++
        }
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
        // First child and pinned, as on every page converted before this one: emitted after the body
        // this bar becomes a sibling of it and paints across the middle of the viewport.
        TopAppBar(
            title = { Text(stringResource(R.string.recognize_music)) },
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
                    onClick = { navController.navigate("recognition_history") },
                    onLongClick = { navController.navigate("recognition_history") },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.history),
                        contentDescription = stringResource(R.string.recognition_history),
                    )
                }
            },
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = auraPagePadding()),
            contentAlignment = Alignment.Center,
        ) {
            when (val status = recognitionStatus) {
                is RecognitionStatus.Ready -> {
                    ReadyState(onStartRecognition = ::startRecognition)
                }

                is RecognitionStatus.Listening -> {
                    ListeningState(
                        onCancel = {
                            com.aura.music.recognition.MusicRecognitionService
                                .reset()
                        },
                    )
                }

                is RecognitionStatus.Processing -> {
                    ProcessingState()
                }

                is RecognitionStatus.Success -> {
                    SuccessState(
                        result = status.result,
                        onPlayOnApp = { result ->
                            // Search for the track on YouTube Music
                            val searchQuery = "${result.title} ${result.artist}"
                            navController.navigate(SearchRoutes.resultRoute(searchQuery))
                        },
                        onTryAgain = {
                            startRecognition()
                        },
                        onClose = ::resetToReady,
                        onSaveToHistory = ::saveToHistory,
                    )
                }

                is RecognitionStatus.NoMatch -> {
                    NoMatchState(
                        message = status.message,
                        onTryAgain = {
                            startRecognition()
                        },
                    )
                }

                is RecognitionStatus.Error -> {
                    ErrorState(
                        message = status.message,
                        onTryAgain = {
                            startRecognition()
                        },
                    )
                }
            }
        }
    }
}

/**
 * The listening disc. Ring and core share one pulse, read inside the layer rather than the body: a
 * state read here would recompose the page sixty times a second on a device with no spare frames.
 */
@Composable
private fun RecognitionDisc(
    onClick: () -> Unit,
    pulse: Boolean,
) {
    val scale =
        if (pulse) {
            rememberInfiniteTransition(label = "recognition pulse").animateFloat(
                initialValue = 1f,
                targetValue = 1.08f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "recognition pulse scale",
            )
        } else {
            null
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(300.apx()),
    ) {
        Box(
            modifier =
                Modifier
                    .graphicsLayer {
                        val s = scale?.value ?: 1f
                        scaleX = s * 1.12f
                        scaleY = s * 1.12f
                    }
                    .size(300.apx())
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    AuraMock.Primary.copy(alpha = 0.24f),
                                    AuraMock.Primary.copy(alpha = 0.07f),
                                    Color.Transparent,
                                ),
                        ),
                    ),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .auraFocused(corner = FullRound, grow = false)
                    .graphicsLayer {
                        val s = scale?.value ?: 1f
                        scaleX = s
                        scaleY = s
                    }
                    .size(196.apx())
                    .clip(CircleShape)
                    .background(AuraMock.Primary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
        ) {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = null,
                tint = AuraMock.OnPrimary,
                modifier = Modifier.size(84.apx()),
            )
        }
    }
}

@Composable
private fun ReadyState(onStartRecognition: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(36.apx()),
    ) {
        RecognitionDisc(onClick = onStartRecognition, pulse = false)
        AuraText(
            value = stringResource(R.string.tap_to_recognize),
            px = 30,
            weight = FontWeight.Medium,
            color = AuraMock.Muted,
        )
    }
}

@Composable
private fun ListeningState(onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.apx()),
    ) {
        RecognitionDisc(onClick = onCancel, pulse = true)
        AuraText(
            value = stringResource(R.string.listening),
            px = 30,
            weight = FontWeight.Bold,
            color = AuraMock.Primary,
        )
        AuraPillButton(
            label = stringResource(R.string.cancel),
            icon = R.drawable.close,
            onClick = onCancel,
        )
    }
}

@Composable
private fun ProcessingState() {
    val rotation =
        rememberInfiniteTransition(label = "recognition processing").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing)),
            label = "recognition processing rotation",
        )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(36.apx()),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .graphicsLayer { rotationZ = rotation.value }
                    .size(220.apx())
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    AuraMock.Primary.copy(alpha = 0.35f),
                                    AuraMock.Primary,
                                ),
                        ),
                    ),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(184.apx()).clip(CircleShape).background(AuraMock.Page),
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = AuraMock.Primary,
                    modifier = Modifier.size(72.apx()),
                )
            }
        }
        AuraText(
            value = stringResource(R.string.processing),
            px = 30,
            weight = FontWeight.Medium,
            color = AuraMock.Muted,
        )
    }
}

@Composable
private fun SuccessState(
    result: RecognitionResult,
    onPlayOnApp: (RecognitionResult) -> Unit,
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
    onSaveToHistory: (RecognitionResult) -> Unit,
) {
    // Save to history when success is shown
    LaunchedEffect(result) {
        onSaveToHistory(result)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.apx()),
    ) {
        Box(
            modifier =
                Modifier
                    .size(340.apx())
                    .clip(RoundedCornerShape(28.apx()))
                    .background(AuraMock.Panel),
        ) {
            AsyncImage(
                model = result.coverArtHqUrl ?: result.coverArtUrl,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.apx()),
        ) {
            AuraText(
                value = result.title,
                px = 52,
                weight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
            )
            AuraText(
                value = result.artist,
                px = 30,
                weight = FontWeight.Medium,
                color = AuraMock.Muted,
            )
            result.album?.let { album ->
                AuraText(
                    value = album,
                    px = 24,
                    color = AuraMock.Muted.copy(alpha = 0.75f),
                )
            }
        }

        // The primary action composes first, so it is the offer the rail's RIGHT and the page's own
        // re-anchor both reach for.
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.apx()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuraPillButton(
                label = stringResource(R.string.play_on_app),
                icon = R.drawable.aura_ic_play,
                filled = true,
                onClick = { onPlayOnApp(result) },
            )
            AuraPillButton(
                label = stringResource(R.string.re_listen),
                icon = R.drawable.mic,
                onClick = onTryAgain,
            )
            AuraPillButton(
                label = stringResource(R.string.close),
                icon = R.drawable.close,
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun NoMatchState(
    message: String,
    onTryAgain: () -> Unit,
) {
    ResultMessageState(
        icon = R.drawable.close,
        title = stringResource(R.string.no_match_found),
        message = message,
        onTryAgain = onTryAgain,
    )
}

@Composable
private fun ErrorState(
    message: String,
    onTryAgain: () -> Unit,
) {
    ResultMessageState(
        icon = R.drawable.error,
        title = stringResource(R.string.recognition_error),
        message = message,
        onTryAgain = onTryAgain,
    )
}

/**
 * A recognition that came back with nothing to play. The two states differ only in glyph and heading,
 * so they share the shape: a flat error plate, the reason, and the one pill that retries.
 */
@Composable
private fun ResultMessageState(
    icon: Int,
    title: String,
    message: String,
    onTryAgain: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(28.apx()),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(168.apx())
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(76.apx()),
            )
        }
        AuraText(
            value = title,
            px = 40,
            weight = FontWeight.Bold,
            color = Color.White,
        )
        AuraText(
            value = message,
            px = 24,
            color = AuraMock.Muted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 40.apx()),
        )
        AuraPillButton(
            label = stringResource(R.string.try_again),
            icon = R.drawable.refresh,
            filled = true,
            onClick = onTryAgain,
        )
    }
}
