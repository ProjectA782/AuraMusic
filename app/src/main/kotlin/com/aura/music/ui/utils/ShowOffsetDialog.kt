package com.aura.music.ui.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.LocalDatabase
import com.aura.music.R
import com.aura.music.db.entities.SongEntity
import com.aura.music.ui.aura.AuraDialogTextField
import com.aura.music.ui.aura.AuraGhostButton
import com.aura.music.ui.aura.apx
import com.aura.music.ui.component.AuraSlider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay

@OptIn(FlowPreview::class)
@Composable
fun ShowOffsetDialog(songProvider: () -> SongEntity?) {
    val database = LocalDatabase.current
    val isTv = LocalAuraTvShell.current
    val song = songProvider()
    var lyricsOffset by rememberSaveable { mutableIntStateOf(song?.lyricsOffset ?: 0) }
    var textFieldValue by rememberSaveable { mutableStateOf(lyricsOffset.toString()) }

    LaunchedEffect(song?.id) {
        song?.let {
            lyricsOffset = it.lyricsOffset
            textFieldValue = lyricsOffset.toString()
        }
    }

    LaunchedEffect(lyricsOffset) {
        songProvider()?.let { song ->
            database.query {
                upsert(
                    song.copy(
                        lyricsOffset = lyricsOffset
                    )
                )
            }
        }
    }

    fun applyInput(newText: String) {
        val sanitized = newText.filter {
            it.isDigit() || (it == '-' && newText.indexOf('-') == 0)
        }

        val limited = if (sanitized.startsWith('-')) {
            sanitized.take(6)
        } else {
            sanitized.take(5)
        }

        textFieldValue = limited

        when {
            limited.isEmpty() -> {
                lyricsOffset = 0
                textFieldValue = "0"
            }

            limited == "-" -> {
            }

            else -> {
                limited.toIntOrNull()?.let { parsedValue ->
                    val clampedValue = parsedValue.coerceIn(-9999, 9999)
                    lyricsOffset = clampedValue

                    if (parsedValue != clampedValue) {
                        textFieldValue = clampedValue.toString()
                    }

                    if (clampedValue == 0 && limited.startsWith('-')) {
                        textFieldValue = "0"
                    }
                }
            }
        }
    }

    fun step(delta: Int) {
        lyricsOffset = (lyricsOffset + delta).coerceIn(-3000, 3000)
        textFieldValue = lyricsOffset.toString()
    }

    fun reset() {
        lyricsOffset = 0
        textFieldValue = "0"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.fast_forward),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.lyrics_offset),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isTv) {
            // The steps sit above the slider, where UP reaches them and LEFT/RIGHT walks the row.
            // The exact-value field sits below, where DOWN lands: a focused text field swallows
            // LEFT/RIGHT for the cursor, so it must never share a row with the step buttons or the
            // remote would strand whoever opens it. Opening on the slider is the point — an offset
            // is set by nudging, not by hunting.
            val sliderFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(250)
                runCatching { sliderFocus.requestFocus() }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraGhostButton(
                    icon = R.drawable.remove,
                    contentDescription = "Decrease",
                    onClick = { step(-50) }
                )

                Spacer(modifier = Modifier.width(16.dp))

                AuraGhostButton(
                    icon = R.drawable.add,
                    contentDescription = "Increase",
                    onClick = { step(50) }
                )

                if (lyricsOffset != 0) {
                    Spacer(modifier = Modifier.width(16.dp))

                    AuraGhostButton(
                        icon = R.drawable.replay,
                        contentDescription = "Reset",
                        onClick = ::reset
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            AuraSlider(
                value = lyricsOffset.toFloat(),
                onValueChange = { newValue ->
                    val rounded = (newValue / 100).toInt() * 100
                    lyricsOffset = rounded
                    textFieldValue = rounded.toString()
                },
                valueRange = -3000f..3000f,
                steps = 59,
                modifier = Modifier.focusRequester(sliderFocus)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OffsetRangeLabels()

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                AuraDialogTextField(
                    value = TextFieldValue(textFieldValue),
                    onValueChange = { applyInput(it.text) },
                    modifier = Modifier.widthIn(min = 160.apx(), max = 260.apx()),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "ms",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = textFieldValue,
                    onValueChange = { applyInput(it) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.widthIn(min = 120.dp, max = 160.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = MaterialTheme.colorScheme.error
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "ms",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                if (lyricsOffset != 0) {
                    Spacer(Modifier.width(8.dp))

                    IconButton(onClick = ::reset) {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = "Reset"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { step(-50) }) {
                    Icon(
                        painter = painterResource(R.drawable.remove),
                        contentDescription = "Decrease"
                    )
                }

                AuraSlider(
                    value = lyricsOffset.toFloat(),
                    onValueChange = { newValue ->
                        val rounded = (newValue / 100).toInt() * 100
                        lyricsOffset = rounded
                        textFieldValue = rounded.toString()
                    },
                    valueRange = -3000f..3000f,
                    steps = 59,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { step(50) }) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = "Increase"
                    )
                }
            }
        }

        if (!isTv) {
            Spacer(modifier = Modifier.height(8.dp))

            OffsetRangeLabels()
        }
    }
}

/** Where the slider's two ends sit, so the range it covers is never a guess. */
@Composable
private fun OffsetRangeLabels() {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp)
    ) {
        Text(
            text = "-3000ms",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "+3000ms",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
