/**
 * Aura Music — the reference `#sidebar`.
 *
 * 96px collapsed, 288px while it holds focus, every size in mock px through [apx] and every colour
 * from [AuraMock]. Drop-in for `AppNavigationRail`: same parameters, same navigation contract.
 */

package com.aura.music.ui.aura


import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.aura.music.LocalAuraRailWidth
import com.aura.music.R
import com.aura.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val RailEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val StatsRoute = "stats"
private const val HistoryRoute = "history"
private const val ProfileRoute = "account"
private const val SettingsRoute = "settings"
private const val HomeLongHoldMillis = 15_000L

/** How long the shell keeps offering focus to the rail before it accepts that something else won. */
private const val FocusAnchorChecks = 30
private const val FocusAnchorIntervalMs = 200L

/** How long the rail waits before claiming a selector nobody holds, so a hand-off can finish. */
private const val FocusAnchorSettleMs = 400L

private data class AuraRailItem(
    val route: String,
    val iconRes: Int,
    val labelRes: Int,
    val label: String,
    val group: Int,
    val topLevel: Boolean = false,
    val screen: Screens? = null,
)

private val AuraRailItems = listOf(
    AuraRailItem(Screens.Home.route, R.drawable.aura_ic_home, R.string.home, "Home", group = 0, topLevel = true),
    AuraRailItem(Screens.Search.route, R.drawable.aura_ic_search, R.string.search, "Search", group = 0, topLevel = true),
    AuraRailItem(Screens.Library.route, R.drawable.aura_ic_library, R.string.filter_library, "Library", group = 0, topLevel = true),
    AuraRailItem(StatsRoute, R.drawable.aura_ic_activity, R.string.stats, "Stats", group = 1),
    // The reference's rail has no History slot and no app bar to hide one in, but the play-history
    // screen still has to stay reachable, so it joins the analytics group in the rail's own style.
    AuraRailItem(HistoryRoute, R.drawable.aura_ic_history, R.string.history, "History", group = 1),
    AuraRailItem(ProfileRoute, R.drawable.aura_ic_user, R.string.account, "Profile", group = 2),
    AuraRailItem(SettingsRoute, R.drawable.aura_ic_settings, R.string.settings, "Settings", group = 2),
)

@Composable
fun AuraRail(
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
    showUpdateBadge: Boolean = false,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val pageFocus = LocalAuraPageFocus.current
    val railAnchor = LocalAuraRailAnchor.current
    val view = LocalView.current
    val initialFocusRequester = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus

    val items = remember(navigationItems) {
        AuraRailItems.mapNotNull { item ->
            if (!item.topLevel) {
                item
            } else {
                // A tab can be demoted by a preference, so it may be absent from the navigation list;
                // the reference's rail has a fixed set, so keep the row and fall back to navigating
                // it by route.
                navigationItems.firstOrNull { it.route == item.route }?.let { item.copy(screen = it) } ?: item
            }
        }
    }

    // Whether the page under the rail is one of the rail's own destinations.
    val anyRowSelected =
        items.any { isRouteSelected(currentRoute, it.route, navigationItems) }

    // Directional search starts from whichever node already holds focus, so a composition with no
    // visible selector answers the remote with nothing at all: every press is dropped and the app
    // looks dead. The rail is the shell's focus home and takes the selector back whenever the window
    // can take keys and none of the three regions the user can see — rail, page, transport banner —
    // has it. That catches a node outside those three quietly eating it, which is what a cold start on
    // this TV does a few seconds after the feed lands. The settle delay lets an in-flight hand-off
    // between the real regions finish first, and any change re-keys this effect and cancels it.
    val pageHolds = LocalAuraPageHoldsFocus.current
    val transportHolds = LocalAuraTransportHoldsFocus.current
    val playerHolds = LocalAuraPlayerHoldsFocus.current
    var windowHasFocus by remember { mutableStateOf(view.hasWindowFocus()) }
    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { windowHasFocus = it }
        observer.addOnWindowFocusChangeListener(listener)
        onDispose { observer.removeOnWindowFocusChangeListener(listener) }
    }
    val selectorElsewhere =
        (pageHolds?.value ?: false) ||
            (transportHolds?.value ?: false) ||
            (playerHolds?.value ?: false)
    val needsAnchor = windowHasFocus && !railHasFocus && !selectorElsewhere
    LaunchedEffect(needsAnchor) {
        if (!needsAnchor) return@LaunchedEffect
        delay(FocusAnchorSettleMs)
        var tries = 0
        while (tries < FocusAnchorChecks && !initialFocusRequester.requestFocusSafely()) {
            delay(FocusAnchorIntervalMs)
            tries++
        }
    }

    // The cold-start anchor takes rail focus while the reveal is still painting, so the collapse ->
    // expand slide runs underneath an opaque splash. It is not free: every animated width re-measures
    // the page Column that shares the shell's Row, measured at 0.7-1.9s a pass on this armv7 TV, which
    // starved the reveal to 6 frames in 9s. Snapping lands the same end state in one pass, and focus
    // still arrives at the same moment.
    val railRevealPending = !AuraSplashSeen.value
    val railWidth by animateDpAsState(
        targetValue = if (expanded) AuraPx.RailExpanded.apx() else AuraPx.RailCollapsed.apx(),
        animationSpec =
            if (railRevealPending) {
                snap()
            } else {
                tween(durationMillis = 300, easing = RailEasing)
            },
        label = "auraRailWidth",
    )

    // The reference keeps the mini player inside `main`, so the bar starts at the rail's edge and
    // slides with it. Publish the live width for full-width overlays to inset themselves by.
    val reportedWidth = LocalAuraRailWidth.current
    SideEffect { reportedWidth.value = railWidth }

    val density = LocalDensity.current
    val borderPx = with(density) { 1.apx().toPx() }
    val borderColor = if (pureBlack) Color.Black else AuraMock.Border.copy(alpha = 0.30f)

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .onFocusEvent { railHasFocus = it.hasFocus }
            .shadow(
                8.apx(),
                RoundedCornerShape(0.dp),
                clip = false,
                ambientColor = AuraMock.Scrim,
                spotColor = AuraMock.Scrim,
            )
            .background(if (pureBlack) Color.Black else AuraMock.Panel)
            .drawWithContent {
                drawContent()
                drawLine(
                    color = borderColor,
                    start = Offset(size.width - borderPx, 0f),
                    end = Offset(size.width - borderPx, size.height),
                    strokeWidth = borderPx,
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.apx())
                .padding(top = 32.apx(), bottom = 32.apx())
                .padding(start = 8.apx()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(AuraPx.RailLogo.apx())
                    .clip(RoundedCornerShape(percent = 27))
                    .background(AuraMock.Primary),
            ) {
                Icon(
                    painter = painterResource(R.drawable.aura_ic_logo_note),
                    contentDescription = null,
                    tint = AuraMock.OnPrimary,
                    modifier = Modifier.size(AuraPx.RailLogoGlyph.apx()),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = auraRailEnter(railRevealPending),
                exit = auraRailExit(railRevealPending),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AuraText(
                        value = "Aura",
                        px = 24,
                        weight = FontWeight.ExtraBold,
                        color = AuraMock.Text,
                        trackingEm = 0.025f,
                        modifier = Modifier.padding(start = 16.apx()),
                    )
                    AuraText(
                        value = "Music",
                        px = 24,
                        weight = FontWeight.Light,
                        color = AuraMock.Primary,
                        trackingEm = 0.025f,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .focusGroup()
                // The page is a different scroll container, so Compose finds nothing to the right of
                // a rail row and DPAD_RIGHT dead-ends. Hand the key to whatever the page published.
                // Answer it on the press, not the release: by then Compose's own search has already
                // walked the selector into whatever shell node sits beside the rail — the top bar, the
                // snackbar host — and the page never gets the key.
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.DirectionRight || event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val sink = pageFocus
                        sink?.requestFirstFocus() ?: false
                    }
                }
                .verticalScroll(rememberScrollState())
                // The reference reserves 96px under the list for the floating bar, but this shell
                // insets the bar past the rail (`padding(start = railWidth)`), so the reservation only
                // made the list taller than the screen: every DOWN scrolled the menu — and its logo —
                // out of view for nothing. Measured overflow with it: 50px on a 1080p panel.
                .padding(start = 12.apx(), end = 12.apx(), top = 8.apx(), bottom = 26.apx()),
            // 7 rows at 84 + two 25px dividers + eight 37px gaps + 34px of padding is 968, which is
            // the column's height once the 112px brand block is gone. Everything here scales with the
            // viewport (`apx` is screenHeight/1080), so that total holds on any panel: resize one of
            // these numbers and the list either scrolls or leaves a dead strip under Settings. Losing
            // the Charts row took one row and one gap — 108px — out of that sum; the eight remaining
            // gaps give 104 of it back and the column's bottom padding the last 4, so the row pitch
            // itself stays at the size the reference was measured at.
            verticalArrangement = Arrangement.spacedBy(37.apx()),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0 && items[index - 1].group != item.group) {
                    RailDivider()
                }

                val isSelected = remember(currentRoute, item.route) {
                    isRouteSelected(currentRoute, item.route, navigationItems)
                }
                // BACK from a page lands on the row that page sits under, not always on Home: the pill
                // the rail expands into reads as "where you are", and pointing it somewhere else every
                // time asks the user to walk back to the entry they just left. A detail route matches
                // no row at all, so those fall back to the shell's own home rather than losing the key.
                val holdsBackAnchor = isSelected || (!anyRowSelected && index == 0)
                // The first row is already named by the cold-start anchor, so reuse it: a second
                // Modifier.focusRequester on one node splits it into two focus targets, the rail lands
                // on the first and the row's ring watches the second (see auraClaimRequester). Rows
                // below it carry their own, minted once per row so one never moves between nodes.
                val backRequester = remember(item.route) { FocusRequester() }
                val backAnchor =
                    if (index == 0) initialFocusRequester else backRequester
                if (holdsBackAnchor && railAnchor != null) {
                    DisposableEffect(railAnchor, item.route) {
                        railAnchor.publish(backAnchor)
                        onDispose { railAnchor.revoke(backAnchor) }
                    }
                }
                val label = if (item.labelRes != 0) stringResource(item.labelRes) else item.label
                val screen = item.screen
                val isProfile = item.route == ProfileRoute
                val longHold = when {
                    screen == Screens.Home -> onHomeLongHold
                    screen == Screens.Search -> onSearchLongClick
                    else -> null
                }

                AuraRailRow(
                    iconRes = item.iconRes,
                    label = label,
                    selected = isSelected,
                    expanded = expanded,
                    badge = isProfile && showUpdateBadge,
                    imageUrl = if (isProfile) accountImageUrl else null,
                    contentDescription = if (isProfile) accountName ?: label else label,
                    longHoldMillis = if (screen == Screens.Home) HomeLongHoldMillis else viewConfiguration.longPressTimeoutMillis,
                    onLongHold = longHold,
                    onClick = {
                        when {
                            screen != null -> onItemClick(screen, isSelected)
                            isProfile && onAccountClick != null -> onAccountClick()
                            else -> onSecondaryClick?.invoke(item.route)
                        }
                    },
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(initialFocusRequester) else Modifier)
                        .then(if (holdsBackAnchor && index != 0) Modifier.focusRequester(backRequester) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun RailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.apx(), vertical = 12.apx())
            .height(1.apx())
            .background(AuraMock.Border.copy(alpha = 0.50f)),
    )
}

/**
 * The rail label transitions, with the width animation taken out while the splash reveal is still
 * on screen. `expandHorizontally` changes a measured width every frame, and these rows sit in the
 * same Row as the page, so each frame re-measures the whole feed underneath the splash.
 */
private fun auraRailEnter(revealPending: Boolean): EnterTransition =
    if (revealPending) {
        EnterTransition.None
    } else {
        fadeIn(tween(200)) + expandHorizontally(tween(200))
    }

private fun auraRailExit(revealPending: Boolean): ExitTransition =
    if (revealPending) {
        ExitTransition.None
    } else {
        fadeOut(tween(150)) + shrinkHorizontally(tween(150))
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AuraRailRow(
    iconRes: Int,
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    contentDescription: String = label,
    badge: Boolean = false,
    longHoldMillis: Long = 0L,
    onLongHold: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val currentOnClick by rememberUpdatedState(onClick)

    if (onLongHold != null) {
        LaunchedEffect(interactionSource, longHoldMillis) {
            var isLongHold = false
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        isLongHold = false
                        delay(longHoldMillis)
                        isLongHold = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongHold?.invoke()
                    }

                    is PressInteraction.Release -> {
                        if (!isLongHold) currentOnClick()
                    }

                    is PressInteraction.Cancel -> {
                        isLongHold = false
                    }
                }
            }
        }
    }

    val content = if (selected) AuraMock.Primary else AuraMock.Muted

    Row(
        modifier = modifier
            .fillMaxWidth()
            // The rail is not a page: its own rows must never answer the RIGHT key it hands out.
            .auraFocused(corner = FullRound, claim = false)
            .clip(RoundedCornerShape(FullRound))
            .background(if (selected) AuraMock.Border.copy(alpha = 0.40f) else Color.Transparent)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (onLongHold == null) currentOnClick() },
            )
            // 44px icon + 2x20px padding is the 84px row pitch the nav column's exact-fill sum needs.
            .padding(horizontal = 10.apx(), vertical = 20.apx()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(AuraPx.RailIcon.apx())
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription,
                    tint = content,
                    modifier = Modifier.size(AuraPx.RailIcon.apx()),
                )
            }
            if (badge) {
                Box(
                    modifier = Modifier
                        .size(12.apx())
                        .clip(CircleShape)
                        .background(AuraMock.Primary),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = auraRailEnter(!AuraSplashSeen.value),
            exit = auraRailExit(!AuraSplashSeen.value),
        ) {
            AuraText(
                value = label,
                px = 18,
                weight = FontWeight.Medium,
                color = content,
                modifier = Modifier.padding(start = 16.apx()),
            )
        }
    }
}

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
