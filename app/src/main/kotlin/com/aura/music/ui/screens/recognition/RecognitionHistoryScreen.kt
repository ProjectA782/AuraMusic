/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.recognition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.music.LocalDatabase
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.db.entities.RecognitionHistory
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.AuraTrackRow
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.aura.auraTrackRows
import com.aura.music.ui.component.DefaultDialog
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.SearchRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * What the recogniser found, one row per match, in the shell the rest of the rail uses.
 *
 * The upstream page was a Material [androidx.compose.material3.Scaffold] over a column of cards: a
 * tap on a card searched for the track and a trash icon deleted it. Neither control exists on a
 * television - a card has no focus ring and the icon only answers to touch - so the rows go through
 * the shell's track list, where the same "more" glyph every other list uses opens the delete
 * confirmation, and clearing the lot moves to the page header where the remote can reach it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionHistoryScreen(navController: NavController) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    val historyItems by database.recognitionHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    var showClearDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<RecognitionHistory?>(null) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm") }

    if (showClearDialog) {
        DefaultDialog(
            onDismiss = { showClearDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.clear_recognition_history)) },
            buttons = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                clearRecognitionHistory()
                            }
                        }
                        showClearDialog = false
                    },
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.clear_recognition_history_confirm),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    itemToDelete?.let { item ->
        DefaultDialog(
            onDismiss = { itemToDelete = null },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.delete)) },
            buttons = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            database.query {
                                deleteRecognitionHistoryById(item.id)
                            }
                        }
                        itemToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.delete_playlist_confirm, item.title),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    val rows = historyItems.map { item ->
        AuraTrackRow(
            id = item.id.toString(),
            title = item.title,
            subtitle = item.artist,
            image = item.coverArtUrl,
            durationText = item.recognizedAt.format(dateFormatter),
            onClick = {
                navController.navigate(SearchRoutes.resultRoute("${item.title} ${item.artist}"))
            },
            onLongClick = { itemToDelete = item },
        )
    }

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    // No named anchor here: a row publishes itself as a landing spot in composition order, so the
    // top row is the first offer the shell's re-anchor finds, and there is nothing above it to aim at.

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
            title = { Text(stringResource(R.string.recognition_history)) },
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
                if (historyItems.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearDialog = true },
                        onLongClick = { showClearDialog = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.clear_all),
                            contentDescription = stringResource(R.string.clear_recognition_history),
                        )
                    }
                }
            },
        )

        if (rows.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = auraPagePadding()),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.history),
                        contentDescription = null,
                        tint = AuraMock.Muted.copy(alpha = 0.5f),
                        modifier = Modifier.size(96.apx()),
                    )
                    Spacer(modifier = Modifier.height(24.apx()))
                    AuraText(
                        value = "No recognition history",
                        px = 26,
                        color = AuraMock.Muted,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentPadding =
                    PaddingValues(
                        start = auraPagePadding(),
                        end = auraPagePadding(),
                        bottom = 132.apx(),
                    ),
                verticalArrangement = Arrangement.spacedBy(20.apx()),
            ) {
                auraTrackRows(
                    rows = rows,
                    rowNav = tvRowNav,
                    rowStart = 0,
                    rowCount = rows.size,
                )
            }
        }
    }
}
