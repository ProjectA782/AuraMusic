/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.R
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.apx
import com.aura.music.utils.Updater

@Composable
fun ReleaseNotesCard() {
    val releaseInfo = Updater.getCachedLatestRelease() ?: return

    if (LocalAuraTvShell.current) {
        // The reference has no elevated card: a notice is a flat #211F26 panel.
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                .background(AuraMock.Panel, RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                .padding(28.apx()),
        ) {
            AuraText(
                value = stringResource(R.string.release_notes),
                px = 26,
                weight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.apx()))
            AuraText(
                value = releaseInfo.description,
                px = 20,
                color = AuraMock.Muted,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        }
        Spacer(modifier = Modifier.height(16.apx()))
        return
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.release_notes),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = releaseInfo.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}
