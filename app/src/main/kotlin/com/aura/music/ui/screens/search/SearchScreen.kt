/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.search

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.aura.music.ui.component.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import com.aura.music.LocalNavController
import com.aura.innertube.models.WatchEndpoint
import com.aura.innertube.utils.YouTubeUrlParser
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraGhostButton
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.aura.auraType
import com.aura.music.ui.aura.requestFocusSafely
import com.aura.music.ui.aura.LocalAuraPageFocus
import com.aura.music.LocalDatabase
import com.aura.music.LocalIsPlayerExpanded
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.LocalPlayerConnection
import com.aura.music.R
import com.aura.music.constants.PauseSearchHistoryKey
import com.aura.music.constants.SearchSource
import com.aura.music.constants.SearchSourceKey
import com.aura.music.db.entities.SearchHistory
import com.aura.music.playback.queues.YouTubeQueue
import com.aura.music.utils.SearchRoutes
import com.aura.music.utils.dataStore
import com.aura.music.utils.rememberEnumPreference
import com.aura.music.utils.rememberPreference
import com.aura.music.utils.spotifySearchActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    pureBlack: Boolean,
    savedStateHandle: SavedStateHandle,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val playerConnection = LocalPlayerConnection.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var isHandlingScrollToTop by remember { mutableStateOf(false) }
    val tvShell = LocalAuraTvShell.current
    // Read here, not inside the key handler: a composition local is only readable in composition,
    // and a preview-key lambda runs long after this screen composed.
    val pageAnchors = LocalAuraPageFocus.current

    /*
     * The on-screen keyboard is a window of its own and holds the D-pad while it is up: RIGHT, LEFT
     * and DOWN then move its own key selection, so the header icons beside the field and the rows
     * under it never get aimed at - and the panel itself covers the lower half of the page, which is
     * what made the suggestions read as a second search screen laid over the first. So a focused
     * field keeps it shut and OK opens it on purpose.
     */
    var tvTyping by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(fieldFocused, tvTyping) {
        if (tvShell && fieldFocused && !tvTyping) keyboardController?.hide()
    }

    val appContext = LocalContext.current
    val isSpotifySearch by remember {
        appContext.dataStore
            .data
            .map { spotifySearchActive(it) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val scrollToTopCount by savedStateHandle.getStateFlow("scrollToTopCount", 0).collectAsStateWithLifecycle(initialValue = 0)

    var lastHandledCount by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (!isPlayerExpanded) {
            kotlinx.coroutines.delay(100)
            try {
                focusRequester.requestFocus()
                if (!tvShell) keyboardController?.show()
            } catch (e: Exception) {
            }
        }
    }
    LaunchedEffect(scrollToTopCount) {
        if (scrollToTopCount > lastHandledCount) {
            lastHandledCount = scrollToTopCount
            isHandlingScrollToTop = true

            kotlinx.coroutines.delay(100)

            if (!isPlayerExpanded) {
                focusManager.clearFocus(force = true)
                kotlinx.coroutines.delay(50)
                try {
                    focusRequester.requestFocus()
                    if (!tvShell) keyboardController?.show()
                } catch (e: Exception) {
                }
            }

            kotlinx.coroutines.delay(500)
            isHandlingScrollToTop = false
        }
    }

    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

    fun handleSearch(searchQuery: String) {
        if (searchQuery.isEmpty()) {
            return
        }

        focusManager.clearFocus()

        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
            is YouTubeUrlParser.ParsedUrl.Video -> {
                playerConnection?.playQueue(
                    YouTubeQueue(
                        WatchEndpoint(videoId = parsedUrl.id),
                    ),
                )
            }

            is YouTubeUrlParser.ParsedUrl.Playlist -> {
                navController.navigate("online_playlist/${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Album -> {
                navController.navigate("album/MPREb_${parsedUrl.id}")
            }

            is YouTubeUrlParser.ParsedUrl.Artist -> {
                navController.navigate("artist/${parsedUrl.id}")
            }

            null -> {
                navController.navigate(SearchRoutes.resultRoute(searchQuery))
            }
        }

        if (!pauseSearchHistory) {
            coroutineScope.launch(Dispatchers.IO) {
                database.query {
                    insert(SearchHistory(query = searchQuery))
                }
            }
        }
    }

    val onSearch: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    val onSearchFromSuggestion: (String) -> Unit = { searchQuery -> handleSearch(searchQuery) }

    /*
     * Voice *input*, not song identification: whatever the remote's microphone hears becomes the
     * query. Google's full-screen voice panel is deliberately not used — it is Google's chrome, it
     * covers the page it is supposed to fill, and on this set-top it handed nothing back. The same
     * recognizer is published as a service, so the field drives that service directly and draws the
     * listening state itself: the words as they arrive, and the error the service reported instead
     * of a panel that closes for no visible reason.
     */
    var voiceOpen by remember { mutableStateOf(false) }
    var voiceReady by remember { mutableStateOf(false) }
    var voiceHeard by remember { mutableStateOf("") }
    var voiceError by remember { mutableIntStateOf(0) }
    var voiceEngine by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var voicePanelUp by remember { mutableStateOf(false) }
    val voicePanelRequester = remember { FocusRequester() }
    val micRequester = remember { FocusRequester() }
    // Reading these through a state object rather than closing over the local: the result callback
    // lives inside a remembered listener, and the page recomposes underneath it.
    val voiceSubmit = rememberUpdatedState<(String) -> Unit> { heard ->
        query = TextFieldValue(heard)
        handleSearch(heard)
    }

    val onVoicePanelConfirm: () -> Unit = {
        val heard = voiceHeard.trim()
        voiceOpen = false
        if (heard.isNotEmpty()) voiceSubmit.value(heard)
    }

    val onVoiceSearchClick: () -> Unit = {
        if (!voiceOpen) {
            voiceHeard = ""
            voiceError = 0
            voiceReady = false
            keyboardController?.hide()
            focusManager.clearFocus()
            voiceOpen = true
        }
    }

    LaunchedEffect(voiceOpen) {
        if (!voiceOpen) {
            voiceEngine?.destroy()
            voiceEngine = null
            return@LaunchedEffect
        }
        if (voiceEngine == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                voiceOpen = false
                voiceError = R.string.no_voice_search
                return@LaunchedEffect
            }
            val engine = SpeechRecognizer.createSpeechRecognizer(appContext)
            engine.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        voiceReady = true
                    }

                    override fun onBeginningOfSpeech() {
                        voiceReady = true
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        voiceReady = false
                    }

                    override fun onError(error: Int) {
                        voiceOpen = false
                        voiceError = voiceErrorMessage(error)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val heard = partialResults.transcript()
                        if (heard.isNotEmpty()) voiceHeard = heard
                    }

                    override fun onResults(results: Bundle?) {
                        val heard = results.transcript()
                        voiceOpen = false
                        if (heard.isEmpty()) {
                            // The service ended the session without an error and without words. Say so:
                            // a panel that simply vanishes reads as a broken mic, which is what this was.
                            voiceError = R.string.voice_no_match
                        } else {
                            voiceHeard = heard
                            voiceSubmit.value(heard)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )
            voiceEngine = engine
            try {
                engine.startListening(voiceRequest(appContext))
            } catch (_: SecurityException) {
                // The manifest asks for the microphone, so this is the user having said no to it
                // once already; the service would otherwise sit silent until the watchdog fired.
                voiceOpen = false
                voiceError = R.string.voice_permission
            }
        }
        var tries = 0
        while (voiceOpen && tries < VoiceInputFocusTries && !voicePanelRequester.requestFocusSafely()) {
            delay(VoiceInputPollMs)
            tries++
        }
        // The service ends every session itself, in six seconds or less, so this never preempts a
        // real answer. It is here for the case where it never answers at all, which would otherwise
        // leave a listening panel with no way out.
        var waited = 0L
        while (voiceOpen && waited < VoiceInputTimeoutMs) {
            delay(VoiceInputPollMs)
            waited += VoiceInputPollMs
        }
        if (voiceOpen) {
            voiceOpen = false
            voiceError = R.string.voice_timeout
        }
    }

    LaunchedEffect(voiceError) {
        if (voiceError != 0) {
            delay(VoiceInputMessageMs)
            voiceError = 0
        }
    }

    // The panel holds focus for as long as it is composed, which is past voiceOpen: an error keeps it
    // up showing what went wrong. Asking the mic for the selector while the panel is still up is
    // overridden when the panel leaves, so wait for it to be gone. Measured on the device: the
    // request lands only from this point, and the ring comes back with it.
    LaunchedEffect(voiceOpen, voiceError) {
        if (voiceOpen || voiceError != 0) {
            voicePanelUp = true
            return@LaunchedEffect
        }
        if (!voicePanelUp) return@LaunchedEffect
        voicePanelUp = false
        var tries = 0
        while (tries < VoiceInputFocusTries && !micRequester.requestFocusSafely()) {
            delay(VoiceInputPollMs)
            tries++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceEngine?.destroy()
            voiceEngine = null
        }
    }

    // The same well radius the dialog inputs use, so the search field and every other TV input are
    // the same shape.
    val fieldShape = RoundedCornerShape(28.apx())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    // The field wore no selector of its own, so a focused field with
                                    // the keyboard shut looked like a dead remote. Ring only: a wash
                                    // across the whole bar would recolour the header. claim = false
                                    // keeps the rail's landing spot on a row rather than the editor.
                                    .auraFocused(
                                        corner = 28.apx(),
                                        fill = false,
                                        grow = false,
                                        shadow = false,
                                        claim = false,
                                        onFocusChanged =
                                            { hasFocus ->
                                                fieldFocused = hasFocus
                                                if (!hasFocus) {
                                                    tvTyping = false
                                                } else if (tvShell && !tvTyping) {
                                                    keyboardController?.hide()
                                                }
                                            },
                                    )
                                    // The reference has no outlined field on TV: an input is the inset
                                    // well the dialogs use. Same well here, so search reads as part of
                                    // the shell instead of as the old Material form control.
                                    .clip(fieldShape)
                                    .background(AuraMock.Page, fieldShape)
                                    .border(
                                        2.apx(),
                                        if (fieldFocused) AuraMock.Primary else AuraMock.Border,
                                        fieldShape,
                                    )
                                    .padding(horizontal = 24.apx(), vertical = 14.apx())
                                    // A single-line field spends DOWN on the caret, which never moves
                                    // and swallows the key. On TV aim it at the suggestions instead, and
                                    // aim at the page's named landing spot rather than letting the
                                    // geometric search pick a row's trailing delete icon — a node with
                                    // nothing to show and nothing to choose. A partial query has to be
                                    // pickable without submitting it.
                                    .onPreviewKeyEvent { event ->
                                        if (!tvShell || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.DirectionDown ->
                                                pageAnchors?.requestFirstFocus()
                                                    ?: focusManager.moveFocus(FocusDirection.Down)
                                            // RIGHT inside a field moves the caret, and at the end of the
                                            // text there is nowhere to move, so the field eats the key and
                                            // the icons beside it — including the mic — never get reached.
                                            Key.DirectionRight ->
                                                focusManager.moveFocus(FocusDirection.Right)
                                            // LEFT gives the field back, but only once the caret is home:
                                            // mid-text it still belongs to the editor.
                                            Key.DirectionLeft ->
                                                query.selection.min == 0 && query.selection.max == 0 &&
                                                    focusManager.moveFocus(FocusDirection.Left)
                                            // The keyboard stays out of the way until it is asked for,
                                            // so OK on the focused field is what puts it on screen.
                                            Key.DirectionCenter -> {
                                                tvTyping = true
                                                keyboardController?.show()
                                                true
                                            }
                                            else -> false
                                        }
                                    },
                            textStyle = auraType(FontWeight.Normal, 24).copy(color = AuraMock.Text),
                            cursorBrush = SolidColor(AuraMock.Primary),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (query.text.isEmpty()) {
                                    Text(
                                        text =
                                            stringResource(
                                                when (searchSource) {
                                                    SearchSource.LOCAL -> R.string.search_library
                                                    SearchSource.ONLINE ->
                                                        if (isSpotifySearch) R.string.search_spotify else R.string.search_yt_music
                                                },
                                            ),
                                        style = auraType(FontWeight.Medium, 24).copy(color = AuraMock.Muted),
                                    )
                                }
                                innerTextField()
                            },
                            keyboardOptions =
                                KeyboardOptions(
                                    imeAction = ImeAction.Search,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onSearch = { onSearch(query.text) },
                                ),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.apx()),
                        ) {
                            // Plain IconButtons draw no selector and are not Aura focus targets, so
                            // RIGHT from the field appeared to do nothing. These take the ring and can
                            // be aimed at.
                            if (query.text.isNotEmpty()) {
                                AuraGhostButton(
                                    icon = R.drawable.close,
                                    onClick = { query = TextFieldValue("") },
                                )
                            }
                            AuraGhostButton(
                                icon =
                                    when (searchSource) {
                                        SearchSource.LOCAL -> R.drawable.library_music
                                        SearchSource.ONLINE -> R.drawable.language
                                    },
                                contentDescription = null,
                                onClick = {
                                    searchSource =
                                        if (searchSource == SearchSource.ONLINE) {
                                            SearchSource.LOCAL
                                        } else {
                                            SearchSource.ONLINE
                                        }
                                },
                            )
                            // Voice input belongs beside the field it fills. Song identification is a
                            // different feature and reaches it from the rail's long press.
                            AuraGhostButton(
                                icon = R.drawable.mic,
                                contentDescription = stringResource(R.string.search_by_voice),
                                focusRequester = micRequester,
                                onClick = onVoiceSearchClick,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
                    ),
            )
        },
        containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .padding(top = paddingValues.calculateTopPadding())
                    .fillMaxSize(),
        ) {
            when (searchSource) {
                SearchSource.LOCAL -> {
                    LocalSearchScreen(
                        query = query.text,
                        onDismiss = { navController.navigateUp() },
                        pureBlack = pureBlack,
                    )
                }

                SearchSource.ONLINE -> {
                    OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = { query = it },
                        onSearch = onSearchFromSuggestion,
                        onDismiss = { /* Don't dismiss when searching from suggestions */ },
                        pureBlack = pureBlack,
                    )
                }
            }

            if (voiceOpen || voiceError != 0) {
                AuraVoicePanel(
                    listening = voiceOpen,
                    ready = voiceReady,
                    heard = voiceHeard,
                    errorRes = voiceError,
                    modifier = Modifier
                        .focusRequester(voicePanelRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter -> {
                                    onVoicePanelConfirm()
                                    true
                                }

                                else -> false
                            }
                        },
                )
            }
        }
    }

    // Handle lifecycle events to manage keyboard visibility
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (isHandlingScrollToTop) return@LifecycleEventObserver
                        // Always hide keyboard when resuming if player is expanded
                        if (isPlayerExpanded) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    }

                    Lifecycle.Event.ON_PAUSE -> {
                        if (isHandlingScrollToTop) return@LifecycleEventObserver
                        // Clear focus when pausing to prevent keyboard from showing on resume
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }

                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Initial check - hide keyboard if player is expanded
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

/**
 * The listening state drawn by this page instead of by the platform: the mock's panel, border, wash
 * and type, floating over the page it is about to fill. The words appear as the service hears them,
 * and when it fails the panel says which failure — the old full-screen chrome closed silently and left
 * the remote with nothing to show for the press.
 */
@Composable
private fun AuraVoicePanel(
    listening: Boolean,
    ready: Boolean,
    heard: String,
    errorRes: Int,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(32.apx())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraMock.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.apx()),
            modifier = modifier
                .auraFocused(
                    corner = 32.apx(),
                    fill = false,
                    grow = false,
                    shadow = false,
                    claim = false,
                )
                .focusable()
                .clip(panelShape)
                .background(AuraMock.Panel)
                .border(2.apx(), AuraMock.Border, panelShape)
                .padding(horizontal = 64.apx(), vertical = 44.apx()),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(104.apx())
                        .clip(CircleShape)
                        // The wash is the level meter: it only lights once the service is actually
                        // taking audio, so a dead microphone looks dead rather than busy.
                        .background(AuraMock.Primary.copy(alpha = if (ready) 0.26f else 0.10f)),
                )
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    tint = if (listening) AuraMock.Primary else AuraMock.Muted,
                    modifier = Modifier.size(52.apx()),
                )
            }
            Text(
                text = when {
                    errorRes != 0 -> stringResource(errorRes)
                    heard.isNotEmpty() -> heard
                    else -> stringResource(R.string.voice_listening)
                },
                style = auraType(FontWeight.Medium, 28).copy(color = AuraMock.Text),
            )
            Text(
                text = stringResource(if (errorRes != 0) R.string.voice_try_again else R.string.voice_hint),
                style = auraType(FontWeight.Normal, 20).copy(color = AuraMock.Muted),
            )
        }
    }
}

/**
 * An Intent, not a Bundle: `startListening(Bundle)` is API 33 and this app runs down to 26. The
 * service only ever reads the extras, so the wrapper is free.
 */
private fun voiceRequest(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

/**
 * The words a result callback carried. The documented key is [RecognizerIntent.EXTRA_RESULTS], but
 * the TV's own recognizer answers under `results_recognition` and leaves the documented one absent
 * — measured here, where every spoken query arrived and was read as empty. The partial bundle adds
 * `android.speech.extra.UNSTABLE_TEXT` as a third place the same text can sit. Which of the three
 * holds a list and which a bare string is the service's choice, and each typed accessor throws on
 * the other's shape rather than returning null, so both are tried.
 */
private fun Bundle?.transcript(): String {
    if (this == null) return ""
    for (key in TranscriptKeys) {
        val spoken = listOf(
            runCatching { getStringArrayList(key)?.firstOrNull { it.isNotBlank() } }.getOrNull(),
            runCatching { getString(key) }.getOrNull(),
        ).firstNotNullOfOrNull { it?.trim() }
        if (!spoken.isNullOrEmpty()) return spoken
    }
    return ""
}

private val TranscriptKeys = listOf(
    "results_recognition",
    RecognizerIntent.EXTRA_RESULTS,
    "android.speech.extra.UNSTABLE_TEXT",
)

/**
 * The service reports a code; the page reports a sentence. Without this the only sign that anything
 * went wrong was a panel that vanished.
 */
private fun voiceErrorMessage(error: Int): Int =
    when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_network
        SpeechRecognizer.ERROR_AUDIO -> R.string.voice_no_mic
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_busy
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_CLIENT -> R.string.voice_unavailable

        else -> R.string.voice_no_match
    }

private const val VoiceInputTimeoutMs = 15_000L
private const val VoiceInputPollMs = 250L
private const val VoiceInputMessageMs = 2_800L

/** The panel is composed on the frame after the press, so the first request usually misses. */
private const val VoiceInputFocusTries = 8
