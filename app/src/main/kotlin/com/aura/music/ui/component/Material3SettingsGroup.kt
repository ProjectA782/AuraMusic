/**
 * Aura Music — the settings row.
 *
 * The reference never draws a row as a bordered card. Its only row is the rail item: a flat,
 * full-round strip that carries a 32px glyph, a gap of 16px, and text in the muted ramp, and that
 * lights up with the shell's focus ring. Every settings screen on the TV shell composes itself out
 * of this component, so the treatment lives here once. Phones keep Material's grouped cards.
 */

package com.aura.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.R
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.FullRound
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.asp
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.aura.auraPagePadding
import com.aura.music.ui.theme.AuraContainer
import com.aura.music.ui.theme.AuraContainerHigh
import com.aura.music.ui.theme.AuraMetrics
import com.aura.music.ui.theme.AuraOnSurfaceVariant
import com.aura.music.ui.theme.AuraPrimaryContainer
import com.aura.music.ui.theme.AuraSettingsShape
import com.aura.music.ui.theme.AuraTextPrimary
import com.aura.music.ui.theme.tvFocusRing

/**
 * The gutter a settings page sits in. The reference gives every view 48px of page padding; phones
 * keep Material's 16dp.
 */
@Composable
fun Modifier.auraSettingsGutter(): Modifier =
    if (LocalAuraTvShell.current) {
        padding(horizontal = auraPagePadding())
    } else {
        padding(horizontal = 16.dp)
    }

/**
 * A group of settings rows under one heading.
 * @param title The heading above the rows, or null for a headless group.
 * @param items The rows themselves.
 */
@Composable
fun Material3SettingsGroup(    title: String? = null,
    items: List<Material3SettingsItem>,
    useLowContrast: Boolean = false
) {
    if (LocalAuraTvShell.current) {
        Column(modifier = Modifier.fillMaxWidth()) {
            title?.let {
                // The reference's `text-3xl font-bold` section heading.
                AuraText(
                    value = it,
                    px = 30,
                    weight = FontWeight.Bold,
                    color = AuraMock.Text,
                    modifier = Modifier.padding(top = 32.apx(), bottom = 20.apx()),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.apx()),
            ) {
                items.forEach { item -> AuraSettingsRow(item = item) }
            }
        }
    } else {
        Material3SettingsGroupCard(title = title, items = items, useLowContrast = useLowContrast)
    }
}

/** The reference's rail row: flat, full-round, glyph + label + trailing affordance. */
@Composable
private fun AuraSettingsRow(
    item: Material3SettingsItem,
) {
    var focused by remember { mutableStateOf(false) }
    val enabled = item.enabled
    val density = LocalDensity.current
    /*
     * A settings row is a pill, and a pill's corner is half its height — but the height is set by the
     * content, so it is only known once the row has measured. FullRound is not a shortcut here: a
     * radius larger than the box is clamped per axis, and on a row 1728 wide by 64 tall that turns
     * "rounded-full" into a flattened ellipse. The wash then falls back to the bounds (a hard
     * rectangle), the ring collapses to two specks at the midpoints, and the clip eats the trailing
     * chevron. Half the measured height is the shape the reference actually draws.
     */
    var rowHeight by remember { mutableStateOf(0.dp) }
    val rowCorner = if (rowHeight > 0.dp) rowHeight / 2f else FullRound

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeight = with(density) { it.height.toDp() } }
            .animateContentSize()
            .auraFocused(corner = rowCorner, fill = false, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(rowCorner))
            .background(
                when {
                    focused -> AuraMock.FocusFill
                    item.isHighlighted -> AuraMock.Border.copy(alpha = 0.40f)
                    else -> Color.Transparent
                },
            )
            .clickable(
                enabled = enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() },
            )
            .padding(horizontal = 20.apx(), vertical = 16.apx()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            item.leadingContent != null -> {
                item.leadingContent.invoke()
                Spacer(Modifier.width(16.apx()))
            }
            item.icon != null -> {
                Box(modifier = Modifier.size(32.apx()), contentAlignment = Alignment.Center) {
                    if (item.showBadge) {
                        BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.error) }) {
                            Icon(
                                painter = item.icon,
                                contentDescription = null,
                                tint = auraRowGlyphColor(enabled, focused),
                                modifier = Modifier.size(32.apx()),
                            )
                        }
                    } else {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = auraRowGlyphColor(enabled, focused),
                            modifier = Modifier.size(32.apx()),
                        )
                    }
                }
                Spacer(Modifier.width(16.apx()))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(
                auraRowTitleStyle(enabled, focused),
                content = item.title,
            )
            item.description?.let { desc ->
                Spacer(Modifier.height(2.apx()))
                ProvideTextStyle(
                    TextStyle(
                        fontSize = 18.asp(),
                        color = if (enabled) AuraMock.Muted else AuraMock.Muted.copy(alpha = 0.38f),
                    ),
                    content = desc,
                )
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(Modifier.width(12.apx()))
            trailing()
        }

        if (item.trailingContent == null && item.onClick != null) {
            Spacer(Modifier.width(12.apx()))
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = auraRowGlyphColor(enabled, focused),
                modifier = Modifier.size(28.apx()),
            )
        }
    }
}

@Composable
private fun auraRowGlyphColor(enabled: Boolean, focused: Boolean) = when {
    !enabled -> AuraMock.Muted.copy(alpha = 0.38f)
    focused -> AuraMock.Primary
    else -> AuraMock.Muted
}

@Composable
private fun auraRowTitleStyle(enabled: Boolean, focused: Boolean) = TextStyle(
    fontSize = 24.asp(),
    fontWeight = FontWeight.Medium,
    color = when {
        !enabled -> AuraMock.Muted.copy(alpha = 0.38f)
        focused -> AuraMock.Text
        else -> AuraMock.Muted
    },
)

/** Material 3 Expressive grouped cards — the phone layout. */
@Composable
private fun Material3SettingsGroupCard(
    title: String? = null,
    items: List<Material3SettingsItem>,
    useLowContrast: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Section title
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AuraMetrics.RailItemSpacing)
        ) {
            items.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .tvFocusRing(cornerRadius = AuraMetrics.SettingsCornerRadius),
                    shape = AuraSettingsShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (!useLowContrast) {
                            AuraContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    border = BorderStroke(1.dp, AuraContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Material3SettingsItemRow(item = item)
                }
            }
        }
    }
}

/**
 * Individual settings item row with Material 3 styling
 */
@Composable
private fun Material3SettingsItemRow(
    item: Material3SettingsItem
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.enabled && item.onClick != null,
                onClick = { item.onClick?.invoke() }
            )
            .padding(AuraMetrics.SettingsPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Custom leading content or Icon with background
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(modifier = Modifier.width(16.dp))
        } else if (item.icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (item.isHighlighted) AuraPrimaryContainer
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        }
                    ) {
                        Icon(
                            painter = item.icon,
                            contentDescription = null,
                            tint = if (!item.enabled)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else if (item.isHighlighted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Icon(
                        painter = item.icon,
                        contentDescription = null,
                        tint = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else if (item.isHighlighted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        // Title and description
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Title content
            ProvideTextStyle(
                MaterialTheme.typography.titleMedium.copy(
                    color = if (!item.enabled)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else
                        AuraTextPrimary
                )
            ) {
                item.title()
            }

            // Description if provided
            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = if (!item.enabled)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else
                            AuraOnSurfaceVariant
                    )
                ) {
                    desc()
                }
            }
        }

        // Trailing content
        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }

        if (item.trailingContent == null && item.onClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Data class for Material 3 settings item
 */
data class Material3SettingsItem(
    val icon: Painter? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null
)
