/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.aura.music.R
import com.aura.music.constants.GridThumbnailHeight
import com.aura.music.constants.ListItemHeight
import com.aura.music.constants.ThumbnailCornerRadius
import com.aura.music.models.SectionType
import com.aura.music.models.SpotifyHomeSection
import com.aura.music.ui.theme.TvRowNav
import com.aura.music.ui.theme.tvFocusRing
import com.aura.music.utils.toAlbumItem
import com.aura.music.utils.toArtistItem
import com.aura.music.utils.toPlaylistItem
import com.aura.music.utils.toSongItem
import com.aura.spotify.SpotifyMapper
import com.aura.spotify.models.SpotifyAlbum
import com.aura.spotify.models.SpotifyArtist
import com.aura.spotify.models.SpotifyPlaylist
import com.aura.spotify.models.SpotifyTrack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Resolves the display title for a Spotify home section.
 * Handles special cases like "spotify_because_you_like:ArtistName".
 */
@Composable
fun resolveSpotifySectionTitle(section: SpotifyHomeSection): String {
    val title = section.title
    return when {
        title.startsWith("spotify_because_you_like:") -> {
            val artistName = title.removePrefix("spotify_because_you_like:")
            stringResource(R.string.spotify_because_you_like, artistName)
        }
        title == "spotify_top_tracks" -> stringResource(R.string.spotify_top_tracks)
        title == "spotify_top_artists" -> stringResource(R.string.spotify_top_artists)
        title == "spotify_made_for_you" -> stringResource(R.string.spotify_made_for_you)
        title == "spotify_discover" -> stringResource(R.string.spotify_discover)
        title == "spotify_your_playlists" -> stringResource(R.string.spotify_your_playlists)
        title == "spotify_new_releases" -> stringResource(R.string.spotify_new_releases)
        title == "spotify_your_charts" -> stringResource(R.string.aura_your_charts)
        title == "spotify_country_charts" -> stringResource(R.string.aura_country_charts)
        title == "spotify_you_might_like" -> stringResource(R.string.aura_you_might_like)
        title == "spotify_home_shorts" -> stringResource(R.string.aura_home_shorts)
        else -> title
    }
}

@Composable
private fun spotlightTile(
    title: String,
    subtitle: String,
    description: String,
    typeLabel: String,
    imageUrl: String?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
): Modifier {
    val spotlight = LocalAuraSpotlight.current
    return Modifier.auraSpotlightOnFocus(
        spotlight = spotlight,
        title = title,
        subtitle = subtitle,
        description = description,
        typeLabel = typeLabel,
        imageUrl = imageUrl,
        onPlay = onPlay,
        onDetails = onDetails,
    )
}

/**
 * Renders a section of tracks as a horizontal scrollable grid (4 rows).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyTrackSectionRow(
    tracks: List<SpotifyTrack>,
    horizontalItemWidth: Dp,
    isPlaying: Boolean,
    currentMediaId: String?,
    onTrackClick: (SpotifyTrack) -> Unit,
    onTrackLongClick: (SpotifyTrack) -> Unit,
    rowNav: TvRowNav,
    rowIndex: Int,
    modifier: Modifier = Modifier,
) {
    // A fixed four-row grid reserves four rows of height even when the anonymous feed only
    // yields a couple of tracks, leaving a blank band before the next section.
    val gridRows = tracks.size.coerceIn(1, 4)
    LazyHorizontalGrid(
        state = rememberLazyGridState(),
        rows = GridCells.Fixed(gridRows),
        contentPadding = WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
        modifier = modifier
            .fillMaxWidth()
            .height(ListItemHeight * gridRows)
    ) {
        itemsIndexed(
            items = tracks,
            key = { _, track -> "spotify_track_${track.id}" }
        ) { index, track ->
            val songItem = remember(track.id) { track.toSongItem() }
            YouTubeListItem(
                item = songItem,
                isActive = false,
                isPlaying = isPlaying,
                isSwipeable = false,
                // rowNav.tile stays first: Compose resolves a FocusRequester by walking the chain
                // toward the inner modifiers, so after combinedClickable it finds no focus target.
                modifier = Modifier
                    .then(rowNav.tile(rowIndex, index))
                    .then(
                        spotlightTile(
                            title = track.name,
                            subtitle = track.artists.joinToString(", ") { it.name },
                            description = track.album?.name.orEmpty(),
                            typeLabel = stringResource(R.string.type_song),
                            imageUrl = songItem.thumbnail,
                            onPlay = { onTrackClick(track) },
                            onDetails = { onTrackLongClick(track) },
                        )
                    )
                    .width(horizontalItemWidth)
                    .combinedClickable(
                        onClick = { onTrackClick(track) },
                        onLongClick = { onTrackLongClick(track) },
                    ),
            )
        }
    }
}

/**
 * Renders a section of artists as a horizontal scrollable row with circular images.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyArtistSectionRow(
    artists: List<SpotifyArtist>,
    onArtistClick: (SpotifyArtist) -> Unit,
    rowNav: TvRowNav,
    rowIndex: Int,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier,
    ) {
        itemsIndexed(
            items = artists,
            key = { _, artist -> "spotify_artist_${artist.id}" }
        ) { index, artist ->
            val thumbnail = remember(artist.id) {
                artist.images.firstOrNull { it.width in 200..400 }?.url
                    ?: artist.images.firstOrNull()?.url
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                // rowNav.tile stays first so its FocusRequester can resolve the clickable node.
                modifier = Modifier
                    .then(rowNav.tile(rowIndex, index))
                    .tvFocusRing()
                    .width(GridThumbnailHeight + 24.dp)
                    .padding(horizontal = 6.dp)
                    .then(
                        spotlightTile(
                            title = artist.name,
                            subtitle = stringResource(R.string.type_artist),
                            description = artist.genres.joinToString("  ·  "),
                            typeLabel = stringResource(R.string.type_artist),
                            imageUrl = thumbnail,
                            onPlay = { onArtistClick(artist) },
                            onDetails = { onArtistClick(artist) },
                        )
                    )
                    .combinedClickable(
                        onClick = { onArtistClick(artist) },
                    ),
            ) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(GridThumbnailHeight)
                        .clip(CircleShape),
                )
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Renders a section of albums as a horizontal row with cover art.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyAlbumSectionRow(
    albums: List<SpotifyAlbum>,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onAlbumPlay: ((SpotifyAlbum) -> Unit)? = null,
    rowNav: TvRowNav,
    rowIndex: Int,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier,
    ) {
        itemsIndexed(
            items = albums,
            key = { _, album -> "spotify_album_${album.id}" }
        ) { index, album ->
            val albumItem = remember(album.id) { album.toAlbumItem() }
            YouTubeGridItem(
                item = albumItem,
                isActive = false,
                isPlaying = false,
                onPlayClick = onAlbumPlay?.let { cb -> { cb(album) } },
                modifier = Modifier
                    .then(rowNav.tile(rowIndex, index))
                    .width(GridThumbnailHeight + 24.dp)
                    .padding(horizontal = 6.dp)
                    .then(
                        spotlightTile(
                            title = album.name,
                            subtitle = album.artists.joinToString(", ") { it.name },
                            description = album.genres.joinToString("  ·  "),
                            typeLabel = album.albumType
                                ?: stringResource(R.string.type_album),
                            imageUrl = albumItem.thumbnail,
                            onPlay = { onAlbumPlay?.invoke(album) ?: onAlbumClick(album) },
                            onDetails = { onAlbumClick(album) },
                        )
                    )
                    .combinedClickable(
                        onClick = { onAlbumClick(album) },
                    ),
            )
        }
    }
}

/**
 * Renders a section of playlists as a horizontal row.
 * Optional [onPlaylistLongClick] enables "Pin to speed dial" from the long-press menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpotifyPlaylistSectionRow(
    playlists: List<SpotifyPlaylist>,
    onPlaylistClick: (SpotifyPlaylist) -> Unit,
    onPlaylistLongClick: ((SpotifyPlaylist) -> Unit)? = null,
    onPlaylistPlay: ((SpotifyPlaylist) -> Unit)? = null,
    rowNav: TvRowNav,
    rowIndex: Int,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier,
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, playlist -> "spotify_playlist_${playlist.id}" }
        ) { index, playlist ->
            val playlistItem = remember(playlist.id) { playlist.toPlaylistItem() }
            // combinedClickable (not pointerInput) so the tile is focusable and D-pad traversal
            // can move down into this row; a non-focusable row below makes focus search fail and
            // the selector disappears.
            YouTubeGridItem(
                item = playlistItem,
                isActive = false,
                isPlaying = false,
                onPlayClick = onPlaylistPlay?.let { cb -> { cb(playlist) } },
                modifier = Modifier
                    .then(rowNav.tile(rowIndex, index))
                    .then(
                        spotlightTile(
                            title = playlist.name,
                            subtitle = playlist.owner?.displayName.orEmpty(),
                            description = playlist.description?.stripHtml().orEmpty(),
                            typeLabel = stringResource(R.string.type_playlist),
                            imageUrl = playlistItem.thumbnail,
                            onPlay = { onPlaylistPlay?.invoke(playlist) ?: onPlaylistClick(playlist) },
                            onDetails = { onPlaylistClick(playlist) },
                        )
                    )
                    .combinedClickable(
                        onClick = { onPlaylistClick(playlist) },
                        onLongClick = onPlaylistLongClick?.let { { it(playlist) } },
                    ),
            )
        }
    }
}

/** Spotify descriptions arrive with `<a>` markup; the hero shows plain text. */
private fun String.stripHtml(): String =
    replace(Regex("<[^>]*>"), "").replace("&amp;", "&").trim()
