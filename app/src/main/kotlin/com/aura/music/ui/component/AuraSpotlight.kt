/**
 * Aura Music
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Shared state for the mock's signature behaviour: the ambient backdrop and the hero banner are
 * driven by whatever tile currently holds focus, so the page re-tints as the D-pad moves.
 */

package com.aura.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent

/**
 * Everything the shell needs to paint the hero text and the blurred cover behind the whole page.
 * A single mutable holder keeps focus updates off the recomposition path of the scrolling rows.
 */
@Stable
class AuraSpotlight {
    var title by mutableStateOf("")
        private set
    var subtitle by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set
    var typeLabel by mutableStateOf("")
        private set
    var imageUrl by mutableStateOf<String?>(null)
        private set
    var onPlay by mutableStateOf<(() -> Unit)?>(null)
        private set
    var onDetails by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun update(
        title: String,
        subtitle: String,
        description: String,
        typeLabel: String,
        imageUrl: String?,
        onPlay: () -> Unit,
        onDetails: () -> Unit,
    ) {
        if (this.imageUrl == imageUrl && this.title == title && this.onPlay === onPlay) return
        this.title = title
        this.subtitle = subtitle
        this.description = description
        this.typeLabel = typeLabel
        this.imageUrl = imageUrl
        this.onPlay = onPlay
        this.onDetails = onDetails
    }
}

/** Provided by MainActivity so any tile can drive the hero without prop drilling. */
val LocalAuraSpotlight = staticCompositionLocalOf { AuraSpotlight() }

@Composable
fun rememberAuraSpotlight(): AuraSpotlight = remember { AuraSpotlight() }

/**
 * Re-target the hero and the ambient wash when this tile gains focus, which is what the mock's
 * `updateHero(data)` does on every `focus` event. Placed before the caller's clickable so the
 * event is observed on the same layout node.
 */
fun Modifier.auraSpotlightOnFocus(
    spotlight: AuraSpotlight,
    title: String,
    subtitle: String,
    description: String,
    typeLabel: String,
    imageUrl: String?,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
): Modifier = onFocusEvent { state ->
    if (state.hasFocus) {
        spotlight.update(
            title = title,
            subtitle = subtitle,
            description = description,
            typeLabel = typeLabel,
            imageUrl = imageUrl,
            onPlay = onPlay,
            onDetails = onDetails,
        )
    }
}
