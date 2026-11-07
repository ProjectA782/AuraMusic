/**
 * Aura Music — the floating action a list shows when you scroll back up.
 *
 * Phones keep Material's FAB. The TV shell has no Material shapes: the reference draws every
 * actionable thing as a full-round pill, so the same stack is rendered that way there.
 */

package com.aura.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.FullRound
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.utils.isScrollingUp

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    showRecognition: Boolean = true,
    showMainAction: Boolean = true,
) {
    FabStack(
        visible = visible && lazyListState.isScrollingUp(),
        icon = icon,
        onClick = onClick,
        onRecognitionClick = onRecognitionClick,
        showRecognition = showRecognition,
        showMainAction = showMainAction,
    )
}

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyGridState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    showRecognition: Boolean = true,
    showMainAction: Boolean = true,
) {
    FabStack(
        visible = visible && lazyListState.isScrollingUp(),
        icon = icon,
        onClick = onClick,
        onRecognitionClick = onRecognitionClick,
        showRecognition = showRecognition,
        showMainAction = showMainAction,
    )
}

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    scrollState: ScrollState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    showRecognition: Boolean = true,
    showMainAction: Boolean = true,
) {
    FabStack(
        visible = visible && scrollState.isScrollingUp(),
        icon = icon,
        onClick = onClick,
        onRecognitionClick = onRecognitionClick,
        showRecognition = showRecognition,
        showMainAction = showMainAction,
    )
}

@Composable
private fun BoxScope.FabStack(
    visible: Boolean,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)?,
    showRecognition: Boolean,
    showMainAction: Boolean,
) {
    if (!showRecognition && !showMainAction) return

    val onTv = LocalAuraTvShell.current

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(if (onTv) 24.apx() else 16.dp),
        ) {
            val recognitionClick = onRecognitionClick?.takeIf { showRecognition }
            val recognitionLabel = if (recognitionClick != null) {
                stringResource(R.string.recognize_music)
            } else {
                null
            }
            if (recognitionClick != null) {
                when {
                    onTv -> AuraFabButton(
                        icon = R.drawable.mic,
                        contentDescription = recognitionLabel,
                        diameter = if (showMainAction) 72 else 96,
                        container = if (showMainAction) AuraMock.Panel else AuraMock.Primary,
                        glyph = if (showMainAction) AuraMock.Muted else AuraMock.OnPrimary,
                        onClick = recognitionClick,
                    )
                    showMainAction -> SmallFloatingActionButton(
                        onClick = recognitionClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.mic),
                            contentDescription = recognitionLabel,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    else -> FloatingActionButton(
                        onClick = recognitionClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.mic),
                            contentDescription = recognitionLabel,
                        )
                    }
                }
                if (showMainAction) Spacer(modifier = Modifier.height(if (onTv) 16.apx() else 12.dp))
            }
            if (showMainAction) {
                if (onTv) {
                    AuraFabButton(
                        icon = icon,
                        contentDescription = null,
                        diameter = 96,
                        container = AuraMock.Primary,
                        glyph = AuraMock.OnPrimary,
                        onClick = onClick,
                    )
                } else {
                    FloatingActionButton(onClick = onClick) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}

/** The reference's `rounded-full` button: a flat disc that takes the shell's focus ring. */
@Composable
private fun AuraFabButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    diameter: Int,
    container: Color,
    glyph: Color,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .auraFocused(corner = FullRound, fill = false, onFocusChanged = { focused = it })
            .size(diameter.apx())
            .clip(CircleShape)
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (focused) AuraMock.OnPrimary else glyph,
            modifier = Modifier.size((diameter * 0.42f).apx()),
        )
    }
}
