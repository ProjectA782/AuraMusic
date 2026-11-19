package com.aura.spotify.models

/**
 * Personalized Spotify home feed returned by the `home` GQL operation.
 * Mirrors what open.spotify.com shows on its landing page: Daily Mix,
 * Discover Weekly, Release Radar, Jump back in, recently played, etc.
 */
data class SpotifyHomeFeed(
    val greeting: String?,
    val sections: List<SpotifyHomeFeedSection>,
)

data class SpotifyHomeFeedSection(
    val sectionUri: String,
    val title: String?,
    val typename: String,
    val totalCount: Int,
    val items: List<SpotifyHomeFeedItem>,
)

/**
 * One card of a home shelf. The shelf is heterogeneous — a "More like …" row mixes playlists,
 * albums, artists and songs — so the type-agnostic fields live on the base, and [sourceTitle] lets a
 * caller label a card with a shelf name of its own.
 */
sealed class SpotifyHomeFeedItem {
    abstract val uri: String
    abstract val id: String
    abstract val name: String
    abstract val imageUrl: String?
    abstract val sourceTitle: String?

    data class Playlist(
        override val uri: String,
        override val id: String,
        override val name: String,
        val description: String?,
        val format: String?,
        val totalCount: Int,
        override val imageUrl: String?,
        val extractedColorHex: String?,
        val ownerName: String?,
        val madeForUsername: String?,
        override val sourceTitle: String? = null,
    ) : SpotifyHomeFeedItem()

    data class Album(
        override val uri: String,
        override val id: String,
        override val name: String,
        val albumType: String?,
        val artists: List<SpotifySimpleArtist>,
        override val imageUrl: String?,
        override val sourceTitle: String? = null,
    ) : SpotifyHomeFeedItem()

    data class Artist(
        override val uri: String,
        override val id: String,
        override val name: String,
        override val imageUrl: String?,
        override val sourceTitle: String? = null,
    ) : SpotifyHomeFeedItem()

    /**
     * A single song. An anonymous home feeds its biggest shelf this way — Spotify's signed-out
     * landing page leads with "Trending songs", 20 track cards — so without this variant the
     * largest shelf of a logged-out Home was the one shelf that could never be shown.
     * [imageUrl] is the album cover, since a track has no artwork of its own.
     */
    data class Track(
        override val uri: String,
        override val id: String,
        override val name: String,
        val artists: List<SpotifySimpleArtist> = emptyList(),
        val albumName: String? = null,
        val durationMs: Int = 0,
        override val imageUrl: String?,
        override val sourceTitle: String? = null,
    ) : SpotifyHomeFeedItem()
}
