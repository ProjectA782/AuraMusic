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
 * albums and artists — so the type-agnostic fields live on the base, and [sourceTitle] lets a
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
}
