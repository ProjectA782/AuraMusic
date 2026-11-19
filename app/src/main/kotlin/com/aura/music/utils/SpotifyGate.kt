/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.utils

import androidx.datastore.preferences.core.Preferences
import com.aura.music.constants.SpotifyAccessTokenKey
import com.aura.music.constants.SpotifyAnonymousHomeKey
import com.aura.music.constants.SpotifyHomeOnlyKey
import com.aura.music.constants.UseSpotifySearchKey

/**
 * Whether online search should hit Spotify instead of YouTube Music, using the same gate as the
 * Spotify home so the results cannot come from a different catalogue than the feed.
 */
fun spotifySearchActive(prefs: Preferences): Boolean {
    // Home provisions an anonymous token on first load, so this reflects "we have a Spotify
    // token in hand", not "the user logged in". The retired enableSpotify switch used to be a
    // term here, which let a logout silence search as well as the feed.
    val hasToken = (prefs[SpotifyAccessTokenKey] ?: "").isNotEmpty() ||
        (prefs[SpotifyAnonymousHomeKey] ?: false)
    if (!hasToken) return false

    return prefs[UseSpotifySearchKey] ?: (prefs[SpotifyHomeOnlyKey] ?: true)
}
