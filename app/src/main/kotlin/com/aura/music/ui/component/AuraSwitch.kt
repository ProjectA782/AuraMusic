/**
 * Aura Music
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The mock's toggle: a #D0BCFF track with a #381E72 thumb when on, and a #4A4458 track with a
 * #CAC4D0 thumb when off. Material 3 derives those four slots from the color scheme in a way that
 * lands on the neutral ramp instead, so settings rows take the toggle from here.
 */

package com.aura.music.ui.component

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.theme.AuraOnPrimary
import com.aura.music.ui.theme.AuraOnSurfaceVariant
import com.aura.music.ui.theme.AuraTrack

@Composable
fun AuraSwitchColors(): SwitchColors {
    val primary = MaterialTheme.colorScheme.primary
    return SwitchDefaults.colors(
        checkedTrackColor = primary,
        checkedThumbColor = AuraOnPrimary,
        checkedBorderColor = Color.Transparent,
        uncheckedTrackColor = AuraTrack,
        uncheckedThumbColor = AuraOnSurfaceVariant,
        uncheckedBorderColor = Color.Transparent,
    )
}

@Composable
fun AuraSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    // Every settings row passes a check/close glyph for the thumb. That glyph is the old
    // Material 3 Expressive toggle, and on the TV it is the single loudest thing left of the
    // previous design, so the thumb stays plain there no matter what the caller asks for.
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = if (LocalAuraTvShell.current) null else thumbContent,
        enabled = enabled,
        colors = AuraSwitchColors(),
    )
}
