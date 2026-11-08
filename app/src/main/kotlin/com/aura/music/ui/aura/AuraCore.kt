/**
 * Aura Music — mock-exact layout primitives.
 *
 * The design reference is authored in CSS pixels against a 1920x1080 viewport. Android TV reports a
 * density that would inflate those numbers, so every size in the Aura shell goes through [apx] and
 * [asp] to keep the rendered result identical to the reference.
 */

package com.aura.music.ui.aura


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.LocalAuraTvShell
import kotlinx.coroutines.delay

/** Aim after the enter transition lands, still ahead of the rail's own re-anchor at 400ms. */
private const val HeroAnchorSettleMs = 220L
private const val HeroAnchorChecks = 12
private const val HeroAnchorIntervalMs = 120L

/** 1 CSS px of the reference, expressed in dp on this screen. Provided by the shell. */
val LocalAuraScale = compositionLocalOf { 1f }

/**
 * The landing spots a page offers the rail's DPAD_RIGHT, best first.
 *
 * A plain mutable list, not snapshot state: the rail only reads it while answering a key press, so
 * nothing composes from it, and a page can carry sixty focused rows. Made of state, every row that
 * claimed or scrolled away would have copied the whole list and notified the snapshot system — work
 * spent on a screen nobody renders.
 */
class AuraPageAnchors {
    private val anchors = ArrayList<FocusRequester>(MaxAnchors)

    /**
     * The offer that currently holds the selector, and whether any does.
     *
     * "The page has focus" is not the same question: the header's back arrow lives in the page too,
     * and a page opened from a row whose tile is gone lands on that arrow, where one ENTER pops back
     * out. The shell needs to tell the two apart — leave a real landing spot alone, sweep chrome onto
     * the best anchor.
     */
    private var holder: FocusRequester? = null

    val anchorHoldsFocus: Boolean get() = holder != null

    val size: Int get() = anchors.size

    /** Offer [requester] to the rail. An explicit anchor goes to the front; a row goes to the back. */
    fun publish(requester: FocusRequester, best: Boolean) {
        if (anchors.contains(requester)) return
        if (anchors.size >= MaxAnchors) {
            // A named anchor outranks anything the list already picked up by accident.
            if (!best) return
            revoke(anchors.lastIndex)
        }
        if (best) anchors.add(0, requester) else anchors.add(requester)
    }

    /**
     * A page recycles rows as it scrolls, so the offer holding the selector can be dropped from under
     * it. Forget it here too, or the shell keeps believing chrome is a landing spot.
     */
    fun revoke(requester: FocusRequester) {
        anchors.remove(requester)
        if (holder == requester) holder = null
    }

    private fun revoke(index: Int) {
        val dropped = anchors.removeAt(index)
        if (holder == dropped) holder = null
    }

    /** Called by the focused element itself, on both the gain and the loss. */
    fun reportFocus(requester: FocusRequester, focused: Boolean) {
        holder =
            when {
                focused -> requester
                holder == requester -> null
                else -> holder
            }
    }

    /**
     * Move the selector to the first offer that still takes it, and answer whether one did.
     *
     * [FocusRequester.requestFocus] returns nothing: an offer whose row has already been disposed, or
     * one the remote cannot stop on at all - a settings row with no action behind it - accepts the call
     * and changes nothing. Asking the offers in turn and stopping at the first one that *reported* the
     * selector is the only test that exists, and answering any earlier than that is how a page opened
     * from another page ends up with its highlight on the header's back arrow: the shell is told the
     * move worked, stops asking, and nothing moved at all.
     */
    fun requestFirstFocus(): Boolean {
        anchors.forEach { requester ->
            requester.requestFocusSafely()
            if (holder != null) return true
        }
        return false
    }

    /**
     * Forget who holds the selector without touching the offers.
     *
     * The shell calls this the moment the destination changes: the outgoing page's row still holds the
     * selector and still counts as a landing spot, so without it the shell would read that stale claim
     * as the new page already being armed and never move the selector at all.
     */
    fun forgetHolder() {
        holder = null
    }

    private companion object {
        /** Enough for the first screenful; the rail only ever needs one reachable target. */
        const val MaxAnchors = 24
    }
}

/**
 * The rail and the page are separate scroll containers and Compose's geometric focus search does not
 * cross from one to the other, so DPAD_RIGHT from the rail had nowhere to go. A page publishes its
 * landing spots here and the rail hands the key to the first of them that takes focus.
 */
val LocalAuraPageFocus = staticCompositionLocalOf<AuraPageAnchors?> { null }

/**
 * The rail row a page hands the selector back to.
 *
 * The rail and the page are separate focus groups, so Compose's geometric search crosses neither way:
 * [AuraPageAnchors] solves the press that goes out of the rail and this solves the one that comes back
 * in, because BACK is the only key the user expects to make that trip.
 */
class AuraRailAnchor {
    private var requester: FocusRequester? = null

    fun publish(requester: FocusRequester) {
        this.requester = requester
    }

    /** The rail re-points the offer at another row; only forget the one it actually made. */
    fun revoke(requester: FocusRequester) {
        if (this.requester === requester) this.requester = null
    }

    /** @return whether the selector moved onto the rail. */
    fun claim(): Boolean = requester?.requestFocusSafely() ?: false
}

val LocalAuraRailAnchor = staticCompositionLocalOf<AuraRailAnchor?> { null }

/**
 * True inside the page the rail points at and false everywhere else in the shell.
 *
 * Every focused element of the page could offer itself as the rail's RIGHT target, so the offer has to
 * be scoped: the page header's back button composes before any row under it, and the full-screen player
 * sits beside the page rather than in it. Landing on the back button and pressing ENTER pops the page
 * the user was trying to reach, so chrome stays out of the list.
 */
val LocalAuraPageContent = compositionLocalOf { false }

/**
 * Park the selector on a page's hero, once the hero is actually on screen.
 *
 * A detail page draws a loading panel until its data lands, and the hero is the only node carrying
 * [heroFocus], so a request fired on entry aims at a detached node and fails: the rail keeps the
 * selector the press started from and the user finds the highlight on Home instead of on the artwork.
 * Keep asking until the row composes and takes it.
 *
 * Re-running when the full-screen player closes is the other half. Dismissing the sheet drops the
 * selection outside every region, the rail then treats itself as the only place left for it, and
 * back from a song lands on Home rather than on the page the song was started from.
 */
@Composable
fun auraAnchorHero(
    heroFocus: FocusRequester,
    heroReady: Boolean = true,
) {
    val playerHolds = LocalAuraPlayerHoldsFocus.current
    val playerFocused = playerHolds?.value ?: false
    LaunchedEffect(heroReady, playerFocused) {
        if (!heroReady || playerFocused) return@LaunchedEffect
        delay(HeroAnchorSettleMs)
        var tries = 0
        while (tries < HeroAnchorChecks && !heroFocus.requestFocusSafely()) {
            delay(HeroAnchorIntervalMs)
            tries++
        }
    }
}

/**
 * Whether the page beside the rail holds the selector, whether its now-playing banner does, and
 * whether the full-screen player does. The rail re-anchors when none of those three — or the rail
 * itself — has it: a node outside them draws no focus ring, so the remote looks dead even though the
 * composition still holds a selection.
 */
val LocalAuraPageHoldsFocus = compositionLocalOf<MutableState<Boolean>?> { null }
val LocalAuraTransportHoldsFocus = compositionLocalOf<MutableState<Boolean>?> { null }
val LocalAuraPlayerHoldsFocus = compositionLocalOf<MutableState<Boolean>?> { null }

/**
 * The anchor a page puts on the element the rail should land on. Safe to call when no shell provides
 * a sink — the requester is then simply unused.
 *
 * An explicit anchor goes to the front of the list: a hero tile names itself, and it wins over
 * whichever row happened to compose first.
 */
@Composable
fun auraPageFocusAnchor(): FocusRequester {
    val sink = LocalAuraPageFocus.current
    val requester = remember { FocusRequester() }
    if (sink != null) {
        DisposableEffect(sink, requester) {
            sink.publish(requester, best = true)
            onDispose { sink.revoke(requester) }
        }
    }
    return requester
}

/**
 * A focused element offers itself as the rail's RIGHT target and joins the page's list of landing
 * spots, in composition order.
 *
 * Aura screens name their own hero with [auraPageFocusAnchor], but the Material screens this app still
 * carries — settings, stats, history, a playlist's rows — never did, so DPAD_RIGHT from the rail had
 * nowhere to go. Compose then answered the key itself and walked the selector into the page header's
 * back button, where one ENTER popped the page. Teaching every legacy screen the convention a screen
 * at a time would take as long as the screens, so the offer rides on the two modifiers every TV
 * element already wears for its focus ring.
 */
@Composable
fun Modifier.auraClaimPageFocus(best: Boolean = false): Modifier {
    val sink = auraAnchorSink() ?: return this
    val requester = remember { FocusRequester() }
    // Not best by default: a named anchor has to outrank an accidental one, and a settings list
    // composes its rows top to bottom, so an offer added at the front leaves the last row — the
    // bottom of the page — holding the landing spot the shell aims at on entry.
    auraOfferAnchor(sink, requester, best = best)
    return then(auraAnchorTarget(requester)).then(auraAnchorWatch(sink, requester))
}

/**
 * Offer a requester the caller already owns to the rail, instead of minting a second one.
 *
 * `Modifier.focusRequester` puts its own focus target under the node, so two of them on one node
 * split it in two: the rail lands on the first and the ring's focus listener watches the second,
 * which leaves the selector invisible on exactly the tiles a row nav aims at.
 */
@Composable
fun Modifier.auraClaimRequester(requester: FocusRequester, best: Boolean = false): Modifier {
    val sink = auraAnchorSink() ?: return this
    auraOfferAnchor(sink, requester, best)
    return then(auraAnchorWatch(sink, requester))
}

/**
 * Watch a requester the page has already offered, without offering it again.
 *
 * Two live offers of one requester arm its revoke twice, and whichever effect disposes first drops
 * the anchor out from under the rail.
 */
@Composable
fun Modifier.auraTrackRequester(requester: FocusRequester): Modifier {
    val sink = auraAnchorSink() ?: return this
    return then(auraAnchorWatch(sink, requester))
}

/**
 * Put [requester] on the node's chain as the rail's landing target.
 *
 * Remembered, and that is the whole point of it being a separate function: a modifier element that
 * is a fresh instance every recomposition compares unequal, so the node re-applies its chain. On
 * this device a screenful of tiles re-applying at once cost more than the splash animation it
 * interrupted. A remembered element compares by reference and changes nothing.
 */
@Composable
private fun auraAnchorTarget(requester: FocusRequester): Modifier =
    remember(requester) { Modifier.focusRequester(requester) }

/** The focus listener that keeps the page's offer list honest, stable across recomposition. */
@Composable
private fun auraAnchorWatch(sink: AuraPageAnchors, requester: FocusRequester): Modifier =
    remember(sink, requester) {
        Modifier.onFocusEvent { state -> sink.reportFocus(requester, state.hasFocus) }
    }

/** The page's landing list, or null when this node is not page content inside the TV shell. */
@Composable
private fun auraAnchorSink(): AuraPageAnchors? =
    if (LocalAuraPageContent.current && LocalAuraTvShell.current) LocalAuraPageFocus.current else null

@Composable
private fun auraOfferAnchor(
    sink: AuraPageAnchors,
    requester: FocusRequester,
    best: Boolean,
) {
    DisposableEffect(sink, requester) {
        sink.publish(requester, best = best)
        onDispose { sink.revoke(requester) }
    }
}

/**
 * [FocusRequester.requestFocus] returns nothing and reports a miss — the node is not attached, or it
 * cannot take focus — as an exception. Swallow it and answer with whether the selector moved.
 */
fun FocusRequester.requestFocusSafely(): Boolean = try {
    requestFocus()
    true
} catch (_: Exception) {
    false
}

/** Mock px -> dp. */
@Composable
fun Int.apx(): Dp = (this * LocalAuraScale.current).dp

/** Mock px -> sp. */
@Composable
fun Int.asp(): TextUnit = (this * LocalAuraScale.current).sp

@Composable
fun Float.apx(): Dp = (this * LocalAuraScale.current).dp

@Composable
fun Float.asp(): TextUnit = (this * LocalAuraScale.current).sp

// ------------------------------------------------------------------ palette ---

/** Every literal colour in the reference, named by what it paints. */
object AuraMock {
    val Page = Color(0xFF141218)
    val Panel = Color(0xFF211F26)
    val Border = Color(0xFF4A4458)
    val Text = Color(0xFFE6E0E9)
    val Muted = Color(0xFFCAC4D0)
    val Primary = Color(0xFFD0BCFF)
    val OnPrimary = Color(0xFF381E72)
    val FocusFill = Color(0x26D0BCFF) // rgba(208,188,255,.15)
    val PillOutline = Color(0xFFEADDFF)
    val Scrim = Color(0x66000000)
}

// ------------------------------------------------------------------- type ---

/** The reference imports Roboto 400/500/700/900; Android's default family is Roboto. */
val AuraRoboto = FontFamily.Default

@Composable
fun auraType(weight: FontWeight, px: Int, trackingEm: Float = 0f): TextStyle = TextStyle(
    fontFamily = AuraRoboto,
    fontWeight = weight,
    fontSize = px.asp(),
    letterSpacing = (trackingEm * px).asp(),
)

/** One line of text sized like the Tailwind class it replaces: text-5xl is 48px, font-bold is 700. */
@Composable
fun AuraText(
    value: String,
    px: Int,
    weight: FontWeight = FontWeight.Normal,
    color: Color = AuraMock.Text,
    trackingEm: Float = 0f,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    modifier: Modifier = Modifier,
) {
    Text(
        text = value,
        style = auraType(weight, px, trackingEm),
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        modifier = modifier,
    )
}

// ------------------------------------------------------------------ focus ---

private val MockEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** A radius big enough that Canvas clamps it into the reference's `rounded-full`. */
val FullRound = 9_999.dp

/**
 * The reference's `.focusable.focused`: 4px outline at 2px offset, a 15% primary fill, the element
 * lifted to 1.05 and a drop shadow. Reports focus through [onFocusChanged] so the caller can
 * recolour its own glyph.
 *
 * `Modifier.clip` clips anything applied after it, so this must sit outside the clip in the chain.
 * Pill buttons pass `ring = false` because `.pill-btn.focused` recolours the same outline.
 * Shell chrome passes `claim = false` so the rail never lands its key on a back button.
 * The player passes `shadow = false, grow = false`: over the blurred artwork backdrop the lift and
 * the drop shadow read as a smudge rather than a highlight, and the disc swap already shows focus.
 * The row a list wants aimed at passes `best = true`, so a press from the header lands there rather
 * than on whichever node Compose's geometric search happens to prefer.
 */
@Composable
fun Modifier.auraFocused(
    corner: Dp = FullRound,
    fill: Boolean = true,
    grow: Boolean = true,
    ring: Boolean = true,
    claim: Boolean = true,
    best: Boolean = false,
    shadow: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
): Modifier {
    val view = LocalView.current
    val density = LocalDensity.current
    val ringPx = with(density) { 4.apx().toPx() }
    val offsetPx = with(density) { 2.apx().toPx() }
    val shadowPx = with(density) { 8.apx().toPx() }

    val focused = remember { mutableStateOf(false) }
    val lift = remember { Animatable(1f, 0f) }
    // The caller's callback is usually an inline lambda, so it is a new instance every time the
    // caller recomposes. Held in state rather than captured, or remembering the chain below would
    // have to key on it and rebuild anyway.
    val notify = rememberUpdatedState(onFocusChanged)

    // A modifier built here is a value the caller already holds, so restarting this scope cannot hand
    // it a new chain. Everything the selector shows therefore has to be read where Compose re-runs
    // it anyway — the draw lambda, the elevation lambda and a flow outside composition. Read in the
    // body instead, the ring, the wash and the lift stay invisible on every element whose caller does
    // not watch focus and recompose itself, which is most of the shelf tiles and hero cards.
    LaunchedEffect(Unit) {
        snapshotFlow { focused.value }.collect { hasFocus ->
            val target = if (hasFocus && grow && !view.isInTouchMode) 1.05f else 1f
            // Only the element being moved onto animates. Measured on a press: 10-18 lift runs, each
            // 207-423ms for a 200ms tween, still running ~1s after a burst, because every ancestor
            // layer of both the old and the new focus target animated its way back down.
            if (target == 1f) lift.snapTo(1f) else lift.animateTo(target, tween(durationMillis = 200, easing = MockEasing))
        }
    }

    // Every element is remembered for the same reason: an inline lambda is a new instance per
    // recomposition, a new instance compares unequal, and an unequal element makes the node rebuild
    // its chain. The home feed wears this on every tile, so one recomposition of the feed re-applied
    // the lot - the passes the vendor's frame monitor timed at 0.4 to 1.7s during a cold start.
    val anchor = if (claim) Modifier.auraClaimPageFocus(best = best) else Modifier
    val liftLayer = remember(view, lift, focused, shadowPx, shadow) {
        Modifier.graphicsLayer {
            scaleX = lift.value
            scaleY = lift.value
            shadowElevation = if (shadow && focused.value && !view.isInTouchMode) shadowPx else 0f
        }
    }
    val watch = remember(view, focused, notify) {
        Modifier.onFocusEvent { state: FocusState ->
            focused.value = state.hasFocus
            notify.value(state.hasFocus && !view.isInTouchMode)
        }
    }
    val selector = remember(density, view, focused, fill, ring, ringPx, offsetPx, corner) {
        Modifier.drawWithContent {
            drawContent()
            if (focused.value && !view.isInTouchMode) {
                // Drawn over the content, not as a background modifier: every tile paints its own
                // opaque panel first, which would swallow the wash.
                if (fill) {
                    drawRoundRect(
                        color = AuraMock.FocusFill,
                        cornerRadius = CornerRadius(with(density) { corner.toPx() }),
                    )
                }
                if (ring) drawAuraRing(AuraMock.Primary, ringPx, offsetPx, corner, density)
            }
        }
    }

    return this
        .then(anchor)
        .then(liftLayer)
        .then(watch)
        .then(selector)
}

/**
 * The reference's `.pill-btn.focused`: the button fills primary, the outline becomes #EADDFF.
 * Pair with [AuraPillGlyphColor] so the glyph flips to on-primary at the same time.
 * [ring] can be dropped, leaving the fill and the glyph flip.
 */
@Composable
fun Modifier.auraPillFocused(
    focused: Boolean,
    corner: Dp = FullRound,
    ring: Boolean = true,
): Modifier {
    val density = LocalDensity.current
    val ringPx = with(density) { 4.apx().toPx() }
    val shape = remember(corner) { RoundedCornerShape(corner) }
    return this
        .then(if (focused) Modifier.background(AuraMock.Primary, shape) else Modifier)
        .drawWithContent {
            drawContent()
            if (ring && focused) drawAuraRing(AuraMock.PillOutline, ringPx, 0f, corner, density)
        }
}

@Composable
fun AuraPillGlyphColor(focused: Boolean, rest: Color = Color.White): Color =
    if (focused) AuraMock.OnPrimary else rest

private fun DrawScope.drawAuraRing(color: Color, strokeWidth: Float, offset: Float, corner: Dp, density: Density) {
    val radiusPx = with(density) { corner.toPx() }
    drawRoundRect(
        color = color,
        topLeft = Offset(-offset, -offset),
        size = Size(size.width + offset * 2f, size.height + offset * 2f),
        cornerRadius = CornerRadius(radiusPx + offset),
        style = Stroke(width = strokeWidth),
    )
}

/** Content colour that flips to on-primary while a pill button holds focus. */
@Composable
fun AuraPillScope(focused: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalContentColor provides if (focused) AuraMock.OnPrimary else LocalContentColor.current,
    ) { content() }
}

// ---------------------------------------------------------------- metrics ---

/** Sizes straight off the reference, in mock px. */
object AuraPx {
    const val RailCollapsed = 96
    const val RailExpanded = 288
    const val RailIcon = 44
    const val RailLogo = 48
    const val RailLogoGlyph = 34
    const val PagePadding = 48
    const val HeroHeight = 450
    const val HeroRadius = 32
    const val HeroPlay = 80
    const val PickCard = 288
    const val PickCardRadius = 28
    const val MiniPlayerHeight = 88

    /** The floating bar clears the viewport edge and the page footer reserves the same gap. */
    const val MiniPlayerBottomMargin = 28
    const val NowPlayingBannerHeight = 104
    const val NowPlayingBannerGutter = 32
    const val NowPlayingBannerCover = 72
    const val NowPlayingBannerControl = 72

    /**
     * The page header's full height: 16 above the 96px control plus 16 below it. Screens that paint
     * their body under the header reserve exactly this, so it is the number the shell's top inset uses.
     */
    const val PageHeader = 128
    const val NowPlayingArt = 600
    const val NowPlayingArtLyrics = 500
    const val NowPlayingArtRadius = 48
    const val ControlBarHeight = 192
    const val PlayButton = 96
    const val ProgressTrack = 12
    const val ProgressThumb = 24
    const val LyricsPanelWidth = 550
    const val LyricsPanelRadius = 48
}

@Composable
fun auraPagePadding(): Dp = AuraPx.PagePadding.apx()

@Composable
fun Modifier.auraPage(): Modifier = this.padding(auraPagePadding())
