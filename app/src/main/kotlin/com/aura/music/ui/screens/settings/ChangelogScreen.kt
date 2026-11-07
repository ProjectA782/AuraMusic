/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraDialogActions
import com.aura.music.ui.aura.AuraDialogScaffold
import com.aura.music.ui.aura.AuraDialogTitle
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.FullRound
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.aura.auraType
import com.aura.music.ui.aura.requestFocusSafely
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.music.R
import com.aura.music.BuildConfig
import com.aura.music.utils.ReleaseInfo
import com.aura.music.utils.Updater
import kotlinx.coroutines.delay

@Composable
fun ChangelogScreen(
    onDismiss: () -> Unit
) {
    var releases by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Updater.getAllReleases().onSuccess { allReleases ->
            releases = allReleases.filter { release ->
                Updater.compareVersions(BuildConfig.BASE_VERSION_NAME, release.tagName) >= 0
            }
            isLoading = false
        }.onFailure {
            isLoading = false
        }
    }

    val firstRowFocus = remember { FocusRequester() }
    // A dialog window starts with no selection of its own, and the rows only exist once the fetch
    // lands, so aim at the first one once it has actually been composed.
    LaunchedEffect(isLoading, releases.size) {
        if (isLoading || releases.isEmpty()) return@LaunchedEffect
        var tries = 0
        while (tries < 10 && !firstRowFocus.requestFocusSafely()) {
            delay(120)
            tries++
        }
    }

    AuraDialogScaffold(onDismiss = onDismiss) {
        AuraDialogTitle(value = stringResource(R.string.changelog))

        Spacer(modifier = Modifier.height(20.apx()))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.apx())
                .background(AuraMock.Border, RoundedCornerShape(FullRound))
        )

        Spacer(modifier = Modifier.height(24.apx()))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(28.apx()),
            contentPadding = PaddingValues(bottom = 8.apx()),
        ) {
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.apx()),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.apx()),
                            strokeWidth = 5.apx(),
                            color = AuraMock.Primary,
                        )
                    }
                }
            } else if (releases.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.apx()),
                        contentAlignment = Alignment.Center,
                    ) {
                        AuraText(
                            value = stringResource(R.string.changelog_empty),
                            px = 24,
                            color = AuraMock.Muted,
                        )
                    }
                }
            } else {
                itemsIndexed(releases) { index, release ->
                    // The row, not its text, has to be the D-pad stop: a plain Column takes no focus,
                    // so a body taller than the panel would never scroll into view.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
                            // Claiming would publish every release as a landing spot for the rail's
                            // DPAD_RIGHT, and the anchor list is capped — a modal must not evict the page.
                            .auraFocused(corner = AuraPx.PickCardRadius.apx(), fill = false, claim = false)
                            .focusable(),
                    ) {
                        ReleaseItem(release)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.apx()))

        AuraDialogActions {
            AuraPillButton(
                label = stringResource(R.string.close),
                filled = true,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
fun ReleaseItem(release: ReleaseInfo) {
    if (LocalAuraTvShell.current) {
        // The reference's badge is a filled primary pill; its body is a flat panel, not a card.
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AuraText(
                    value = release.tagName,
                    px = 18,
                    weight = FontWeight.Bold,
                    color = AuraMock.OnPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(FullRound))
                        .background(AuraMock.Primary, RoundedCornerShape(FullRound))
                        .padding(horizontal = 16.apx(), vertical = 6.apx()),
                )

                AuraText(
                    value = release.releaseDate.split("T").firstOrNull() ?: "",
                    px = 20,
                    color = AuraMock.Muted,
                )
            }

            Spacer(modifier = Modifier.height(12.apx()))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                    .background(AuraMock.Panel, RoundedCornerShape(AuraPx.PickCardRadius.apx()))
                    .padding(28.apx()),
            ) {
                ProvideTextStyle(auraType(FontWeight.Normal, 24)) {
                    CompositionLocalProvider(LocalContentColor provides AuraMock.Muted) {
                        MarkdownText(release.description)
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape
            ) {
                Text(
                    text = release.tagName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = release.releaseDate.split("T").firstOrNull() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                MarkdownText(release.description)
            }
        }
    }
}

@Composable
fun MarkdownText(text: String) {
    val lines = text.split("\n")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.filter { it.isNotBlank() }.forEach { line ->
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("#")) {
                val level = trimmedLine.takeWhile { it == '#' }.length
                val headerText = trimmedLine.substring(level).trim()
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = headerText,
                        style = when (level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.headlineSmall
                            else -> MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val isListItem = trimmedLine.startsWith("- ") || trimmedLine.startsWith("* ")
                val contentText = if (isListItem) {
                    trimmedLine.substring(2).trim()
                } else {
                    trimmedLine
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (isListItem) {
                            Text(
                                text = stringResource(R.string.list_bullet),
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        // Nothing on a remote can follow a link, so a URL stays the literal text it is.
                        Text(
                            text = contentText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    
                    if (isListItem) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
