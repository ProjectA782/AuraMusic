/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused

@Composable
private fun NewActionButton(
    icon: @Composable () -> Unit,
    text: String,
    onClick: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val animatedBackground by animateColorAsState(
        targetValue = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "background",
    )

    val animatedContent by animateColorAsState(
        targetValue = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        animationSpec = tween(200),
        label = "content",
    )

    var performAction by remember { mutableStateOf(false) }

    if (performAction) {
        onClick()
        LaunchedEffect(Unit) {
            performAction = false
        }
    }

    if (LocalAuraTvShell.current) {
        // The reference's action tile: a flat panel, its glyph in the accent when the remote is on it,
        // and no Material card elevation anywhere.
        var tvFocused by remember { mutableStateOf(false) }
        Column(
            modifier =
                modifier
                    .auraFocused(corner = AuraPx.PickCardRadius.apx(), fill = false, onFocusChanged = { tvFocused = it })
                    .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                    .background(if (tvFocused) AuraMock.FocusFill else AuraMock.Panel)
                    .border(
                        width = 2.apx(),
                        color = if (tvFocused) AuraMock.Primary else AuraMock.Border.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(AuraPx.PickCardRadius.apx()),
                    )
                    .clickable(enabled = enabled) { performAction = true }
                    .padding(vertical = 20.apx(), horizontal = 12.apx()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(28.apx()),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides
                        when {
                            !enabled -> AuraMock.Muted.copy(alpha = 0.4f)
                            tvFocused -> AuraMock.Primary
                            else -> AuraMock.Text
                        },
                    content = icon,
                )
            }

            Spacer(modifier = Modifier.height(10.apx()))

            AuraText(
                value = text,
                px = 18,
                weight = FontWeight.Medium,
                color = when {
                    !enabled -> AuraMock.Muted.copy(alpha = 0.4f)
                    tvFocused -> AuraMock.Text
                    else -> AuraMock.Muted
                },
                maxLines = 2,
                modifier = Modifier.basicMarquee(),
            )
        }
        return
    }

    Card(
        modifier =
            modifier
                .clickable(enabled = enabled) { performAction = true },
        colors =
            CardDefaults.cardColors(
                containerColor = animatedBackground,
            ),
        shape = RoundedCornerShape(16.dp),
        elevation =
            CardDefaults.cardElevation(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = animatedContent,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}

@Composable
fun NewActionGrid(
    actions: List<NewAction>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
) {
    val rows = actions.chunked(columns)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { action ->
                    NewActionButton(
                        icon = action.icon,
                        text = action.text,
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f),
                        enabled = action.enabled,
                        backgroundColor =
                            if (action.backgroundColor !=
                                Color.Unspecified
                            ) {
                                action.backgroundColor
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        contentColor =
                            if (action.contentColor !=
                                Color.Unspecified
                            ) {
                                action.contentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }

                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

data class NewAction(
    val icon: @Composable () -> Unit,
    val text: String,
    val onClick: @Composable () -> Unit,
    val enabled: Boolean = true,
    val backgroundColor: Color = Color.Unspecified,
    val contentColor: Color = Color.Unspecified,
)
