/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraType

@Composable
fun EmptyPlaceholder(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
) {
    val tv = LocalAuraTvShell.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier =
        modifier
            .fillMaxSize()
            .padding(12.dp),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (tv) AuraMock.Muted.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onBackground,
            ),
            modifier = Modifier.size(if (tv) 120.apx() else 64.dp),
        )

        Spacer(Modifier.height(if (tv) 28.apx() else 12.dp))

        Text(
            text = text,
            style = if (tv) auraType(FontWeight.Medium, 26) else MaterialTheme.typography.bodyLarge,
            color = if (tv) AuraMock.Muted else Color.Unspecified,
        )
    }
}
