/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraDialogOption

@Composable
fun <T> EnumDialog(
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
    title: String,
    current: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
) {
    val tvShell = LocalAuraTvShell.current
    ListDialog(
        onDismiss = onDismiss,
    ) {
        items(values) { value ->
            if (tvShell) {
                AuraDialogOption(
                    label = valueText(value),
                    description = valueDescription?.invoke(value),
                    selected = value == current,
                    showRadio = true,
                    onClick = { onSelect(value) },
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(value)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(
                        selected = value == current,
                        onClick = null,
                    )

                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                    ) {
                        Text(
                            text = valueText(value),
                        )
                        if (valueDescription != null) {
                            Text(
                                text = valueDescription(value),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
