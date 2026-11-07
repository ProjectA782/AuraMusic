/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.aura.music.R
import com.aura.music.ui.theme.AuraContainer
import com.aura.music.ui.theme.AuraContainerHigh
import com.aura.music.ui.theme.AuraMetrics
import com.aura.music.ui.theme.AuraOnSurfaceVariant
import com.aura.music.ui.theme.AuraSettingsShape
import com.aura.music.ui.theme.AuraTextPrimary
import com.aura.music.ui.theme.tvFocusRing

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(16.dp),
    )
}

@Composable
@Deprecated("Use Material3SettingsGroup instead :)")
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        modifier
            .fillMaxWidth()
            .tvFocusRing(cornerRadius = AuraMetrics.SettingsCornerRadius)
            .clip(AuraSettingsShape)
            .background(AuraContainer, AuraSettingsShape)
            .border(1.dp, AuraContainerHigh, AuraSettingsShape)
            .clickable(
                enabled = isEnabled && onClick != null,
                onClick = onClick ?: {},
            ).alpha(if (isEnabled) 1f else 0.5f)
            .padding(horizontal = AuraMetrics.SettingsPadding, vertical = AuraMetrics.SettingsPadding),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                icon()
            }

            Spacer(Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f),
        ) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = AuraTextPrimary)) {
                title()
            }

            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.titleSmall,
                    color = AuraOnSurfaceVariant,
                )
            }

            content?.invoke()
        }

        if (trailingContent != null) {
            Spacer(Modifier.width(12.dp))

            trailingContent()
        }
    }
}

@Composable
@Deprecated("Use the Switch component instead")
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            AuraSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = isEnabled,
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            id = if (checked) R.drawable.check else R.drawable.close
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            )
        },
        onClick = { onCheckedChange(!checked) },
        isEnabled = isEnabled
    )
}
