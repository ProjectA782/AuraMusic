/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.theme

import android.view.View
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.music.ui.aura.auraClaimPageFocus
import com.aura.music.ui.aura.auraClaimRequester
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

private const val MaxAimFrames = 4
private const val MaxAnchorTries = 4

/** A ceiling on waiting for a scroll to carry a row in, in case the animation never settles. */
private const val MaxTravelFrames = 120

/** Where a row that had to be composed comes to rest: a little above the middle of the screen. */
private const val RevealLine = 0.55f

/**
 * The bottom of the band a focused row may sit in. Pages reserve the last stretch of the viewport
 * for the floating mini-player, so a row inside the reserve is highlighted but unreadable.
 */
private const val BandBottom = 0.88f

/**
 * Draws a ring around this element while it holds focus.
 *
 * D-pad traversal already worked; nothing rendered it, so focus was invisible on TV.
 * Applied per element rather than through LocalIndication because Material3 components pass an
 * explicit ripple indication of their own, which shadows anything provided there.
 */
@Composable
fun Modifier.tvFocusRing(
    ringWidth: Dp = 3.dp,
    ringPadding: Dp = 3.dp,
    cornerRadius: Dp = 12.dp,
    claim: Boolean = true,
): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val ringColor = MaterialTheme.colorScheme.primary
    val haloColor = ringColor.copy(alpha = 0.35f)

    val ringPx = with(density) { ringWidth.toPx() }
    val padPx = with(density) { ringPadding.toPx() }
    val radiusPx = with(density) { cornerRadius.toPx() }
    val haloPx = with(density) { (ringWidth * 2f).toPx() }
    val haloRadiusPx = with(density) { (cornerRadius + ringWidth).toPx() }

    val focused = remember { mutableStateOf(false) }

    // Both elements below are remembered, and that is the fix rather than a style choice. A modifier
    // lambda written inline is a new instance every recomposition, a new instance compares unequal,
    // and an unequal element makes the node rebuild its whole chain. Every tile on a TV page wears
    // these, so one recomposition of a feed re-applied hundreds of chains - the passes the vendor's
    // frame monitor timed at 0.4 to 1.7s, all of them landing inside the splash reveal.
    val watch = remember(focused) {
        Modifier.onFocusEvent { state: FocusState -> focused.value = state.hasFocus }
    }
    val ring =
        remember(
            view,
            focused,
            ringColor,
            haloColor,
            ringPx,
            padPx,
            radiusPx,
            haloPx,
            haloRadiusPx,
        ) {
            Modifier.drawWithContent {
                drawContent()
                if (focused.value && !view.isInTouchMode) {
                    drawFocusRing(
                        ringColor = ringColor,
                        haloColor = haloColor,
                        ringPx = ringPx,
                        padPx = padPx,
                        radiusPx = radiusPx,
                        haloPx = haloPx,
                        haloRadiusPx = haloRadiusPx,
                    )
                }
            }
        }

    return then(watch)
        .then(ring)
        // Every ring is a focusable the page could hand the rail's RIGHT key to — unless it is a
        // row's outer frame, which only watches the focusable inside it and cannot take focus itself.
        .then(if (claim) Modifier.auraClaimPageFocus() else Modifier)
}

private fun DrawScope.drawFocusRing(
    ringColor: Color,
    haloColor: Color,
    ringPx: Float,
    padPx: Float,
    radiusPx: Float,
    haloPx: Float,
    haloRadiusPx: Float,
) {
    val insetByHalo = Size(
        (size.width - padPx * 2f + haloPx).coerceAtLeast(0f),
        (size.height - padPx * 2f + haloPx).coerceAtLeast(0f),
    )
    drawRoundRect(
        color = haloColor,
        topLeft = Offset(padPx - haloPx / 2f, padPx - haloPx / 2f),
        size = insetByHalo,
        cornerRadius = CornerRadius(haloRadiusPx),
        style = Stroke(width = haloPx),
    )
    drawRoundRect(
        color = ringColor,
        topLeft = Offset(padPx, padPx),
        size = Size(
            (size.width - padPx * 2f).coerceAtLeast(0f),
            (size.height - padPx * 2f).coerceAtLeast(0f),
        ),
        cornerRadius = CornerRadius(radiusPx),
        style = Stroke(width = ringPx),
    )
}

@Composable
fun rememberTvRowNav(listState: LazyListState): TvRowNav {
    val nav = remember(listState) { TvRowNav(listState) }
    // The nav outlives every row: its moves are launched on its own scope, so release it with the
    // screen rather than leaking a runnable scope past it.
    DisposableEffect(nav) {
        onDispose { nav.release() }
    }
    return nav
}

/**
 * Vertical D-pad navigation across the stacked rows of a lazy feed.
 *
 * Compose answers a directional press by walking focusables in tree order before it looks at
 * geometry, so DOWN from a tile escapes into the navigation rail and then the mini-player, which
 * holds every later press. Rows below the fold are not even composed, so there is nothing there to
 * land on. Each row therefore claims its own up/down presses and moves focus to the neighbouring
 * row directly.
 *
 * Moving the list is what makes a press feel heavy, so the nav keeps a table of which lazy item
 * hosts each row — rebuilt from the layout on every press — and scrolls only when the neighbour has
 * nothing on screen to light up. A press whose target is already visible moves the selector and
 * leaves the page exactly where it was.
 */
class TvRowNav internal constructor(private val listState: LazyListState) {
    private val requesters = mutableMapOf<String, FocusRequester>()

    /** Which item in the list hosts each row, claimed while the row composes and dropped when it goes. */
    private val rowOfItem = mutableMapOf<Any, Int>()

    /** Where this layout actually places those rows, and the item distance between two of them. */
    private var rowPositions = mapOf<Int, Int>()
    private var itemsPerRow = 1

    private var focusedCol = 0
    private var busy = false

    /** Rows the presses asked for while [busy]: see [spendQueuedSteps]. */
    private var pendingSteps = 0

    /**
     * The one coroutine that is allowed to move the list right now. Two of ours overlapped on every
     * press of a measured run and the second threw MutationInterruptedException through the first,
     * so each row stopped wherever the fight ended instead of where the band needed it.
     */
    private var scroll: Job? = null

    /**
     * Which press the running aim belongs to. A measured burst left a selector that had already moved
     * 605 ms earlier being yanked back by the aim loop an older press had left pending, so an aim
     * checks this before it touches focus.
     */
    private var moveToken = 0

    /**
     * Scrolling to the neighbour pushes the row that handled the press out of the viewport, which
     * cancels a scope owned by that row mid-move and silently strands focus. This scope is tied to
     * the screen, not to any one row, so a move always finishes. [AndroidUiDispatcher.Main] rather
     * than [Dispatchers.Main] because the scroll animation needs the frame clock it carries.
     */
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)

    internal fun release() {
        scope.cancel()
    }

    /**
     * Marks one tile so the nav can aim at it, and remembers the column the user was on.
     *
     * A hero passes [best] so the rail's RIGHT lands there rather than on whichever element happened
     * to compose first — on home that is the reload button in the page title.
     *
     * A page that already owns a requester — its hero anchor — passes it as [anchor]. Chaining a
     * second `Modifier.focusRequester` onto this node would split it, and the rail would land on the
     * half that draws no ring.
     */
    @Composable
    fun tile(row: Int, col: Int, best: Boolean = false, anchor: FocusRequester? = null): Modifier {
        val key = "$row:$col"
        val requester = anchor ?: requesters.getOrPut(key) { FocusRequester() }
        if (anchor != null) requesters[key] = requester
        // Rows below the fold are not composed, so a stale requester would aim at a detached node.
        DisposableEffect(key) {
            onDispose { if (requesters[key] === requester) requesters.remove(key) }
        }
        val target = remember(requester) { Modifier.focusRequester(requester) }
        val mark = remember(this, col) {
            Modifier.onFocusEvent { state ->
                if (state.hasFocus) focusedCol = col
            }
        }
        // The tile owns the node's only focus target, so it is also the one the page offers the
        // rail; the ring on this node must not mint a second. Both elements are remembered for the
        // same reason tvFocusRing remembers its own: an inline lambda re-applies the chain.
        return target.auraClaimRequester(requester, best).then(mark)
    }

    /**
     * Register a tile whose focus target a component already owns and applies itself — a chip that
     * takes a [FocusRequester] rather than minting one — so it joins the vertical chain without
     * [tile] handing the node a second target. Two of them split the node, and the selector then
     * lands on the half that draws no ring.
     */
    @Composable
    fun stop(
        row: Int,
        col: Int,
        requester: FocusRequester,
        best: Boolean = false,
    ): Modifier {
        val key = "$row:$col"
        requesters[key] = requester
        DisposableEffect(key, requester) {
            onDispose { if (requesters[key] === requester) requesters.remove(key) }
        }
        val mark = remember(this, col) {
            Modifier.onFocusEvent { state ->
                if (state.hasFocus) focusedCol = col
            }
        }
        return Modifier.auraClaimRequester(requester, best).then(mark)
    }

    /**
     * Applied to a row's root: claims the up/down presses made by any tile inside it and moves
     * focus to the neighbouring row. [rowCount] bounds the chain so the first and last rows hand
     * their presses back to the rest of the screen.
     */
    @Composable
    fun row(
        index: Int,
        itemKey: Any,
        rowCount: Int,
    ): Modifier {
        DisposableEffect(itemKey, index) {
            rowOfItem[itemKey] = index
            onDispose { if (rowOfItem[itemKey] == index) rowOfItem.remove(itemKey) }
        }
        return Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val down = when (event.key) {
                Key.DirectionDown -> true
                Key.DirectionUp -> false
                else -> return@onPreviewKeyEvent false
            }
            val target = if (down) index + 1 else index - 1
            if (target !in 0 until rowCount) return@onPreviewKeyEvent false
            if (busy) {
                pendingSteps = (pendingSteps + if (down) 1 else -1).coerceIn(-rowCount, rowCount)
                return@onPreviewKeyEvent true
            }
            aim(index, target, rowCount)
        }
    }

    /**
     * Spend one press on [target]: move the selector on this frame when the row is already composed,
     * and bring the row in first when it is not. Returns whether the press was taken.
     */
    private fun aim(
        index: Int,
        target: Int,
        rowCount: Int,
    ): Boolean {
        val down = target > index
        refreshRowPositions()
        val requester = nearestRequester(target)
        if (requester != null) {
            // Light the selector on this frame, then glide the row into the band behind it.
            // The glide used to come first, so every press this handler took showed nothing for
            // ~330ms while the list animated — measured 322-336ms to land, against 6-29ms when
            // Compose's own search handled the same press.
            val landed = runCatching { requester.requestFocus() }
                .onFailure { Timber.w(it, "tvRowNav: row($index) -> $target — requestFocus failed") }
                .getOrDefault(false)
            if (landed) {
                if (!isInBand(target)) {
                    ++moveToken
                    scroll = scope.launch {
                        try {
                            glideIntoBand(target)
                        } catch (e: CancellationException) {
                            // Both ways this glide stops early are the list changing hands, not a
                            // fault: a newer press took it, or landing the selector made Compose
                            // bring the row into view itself (its MutationInterruptedException is a
                            // CancellationException, and internal, so it can only be caught here).
                            // Measured either way the row still ends up inside the band.
                            Timber.d("tvRowNav: row $target glide handed the list to $e")
                        } catch (e: Exception) {
                            Timber.w(e, "tvRowNav: glide of row $target failed")
                        }
                    }
                }
                return true
            }
            // A row that was registered but takes no focus would otherwise eat every press
            // aimed at it and strand the chain here. Release the key and let the search run.
            Timber.w("tvRowNav: row($index) -> $target — target refused focus, releasing the press")
            return false
        }
        // The neighbour is not composed yet: only now does the press wait on a scroll.
        busy = true
        val token = ++moveToken
        scope.launch {
            try {
                moveToRow(target, from = index, down = down, token = token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "tvRowNav: moveToRow($target) threw")
            } finally {
                busy = false
                spendQueuedSteps(target, rowCount, token)
            }
        }
        return true
    }

    /**
     * Hand back the presses that arrived while this move was running. The TV repeats a held key every
     * few tens of ms and a row below the fold still costs ~100ms to bring in, so a measured burst of
     * six swallowed two: the handler took the key, nothing moved, and the step was never retried.
     * The net is spent as one move to where those presses were heading.
     */
    private fun spendQueuedSteps(
        landed: Int,
        rowCount: Int,
        token: Int,
    ) {
        val steps = pendingSteps
        if (steps == 0 || token != moveToken) return
        pendingSteps = 0
        val next = (landed + steps).coerceIn(0, rowCount - 1)
        if (next != landed) {
            aim(index = landed, target = next, rowCount = rowCount)
        }
    }

    private suspend fun moveToRow(
        target: Int,
        from: Int,
        down: Boolean,
        token: Int,
    ) {
        // A newer press has already moved the selector since this aim was queued; landing now would
        // drag it back to where the old press was heading.
        fun stillOurs() = token == moveToken
        nearestRequester(target)?.let { requester ->
            // On screen, but straddling an edge: light it up, then slide the row in behind it.
            if (stillOurs()) {
                runCatching { requester.requestFocus() }
                    .onFailure { Timber.w(it, "tvRowNav: moveToRow($target) — requestFocus failed") }
                glideIntoBand(target)
            }
            return
        }
        // The neighbour is not composed yet, so a scroll has to bring it in before focus can land.
        // Aim at it from the row table instead of stepping one item at a time: on these pages the
        // distance between neighbouring rows is steady, so the first aim usually lands.
        var anchor = predictedAnchor(target, from)
        repeat(MaxAnchorTries) {
            // Start the travel, then wait on the node rather than on the animation. The row comes
            // into composition part-way through, and awaiting the whole animation first measured
            // 633ms and 1283ms of nothing lighting up — long enough for the presses aimed at the
            // rows crossing during it to be swallowed one by one.
            val travelling = scope.launch {
                claimScroll()
                try {
                    listState.animateScrollToItem(anchor, revealOffset())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "tvRowNav: row $target travel to item $anchor failed")
                }
            }
            scroll = travelling
            val requester = awaitRow(target) { travelling.isActive }
            travelling.cancel()
            if (!stillOurs()) return
            if (requester != null) {
                runCatching { requester.requestFocus() }
                    .onFailure { Timber.w(it, "tvRowNav: moveToRow($target) — requestFocus failed") }
                // The travel was cut short wherever the node appeared, so finish carrying it in.
                glideIntoBand(target)
                return
            }
            val further = anchorPastComposition(anchor, down) ?: return
            anchor = further
        }
    }

    /**
     * Waits out the frames until [target] composes and its tiles register.
     *
     * A row crossing in on an animation is not a miss while that animation still runs, so [travelling]
     * holds the wait open; the count against [MaxAimFrames] only starts once the list has settled,
     * with [MaxTravelFrames] the ceiling if it never does.
     */
    private suspend fun awaitRow(
        target: Int,
        travelling: () -> Boolean = { false },
    ): FocusRequester? {
        var frames = 0
        var idleFrames = 0
        while (idleFrames <= MaxAimFrames && frames <= MaxTravelFrames) {
            refreshRowPositions()
            nearestRequester(target)?.let { return it }
            withFrameNanos { }
            frames++
            if (!travelling()) idleFrames++
        }
        return null
    }

    /** Records where this layout places each row, and what two rows on screen say about their gap. */
    private fun refreshRowPositions() {
        val placed = HashMap<Int, Int>()
        listState.layoutInfo.visibleItemsInfo.forEach { item ->
            rowOfItem[item.key]?.let { row -> placed[row] = item.index }
        }
        val rows = placed.keys.sorted()
        for (i in 1 until rows.size) {
            if (rows[i] == rows[i - 1] + 1) {
                val gap = placed.getValue(rows[i]) - placed.getValue(rows[i - 1])
                if (gap > 0) itemsPerRow = gap
            }
        }
        rowPositions = placed
    }

    /** Item index the neighbour is expected to sit at, once the rows placed on screen say where. */
    private fun predictedAnchor(
        target: Int,
        from: Int,
    ): Int {
        val fromIndex = rowPositions[from] ?: target
        val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
        return (fromIndex + itemsPerRow * (target - from)).coerceIn(0, lastIndex)
    }

    /**
     * After a miss only one thing is certain: everything this layout composed is not the neighbour,
     * so step past that edge. Always progress, and null once the list itself runs out.
     */
    private fun anchorPastComposition(
        anchor: Int,
        down: Boolean,
    ): Int? {
        val info = listState.layoutInfo
        val edge = info.visibleItemsInfo
        val next = if (down) {
            (edge.lastOrNull()?.index ?: anchor) + 1
        } else {
            (edge.firstOrNull()?.index ?: anchor) - 1
        }
        if (next == anchor || next !in 0 until info.totalItemsCount) {
            Timber.w("tvRowNav: no row beyond item $anchor — nothing left to try")
            return null
        }
        Timber.w("tvRowNav: aim at item $anchor missed — continuing at $next")
        return next
    }

    /** The row's top and bottom in the current layout, or null while the list does not place it. */
    private fun rowBounds(row: Int): Pair<Int, Int>? {
        val index = rowPositions[row] ?: return null
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            ?: return null
        return item.offset to item.offset + item.size
    }

    private fun band(): IntRange {
        val info = listState.layoutInfo
        return info.viewportStartOffset..(info.viewportEndOffset * BandBottom).roundToInt()
    }

    private fun isInBand(row: Int): Boolean {
        val bounds = rowBounds(row) ?: return false
        val band = band()
        return bounds.first >= band.first && bounds.second <= band.last
    }

    /** The smallest scroll that puts [row] back inside the band; nothing at all when it already fits. */
    private suspend fun glideIntoBand(row: Int) {
        val bounds = rowBounds(row) ?: return
        val band = band()
        val delta = when {
            bounds.second > band.last -> (bounds.second - band.last).toFloat()
            bounds.first < band.first -> (bounds.first - band.first).toFloat()
            else -> return
        }
        claimScroll()
        listState.animateScrollBy(delta)
        refreshRowPositions()
    }

    /** Take the list from whichever scroll is in flight; only one of ours may run at a time. */
    private suspend fun claimScroll() {
        val self = coroutineContext[Job]
        if (self !== scroll) scroll?.cancel()
        scroll = self
    }

    private fun revealOffset(): Int =
        (listState.layoutInfo.viewportEndOffset * RevealLine).roundToInt()

    private fun nearestRequester(row: Int): FocusRequester? {
        val cols = requesters.keys
            .filter { it.startsWith("$row:") }
            .mapNotNull { it.substringAfter(':').toIntOrNull() }
        val col = cols.minByOrNull { abs(it - focusedCol) } ?: return null
        return requesters["$row:$col"]
    }
}

@Composable
fun rememberAuraStopChain(): AuraStopChain = remember { AuraStopChain() }

/**
 * D-pad navigation for a screen that is built by hand instead of scrolled: the full-screen player.
 *
 * [TvRowNav] needs a lazy list to aim through, and a player has none — it is three rows of controls
 * over an artwork column. Left to Compose's geometric search, a press from any of them walks out of
 * the screen and into the rail and the page behind it, which the player covers completely: the
 * remote keeps working, the ring lights up nothing the viewer can see, and every later press is
 * spent in the dark.
 *
 * So the screen declares its controls as (row, column) stops and this answers the four directions
 * among them alone, swallowing a press at the edge rather than letting it leave. [AuraStopChain.nav]
 * stays quiet while focus sits anywhere outside the chain, so an overlay's own navigation — the
 * queue, the lyrics scroller — keeps the keys it is handed.
 */
class AuraStopChain {
    private val requesters = mutableMapOf<String, FocusRequester>()
    private val colsOf = mutableMapOf<Int, MutableSet<Int>>()

    private var focusedKey: String? = null
    private var focusedRow = -1
    private var focusedCol = 0
    private var focusedOwnsHorizontal = false

    /**
     * Whether one of the chain's controls holds the selector. A screen opens its chain with an
     * animation still running, so the first focus request can miss; this is what it watches to know
     * whether to keep asking.
     */
    val holdsFocus = mutableStateOf(false)

    /** A control's place in the grid. [ownsHorizontal] leaves left/right to the control itself. */
    @Composable
    fun stop(
        row: Int,
        col: Int,
        ownsHorizontal: Boolean = false,
        requester: FocusRequester? = null,
    ): Modifier {
        val key = "$row:$col"
        val target = requester ?: requesters.getOrPut(key) { FocusRequester() }
        requesters[key] = target
        val cols = colsOf.getOrPut(row) { mutableSetOf() }
        cols.add(col)
        DisposableEffect(key) {
            onDispose {
                if (requesters[key] === target) requesters.remove(key)
                cols.remove(col)
                if (focusedKey == key) {
                    focusedKey = null
                    holdsFocus.value = false
                }
            }
        }
        return Modifier
            .focusRequester(target)
            .onFocusEvent { state ->
                if (state.hasFocus) {
                    focusedKey = key
                    focusedRow = row
                    focusedCol = col
                    focusedOwnsHorizontal = ownsHorizontal
                    holdsFocus.value = true
                } else if (focusedKey == key) {
                    focusedKey = null
                    holdsFocus.value = false
                }
            }
    }

    /** Applied to the screen's root: keeps the four directions inside the chain. */
    fun nav(): Modifier = Modifier.onPreviewKeyEvent { event ->
        if (focusedKey == null) return@onPreviewKeyEvent false
        val step = when (event.key) {
            Key.DirectionUp -> -1 to 0
            Key.DirectionDown -> 1 to 0
            Key.DirectionLeft -> if (focusedOwnsHorizontal) null else 0 to -1
            Key.DirectionRight -> if (focusedOwnsHorizontal) null else 0 to 1
            else -> return@onPreviewKeyEvent false
        } ?: return@onPreviewKeyEvent false
        // Answer the press, not the release: Compose's own focus search also runs on KeyDown, so a
        // release here glides a second time — from wherever that search landed, which may be a node
        // behind the screen this chain covers. Both halves are consumed, so the control the glide
        // just moved onto cannot read a release as the end of a drag it was never given.
        if (event.type == KeyEventType.KeyDown) glide(step.first, step.second)
        true
    }

    /**
     * One step in the grid, clamped to it: a row keeps the column it is asked for when it has one,
     * and takes its own nearest otherwise. A press at the edge is consumed without moving — the
     * screen is finished, and there is nothing beyond it worth lighting up.
     */
    private fun glide(
        dRow: Int,
        dCol: Int,
    ): Boolean {
        if (dCol != 0) {
            val cols = colsOf[focusedRow]?.sorted() ?: return true
            val at = cols.indexOf(focusedCol)
            if (at >= 0) land(focusedRow, cols.getOrNull(at + dCol))
            return true
        }
        val rows = colsOf.keys.sorted()
        val at = rows.indexOf(focusedRow)
        if (at < 0) return true
        val target = rows.getOrNull(at + dRow) ?: return true
        val cols = colsOf.getValue(target).sorted()
        val col = cols.firstOrNull { it == focusedCol } ?: cols.minByOrNull { abs(it - focusedCol) }
        land(target, col)
        return true
    }

    private fun land(
        row: Int,
        col: Int?,
    ) {
        if (col == null) return
        val requester = requesters["$row:$col"] ?: return
        runCatching { requester.requestFocus() }
            .onFailure { Timber.w(it, "auraStopChain: row($row) col($col) — requestFocus failed") }
    }
}

/**
 * The remote's MENU key, wired to the action a long hold would perform.
 *
 * Rows that open their details sheet only on long-press are unreachable without a touch screen: a
 * D-pad listener has no long hold, and ENTER is already spent starting the track. MENU carries no
 * other meaning in this app, so on a row that has a menu it becomes that row's menu button.
 */
fun Modifier.auraMenuKey(onMenu: (() -> Unit)?): Modifier =
    if (onMenu == null) {
        this
    } else {
        onKeyEvent { event ->
            if (event.key != Key.Menu || event.type != KeyEventType.KeyUp) {
                false
            } else {
                onMenu()
                true
            }
        }
    }
