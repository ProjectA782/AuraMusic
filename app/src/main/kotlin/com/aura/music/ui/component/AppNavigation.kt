/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aura.music.R
import com.aura.music.ui.screens.Screens
import com.aura.music.ui.theme.AuraBackground
import com.aura.music.ui.theme.AuraContainerHigh
import com.aura.music.ui.theme.AuraMetrics
import com.aura.music.ui.theme.AuraOnPrimary
import com.aura.music.ui.theme.AuraOnSurfaceVariant
import com.aura.music.ui.theme.AuraPrimaryContainer
import com.aura.music.ui.theme.Edge
import com.aura.music.ui.theme.auraEdge
import com.aura.music.ui.theme.tvFocusRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    if (navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")) return true

    // Fix: match the route template, not the resolved route
    if (screenRoute == "search_input" &&
        (currentRoute.startsWith("search/") || currentRoute == "search/{query}")) return true

    return false
}

/** `bg-[#4F378B]/40` behind the active row, `#D0BCFF` glyph and label; inactive rows are `#CAC4D0`. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailRow(
    iconRes: Int,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) AuraPrimaryContainer.copy(alpha = 0.40f) else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.primary else AuraOnSurfaceVariant

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(container)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    horizontal = AuraMetrics.RailRowPaddingHorizontal,
                    vertical = AuraMetrics.RailRowPaddingVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = content,
            modifier = Modifier.size(AuraMetrics.RailIconSize),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = AuraMetrics.RailLabelGap),
            )
        }
    }
}

@Composable
private fun RailLabel(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
        modifier = modifier,
        content = content,
    )
}

/**
 * The Aura rail: brand block, primary destinations, then the surfaces that used to live in the TV
 * header (stats, history, settings) and the account avatar, all in one focusable column.
 *
 * It expands while it holds focus so the D-pad user can read what each glyph is, and collapses as
 * soon as focus moves into the content, which is where the hero banner needs the width.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
    onSecondaryClick: ((String) -> Unit)? = null,
    accountName: String? = null,
    accountImageUrl: String? = null,
    onAccountClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val initialFocusRequester = remember { FocusRequester() }
    val isInTouchMode = LocalView.current.isInTouchMode
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus

    val secondaryEntries =
        remember {
            listOf(
                R.string.stats to R.drawable.stats,
                R.string.history to R.drawable.history,
                R.string.settings to R.drawable.settings,
            )
        }

    // Nothing holds focus when the app opens on a TV, so the first D-pad press has no
    // visible origin. Anchor it to the rail.
    LaunchedEffect(isInTouchMode) {
        if (!isInTouchMode) {
            delay(250)
            runCatching { initialFocusRequester.requestFocus() }
        }
    }

    val railWidth by animateDpAsState(
        targetValue = if (expanded) AuraMetrics.RailExpandedWidth else AuraMetrics.RailCollapsedWidth,
        animationSpec = tween(220),
        label = "auraRailWidth",
    )

    Column(
        modifier =
            modifier
                .width(railWidth)
                .fillMaxHeight()
                .onFocusEvent { railHasFocus = it.hasFocus }
                .background(
                    if (pureBlack) Color.Black else AuraBackground.copy(alpha = 0.90f),
                )
                .auraEdge(Edge.Right, AuraContainerHigh),
    ) {
        // Brand block: primary circle with the note glyph, then "Aura" extrabold + "Music" light.
        Row(
            modifier = Modifier.padding(horizontal = AuraMetrics.RailPadding, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(AuraMetrics.RailBrandIconSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = AuraOnPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            RailLabel(visible = expanded) {
                Row(modifier = Modifier.padding(start = AuraMetrics.RailLabelGap)) {
                    Text(
                        text = "Aura",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AuraMetrics.RailItemSpacing, Alignment.CenterVertically),
        ) {
            navigationItems.forEachIndexed { index, screen ->
                val isSelected = remember(currentRoute, screen.route) {
                    isRouteSelected(currentRoute, screen.route, navigationItems)
                }
                val currentIsSelected by rememberUpdatedState(isSelected)
                val iconRes = remember(isSelected, screen) {
                    if (isSelected) screen.iconIdActive else screen.iconIdInactive
                }

                val isSearchItem = screen == Screens.Search && onSearchLongClick != null
                val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
                val interactionSource = remember { MutableInteractionSource() }

                // Long press detection using InteractionSource
                if (isSearchItem || isHomeHoldItem) {
                    LaunchedEffect(interactionSource) {
                        var isLongClick = false
                        interactionSource.interactions.collectLatest { interaction ->
                            when (interaction) {
                                is PressInteraction.Press -> {
                                    isLongClick = false
                                    delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                    isLongClick = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                                }
                                is PressInteraction.Release -> {
                                    if (!isLongClick) {
                                        onItemClick(screen, currentIsSelected)
                                    }
                                }
                                is PressInteraction.Cancel -> {
                                    isLongClick = false
                                }
                            }
                        }
                    }
                }

                RailRow(
                    iconRes = iconRes,
                    label = stringResource(screen.titleId),
                    selected = isSelected,
                    expanded = expanded,
                    onClick = {
                        if (!isSearchItem && !isHomeHoldItem) {
                            onItemClick(screen, currentIsSelected)
                        }
                        // Long presses are handled via InteractionSource
                    },
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .padding(horizontal = AuraMetrics.RailPadding)
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(initialFocusRequester)
                                } else {
                                    Modifier
                                },
                            )
                            .tvFocusRing(cornerRadius = 28.dp),
                )
            }

            if (onSecondaryClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                secondaryEntries.forEach { entry ->
                    RailRow(
                        iconRes = entry.second,
                        label = stringResource(entry.first),
                        selected = false,
                        expanded = expanded,
                        onClick = { onSecondaryClick(routeForSecondary(entry.first)) },
                        interactionSource = remember(entry.first) { MutableInteractionSource() },
                        modifier =
                            Modifier
                                .padding(horizontal = AuraMetrics.RailPadding)
                                .tvFocusRing(cornerRadius = 28.dp),
                    )
                }
            }
        }

        // Account block
        val accountInteractionSource = remember { MutableInteractionSource() }
        val accountClickable =
            if (onAccountClick != null) {
                Modifier.combinedClickable(
                    interactionSource = accountInteractionSource,
                    indication = null,
                    onClick = onAccountClick,
                )
            } else {
                Modifier
            }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AuraMetrics.RailPadding, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(AuraMetrics.HeaderAccountSize)
                        .clip(CircleShape)
                        .then(accountClickable)
                        .tvFocusRing(cornerRadius = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (accountImageUrl != null) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = stringResource(R.string.account),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(AuraMetrics.HeaderAccountSize).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(AuraMetrics.HeaderAccountSize)
                                .clip(CircleShape)
                                .background(AuraPrimaryContainer.copy(alpha = 0.40f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.account),
                            contentDescription = stringResource(R.string.account),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AuraMetrics.RailIconSize),
                        )
                    }
                }
            }
            RailLabel(visible = expanded) {
                Column(modifier = Modifier.padding(start = AuraMetrics.RailLabelGap)) {
                    Text(
                        text = accountName?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.account),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun routeForSecondary(labelRes: Int): String =
    when (labelRes) {
        R.string.stats -> "stats"
        R.string.history -> "history"
        else -> "settings"
    }

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    onHomeLongHold: (() -> Unit)? = null,
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val initialFocusRequester = remember { FocusRequester() }
    val isInTouchMode = LocalView.current.isInTouchMode

    LaunchedEffect(isInTouchMode) {
        if (!isInTouchMode) {
            delay(250)
            runCatching { initialFocusRequester.requestFocus() }
        }
    }

    NavigationBar(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        navigationItems.forEachIndexed { index, screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val currentIsSelected by rememberUpdatedState(isSelected)
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }

            val isSearchItem = screen == Screens.Search && onSearchLongClick != null
            val isHomeHoldItem = screen == Screens.Home && onHomeLongHold != null
            val interactionSource = remember { MutableInteractionSource() }

            // Long press detection using InteractionSource
            if (isSearchItem || isHomeHoldItem) {
                LaunchedEffect(interactionSource) {
                    var isLongClick = false
                    interactionSource.interactions.collectLatest { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> {
                                isLongClick = false
                                delay(if (isHomeHoldItem) 15_000L else viewConfiguration.longPressTimeoutMillis)
                                isLongClick = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isHomeHoldItem) onHomeLongHold.invoke() else onSearchLongClick?.invoke()
                            }
                            is PressInteraction.Release -> {
                                if (!isLongClick) {
                                    onItemClick(screen, currentIsSelected)
                                }
                            }
                            is PressInteraction.Cancel -> {
                                isLongClick = false
                            }
                        }
                    }
                }
            }

            NavigationBarItem(
                modifier = if (index == 0) {
                    Modifier
                        .focusRequester(initialFocusRequester)
                        .tvFocusRing()
                } else {
                    Modifier.tvFocusRing()
                },
                selected = isSelected,
                onClick = {
                    if (!isSearchItem && !isHomeHoldItem) {
                        onItemClick(screen, currentIsSelected)
                    }
                    // Long presses are handled via InteractionSource
                },
                interactionSource = interactionSource,
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                },
                label = if (!slimNav) {
                    {
                        Text(
                            text = stringResource(screen.titleId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else null
            )
        }
    }
}
