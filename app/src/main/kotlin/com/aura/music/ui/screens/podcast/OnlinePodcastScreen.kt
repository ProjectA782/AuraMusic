package com.aura.music.ui.screens.podcast

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import com.aura.music.ui.component.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.aura.music.ui.utils.resize
import com.aura.innertube.models.PodcastItem
import timber.log.Timber
import com.aura.music.LocalAuraTvShell
import com.aura.music.LocalDatabase
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.db.entities.PodcastEntity
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.models.toMediaMetadata
import com.aura.music.extensions.toMediaItem
import com.aura.music.playback.queues.ListQueue
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.LocalMenuState
import com.aura.music.ui.component.YouTubeListItem
import com.aura.music.ui.menu.YouTubeSongMenu
import com.aura.music.ui.theme.rememberTvRowNav
import com.aura.music.ui.utils.backToMain
import com.aura.music.viewmodels.OnlinePodcastViewModel
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePodcastScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePodcastViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val podcast by viewModel.podcast.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val libraryPodcast by viewModel.libraryPodcast.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    val isTv = LocalAuraTvShell.current
    val tvRowNav = rememberTvRowNav(lazyListState)
    val firstEpisodeFocus = remember { FocusRequester() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredEpisodes = remember(episodes, query) {
        if (query.text.isEmpty()) episodes
        else episodes.filter { episode ->
            episode.title.contains(query.text, ignoreCase = true) ||
                episode.author?.name?.contains(query.text, ignoreCase = true) == true
        }
    }

    LaunchedEffect(isTv, isSearching, filteredEpisodes.firstOrNull()?.id) {
        if (isTv && !isSearching && filteredEpisodes.isNotEmpty()) {
            delay(250)
            runCatching { firstEpisodeFocus.requestFocus() }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            if (podcast == null && isLoading) {
                item(key = "loading_placeholder") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            } else if (error != null) {
                item(key = "error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = error ?: stringResource(R.string.error_unknown),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = { viewModel.retry() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            } else {
                podcast?.let { podcastItem ->
                    if (!isSearching) {
                        item(key = "podcast_header") {
                            val context = LocalContext.current
                            PodcastHeader(
                                podcast = podcastItem,
                                episodeCount = episodes.size,
                                inLibrary = libraryPodcast?.inLibrary == true,
                                onLibraryClick = { viewModel.toggleLibrary() },
                                onViewChannelClick = {
                                    val channelId = podcastItem.channelId ?: podcastItem.author?.id
                                    if (channelId != null) {
                                        navController.navigate("artist/$channelId?isPodcastChannel=true")
                                    }
                                },
                                modifier = if (isTv) {
                                    tvRowNav.row(
                                        index = 0,
                                        itemKey = "podcast_header",
                                        rowCount = filteredEpisodes.size + 1,
                                    )
                                } else {
                                    Modifier
                                },
                                libraryModifier = if (isTv) tvRowNav.tile(0, 0) else Modifier,
                                channelModifier = if (isTv) tvRowNav.tile(0, 1) else Modifier,
                            )
                        }
                    }

                    itemsIndexed(
                        items = filteredEpisodes,
                        key = { _, episode -> episode.id }
                    ) { index, episode ->
                        YouTubeListItem(
                            item = episode,
                            isActive = mediaMetadata?.id == episode.id,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .then(
                                    if (isTv) {
                                        tvRowNav.row(
                                            index = index + 1,
                                            itemKey = episode.id,
                                            rowCount = filteredEpisodes.size + 1,
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (episode.id == mediaMetadata?.id) {
                                            playerConnection.togglePlayPause()
                                        } else {
                                            Timber.d("Playing episode: ${episode.title}, index: $index, total episodes: ${filteredEpisodes.size}")
                                            val mediaItems = filteredEpisodes.map { it.toMediaMetadata().toMediaItem() }
                                            Timber.d("Created ${mediaItems.size} media items for queue")
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = podcast?.title,
                                                    items = mediaItems,
                                                    startIndex = index
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            YouTubeSongMenu(episode.asSongItem(), menuState::dismiss)
                                        }
                                    }
                                )
                                .then(
                                    if (isTv) {
                                        tvRowNav.tile(
                                            row = index + 1,
                                            col = 0,
                                            best = index == 0,
                                            anchor = if (index == 0) firstEpisodeFocus else null,
                                        )
                                    } else Modifier,
                                )
                                .then(
                                    if (isTv) Modifier.auraFocused(grow = false, claim = index != 0)
                                    else Modifier,
                                )
                                .animateItem(),
                            trailingContent = {
                                IconButton(onClick = {
                                    menuState.show {
                                        YouTubeSongMenu(episode.asSongItem(), menuState::dismiss)
                                    }
                                }) {
                                    Icon(painterResource(R.drawable.more_vert), null)
                                }
                            }
                        )
                    }
                }
            }
        }

        TopAppBar(
            modifier = if (isTv && !isSearching) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown &&
                        filteredEpisodes.isNotEmpty()
                    ) {
                        runCatching { firstEpisodeFocus.requestFocus() }.getOrDefault(false)
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
            title = {
                if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                } else if (lazyListState.firstVisibleItemIndex > 0) {
                    Text(podcast?.title ?: "")
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching) navController.backToMain()
                    }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            actions = {
                if (!isSearching) {
                    IconButton(onClick = { isSearching = true }) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.search)
                        )
                    }
                }
            },
            scrollBehavior = scrollBehavior
        )
    }
}

@Composable
private fun PodcastHeader(
    podcast: PodcastItem,
    episodeCount: Int,
    inLibrary: Boolean,
    onLibraryClick: () -> Unit,
    onViewChannelClick: () -> Unit,
    modifier: Modifier = Modifier,
    libraryModifier: Modifier = Modifier,
    channelModifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(podcast.thumbnail?.resize(1080, 1080))
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = podcast.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        podcast.author?.name?.let { authorName ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = authorName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = podcast.episodeCountText ?: "$episodeCount episodes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onLibraryClick,
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (inLibrary)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        Color.Transparent
                ),
                shape = RoundedCornerShape(50),
                modifier = libraryModifier.height(40.dp)
            ) {
                Icon(
                    painter = painterResource(if (inLibrary) R.drawable.library_add_check else R.drawable.library_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library)
                )
            }

            OutlinedButton(
                onClick = onViewChannelClick,
                shape = RoundedCornerShape(50),
                modifier = channelModifier.height(40.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.view_channel)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
