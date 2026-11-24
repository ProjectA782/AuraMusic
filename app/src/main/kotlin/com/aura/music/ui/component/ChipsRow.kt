/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.R
import com.aura.music.ui.aura.AuraChip
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraPageFocusAnchor
import com.aura.music.ui.theme.AuraContainerHigh
import com.aura.music.ui.theme.AuraOnSurfaceVariant
import com.aura.music.ui.theme.AuraPrimaryContainer

/**
 * The pill naming the filter a library page is currently showing; pressing it drops back to the mixed
 * library view, so it is an exit rather than a choice.
 *
 * The shell takes it in [AuraChip] style: the Material chip's rounded square and its leading cross sit
 * wrong over the Aura page, and the label already says what pressing it leaves.
 */
@Composable
fun FilterExitChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalAuraTvShell.current) {
        AuraChip(
            label = label,
            selected = true,
            onClick = onClick,
            modifier = modifier,
        )
        return
    }

    FilterChip(
        modifier = modifier,
        label = { Text(label) },
        selected = true,
        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(painter = painterResource(R.drawable.close), contentDescription = stringResource(R.string.close_chip))
        },
    )
}

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        Spacer(Modifier.width(12.dp))

        // Material pages have no Aura hero, so their chip row is the thing DPAD_RIGHT and a
        // back-restore should land on.
        val pageAnchor = if (LocalAuraTvShell.current) auraPageFocusAnchor() else null

        chips.forEachIndexed { index, (value, label) ->
            val selected = currentValue == value
            if (LocalAuraTvShell.current) {
                AuraChip(
                    label = label,
                    selected = selected,
                    onClick = { onValueUpdate(value) },
                    focusRequester = if (index == 0) pageAnchor else null,
                )
            } else {
                FilterChip(
                    label = { Text(label) },
                    selected = selected,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = if (selected) {
                            AuraPrimaryContainer.copy(alpha = 0.40f)
                        } else {
                            containerColor
                        },
                        labelColor = AuraOnSurfaceVariant,
                        selectedContainerColor = AuraPrimaryContainer.copy(alpha = 0.40f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                    onClick = { onValueUpdate(value) },
                    shape = CircleShape,
                    border = BorderStroke(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else AuraContainerHigh,
                    ),
                )
            }

            Spacer(Modifier.width(if (LocalAuraTvShell.current) 20.apx() else 8.dp))
        }
    }
}
