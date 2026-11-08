/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.innertube.YouTube
import com.aura.innertube.models.AlbumItem
import com.aura.innertube.models.Artist
import com.aura.innertube.models.ArtistItem
import com.aura.innertube.models.PlaylistItem
import com.aura.innertube.models.SongItem
import kotlinx.coroutines.flow.combine
import com.aura.innertube.models.WatchEndpoint
import com.aura.innertube.models.BrowseEndpoint
import com.aura.innertube.models.YTItem
import com.aura.innertube.models.filterExplicit
import com.aura.innertube.models.filterVideoSongs
import com.aura.innertube.models.filterYoutubeShorts
import com.aura.innertube.pages.ExplorePage
import com.aura.innertube.pages.HomePage
import com.aura.innertube.utils.completed
import com.aura.music.constants.EnableSpotifyKey
import com.aura.music.constants.SpotifyHomeOnlyKey
import com.aura.music.constants.HideExplicitKey
import com.aura.music.constants.HideVideoSongsKey
import com.aura.music.constants.HideYoutubeShortsKey
import com.aura.music.constants.InnerTubeCookieKey
import com.aura.music.constants.QuickPicks
import com.aura.music.constants.QuickPicksKey
import com.aura.music.constants.SpotifyAccessTokenKey
import com.aura.music.constants.SpotifyAnonymousHomeKey
import com.aura.music.utils.SpotifyTokenManager
import com.aura.music.constants.SpotifyTokenExpiryKey
import com.aura.music.constants.UseSpotifyHomeKey
import com.aura.music.db.MusicDatabase
import com.aura.music.db.entities.Album
import com.aura.music.db.entities.LocalItem
import com.aura.music.db.entities.Song
import com.aura.music.db.entities.SpeedDialItem
import com.aura.spotify.models.SpotifyAlbum
import com.aura.spotify.models.SpotifyArtist
import com.aura.spotify.models.SpotifyHomeFeedItem
import com.aura.spotify.models.SpotifyHomeFeedSection
import com.aura.spotify.models.SpotifyImage
import com.aura.spotify.models.SpotifyPlaylist
import com.aura.spotify.models.SpotifyPlaylistOwner
import com.aura.spotify.models.SpotifyPlaylistTracksRef
import com.aura.spotify.models.SpotifyTrack
import com.aura.music.extensions.filterVideoSongs
import com.aura.music.extensions.toEnum
import com.aura.music.models.SectionType
import com.aura.music.models.SimilarRecommendation
import com.aura.music.models.SpotifyHomeSection
import com.aura.music.models.sectionType
import com.aura.music.models.toSpotifyAlbum
import com.aura.music.models.toSpotifyArtist
import com.aura.music.models.toSpotifyPlaylist
import com.aura.music.utils.NetworkConnectivityObserver
import com.aura.music.utils.SyncUtils
import com.aura.music.utils.dataStore
import com.aura.music.utils.safeDataStoreEdit
import com.aura.music.utils.get
import com.aura.music.playback.SpotifyProfileCache
import com.aura.music.utils.reportException
import com.aura.spotify.Spotify
import com.aura.spotify.SpotifyChartRows
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.serialization.json.Json
import com.aura.music.constants.AccountNameKey

data class DailyDiscoverItem(
    val seed: Song,
    val recommendation: YTItem,
    val relatedEndpoint: BrowseEndpoint?
)

data class CommunityPlaylistItem(
    val playlist: PlaylistItem,
    val songs: List<SongItem>
)

/** How many `getAlbum` round-trips the New Releases warm-up runs at once. */
private const val NewReleasePrefetchConcurrency = 6

internal fun buildSpeedDialItems(
    pinned: List<YTItem>,
    keepListening: List<YTItem>,
    quickPicks: List<YTItem>,
    home: List<YTItem>,
): List<YTItem> =
    (pinned + keepListening + quickPicks + home)
        .distinctBy { it.id }
        .take(27)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    val isRandomizing = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val recentlyPlayed = MutableStateFlow<List<Song>?>(null)
    val dailyDiscover = MutableStateFlow<List<DailyDiscoverItem>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val communityPlaylists = MutableStateFlow<List<CommunityPlaylistItem>?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // Official API data for podcast sections
    val savedPodcastShows = MutableStateFlow<List<com.aura.innertube.models.PodcastItem>>(emptyList())
    val episodesForLater = MutableStateFlow<List<SongItem>>(emptyList())

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    val pinnedSpeedDialItems: StateFlow<List<SpeedDialItem>> =
        database.speedDialDao.getAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val speedDialItems: StateFlow<List<YTItem>> =
        combine(
            database.speedDialDao.getAll(),
            keepListening,
            quickPicks,
            homePage,
        ) { pinned, keepListening, quick, home ->
            buildSpeedDialItems(
                pinned = pinned.map { it.toYTItem() },
                keepListening =
                    keepListening.orEmpty().mapNotNull { item ->
                        when (item) {
                            is Song ->
                                SongItem(
                                    id = item.id,
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is Album ->
                                AlbumItem(
                                    browseId = item.id,
                                    playlistId = item.album.playlistId ?: "",
                                    title = item.title,
                                    artists = item.artists.map { Artist(name = it.name, id = it.id) },
                                    year = item.album.year,
                                    thumbnail = item.thumbnailUrl ?: "",
                                )

                            is com.aura.music.db.entities.Artist ->
                                ArtistItem(
                                    id = item.id,
                                    title = item.title,
                                    thumbnail = item.thumbnailUrl,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )

                            else -> null
                        }
                    },
                quickPicks =
                    quick.orEmpty().map { song ->
                        SongItem(
                            id = song.id,
                            title = song.title,
                            artists = song.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = song.thumbnailUrl ?: "",
                        )
                    },
                home = home?.sections.orEmpty().flatMap { it.items },
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun getRandomItem(): YTItem? {
        try {
            isRandomizing.value = true
            // Visual feedback for the animation
            kotlinx.coroutines.delay(1000)

            val userSongs = mutableListOf<YTItem>()
            val otherSources = mutableListOf<YTItem>()

            quickPicks.value?.let { songs ->
                userSongs.addAll(songs.map { song ->
                    SongItem(
                        id = song.id,
                        title = song.title,
                        artists = song.artists.map { Artist(name = it.name, id = it.id) },
                        thumbnail = song.thumbnailUrl ?: "",
                        explicit = false
                    )
                })
            }

            keepListening.value?.let { items ->
                items.forEach { item ->
                    when (item) {
                        is Song -> userSongs.add(SongItem(
                            id = item.id,
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            thumbnail = item.thumbnailUrl ?: "",
                            explicit = false
                        ))
                        is Album -> otherSources.add(AlbumItem(
                            browseId = item.id,
                            playlistId = item.album.playlistId ?: "",
                            title = item.title,
                            artists = item.artists.map { Artist(name = it.name, id = it.id) },
                            year = item.album.year,
                            thumbnail = item.thumbnailUrl ?: ""
                        ))
                        is com.aura.music.db.entities.Artist -> otherSources.add(ArtistItem(
                            id = item.id,
                            title = item.title,
                            thumbnail = item.thumbnailUrl,
                            shuffleEndpoint = null,
                            radioEndpoint = null
                        ))
                        else -> {}
                    }
                }
            }

            otherSources.addAll(allYtItems.value)

            // Probability: 80% User Songs, 20% Other Sources
            val item = if (userSongs.isNotEmpty() && (otherSources.isEmpty() || Random.nextFloat() < 0.8f)) {
                userSongs.distinctBy { it.id }.shuffled().firstOrNull()
            } else {
                otherSources.distinctBy { it.id }.shuffled().firstOrNull()
            } ?: userSongs.firstOrNull() ?: otherSources.firstOrNull()

            return item
        } finally {
            isRandomizing.value = false
        }
    }

    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)

    // Spotify home sections: populated when UseSpotifyHomeKey is enabled
    val spotifyHomeSections = MutableStateFlow<List<SpotifyHomeSection>?>(null)
    val newReleaseTracks = MutableStateFlow<Map<String, List<SpotifyTrack>>>(emptyMap())
    val useSpotifyHome: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val enabled = prefs[EnableSpotifyKey] ?: true
        val useForHome = prefs[UseSpotifyHomeKey] ?: true
        val hasToken = (prefs[SpotifyAccessTokenKey] ?: "").isNotEmpty() ||
            (prefs[SpotifyAnonymousHomeKey] ?: false)
        enabled && useForHome && hasToken
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, true)

    val spotifyHomeOnly: StateFlow<Boolean> = context.dataStore.data.map { prefs ->
        val enabled = prefs[EnableSpotifyKey] ?: true
        val useForHome = prefs[UseSpotifyHomeKey] ?: true
        val homeOnly = prefs[SpotifyHomeOnlyKey] ?: true
        val hasToken = (prefs[SpotifyAccessTokenKey] ?: "").isNotEmpty() ||
            (prefs[SpotifyAnonymousHomeKey] ?: false)
        enabled && useForHome && homeOnly && hasToken
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Lazily, true)

    /** Set of speed dial item ids (used to show Pin/Unpin for Spotify playlists in home section). */
    val pinnedSpeedDialIds: StateFlow<Set<String>> = database.speedDialDao.getAll()
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun togglePin(item: YTItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromYTItem(item)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }

    /**
     * Toggles pin state for a Spotify playlist (e.g. Made for You, Discover Weekly).
     * Works when Spotify-only home is enabled so that playlists can be pinned from the Spotify section.
     */
    fun togglePin(spotifyPlaylist: SpotifyPlaylist) {
        viewModelScope.launch(Dispatchers.IO) {
            val speedDialItem = SpeedDialItem.fromSpotifyPlaylist(spotifyPlaylist)
            val isPinned = database.speedDialDao.isPinned(speedDialItem.id).first()
            if (isPinned) {
                database.speedDialDao.delete(speedDialItem.id)
            } else {
                database.speedDialDao.insert(speedDialItem)
            }
        }
    }

    private suspend fun getDailyDiscover() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val likedSongs = database.likedSongsByCreateDateAsc().first()
        if (likedSongs.isEmpty()) return

        val seeds = likedSongs.shuffled().distinctBy { it.id }.take(5)
        
        // Use a synchronized list to collect results safely from concurrent coroutines
        val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

        kotlinx.coroutines.coroutineScope {
            seeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            val recommendations = page.songs
                                .filter { item ->
                                    if (hideVideoSongs && item.isVideoSong) return@filter false
                                    if (item.explicit) return@filter false
                                    true
                                }
                                .shuffled()

                            // Simple check to avoid immediate duplicate of seed
                            val recommendation = recommendations.firstOrNull { rec ->
                                rec.id != seed.id
                            }

                            if (recommendation != null) {
                                items.add(
                                    DailyDiscoverItem(
                                        seed = seed,
                                        recommendation = recommendation,
                                        relatedEndpoint = endpoint
                                    )
                                )
                            }
                        }
                    }
                }
            }.forEach { it.join() }
        }
        
        // Final deduplication just in case multiple seeds recommended the same song
        dailyDiscover.value = items.toList().distinctBy { it.recommendation.id }.shuffled()
    }

    private suspend fun getQuickPicks() {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                val relatedSongs = database.quickPicks().first().filterVideoSongs(hideVideoSongs)
                val forgotten = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).take(8)

                // Get similar songs from YouTube based on recent listening
                val recentSong = database.latestEvent().first()?.song
                val ytSimilarSongs = mutableListOf<Song>()

                if (recentSong != null) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = recentSong.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            // Convert YouTube songs to local Song format if they exist in database
                            page.songs.take(10).forEach { ytSong ->
                                database.song(ytSong.id).first()?.let { localSong ->
                                    if (!hideVideoSongs || !localSong.song.isVideo) {
                                        ytSimilarSongs.add(localSong)
                                    }
                                }
                            }
                        }
                    }
                }

                // Combine all sources and remove duplicates
                val combined = (relatedSongs + forgotten + ytSimilarSongs)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(20)

                quickPicks.value = combined.ifEmpty { relatedSongs.shuffled().take(20) }
            }
            QuickPicks.LAST_LISTEN -> {
                val song = database.latestEvent().first()?.song
                if (song != null && database.hasRelatedSongs(song.id)) {
                    quickPicks.value = database.getRelatedSongs(song.id).first().filterVideoSongs(hideVideoSongs).shuffled().take(20)
                }
            }
        }
    }

    private suspend fun getCommunityPlaylists() {
        val fromTimeStamp = LocalDateTime.now().minusWeeks(4)
        val artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
        val songSeeds = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 5, offset = 0, toTimeStamp = LocalDateTime.now()).first()
            .shuffled().take(2)

        val candidatePlaylists = java.util.Collections.synchronizedList(mutableListOf<PlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            artistSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    YouTube.artist(seed.id).onSuccess { page ->
                        page.sections.forEach { section ->
                            section.items.filterIsInstance<PlaylistItem>().forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    playlist.author?.name != seed.artist.name &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
            
            songSeeds.map { seed ->
                launch(Dispatchers.IO) {
                    val endpoint = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.relatedEndpoint
                    if (endpoint != null) {
                        YouTube.related(endpoint).onSuccess { page ->
                            page.playlists.forEach { playlist ->
                                if (playlist.author?.name != "YouTube Music" && 
                                    playlist.author?.name != "YouTube" && 
                                    playlist.author?.name != "Playlist" &&
                                    !playlist.id.startsWith("RD") &&
                                    !playlist.id.startsWith("OLAK")
                                ) {
                                    candidatePlaylists.add(playlist)
                                }
                            }
                        }
                    }
                }
            }
        }

        val uniqueCandidates = candidatePlaylists.distinctBy { it.id }.shuffled().take(5)

        val playlists = java.util.Collections.synchronizedList(mutableListOf<CommunityPlaylistItem>())

        kotlinx.coroutines.coroutineScope {
            uniqueCandidates.map { playlist ->
                launch(Dispatchers.IO) {
                    YouTube.playlist(playlist.id).onSuccess { page ->
                        val songs = page.songs.take(10)
                        if (songs.isNotEmpty()) {
                            // Use song count from the playlist page if available, otherwise use original
                            val songCountText = page.playlist.songCountText ?: playlist.songCountText
                            val updatedPlaylist = playlist.copy(songCountText = songCountText)
                            playlists.add(CommunityPlaylistItem(updatedPlaylist, songs))
                        }
                    }
                }
            }.forEach { it.join() }
        }

        communityPlaylists.value = playlists.shuffled()
    }

    private suspend fun load() {
        isLoading.value = true
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        val fromTimeStamp = LocalDateTime.now().minusWeeks(2)

        // Read Spotify preferences directly from DataStore to avoid StateFlow race condition
        val prefs = context.dataStore.data.first()
        val spotifyEnabled = prefs[EnableSpotifyKey] ?: true
        val spotifyUseForHome = prefs[UseSpotifyHomeKey] ?: true
        val spotifyHomeOnlyPref = prefs[SpotifyHomeOnlyKey] ?: true
        val spotifyHasToken = (prefs[SpotifyAccessTokenKey] ?: "").isNotEmpty() ||
            (prefs[SpotifyAnonymousHomeKey] ?: false)
        val isSpotifyHome = spotifyEnabled && spotifyUseForHome && spotifyHasToken
        val isSpotifyOnly = isSpotifyHome && spotifyHomeOnlyPref

        // Local play history — always loaded regardless of Spotify mode
        recentlyPlayed.value = database.events().first()
            .distinctBy { it.song.id }
            .take(40)
            .map { it.song }
            .filterVideoSongs(hideVideoSongs)

        // When Spotify-only mode is active, skip all YouTube-based content
        if (!isSpotifyOnly) {
            getQuickPicks()
            getDailyDiscover()
            getCommunityPlaylists()
            forgottenFavorites.value = database.forgottenFavorites().first().filterVideoSongs(hideVideoSongs).shuffled().take(20)

            val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5).first().filterVideoSongs(hideVideoSongs).shuffled().take(10)
            val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2).first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
            val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp).first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
            keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

            if (YouTube.cookie != null) {
                viewModelScope.launch(Dispatchers.IO) { loadAccountInfo() }
                viewModelScope.launch(Dispatchers.IO) { loadAccountPlaylists() }
            }

            viewModelScope.launch(Dispatchers.IO) {
            val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 15).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(4)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        page.sections.takeLast(3).forEach { section ->
                            items += section.items
                        }
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .distinctBy { item -> item.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .take(12)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val songRecommendations = database.mostPlayedSongs(fromTimeStamp = fromTimeStamp, limit = 15, offset = 0, toTimeStamp = LocalDateTime.now()).first()
                .filter { it.album != null }
                .shuffled().take(3)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(10) +
                                page.albums.shuffled().take(5) +
                                page.artists.shuffled().take(3) +
                                page.playlists.shuffled().take(3))
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }

            val albumRecommendations = database.mostPlayedAlbums(fromTimeStamp, limit = 10).first()
                .filter { it.album.thumbnailUrl != null && !it.id.startsWith("spotify:") }
                .shuffled().take(2)
                .mapNotNull { album ->
                    val items = mutableListOf<YTItem>()
                    YouTube.album(album.id).onSuccess { page ->
                        page.otherVersions.let { items += it }
                    }
                    album.artists.firstOrNull()?.id?.let { artistId ->
                        YouTube.artist(artistId).onSuccess { page ->
                            page.sections.lastOrNull()?.items?.let { items += it }
                        }
                    }
                    SimilarRecommendation(
                        title = album,
                        items = items
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .filterVideoSongs(hideVideoSongs)
                            .shuffled()
                            .take(10)
                            .ifEmpty { return@mapNotNull null }
                    )
                }

                similarRecommendations.value = (artistRecommendations + songRecommendations + albumRecommendations).shuffled()
            }
        }

        // Load remote content: Spotify or YouTube depending on preference
        Timber.d("spotifyHome: gate isSpotifyHome=$isSpotifyHome isSpotifyOnly=$isSpotifyOnly")
        if (isSpotifyHome && SpotifyTokenManager.ensureAuthenticated()) {
            Timber.d("spotifyHome: auth OK, loading Spotify sections")
            loadSpotifyHomeSections(hideExplicit)
        } else if (!isSpotifyOnly) {
            Timber.d("spotifyHome: falling back to YouTube home (auth failed or not enabled)")
            spotifyHomeSections.value = null

            YouTube.home().onSuccess { page ->
                homePage.value = page.copy(
                    sections = page.sections.mapNotNull { section ->
                        val filteredItems = section.items.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts)
                        if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                    }
                )
            }.onFailure {
                reportException(it)
            }
        }

        if (!isSpotifyOnly) {
            allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
                .filter { it is Song || it is Album }
            allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                    homePage.value?.sections?.flatMap { it.items }.orEmpty()
        }

        isLoading.value = false

        // Load YouTube explore (mood & genres) in the background — it is also shown
        // alongside Spotify home sections, but never in Spotify-only mode.
        // dailyDiscover, communityPlaylists and similarRecommendations already ran
        // synchronously above (gated by !isSpotifyOnly); they are no longer re-run here.
        if (!isSpotifyOnly) {
            viewModelScope.launch(Dispatchers.IO) {
                YouTube.explore().onSuccess { page ->
                    explorePage.value = page.copy(
                        newReleaseAlbums = page.newReleaseAlbums.filterExplicit(hideExplicit)
                    )
                }.onFailure { reportException(it) }
            }
        }
    }

    private suspend fun loadSpotifyHomeSections(hideExplicit: Boolean) {
        Timber.d("spotifyHome: loadSpotifyHomeSections() START (hideExplicit=$hideExplicit)")
        val sections = mutableListOf<SpotifyHomeSection>()
        var newReleaseAlbums = emptyList<SpotifyAlbum>()

        try {
            // These sources are independent — fetch them concurrently and then
            // assemble the sections in a fixed display order. Previously they ran
            // strictly sequentially (~1-2.7s of chained round-trips).
            var chartRows = SpotifyChartRows()
            val (profileTracks, newReleasesResult, homeResult) = coroutineScope {
                val topTracksDeferred = async { SpotifyProfileCache.getTopTracks(context, database, limit = 20) }
                val newReleasesDeferred = async { Spotify.newReleases(limit = 20) }
                val chartsDeferred = async { Spotify.chartRows() }
                val homeDeferred = async { Spotify.home(sectionItemsLimit = 10) }
                chartsDeferred.await().onSuccess { chartRows = it }.onFailure { e ->
                    // An empty personal shelf with no failure means the session fell back to anonymous.
                    Timber.w(e, "spotifyHome: chartRows() FAILED — ${e.javaClass.simpleName}: ${e.message}")
                }
                Triple(topTracksDeferred.await(), newReleasesDeferred.await(), homeDeferred.await())
            }

            Timber.d("spotifyHome: top tracks from profile cache = ${profileTracks.size}")
            val topTracks = if (hideExplicit) profileTracks.filter { !it.explicit } else profileTracks
            if (topTracks.isNotEmpty()) {
                sections.add(SpotifyHomeSection(
                    title = "spotify_top_tracks",
                    type = SectionType.TRACKS,
                    tracks = topTracks,
                ))
                Timber.d("spotifyHome: added pinned section 'Your Top Tracks' (${topTracks.size} tracks)")
            } else {
                Timber.w("spotifyHome: no top tracks — skipping pinned section")
            }

            newReleasesResult.onSuccess { newReleases ->
                val albums = newReleases.albums?.items.orEmpty()
                newReleaseAlbums = albums
                if (albums.isNotEmpty()) {
                    sections.add(SpotifyHomeSection(
                        title = "spotify_new_releases",
                        type = SectionType.ALBUMS,
                        albums = albums,
                    ))
                    Timber.d("spotifyHome: added pinned section 'New Releases' (${albums.size} albums)")
                } else {
                    Timber.w("spotifyHome: newReleases returned 0 albums — skipping section")
                }
            }.onFailure { e ->
                Timber.e(e, "spotifyHome: newReleases() FAILED — ${e.javaClass.simpleName}: ${e.message}")
            }

            // The rail has no Charts tab, so both shelves live here. A tile's ranked list is an
            // ordinary playlist screen, so nothing beyond these searches is fetched for the rows.
            if (chartRows.personal.isNotEmpty()) {
                sections.add(SpotifyHomeSection(
                    title = "spotify_your_charts",
                    type = SectionType.PLAYLISTS,
                    playlists = chartRows.personal,
                ))
                Timber.d("spotifyHome: added pinned section 'Your Charts' (${chartRows.personal.size}: ${chartRows.personal.map { it.name }})")
            } else {
                Timber.w("spotifyHome: no madeFor playlists — skipping Your Charts (anonymous session?)")
            }
            if (chartRows.country.isNotEmpty()) {
                sections.add(SpotifyHomeSection(
                    title = "spotify_country_charts",
                    type = SectionType.PLAYLISTS,
                    playlists = chartRows.country,
                ))
                Timber.d("spotifyHome: added pinned section 'Country Charts' (${chartRows.country.size}: ${chartRows.country.map { it.name }})")
            } else {
                Timber.w("spotifyHome: chartRows().country returned 0 — skipping Country Charts section")
            }

            homeResult.onSuccess { feed ->
                Timber.d("spotifyHome: home() OK — greeting='${feed.greeting}' rawSections=${feed.sections.size}")
                feed.sections.forEachIndexed { i, s ->
                    Timber.d("spotifyHome:   raw[$i] title='${s.title}' type=${s.typename} items=${s.items.size} totalCount=${s.totalCount}")
                }
                // Spotify sends its "More like …" and "Made for you" shelves one card at a time, which
                // used to become one shelf per card — twenty rows of a single tile. Gathering cards by
                // the shelf they came from puts each name back as a row header with every tile under it.
                // Only a name carrying a lone leftover card folds into "You might like", so no tile is
                // dropped and no row is one tile wide.
                val singles = linkedMapOf<String, MutableList<SpotifyHomeFeedItem>>()
                feed.sections.forEach { raw ->
                    val converted = convertHomeSection(raw)
                    if (converted == null) {
                        Timber.d("spotifyHome: SKIPPED '${raw.title ?: "<no title>"}' (typename=${raw.typename})")
                    } else if (converted.items.size == 1) {
                        singles.getOrPut(converted.title) { mutableListOf() } += converted.items.first()
                    } else {
                        sections.add(converted)
                        Timber.d("spotifyHome: ADDED '${converted.title}' type=${converted.type} items=${converted.items.size}")
                    }
                }
                val leftovers = mutableListOf<SpotifyHomeFeedItem>()
                singles.forEach { (name, cards) ->
                    val unique = cards.distinctBy { item -> item.uri }
                    val home = sections.indexOfFirst { it.title == name && it.items.isNotEmpty() }
                    when {
                        // The same shelf can arrive both full and card-by-card; a second row with
                        // the same header would read as a bug, so the loose cards join its tiles.
                        home >= 0 -> {
                            val merged = (sections[home].items + unique).distinctBy { item -> item.uri }
                            sections[home] = sections[home].copy(items = merged)
                            Timber.d("spotifyHome: merged ${unique.size} cards into existing row '$name' (${merged.size} cards)")
                        }
                        unique.size >= 2 -> {
                            sections.add(
                                SpotifyHomeSection(
                                    title = name,
                                    type = unique.first().sectionType(),
                                    items = unique,
                                ),
                            )
                            Timber.d("spotifyHome: gathered ${unique.size} single-card shelves '$name' into one row")
                        }
                        else -> leftovers += unique
                    }
                }
                if (leftovers.isNotEmpty()) {
                    val unique = leftovers.distinctBy { item -> item.uri }
                    sections.add(
                        SpotifyHomeSection(
                            title = "spotify_you_might_like",
                            type = unique.first().sectionType(),
                            items = unique,
                        ),
                    )
                    Timber.d("spotifyHome: folded ${leftovers.size} leftover cards into 'You might like' (${unique.size} cards)")
                }
            }.onFailure { e ->
                Timber.e(e, "spotifyHome: home() FAILED — ${e.javaClass.simpleName}: ${e.message}")
            }
        } catch (e: Exception) {
            Timber.e(e, "spotifyHome: loadSpotifyHomeSections() threw ${e.javaClass.simpleName}: ${e.message}")
            reportException(e)
        }

        Timber.d("spotifyHome: loadSpotifyHomeSections() END — final sections=${sections.size}")
        spotifyHomeSections.value = sections.ifEmpty { null }
        if (sections.isEmpty()) Timber.w("spotifyHome: flow set to NULL — UI will show nothing")

        homePage.value = null
        explorePage.value = null

        prefetchNewReleaseTracks(newReleaseAlbums)
    }

    /**
     * Warms the New Releases track lists in the background once Home has landed. A tile on that
     * shelf queues the whole shelf, which would otherwise need one `getAlbum` round-trip per release
     * at the moment of the press; publishing them as they arrive is what keeps the press instant.
     */
    private fun prefetchNewReleaseTracks(albums: List<SpotifyAlbum>) {
        newReleaseTracks.value = emptyMap()
        if (albums.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val gate = Semaphore(NewReleasePrefetchConcurrency)
            albums.map { album ->
                launch(Dispatchers.IO) {
                    gate.withPermit {
                        val tracks = Spotify.album(album.id).getOrNull()?.tracks?.items
                            ?.filter { !it.isLocal && it.id.isNotEmpty() }
                            .orEmpty()
                        if (tracks.isNotEmpty()) {
                            newReleaseTracks.update { pending -> pending + (album.id to tracks) }
                        }
                    }
                }
            }.forEach { worker -> worker.join() }
            Timber.d("spotifyHome: prefetched ${newReleaseTracks.value.size}/${albums.size} New Releases track lists")
        }
    }

    /**
     * Converts a Spotify home-feed section (mixed types, Spotify-localized title)
     * into our [SpotifyHomeSection] model. [SpotifyHomeSection.type] carries the
     * dominant content type for the hero and the play action, while [SpotifyHomeSection.items]
     * keeps every card in the order Spotify sent it, so a heterogeneous shelf no longer loses its
     * minority items. Episode-only sections are skipped (not playable as tracks); the titleless
     * shorts row is the one titleless section with a name of its own.
     */
    private fun convertHomeSection(feedSection: SpotifyHomeFeedSection): SpotifyHomeSection? {
        // Spotify's own top-of-home tiles ride in a HomeShortsSectionData that carries no section
        // title - it is a row of shortcuts, not a shelf - so requiring one dropped the whole row:
        // that is where "Fresh new music", "Party" and "Recommended stations" live. Any other
        // titleless section stays dropped, because there is no name for what it would be.
        val title =
            feedSection.title
                ?: feedSection.typename
                    .takeIf { it == "HomeShortsSectionData" }
                    ?.let { "spotify_home_shorts" }
                ?: return null

        val playlists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Playlist>()
        val albums = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Album>()
        val artists = feedSection.items.filterIsInstance<SpotifyHomeFeedItem.Artist>()

        val counts = listOf(
            SectionType.PLAYLISTS to playlists.size,
            SectionType.ALBUMS to albums.size,
            SectionType.ARTISTS to artists.size,
        )
        val (dominant, size) = counts.maxByOrNull { it.second } ?: return null
        if (size == 0) return null

        return SpotifyHomeSection(
            title = title,
            type = dominant,
            playlists = playlists.map { it.toSpotifyPlaylist() },
            albums = albums.map { it.toSpotifyAlbum() },
            artists = artists.map { it.toSpotifyArtist() },
            items = feedSection.items,
        )
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).mapNotNull { section ->
                    val filteredItems = section.items
                        .filterOutNulls()
                        .filterExplicit(hideExplicit)
                        .filterVideoSongs(hideVideoSongs)
                        .filterYoutubeShorts(hideYoutubeShorts)
                    if (filteredItems.isEmpty()) null else section.copy(items = filteredItems)
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
            val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
            val nextSections = YouTube.home(params = chip.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.mapNotNull { section ->
                    section.copy(items = section.items.filterOutNulls().filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                }
            )
            selectedChip.value = chip

            // Fetch podcast-specific data when podcasts chip is selected
            if (chip.title.contains("Podcast", ignoreCase = true)) {
                fetchPodcastData()
            }
        }
    }

    private suspend fun fetchPodcastData() {
        // Fetch saved podcast shows from official API
        YouTube.savedPodcastShows().onSuccess { shows ->
            savedPodcastShows.value = shows.filterOutNulls()
        }.onFailure {
            reportException(it)
        }

        // Fetch episodes for later from official API
        YouTube.episodesForLater().onSuccess { episodes ->
            episodesForLater.value = episodes.filterOutNulls()
        }.onFailure {
            reportException(it)
        }
    }

    private suspend fun loadAccountInfo() {
        YouTube.accountInfo().onSuccess { info ->
            accountName.value = info.name
            accountImageUrl.value = info.thumbnailUrl
        }.onFailure {
            reportException(it)
        }
    }

    private val playlistCacheJson = Json { ignoreUnknownKeys = true }
    private val playlistCacheKey = androidx.datastore.preferences.core.stringPreferencesKey("spotify_cached_playlists_json")

    private suspend fun loadCachedPlaylists(): List<SpotifyPlaylist> {
        return try {
            val prefs = context.dataStore.data.first()
            val jsonStr = prefs[playlistCacheKey] ?: return emptyList()
            playlistCacheJson.decodeFromString<List<SpotifyPlaylist>>(jsonStr)
        } catch (e: Exception) {
            Timber.w(e, "HomeVM: failed to load cached playlists")
            emptyList()
        }
    }

    private suspend fun loadAccountPlaylists() {
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
            accountPlaylists.value = it.items.filterIsInstance<PlaylistItem>()
                .filterOutNulls()
                .filterNot { it.id == "SE" }
                .filterYoutubeShorts(hideYoutubeShorts)
        }.onFailure {
            reportException(it)
        }
    }

    /**
     * Safely filters out null items from a list whose type says non-null
     * but may contain nulls at runtime due to JSON parsing.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> List<T>.filterOutNulls(): List<T> =
        (this as List<T?>).filterNotNull()

    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            // If a chip is selected, reload the chip's content instead of the default home
            val currentChip = selectedChip.value
            if (currentChip != null) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
                val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
                val nextSections = YouTube.home(params = currentChip.endpoint?.params).getOrNull()
                if (nextSections != null) {
                    homePage.value = nextSections.copy(
                        chips = homePage.value?.chips,
                        sections = nextSections.sections.mapNotNull { section ->
                            section.copy(items = section.items.filterOutNulls().filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs).filterYoutubeShorts(hideYoutubeShorts))
                        }
                    )
                }
            } else {
                load()
            }
            isRefreshing.value = false
        }
        // Run sync when user manually refreshes
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }
    }

    init {
        // Run sync in separate coroutine with cooldown to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.tryAutoSync()
        }

        var wasOffline = !networkConnectivity.networkStatus.value
        viewModelScope.launch(Dispatchers.IO) {
            networkConnectivity.networkStatus.collect { isConnected ->
                if (!isConnected) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    refresh()
                }
            }
        }

        // Listen for cookie changes and reload account data
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] to it[AccountNameKey] }
                .distinctUntilChanged()
                .collect { (cookie, savedAccountName) ->
                    if (!cookie.isNullOrEmpty()) {
                        YouTube.cookie = cookie
                        accountName.value = savedAccountName.orEmpty().ifBlank { "Guest" }
                        loadAccountInfo()
                    } else {
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    }
                }
        }

        // Listen for HideYoutubeShorts preference changes and reload account playlists instantly
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[HideYoutubeShortsKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    if (YouTube.cookie != null && accountPlaylists.value != null) {
                        loadAccountPlaylists()
                    }
                }
        }

        // Issue #145: Spotify pinned artwork (e.g. Discover Weekly) was captured at
        // pin time and never refreshed, so the speed dial kept showing the old image
        // even after Spotify rotated the playlist cover. Fetch the live playlist on
        // startup and update the cached SpeedDialItem when the thumbnail URL changed.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val enabled = context.dataStore.get(EnableSpotifyKey, true)
                val token = context.dataStore.get(SpotifyAccessTokenKey, "")
                if (!enabled || token.isEmpty()) return@launch
                val pinned = database.speedDialDao.getAll().first()
                pinned.asSequence()
                    .filter { it.id.startsWith("spotify:") }
                    .forEach { item ->
                        val playlistId = item.id.removePrefix("spotify:")
                        Spotify.playlist(playlistId).onSuccess { playlist ->
                            val freshThumb = com.aura.spotify.SpotifyMapper
                                .getPlaylistThumbnail(playlist)
                            if (!freshThumb.isNullOrEmpty() && freshThumb != item.thumbnailUrl) {
                                database.speedDialDao.insert(
                                    item.copy(
                                        title = playlist.name.ifEmpty { item.title },
                                        thumbnailUrl = freshThumb,
                                    )
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                Timber.w(e, "Failed to refresh Spotify pinned thumbnails")
            }
        }
    }

    private var isHomeDataLoaded = false

    fun loadHomeData() {
        if (isHomeDataLoaded) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cookie = context.dataStore.data
                    .map { it[InnerTubeCookieKey] }
                    .distinctUntilChanged()
                    .first()

                if (!cookie.isNullOrEmpty()) {
                    YouTube.cookie = cookie
                }

                isHomeDataLoaded = true
                load()
            } catch (e: Exception) {
                isHomeDataLoaded = false
                Timber.e(e, "Failed to load home data")
            }
        }
    }
}
