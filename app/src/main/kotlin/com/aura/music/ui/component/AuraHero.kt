/**
 * Aura Music — the phone home hero: a plain-text banner over the focused shelf, not a card.
 */

package com.aura.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.music.R
import com.aura.music.ui.theme.AuraContainer
import com.aura.music.ui.theme.AuraMetrics
import com.aura.music.ui.theme.AuraOnPrimary
import com.aura.music.ui.theme.AuraPrimaryContainer
import com.aura.music.ui.theme.tvFocusRing

/**
 * FEATURED / type badges, artist, oversized title, two lines of description and the two pills.
 * Content comes from [LocalAuraSpotlight], which every focusable tile updates.
 */
@Composable
fun AuraHero(modifier: Modifier = Modifier) {
    val spotlight = LocalAuraSpotlight.current
    val playInteractionSource = remember { MutableInteractionSource() }
    val detailsInteractionSource = remember { MutableInteractionSource() }
    val onPlay = spotlight.onPlay
    val onDetails = spotlight.onDetails

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(AuraMetrics.HeroMinHeight)
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AuraHeroBadge(text = stringResource(R.string.featured), filled = true)
            if (spotlight.typeLabel.isNotBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                AuraHeroBadge(text = spotlight.typeLabel, filled = false)
            }
            if (spotlight.subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = spotlight.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = spotlight.title,
            style = MaterialTheme.typography.displayLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (spotlight.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = spotlight.description,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 620.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(AuraMetrics.HeroButtonGap)) {
            if (onPlay != null) {
                AuraHeroAction(
                    text = stringResource(R.string.play_now),
                    iconRes = R.drawable.play,
                    filled = true,
                    interactionSource = playInteractionSource,
                    onClick = onPlay,
                )
            }
            if (onDetails != null) {
                AuraHeroAction(
                    text = stringResource(R.string.details),
                    iconRes = R.drawable.info,
                    filled = false,
                    interactionSource = detailsInteractionSource,
                    onClick = onDetails,
                )
            }
        }
    }
}

@Composable
private fun AuraHeroAction(
    text: String,
    iconRes: Int,
    filled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else AuraContainer)
            .tvFocusRing(cornerRadius = 28.dp)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(
                horizontal = AuraMetrics.HeroButtonPaddingHorizontal,
                vertical = AuraMetrics.HeroButtonPaddingVertical,
            ),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (filled) AuraOnPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(AuraMetrics.HeroIconSize),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
            color = if (filled) AuraOnPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * FEATURED is a solid primary pill; the type badge next to it uses the deep purple container.
 * Both are uppercase with wide tracking, exactly as the mock renders them.
 */
@Composable
private fun AuraHeroBadge(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.primary else AuraPrimaryContainer)
            .padding(
                horizontal = AuraMetrics.HeroBadgePaddingHorizontal,
                vertical = AuraMetrics.HeroBadgePaddingVertical,
            ),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) AuraOnPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
