/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import com.aura.music.ui.component.AuraSwitch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.aura.music.ui.component.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.constants.SPONSORBLOCK_ALL_CATEGORIES
import com.aura.music.constants.SPONSORBLOCK_DEFAULT_CATEGORIES
import com.aura.music.constants.SponsorBlockCategoriesKey
import com.aura.music.constants.SponsorBlockEnabledKey
import com.aura.music.constants.SponsorBlockShowToastKey
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.Material3SettingsGroup
import com.aura.music.ui.component.Material3SettingsItem
import com.aura.music.ui.component.auraSettingsGutter
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SponsorBlockSettings(navController: NavController) {
    val (enabled, onEnabledChange) = rememberPreference(SponsorBlockEnabledKey, defaultValue = false)
    val (showToast, onShowToastChange) = rememberPreference(SponsorBlockShowToastKey, defaultValue = false)
    val (categoriesRaw, onCategoriesChange) = rememberPreference(
        SponsorBlockCategoriesKey,
        defaultValue = SPONSORBLOCK_DEFAULT_CATEGORIES,
    )

    val selected = remember(categoriesRaw) {
        categoriesRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun toggleCategory(category: String) {
        val updated = if (category in selected) selected - category else selected + category
        onCategoriesChange(updated.joinToString(","))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        // The shell gives this destination an exact page box, so a bar emitted after the list
        // becomes a sibling of it and paints over the rows. It is the page heading: first child,
        // pinned, with the list scrolling under it.
        TopAppBar(
            title = { Text(stringResource(R.string.sponsorblock)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // The old top inset plus the hand-compensated padding(top = 64.dp) pushed the
                // rows down to clear the bar that used to paint over them; the pinned header
                // above now does that job, so only the horizontal padding is left.
                .auraSettingsGutter()
        ) {
        Material3SettingsGroup(
            title = stringResource(R.string.sponsorblock),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.fast_forward),
                    title = { Text(stringResource(R.string.sponsorblock_enable)) },
                    description = { Text(stringResource(R.string.sponsorblock_enable_desc)) },
                    trailingContent = {
                        AuraSwitch(
                            checked = enabled,
                            onCheckedChange = onEnabledChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (enabled) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onEnabledChange(!enabled) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.notification),
                    title = { Text(stringResource(R.string.sponsorblock_show_toast)) },
                    description = { Text(stringResource(R.string.sponsorblock_show_toast_desc)) },
                    enabled = enabled,
                    trailingContent = {
                        AuraSwitch(
                            checked = showToast,
                            enabled = enabled,
                            onCheckedChange = onShowToastChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (showToast) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { if (enabled) onShowToastChange(!showToast) }
                ),
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.sponsorblock_categories),
            items = SPONSORBLOCK_ALL_CATEGORIES.map { category ->
                val isChecked = category in selected
                Material3SettingsItem(
                    title = { Text(stringResource(categoryLabel(category))) },
                    enabled = enabled,
                    trailingContent = {
                        Checkbox(
                            checked = isChecked,
                            enabled = enabled,
                            onCheckedChange = { toggleCategory(category) }
                        )
                    },
                    onClick = { if (enabled) toggleCategory(category) }
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.sponsorblock_attribution),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun categoryLabel(category: String): Int = when (category) {
    "sponsor" -> R.string.sponsorblock_category_sponsor
    "selfpromo" -> R.string.sponsorblock_category_selfpromo
    "interaction" -> R.string.sponsorblock_category_interaction
    "intro" -> R.string.sponsorblock_category_intro
    "outro" -> R.string.sponsorblock_category_outro
    "preview" -> R.string.sponsorblock_category_preview
    "music_offtopic" -> R.string.sponsorblock_category_music_offtopic
    "filler" -> R.string.sponsorblock_category_filler
    else -> R.string.sponsorblock_category_sponsor
}
