/**
 * Aura Music — the podcast page in the Material You TV reference's layout.
 *
 * Assembled from the reference's own blocks: the hero card carries the cover, the title, the author
 * and the episode count, and its play disc queues everything; below it one rail row per episode. The
 * D-pad chain is hero, then the episodes, so a page whose feed never arrived still has a stop to hold.
 */

package com.aura.music.ui.aura

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.extensions.toMediaItem
import com.aura.music.models.toMediaMetadata
import com.aura.music.playback.queues.ListQueue
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.utils.makeTimeString
import com.aura.music.viewmodels.OnlinePodcastViewModel

/**
 * A podcast streamed from YouTube Music, reached from a library shelf or a search result.
 *
 * The whole feed is queued as one [ListQueue] rather than resolved through a browse endpoint, so an
 * episode starts playing the moment its row is pressed. The page header lives in the shell, so the
 * title is the hero's heading text here and there is no app bar of its own.
 */
@Composable
fun AuraPodcast(
    navController: NavController,
    viewModel: OnlinePodcastViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val podcast by viewModel.podcast.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val libraryPodcast by viewModel.libraryPodcast.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playAt: (Int) -> Unit = { index ->
        val page = podcast
        if (page != null && episodes.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = page.title,
                    items = episodes.map { it.toMediaMetadata().toMediaItem() },
                    startIndex = index,
                ),
            )
        }
    }

    val episodeRows = episodes.mapIndexed { index, episode ->
        AuraTrackRow(
            id = episode.id,
            label = "${index + 1}",
            title = episode.title,
            subtitle = episode.publishDateText,
            image = episode.thumbnail,
            durationText = episode.duration?.let { makeTimeString(it * 1000L) },
            badges = if (episode.explicit) listOf(R.drawable.explicit) else emptyList(),
            active = episode.id == mediaMetadata?.id,
            onClick = {
                if (episode.id == mediaMetadata?.id) {
                    playerConnection.togglePlayPause()
                } else {
                    playAt(index)
                }
            },
        )
    }

    var nextRow = 0
    val heroRow = nextRow++
    val actionsRow = if (podcast != null) nextRow++ else -1
    val episodesRowStart = nextRow
    nextRow += episodeRows.size
    val noticeRow = if (podcast != null && episodeRows.isEmpty()) nextRow++ else -1
    val rowCount = nextRow

    val listState = rememberLazyListState()
    val tvRowNav = rememberTvRowNav(listState)
    val heroFocus = remember { FocusRequester() }
    auraAnchorHero(heroFocus, podcast != null)

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
        item(key = "aura_podcast_hero") {
            val page = podcast
            if (page == null) {
                PodcastPendingBlock(
                    failed = !isLoading && error != null,
                    onRetry = viewModel::retry,
                    message = error.orEmpty(),
                    modifier = tvRowNav.tile(heroRow, 0),
                )
            } else {
                AuraHeroBlock(
                    pick = AuraPick(
                        id = page.id,
                        title = page.title,
                        subtitle = listOfNotNull(
                            page.author?.name,
                            page.episodeCountText
                                ?: pluralStringResource(R.plurals.n_episode, episodes.size, episodes.size),
                        ).joinToString("  ·  "),
                        image = page.thumbnail,
                        onClick = { playAt(0) },
                    ),
                    badge = stringResource(R.string.type_podcast).uppercase(),
                    modifier = tvRowNav.tile(heroRow, 0, best = true, anchor = heroFocus),
                )
            }
        }

        if (podcast != null) {
            item(key = "aura_podcast_actions") {
                val page = podcast
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.apx()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = actionsRow,
                                itemKey = "aura_podcast_actions",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup(),
                ) {
                    var col = 0
                    val inLibrary = libraryPodcast?.inLibrary == true
                    AuraPillButton(
                        label = stringResource(
                            if (inLibrary) R.string.remove_from_library else R.string.add_to_library,
                        ),
                        icon = if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
                        filled = inLibrary,
                        onClick = viewModel::toggleLibrary,
                        modifier = tvRowNav.tile(actionsRow, col++),
                    )
                    val channelId = page?.channelId ?: page?.author?.id
                    if (channelId != null) {
                        AuraPillButton(
                            label = stringResource(R.string.view_channel),
                            icon = R.drawable.person,
                            onClick = {
                                navController.navigate("artist/$channelId?isPodcastChannel=true")
                            },
                            modifier = tvRowNav.tile(actionsRow, col++),
                        )
                    }
                }
            }
        }

        auraTrackRows(
            rows = episodeRows,
            rowNav = tvRowNav,
            rowStart = episodesRowStart,
            rowCount = rowCount,
        )

        if (noticeRow >= 0) {
            item(key = "aura_podcast_notice") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            tvRowNav.row(
                                index = noticeRow,
                                itemKey = "aura_podcast_notice",
                                rowCount = rowCount,
                            ),
                        )
                        .focusGroup(),
                ) {
                    PodcastEmptyBlock(
                        message = pluralStringResource(R.plurals.n_episode, 0, 0),
                        modifier = tvRowNav.tile(noticeRow, 0),
                    )
                }
            }
        }
    }
}

/** The reference's panel, standing in for a podcast whose feed has not landed or came back failed. */
@Composable
private fun PodcastPendingBlock(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.error_unknown),
) {
    val shape = RoundedCornerShape(AuraPx.HeroRadius.apx())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.apx()),
        modifier = modifier
            .fillMaxWidth()
            .height(AuraPx.HeroHeight.apx())
            .auraFocused(corner = AuraPx.HeroRadius.apx(), grow = false)
            .clip(shape)
            .background(AuraMock.Panel)
            .padding(48.apx()),
    ) {
        if (failed) {
            AuraText(value = message, px = 30, color = AuraMock.Muted, maxLines = 3)
            AuraPillButton(
                label = stringResource(R.string.retry_button),
                icon = R.drawable.aura_ic_play,
                filled = true,
                onClick = onRetry,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(64.apx()),
                strokeWidth = 5.apx(),
                color = AuraMock.Primary,
            )
        }
    }
}

/** A podcast can parse with zero episodes, which has to read as an answer rather than as a blank page. */
@Composable
private fun PodcastEmptyBlock(
    message: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AuraPx.HeroRadius.apx())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.apx()),
        modifier = modifier
            .fillMaxWidth()
            .height(AuraPx.HeroHeight.apx())
            .auraFocused(corner = AuraPx.HeroRadius.apx(), grow = false)
            .clip(shape)
            .background(AuraMock.Panel)
            .padding(48.apx()),
    ) {
        AuraText(value = message, px = 30, color = AuraMock.Muted, maxLines = 3)
    }
}
