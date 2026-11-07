/**
 * Aura Music — the secondary screens' page header.
 *
 * The reference has no app bar anywhere: each view is a flat page whose heading is just large text
 * painted on the page background. Every secondary screen already builds its own bar, so the swap is
 * one import per file rather than one edit per screen. Phones keep the stock Material bar.
 */

package com.aura.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.aura.auraType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    if (LocalAuraTvShell.current) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                // The shell gives the page an exact height (NavHost sits under a weight), and a
                // sibling root inherits it — so an unbounded bar is stretched over the whole
                // viewport, its content centred at mid-screen and its opaque background painted
                // across the list underneath. `height` cannot refuse that: it re-clamps to the
                // incoming constraints, so only `requiredHeight` actually keeps the bar a bar.
                .requiredHeight(AuraPx.PageHeader.apx())
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = AuraPx.PagePadding.apx(),
                    // 16 + the 96px control + 16 is AuraPx.PageHeader: the height the Material bar used
                    // to reserve, so a taller heading clips the first row of the screens below it.
                    // The shell's top inset is taken from that same token.
                    top = 16.apx(),
                    end = 32.apx(),
                    bottom = 16.apx(),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Every screen builds its own back button as a Material icon button, so the ring the rest
            // of the shell draws has no part of theirs to attach to. The slot holds exactly one
            // control, so a ring on the group is a ring on the button.
            // It is also the first focused thing on a page to compose, so it stands out of the rail's
            // landing spots: DPAD_RIGHT onto a back button is one ENTER away from popping the page.
            Box(
                modifier = Modifier
                    .focusGroup()
                    .auraFocused(corner = 20.apx(), fill = false, grow = false, claim = false),
            ) {
                navigationIcon()
            }
            Box(
                modifier = Modifier.padding(start = 8.apx()).weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Screens hand over a bare `Text(...)`, so the reference's heading style is
                // supplied through the text-style local instead of rewriting every call site.
                CompositionLocalProvider(
                    LocalTextStyle provides auraType(FontWeight.Normal, 40),
                    LocalContentColor provides AuraMock.Muted,
                    content = title,
                )
            }
            actions()
        }
    } else {
        androidx.compose.material3.TopAppBar(
            title = title,
            modifier = modifier,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}
