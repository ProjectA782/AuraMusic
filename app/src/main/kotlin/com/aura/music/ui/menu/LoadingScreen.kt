@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aura.music.LocalAuraTvShell
import com.aura.music.R
import com.aura.music.ui.aura.AuraDialogActions
import com.aura.music.ui.aura.AuraDialogScaffold
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.FullRound
import com.aura.music.ui.aura.apx

@Composable
fun LoadingScreen(
    isVisible: Boolean,
    value: Int,
    songTitle: String? = null,
    onCancel: (() -> Unit)? = null
) {
    if (!isVisible) return

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
    ) {
        if (LocalAuraTvShell.current) {
            // The reference's progress bar is a flat rounded track, not the expressive wavy one.
            AuraDialogScaffold(
                onDismiss = { },
                modifier = Modifier.widthIn(max = 900.apx()),
                maxWidthPx = 900,
            ) {
                AuraText(
                    value = stringResource(R.string.importing_playlist),
                    px = 30,
                    weight = FontWeight.Bold,
                    color = AuraMock.Text,
                )
                Spacer(modifier = Modifier.height(8.apx()))
                AuraText(
                    value = songTitle?.takeIf { it.isNotBlank() } ?: "…",
                    px = 22,
                    color = AuraMock.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(24.apx()))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.apx())
                        .background(AuraMock.Page, RoundedCornerShape(FullRound))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value / 100f)
                            .height(12.apx())
                            .background(AuraMock.Primary, RoundedCornerShape(FullRound))
                    )
                }
                Spacer(modifier = Modifier.height(16.apx()))
                AuraText(
                    value = stringResource(R.string.progress_percent, value.toString()),
                    px = 20,
                    color = AuraMock.Muted,
                )
                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(24.apx()))
                    AuraDialogActions {
                        AuraPillButton(
                            label = stringResource(R.string.cancel),
                            onClick = onCancel,
                        )
                    }
                }
            }
            return@Dialog
        }

        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.playlist_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp)
            )

            Text(
                text = stringResource(R.string.importing_playlist),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (songTitle != null && songTitle.isNotBlank()) {
                Text(
                    text = songTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearWavyProgressIndicator(
                progress = { value / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )

            Text(
                text = stringResource(R.string.progress_percent, value.toString()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (onCancel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}
