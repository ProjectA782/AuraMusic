/**
 * Aura Music — the now-playing banner heading every page of the TV shell.
 *
 * Cover, title and artist, then prev, play/pause and next: the transport a listener reaches while
 * still browsing. Choosing the track column opens the full-screen player.
 *
 * This replaces the reference's floating bar. A bar at the foot of the page sits under its last row,
 * so DPAD_DOWN answered for the transport before the list still waiting to be scrolled ever saw the
 * press; a banner above the content leaves DOWN to the page and gives UP to the selector.
 */

package com.aura.music.ui.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.aura.music.LocalListenTogetherManager
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.listentogether.RoomRole
import com.aura.music.playback.PlayerConnection
import com.aura.music.ui.utils.resize
import com.aura.music.utils.joinToArtistString

@Composable
fun AuraNowPlayingBanner(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    val listenTogetherManager = LocalListenTogetherManager.current
    val roleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = roleState?.value == RoomRole.GUEST

    // The rail counts the transport as one of the places the selector may legitimately live, so say
    // whether it is here.
    val transportHolds = LocalAuraTransportHoldsFocus.current

    // A banner that disappears with the selector still on it leaves the next press with nothing to
    // move, so hand the key back to the page's own landing spot.
    val pageFocus = LocalAuraPageFocus.current
    val view = LocalView.current
    DisposableEffect(view) {
        onDispose {
            view.post {
                if (view.findFocus() == null) {
                    pageFocus?.requestFirstFocus()
                }
            }
        }
    }

    val conjunction = stringResource(R.string.and)

    // The transport stops stay out of the D-pad until the selector is already inside the banner.
    // Directional search ranks the candidates that overlap the presser, and the buttons sit above the
    // right of every page, so a plain UP from a tile or the page header would land on skip-next and
    // one ENTER would move the track the listener was about to choose. With the buttons locked, the
    // only candidate above the page is the track column, which is the stop that opens the player.
    var bannerHoldsFocus by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.apx()),
        modifier = modifier
            .padding(
                start = auraPagePadding(),
                top = AuraPx.NowPlayingBannerGutter.apx(),
                end = auraPagePadding(),
            )
            .fillMaxWidth()
            .height(AuraPx.NowPlayingBannerHeight.apx())
            .onFocusEvent {
                transportHolds?.value = it.hasFocus
                bannerHoldsFocus = it.hasFocus
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.apx()),
            modifier = Modifier
                .weight(1f)
                // No ring and no shadow: the banner is page furniture, so focus lights it the way the
                // full player's transport does — a wash over the content, not an outline around it.
                .auraFocused(corner = 20.dp, grow = false, ring = false, shadow = false, claim = false)
                .clip(RoundedCornerShape(20.apx()))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExpand,
                )
                .padding(horizontal = 12.apx(), vertical = 8.apx()),
        ) {
            Box(
                modifier = Modifier
                    .size(AuraPx.NowPlayingBannerCover.apx())
                    .clip(RoundedCornerShape(16.apx()))
                    .background(AuraMock.Page),
            ) {
                mediaMetadata?.let { metadata ->
                    val thumbnailUrl = remember(metadata.thumbnailUrl) { metadata.thumbnailUrl?.resize(256, 256) }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                mediaMetadata?.let { metadata ->
                    AuraText(
                        value = metadata.title,
                        px = 24,
                        weight = FontWeight.Bold,
                        color = AuraMock.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(2.apx()))
                    if (metadata.artists.any { it.name.isNotBlank() }) {
                        AuraText(
                            value = metadata.artists.joinToArtistString(" $conjunction ") { it.name },
                            px = 20,
                            weight = FontWeight.Medium,
                            color = AuraMock.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.apx()),
        ) {
            AuraGhostButton(
                icon = R.drawable.aura_ic_skip_back,
                boxPx = AuraPx.NowPlayingBannerControl,
                glyphPx = 30,
                claim = false,
                fill = true,
                ring = false,
                shadow = false,
                enabled = canSkipPrevious && !isListenTogetherGuest,
                modifier = Modifier.focusProperties { canFocus = bannerHoldsFocus },
                onClick = playerConnection::seekToPrevious,
            )
            AuraBannerPlayPause(
                playerConnection = playerConnection,
                playbackState = playbackState,
                isPlaying = isPlaying,
                focusable = bannerHoldsFocus,
            )
            AuraGhostButton(
                icon = R.drawable.aura_ic_skip_forward,
                boxPx = AuraPx.NowPlayingBannerControl,
                glyphPx = 30,
                claim = false,
                fill = true,
                ring = false,
                shadow = false,
                enabled = canSkipNext && !isListenTogetherGuest,
                modifier = Modifier.focusProperties { canFocus = bannerHoldsFocus },
                onClick = playerConnection::seekToNext,
            )
        }
    }
}

@Composable
private fun AuraBannerPlayPause(
    playerConnection: PlayerConnection,
    playbackState: Int,
    isPlaying: Boolean,
    focusable: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val ended = playbackState == Player.STATE_ENDED
    // The mock nudges the triangle right so it reads optically centred in the circle.
    val glyphOffset = if (isPlaying) 0.apx() else 4.apx()

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .focusProperties { canFocus = focusable }
            .auraFocused(
                corner = FullRound,
                fill = false,
                grow = false,
                ring = false,
                shadow = false,
                claim = false,
                onFocusChanged = { focused = it },
            )
            .size(AuraPx.NowPlayingBannerControl.apx())
            .clip(CircleShape)
            .background(AuraMock.Primary, CircleShape)
            .auraPillFocused(focused = focused, corner = FullRound, ring = false)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                if (ended) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.togglePlayPause()
                }
            },
    ) {
        Icon(
            painter = painterResource(
                if (isPlaying) R.drawable.aura_ic_pause else R.drawable.aura_ic_play,
            ),
            contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
            tint = AuraPillGlyphColor(focused),
            modifier = Modifier
                .size(32.apx())
                .padding(start = glyphOffset),
        )
    }
}
