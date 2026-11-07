/**
 * Aura Music — the settings slider.
 *
 * Material's slider only answers to touch, so on a TV its value can never be changed. This wrapper
 * keeps the reference's look — a thin rounded track with the accent filling it and a disc thumb —
 * and takes DPAD_LEFT / DPAD_RIGHT as well as tap and drag. Phone builds delegate to Material.
 */

package com.aura.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import kotlin.math.roundToInt

@Composable
fun AuraSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    if (!LocalAuraTvShell.current) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        return
    }

    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span == 0f) 0f else ((value - valueRange.start) / span).coerceIn(0f, 1f)
    // One key press is a twentieth of the range unless the slider declares discrete stops.
    val nudge = if (steps > 0) 1f / (steps + 1) else 1f / 20f

    fun commit(nextFraction: Float) {
        val raw = valueRange.start + nextFraction.coerceIn(0f, 1f) * span
        val snapped = if (steps > 0) {
            val quantum = span / (steps + 1)
            valueRange.start + ((raw - valueRange.start) / quantum).roundToInt() * quantum
        } else {
            raw
        }
        onValueChange(snapped.coerceIn(valueRange))
        onValueChangeFinished?.invoke()
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(72.apx())
            .auraFocused(corner = 24.apx(), fill = false, grow = false)
            .focusable(enabled)
            .onPreviewKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> commit(fraction - nudge)
                    Key.DirectionRight -> commit(fraction + nudge)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .pointerInput(enabled, valueRange, steps) {
                detectTapGestures { offset ->
                    if (enabled) commit(offset.x / size.width)
                }
            }
            .pointerInput(enabled, valueRange, steps) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> if (enabled) commit(offset.x / size.width) },
                    onHorizontalDrag = { change, _ ->
                        if (enabled) commit(change.position.x / size.width)
                    },
                )
            }
            .padding(horizontal = 20.apx()),
    ) {
        val thumb = AuraPx.ProgressThumb.apx()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AuraPx.ProgressTrack.apx())
                .clip(CircleShape)
                .background(AuraMock.Border.copy(alpha = 0.45f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(AuraPx.ProgressTrack.apx())
                    .clip(CircleShape)
                    .background(AuraMock.Primary),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (maxWidth * fraction - thumb / 2).coerceAtLeast(0.dp))
                .size(thumb)
                .clip(CircleShape)
                .background(AuraMock.Primary),
        )
    }
}
