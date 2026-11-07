/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.aura.music.R
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraGhostButton
import com.aura.music.ui.aura.AuraListRow
import com.aura.music.ui.aura.apx
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class DraggableLyricsProviderItem(
    val id: String,
    val name: String,
    val icon: Painter,
)

@Composable
fun DraggableLyricsProviderList(
    items: MutableList<DraggableLyricsProviderItem>,
    onItemsReordered: (List<DraggableLyricsProviderItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()
    var hasDragged by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
    ) { from, to ->
        val movedItem = items.removeAt(from.index)
        items.add(to.index, movedItem)
        hasDragged = true
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasDragged) {
            onItemsReordered(items.toList())
            hasDragged = false
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
    ) {
        itemsIndexed(
            items,
            key = { _, item -> item.id }
        ) { index, item ->
            ReorderableItem(
                state = reorderableState,
                key = item.id,
            ) {
                if (LocalAuraTvShell.current) {
                    // A drag handle needs a finger. On the remote the same reorder is two buttons,
                    // so the setting is not lost with the touch-only gesture.
                    AuraListRow(
                        title = item.name,
                        label = (index + 1).toString(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.apx()),
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.apx())) {
                                AuraGhostButton(
                                    icon = R.drawable.arrow_upward,
                                    enabled = index > 0,
                                    boxPx = 56,
                                    glyphPx = 24,
                                    onClick = {
                                        if (index > 0) {
                                            items.add(index - 1, items.removeAt(index))
                                            onItemsReordered(items.toList())
                                        }
                                    },
                                )
                                AuraGhostButton(
                                    icon = R.drawable.arrow_downward,
                                    enabled = index < items.lastIndex,
                                    boxPx = 56,
                                    glyphPx = 24,
                                    onClick = {
                                        if (index < items.lastIndex) {
                                            items.add(index + 1, items.removeAt(index))
                                            onItemsReordered(items.toList())
                                        }
                                    },
                                )
                            }
                        },
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { },
                                modifier = Modifier.draggableHandle(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.drag_handle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
