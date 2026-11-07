/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.models

import com.aura.spotify.models.SpotifyAlbum
import com.aura.spotify.models.SpotifyArtist
import com.aura.spotify.models.SpotifyHomeFeedItem
import com.aura.spotify.models.SpotifyImage
import com.aura.spotify.models.SpotifyPlaylist
import com.aura.spotify.models.SpotifyPlaylistOwner
import com.aura.spotify.models.SpotifyPlaylistTracksRef
import com.aura.spotify.models.SpotifyTrack

/**
 * Represents a section in the Spotify-powered home screen.
 *
 * [type] and the typed lists describe the shelf's dominant content — the hero and the pinned rows
 * (top tracks, new releases) are single-typed and use them. A shelf straight out of Spotify's feed
 * mixes cards, so it also carries them in [items], in the order Spotify sent them, and the row is
 * allowed to show all of them instead of only the dominant type.
 */
data class SpotifyHomeSection(
    val title: String,
    val type: SectionType,
    val tracks: List<SpotifyTrack> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val items: List<SpotifyHomeFeedItem> = emptyList(),
)

enum class SectionType {
    TRACKS,
    ARTISTS,
    ALBUMS,
    PLAYLISTS,
}

/** Which of the four shelf shapes a feed card would fill on its own. */
fun SpotifyHomeFeedItem.sectionType(): SectionType =
    when (this) {
        is SpotifyHomeFeedItem.Playlist -> SectionType.PLAYLISTS
        is SpotifyHomeFeedItem.Album -> SectionType.ALBUMS
        is SpotifyHomeFeedItem.Artist -> SectionType.ARTISTS
    }

/**
 * A feed card is a thin projection of what Spotify's shelf sent; the menus and players downstream
 * expect the full models, so every consumer converts through these three.
 */
fun SpotifyHomeFeedItem.Playlist.toSpotifyPlaylist(): SpotifyPlaylist =
    SpotifyPlaylist(
        id = id,
        name = name,
        description = description,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
        owner = ownerName?.let { SpotifyPlaylistOwner(displayName = it) },
        tracks = SpotifyPlaylistTracksRef(total = totalCount),
        uri = uri,
    )

fun SpotifyHomeFeedItem.Album.toSpotifyAlbum(): SpotifyAlbum =
    SpotifyAlbum(
        id = id,
        name = name,
        albumType = albumType,
        artists = artists,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
        uri = uri,
    )

fun SpotifyHomeFeedItem.Artist.toSpotifyArtist(): SpotifyArtist =
    SpotifyArtist(
        id = id,
        name = name,
        images = imageUrl?.let { listOf(SpotifyImage(url = it)) } ?: emptyList(),
        uri = uri,
    )
