package com.aura.innertube.pages

import com.aura.innertube.models.Artist
import com.aura.innertube.models.MusicResponsiveListItemRenderer
import com.aura.innertube.models.Run
import com.aura.innertube.models.Runs
import com.aura.innertube.models.SongItem
import com.aura.innertube.models.Thumbnail
import com.aura.innertube.models.ThumbnailRenderer
import com.aura.innertube.models.Thumbnails
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPageTest {
    @Test
    fun `card song inherits artist when response only contains type and duration`() {
        val renderer = songRenderer(
            title = "Howling",
            metadata = listOf(
                Run("Song", null),
                Run(" • ", null),
                Run("3:04", null),
                Run("6m\u00a0plays", null),
            ),
        )
        val fallbackArtist = Artist("Lupus Nocte", "UC123")

        val song = SearchPage.toYTItem(
            renderer,
            fallbackArtists = listOf(fallbackArtist),
        ) as SongItem

        assertEquals(listOf(fallbackArtist), song.artists)
        assertEquals(184, song.duration)
    }

    private fun songRenderer(
        title: String,
        metadata: List<Run>,
    ) = MusicResponsiveListItemRenderer(
        badges = null,
        fixedColumns = null,
        flexColumns = listOf(
            flexColumn(listOf(Run(title, null))),
            flexColumn(metadata),
        ),
        thumbnail = ThumbnailRenderer(
            musicThumbnailRenderer = ThumbnailRenderer.MusicThumbnailRenderer(
                thumbnail = Thumbnails(listOf(Thumbnail("https://example.com/cover.jpg", null, null))),
                thumbnailCrop = null,
                thumbnailScale = null,
            ),
            musicAnimatedThumbnailRenderer = null,
            croppedSquareThumbnailRenderer = null,
        ),
        menu = null,
        playlistItemData = MusicResponsiveListItemRenderer.PlaylistItemData(
            playlistSetVideoId = null,
            videoId = "videoId",
        ),
        overlay = null,
        navigationEndpoint = null,
    )

    private fun flexColumn(runs: List<Run>) = MusicResponsiveListItemRenderer.FlexColumn(
        MusicResponsiveListItemRenderer.FlexColumn.MusicResponsiveListItemFlexColumnRenderer(Runs(runs)),
    )
}
