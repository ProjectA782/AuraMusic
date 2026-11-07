/**
 * Aura Music — the reference's reusable surface blocks.
 *
 * The design file draws three screens in full (rail + home, mini-player, now-playing) and leaves the
 * rest as a title over one pill, so every other page in the shell is composed from these primitives
 * instead of borrowing a layout that already existed in the codebase: hero card, pick shelf, rail
 * row, pill button, ghost button.
 */

package com.aura.music.ui.aura


import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.aura.music.R
import com.aura.music.ui.theme.TvRowNav

private val HeroTint = AuraMock.Page
private val PickTintTopLeft = Color(0x66581C87)
private val ThumbnailRadius = 12

/** One tile of a shelf: what the reference shows inside every `w-72 h-72` card. */
data class AuraPick(
    val id: String,
    val title: String,
    val subtitle: String,
    val image: String?,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * The reference's `text-3xl font-bold text-white` shelf heading; a chevron when it opens a page.
 *
 * A heading with nothing to open is still a stop in the vertical chain — the row nav aims at it —
 * so it has to take focus anyway. Without a focus target there the press is spent and the selector
 * dead-stops on the last track above the shelf.
 */
@Composable
fun AuraSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .auraFocused(corner = FullRound, fill = false, onFocusChanged = { focused = it })
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.focusable())
            .padding(horizontal = 8.apx(), vertical = 6.apx()),
    ) {
        AuraText(
            value = title,
            px = 30,
            weight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = if (focused) AuraMock.OnPrimary else AuraMock.Primary,
                modifier = Modifier.size(28.apx()),
            )
        }
    }
}

/**
 * The reference's hero: full-width card, 450px tall, radius 32, art faded under a scrim that melts
 * into the page, badge pill over a 72px title, and the 80px primary play disc at the baseline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraHeroBlock(
    pick: AuraPick,
    badge: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AuraPx.HeroRadius.apx())
    Box(
        modifier = modifier
            .auraFocused(corner = AuraPx.HeroRadius.apx(), grow = false, claim = false)
            .fillMaxWidth()
            .height(AuraPx.HeroHeight.apx())
            .clip(shape)
            .background(AuraMock.Panel)
            .combinedClickable(onClick = pick.onClick, onLongClick = pick.onLongClick),
    ) {
        AsyncImage(
            model = pick.image,
            contentDescription = pick.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .alpha(0.6f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            HeroTint.copy(alpha = 0.4f),
                            HeroTint,
                        ),
                    ),
                ),
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 48.apx())
                .padding(bottom = 48.apx()),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 16.apx())
                        .background(AuraMock.Primary, RoundedCornerShape(FullRound)),
                ) {
                    AuraText(
                        value = badge,
                        px = 14,
                        weight = FontWeight.Bold,
                        color = AuraMock.OnPrimary,
                        trackingEm = 0.05f,
                        modifier = Modifier.padding(horizontal = 16.apx(), vertical = 8.apx()),
                    )
                }
                AuraText(
                    value = pick.title,
                    px = 72,
                    weight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.apx()),
                )
                AuraText(
                    value = pick.subtitle,
                    px = 30,
                    weight = FontWeight.Medium,
                    color = AuraMock.Muted,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 24.apx())
                    .size(AuraPx.HeroPlay.apx())
                    .background(AuraMock.Primary, CircleShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.aura_ic_play),
                    contentDescription = null,
                    tint = AuraMock.OnPrimary,
                    modifier = Modifier.size(40.apx()),
                )
            }
        }
    }
}

/** The reference's shelf of `w-72 h-72 rounded-3xl` cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraPickRow(
    picks: List<AuraPick>,
    rowNav: TvRowNav,
    rowIndex: Int,
    rowCount: Int,
    itemKey: Any,
) {
    val cornerRadius = AuraPx.PickCardRadius.apx()
    val corner = RoundedCornerShape(cornerRadius)
    // A LazyRow clips at its viewport edge, which would cut the auraFocused ring and the 1.05 lift
    // of the tiles at either end. contentPadding grows the wrap-content viewport, so the ring has
    // 16px of unclipped gutter and the cards still land where the mock puts them.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(32.apx()),
        contentPadding = PaddingValues(16.apx()),
        modifier = Modifier
            .then(rowNav.row(index = rowIndex, itemKey = itemKey, rowCount = rowCount))
            .focusGroup(),
    ) {
        itemsIndexed(picks, key = { _, pick -> "aura_pick_${pick.id}" }) { col, pick ->
            Box(
                modifier = rowNav.tile(rowIndex, col)
                    .auraFocused(corner = cornerRadius, claim = false)
                    .size(AuraPx.PickCard.apx())
                    .clip(corner)
                    .background(AuraMock.Panel)
                    .combinedClickable(onClick = pick.onClick, onLongClick = pick.onLongClick),
            ) {
                AsyncImage(
                    model = pick.image,
                    contentDescription = pick.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(PickTintTopLeft, Color.Transparent),
                                start = Offset.Zero,
                                end = Offset.Infinite,
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(24.apx()),
                ) {
                    AuraText(
                        value = pick.title,
                        px = 24,
                        weight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                    )
                    AuraText(
                        value = pick.subtitle,
                        px = 16,
                        color = AuraMock.Muted,
                    )
                }
            }
        }
    }
}

/**
 * The reference's rail row, used wherever the design has a list instead of a grid: flat, full-round,
 * `gap-4` between a leading tile and two lines of text, with whatever the caller needs at the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    image: String? = null,
    @DrawableRes icon: Int? = null,
    label: String? = null,
    active: Boolean = false,
    best: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // `best` forwards the row's claim on the page's landing spot, so a press from a header
            // or an input aims at the title rather than at whichever trailing control sits nearest.
            .auraFocused(corner = FullRound, fill = false, best = best, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(FullRound))
            .background(if (active) AuraMock.Border.copy(alpha = 0.40f) else Color.Transparent)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 20.apx(), vertical = 14.apx()),
    ) {
        label?.let {
            // Track numbers: the reference has no list with them, so they take the muted 18px
            // caption scale in a fixed gutter and never shift the title's left edge.
            AuraText(
                value = it,
                px = 18,
                color = AuraMock.Muted,
                modifier = Modifier.width(44.apx()),
            )
        }
        if (image != null) {
            Box(
                modifier = Modifier
                    .size(64.apx())
                    .clip(RoundedCornerShape(ThumbnailRadius))
                    .background(AuraMock.Panel),
            ) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Spacer(modifier = Modifier.width(20.apx()))
        }
        if (icon != null) {
            // A glyph where the reference puts an artwork tile, so an icon row keeps the same gutter.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.apx())
                    .clip(RoundedCornerShape(ThumbnailRadius))
                    .background(AuraMock.Panel),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (focused) AuraMock.Primary else AuraMock.Muted,
                    modifier = Modifier.size(30.apx()),
                )
            }
            Spacer(modifier = Modifier.width(20.apx()))
        }
        Column(modifier = Modifier.weight(1f)) {
            AuraText(
                value = title,
                px = 24,
                weight = FontWeight.Medium,
                color = when {
                    focused -> Color.White
                    active -> AuraMock.Primary
                    else -> AuraMock.Text
                },
            )
            subtitle?.let {
                AuraText(
                    value = it,
                    px = 18,
                    color = AuraMock.Muted,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.apx()))
        trailing()
    }
}

/** The reference's `.pill-btn`: filled primary at rest when it is the action, outlined when not. */
@Composable
fun AuraPillButton(
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(FullRound)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.apx()),
        modifier = modifier
            .auraFocused(corner = FullRound, fill = false, onFocusChanged = { focused = it })
            .then(
                if (filled || focused) {
                    Modifier.background(AuraMock.Primary, shape)
                } else {
                    Modifier.border(2.apx(), AuraMock.PillOutline, shape)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 32.apx(), vertical = 14.apx()),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (focused || filled) AuraMock.OnPrimary else Color.White,
                modifier = Modifier.size(24.apx()),
            )
        }
        AuraText(
            value = label,
            px = 22,
            weight = FontWeight.Bold,
            color = if (focused || filled) AuraMock.OnPrimary else Color.White,
        )
    }
}

/**
 * The reference's filter pill: `px-10 py-4 bg-[#211F26] rounded-full`, filled with the accent once
 * it is the active filter. Used by the library and search chip rows.
 */
@Composable
fun AuraChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    val shape = RoundedCornerShape(FullRound)
    // A caller-supplied anchor is already offered to the rail, so claim it rather than mint a second
    // focus target — two `focusRequester` modifiers on one node split it, and the rail lands on the
    // half that draws no ring.
    Box(
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it).auraTrackRequester(it) } ?: Modifier)
            .auraFocused(
                corner = FullRound,
                fill = false,
                claim = focusRequester == null,
                onFocusChanged = { focused = it },
            )
            .clip(shape)
            .background(if (active) AuraMock.Primary else AuraMock.Panel)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 40.apx(), vertical = 16.apx()),
    ) {
        AuraText(
            value = label,
            px = 22,
            weight = FontWeight.Bold,
            color = if (active) AuraMock.OnPrimary else AuraMock.Text,
        )
    }
}

/**
 * A control with no shape of its own in the reference: a bare glyph that only the ring decorates.
 * Shell chrome passes `claim = false` so the rail never lands its RIGHT on it.
 * The banner passes `ring = false, shadow = false, fill = true` so the transport stays flush with the
 * page: focus washes the glyph's own square rather than drawing an outline around it.
 */
@Composable
fun AuraGhostButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    boxPx: Int = 64,
    glyphPx: Int = 28,
    glyph: Color = AuraMock.Muted,
    enabled: Boolean = true,
    claim: Boolean = true,
    fill: Boolean = false,
    ring: Boolean = true,
    shadow: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // A caller-supplied anchor replaces the button's own rather than joining it: two of them on one
    // node split it, and the ring's listener watches the half the caller cannot aim at.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(
                focusRequester
                    ?.let { Modifier.focusRequester(it).auraClaimRequester(it) }
                    ?: Modifier,
            )
            .auraFocused(
                corner = FullRound,
                fill = fill,
                ring = ring,
                shadow = shadow,
                claim = claim && focusRequester == null,
                onFocusChanged = { focused = it },
            )
            .size(boxPx.apx())
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = when {
                !enabled -> glyph.copy(alpha = 0.25f)
                focused -> Color.White
                else -> glyph
            },
            modifier = Modifier.size(glyphPx.apx()),
        )
    }
}

/**
 * One line of a detail page's track list, normalised so a database query, a YouTube playlist and a
 * Spotify album all render through [auraTrackRows].
 */
data class AuraTrackRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val image: String? = null,
    val label: String? = null,
    val durationText: String? = null,
    val badges: List<Int> = emptyList(),
    val active: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * The reference's rail rows, spread one [androidx.compose.foundation.lazy.LazyColumn] item per track.
 * Each item is its own stop in the vertical D-pad chain — [rowStart] through
 * `rowStart + rows.size - 1` — so a press walks the list instead of jumping past it, and the row
 * that holds the last track hands the next press to whatever shelf follows.
 */
fun LazyListScope.auraTrackRows(
    rows: List<AuraTrackRow>,
    rowNav: TvRowNav,
    rowStart: Int,
    rowCount: Int,
) {
    rows.forEachIndexed { offset, row ->
        val index = rowStart + offset
        val itemKey = "aura_track_${row.id}"
        item(key = itemKey) {
            AuraListRow(
                title = row.title,
                subtitle = row.subtitle,
                image = row.image,
                label = row.label,
                active = row.active,
                onClick = row.onClick,
                onLongClick = row.onLongClick,
                modifier = Modifier
                    .then(rowNav.row(index = index, itemKey = itemKey, rowCount = rowCount))
                    .focusGroup()
                    .then(rowNav.tile(index, 0)),
                trailing = {
                    row.badges.forEach { res ->
                        Icon(
                            painter = painterResource(res),
                            contentDescription = null,
                            tint = AuraMock.Muted,
                            modifier = Modifier.size(24.apx()),
                        )
                        Spacer(modifier = Modifier.width(10.apx()))
                    }
                    row.durationText?.let {
                        AuraText(value = it, px = 20, color = AuraMock.Muted)
                        Spacer(modifier = Modifier.width(12.apx()))
                    }
                    row.onLongClick?.let { openMenu ->
                        AuraGhostButton(
                            icon = R.drawable.aura_ic_more_horizontal,
                            boxPx = 52,
                            glyphPx = 24,
                            onClick = openMenu,
                        )
                    }
                },
            )
        }
    }
}
