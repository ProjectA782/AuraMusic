/**
 * Aura Music — rolling "Played in Aura" Spotify playlist.
 *
 * Original to this project: nothing upstream writes playback back to Spotify.
 */

package com.aura.music.playback

import android.content.Context
import android.util.LruCache
import com.aura.music.constants.EnableSpotifyKey
import com.aura.music.constants.SpotifyAccessTokenKey
import com.aura.music.constants.SpotifyPlaysPlaylistIdKey
import com.aura.music.constants.SpotifySpDcKey
import com.aura.music.constants.SpotifySyncPlaysKey
import com.aura.music.db.MusicDatabase
import com.aura.music.utils.dataStore
import com.aura.music.utils.get
import com.aura.music.utils.safeDataStoreEdit
import com.aura.spotify.Spotify
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Appends songs the user actually listened to into one Spotify playlist and keeps it
 * trimmed to the newest [MAX_ENTRIES]. Writes need a real session — the anonymous
 * token can read the catalogue but cannot mutate a library.
 */
object SpotifyPlaySync {
    const val PLAYLIST_NAME = "Played in Aura"

    private const val TAG = "SpotifyPlaySync"
    private const val MAX_ENTRIES = 50
    private const val TRIM_SCAN_SIZE = 80

    private val recentlyAdded = LruCache<String, Unit>(400)
    private val writeLock = Mutex()

    @Volatile
    private var mapper: SpotifyYouTubeMapper? = null

    /**
     * Called once per playback session that cleared the app's listen threshold.
     * Absent title/artist, or a song with no Spotify counterpart, is skipped silently.
     */
    suspend fun sync(
        context: Context,
        database: MusicDatabase,
        youtubeId: String,
        title: String,
        artist: String,
    ) {
        if (!isEnabled(context)) return

        val uri = SpotifyMetadataRegistry.get(youtubeId)?.uri
            ?: mapperFor(database).resolveToSpotifyUri(
                youtubeId = youtubeId,
                title = title,
                artist = artist,
            )
        if (uri == null) {
            Timber.tag(TAG).w("No Spotify match for '$artist — $title' ($youtubeId)")
            return
        }
        if (recentlyAdded[uri] != null) return

        val playlistId = resolvePlaylistId(context) ?: return

        writeLock.withLock {
            Spotify.addTracksToPlaylist(playlistId, listOf(uri)).onFailure {
                Timber.tag(TAG).w(it, "Could not add $uri to $playlistId")
                return
            }
            recentlyAdded.put(uri, Unit)
            Timber.tag(TAG).d("Added $uri to '$PLAYLIST_NAME' ($playlistId)")
            trimToNewest(playlistId)
        }
    }

    /**
     * Creates or adopts the playlist up front so the settings row can report the
     * outcome instead of failing quietly on the first played song.
     */
    suspend fun ensurePlaylist(context: Context): String? = resolvePlaylistId(context)

    private fun isEnabled(context: Context): Boolean {
        val store = context.dataStore
        if (!store.get(SpotifySyncPlaysKey, false)) return false
        if (!store.get(EnableSpotifyKey, true)) return false
        val hasSession =
            store.get(SpotifyAccessTokenKey, "").isNotEmpty() &&
                store.get(SpotifySpDcKey, "").isNotEmpty()
        if (!hasSession) {
            Timber.tag(TAG).d("Skipped: Spotify session is anonymous, writes are impossible")
            return false
        }
        return true
    }

    /**
     * Stored id first, then a playlist the user already owns, then create one.
     * Spotify withdrew playlist creation from third-party tokens in November 2024,
     * so adopting an existing one is the common path, not the exception.
     */
    private suspend fun resolvePlaylistId(context: Context): String? {
        val stored = context.dataStore.get(SpotifyPlaysPlaylistIdKey, "")
        if (stored.isNotEmpty()) return stored

        val existing = Spotify.myPlaylists(limit = 50).getOrNull()
            ?.items
            ?.firstOrNull { it.name.equals(PLAYLIST_NAME, ignoreCase = true) }
            ?.id
        if (existing != null) {
            Timber.tag(TAG).d("Adopting existing '$PLAYLIST_NAME' ($existing)")
            return existing.also { persistPlaylistId(context, it) }
        }

        val created = Spotify.createPlaylist(
            name = PLAYLIST_NAME,
            description = "Filled by Aura Music as songs finish playing.",
        ).onFailure { Timber.tag(TAG).w(it, "'$PLAYLIST_NAME' could not be created") }.getOrNull()
            ?: return null

        Timber.tag(TAG).d("Created '$PLAYLIST_NAME' ($created)")
        return created.also { persistPlaylistId(context, it) }
    }

    private suspend fun persistPlaylistId(
        context: Context,
        playlistId: String,
    ) {
        context.safeDataStoreEdit { it[SpotifyPlaysPlaylistIdKey] = playlistId }
    }

    /**
     * New items land at the bottom, so the oldest listens are the first entries.
     */
    private suspend fun trimToNewest(playlistId: String) {
        val page = Spotify.playlistTracks(playlistId, limit = TRIM_SCAN_SIZE).getOrNull()
            ?: return
        val overflow = page.total - MAX_ENTRIES
        if (overflow <= 0) return

        val oldest = page.items.take(overflow).mapNotNull { entry ->
            val uid = entry.uid ?: return@mapNotNull null
            Spotify.PlaylistItemRef(
                uri = entry.track?.uri ?: "spotify:track:${entry.track?.id.orEmpty()}",
                uid = uid,
            )
        }
        if (oldest.isEmpty()) return

        Spotify.removeTracksFromPlaylist(playlistId, oldest).onFailure {
            Timber.tag(TAG).w(it, "Could not trim ${oldest.size} old items from $playlistId")
        }
    }

    private fun mapperFor(database: MusicDatabase): SpotifyYouTubeMapper =
        mapper ?: SpotifyYouTubeMapper(database).also { mapper = it }
}
