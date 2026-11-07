/**
 * Aura Music — the reference `aura_splash_screen.html`.
 *
 * The reveal is the whole point of that file, so it is reproduced stage for stage: the wordmark's
 * outline is drawn on by an animated stroke, the Primary fill floods in behind it while the stroke
 * thins away and a glow expands, MUSIC slides up into the descenders, and the group keeps floating
 * on the reference's 4s loop until the panel leaves.
 *
 * Two of the reference's CSS effects have no cheap equivalent here, so they are substituted for:
 * the dash running along each glyph path becomes a left-to-right clip over the stroked wordmark,
 * and `drop-shadow(0 0 45px)` becomes a stack of the wordmark stroked at widening widths, which
 * dilates the outline the way a blur does. Neither substitution is only about cost — `Modifier.blur`
 * is a `RenderEffect`, which this TV's API level ignores outright, so a blurred copy produced no
 * pixels at all while still costing a render pass.
 *
 * Why this paints on its own thread: the reveal is 1.6s long and a cold start does not hand the main
 * thread back for about 6s — the launch measured 2 frames in the first 6 seconds. A Compose animation
 * cannot draw more frames than that thread is given, so the timeline was not a slow reveal but six
 * stills spread over six seconds. A SurfaceView buffer is owned by its own thread, so the mark is
 * laid out once into bitmaps and blitted at 30fps regardless of what the app is busy booting.
 * [AuraSplashCompose] is the original Compose implementation, kept as the fallback for the case where
 * the surface yields no canvas.
 */

package com.aura.music.ui.aura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * The reference's `drawStroke 2s`, `fillAndGlow 1.5s ... 1.5s` and `slideUpFade 1s ... 2.5s`, run at
 * 0.35x. The proportions — stroke first, fill overlapping its tail, subtitle last, then hold and out —
 * are kept so it reads as the same reveal, faster.
 */
private const val SplashLeadMs = 40L
private const val SplashStrokeMs = 700
private const val SplashFillAtMs = 520L
private const val SplashFillMs = 520
private const val SplashSubAtMs = 880L
private const val SplashSubMs = 350
private const val SplashHoldMs = 180L
private const val SplashOutMs = 180

/** Lead + the last stage (subtitle in) + hold + out. */
private const val SplashTotalMs = SplashLeadMs + SplashSubAtMs + SplashSubMs + SplashHoldMs + SplashOutMs

/** Where the veil starts, counted from the first frame of the reveal. */
private const val SplashOutAtMs = SplashSubAtMs + SplashSubMs + SplashHoldMs

/**
 * 30fps rather than the panel's 60: a frame here is a 1920x1080 software buffer, and the reveal
 * spends one core on it while cold start spends another on the shell. Half the work per second for
 * the same 1.6s, and a cadence nobody can tell from smooth at three metres.
 */
private const val SplashFrameMs = 1000L / 30L

/**
 * How long after the timeline the panel leaves even if the renderer never reported back. A surface
 * that is created but never delivers a buffer would otherwise cover the app with an invisible sheet
 * for the rest of the process.
 */
private const val SplashWatchdogMs = 2500L

private const val SplashTag = "AuraSplash"

private const val Word = "AURA"
private const val Sub = "MUSIC"

/** `font-size: 160px` on a panel this size, `stroke-width: 4px`, `drop-shadow 0 0 45px`. */
private const val SplashWordPx = 176
private const val SplashSubPx = 44
private const val SplashStrokePx = 5f
private const val SplashWordTrackingEm = 0.1f
private const val SplashSubTrackingEm = 0.5f

/** `-mt-10` parks MUSIC in the wordmark's descenders; `slideUpFade` lifts it the last 20px. */
private const val SplashOverlapPx = 40f
private const val SplashSubRisePx = 20f

/**
 * Blurring a solid shape leaves alpha `0.7 * Phi(-s / 22.5)` at distance `s` from the outline (CSS
 * blur radius = 2 sigma), and a point covered by passes 1..j composites to `1 - Product(1 - a)`, so
 * each pass's alpha is `(1 - T(s_j)) / (1 - T(s_j+1))`. That is what these eight half-widths and
 * alphas are; three passes were visibly three rings, and widening the stack further buys bands
 * nobody sees.
 *
 * The surface path draws all eight once into a single bitmap, because their only animated property
 * is the group alpha the fill stage runs them through.
 */
private val SplashGlowPasses = listOf(
    6f to 0.065f,
    14f to 0.072f,
    24f to 0.070f,
    36f to 0.060f,
    50f to 0.046f,
    66f to 0.029f,
    84f to 0.015f,
    104f to 0.007f,
)

/** `floatMove 4s ease-in-out infinite`: `translateY(-15px) scale(1.02)`. */
private const val SplashFloatMs = 4000
private const val SplashBobPx = 15f
private const val SplashBobScale = 0.02f

/** Every keyframe in the reference runs on `cubic-bezier(0.25, 1, 0.5, 1)`. */
private val SplashEasing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
private val SplashFloatEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Set once the reveal has played, so the animation is a cold-start event rather than something the
 * shell re-runs on every recomposition or navigation. A process-level flag is what that takes: it
 * survives the activity's own state and is cleared only when the process actually dies.
 */
val AuraSplashSeen = mutableStateOf(false)

/**
 * Sizes and colours resolved into device pixels while the composition still owns them: the Aura
 * scale and the density are what turn mock px into something a [Paint] can use, and the render
 * thread has access to neither.
 */
private class SplashSpec(
    val wordPx: Float,
    val subPx: Float,
    val apx: Float,
    val pageArgb: Int,
    val primaryArgb: Int,
    val mutedArgb: Int,
)

@Composable
fun AuraSplash(onFinished: () -> Unit) {
    val density = LocalDensity.current
    val scale = LocalAuraScale.current
    val spec = remember(density, scale) {
        with(density) {
            SplashSpec(
                wordPx = (SplashWordPx * scale).sp.toPx(),
                subPx = (SplashSubPx * scale).sp.toPx(),
                apx = (1f * scale).dp.toPx(),
                pageArgb = AuraMock.Page.toArgb(),
                primaryArgb = AuraMock.Primary.toArgb(),
                mutedArgb = AuraMock.Muted.toArgb(),
            )
        }
    }

    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onFinished()
        }
    }
    var surfaceDead by remember { mutableStateOf(false) }

    // The renderer runs on a thread nobody else can see the end of, so the panel also leaves on a
    // timer. Generous enough that a fallback reveal started late still finishes on its own.
    LaunchedEffect(Unit) {
        delay(SplashTotalMs + SplashWatchdogMs)
        finish()
    }

    if (surfaceDead) {
        AuraSplashCompose(finish)
    } else {
        AndroidView(
            factory = { context ->
                SplashRevealView(
                    context = context,
                    spec = spec,
                    onFinish = finish,
                    onFailed = { surfaceDead = true },
                )
            },
            modifier = Modifier.fillMaxSize(),
            onRelease = { view -> view.shutdown() },
        )
    }
}

// ------------------------------------------------------------- surface path ---

/** One frame's worth of eased progress, read off the clock rather than off animated values. */
private class SplashFrameState(
    val draw: Float,
    val fill: Float,
    val sub: Float,
    val veil: Float,
    val bob: Float,
)

private fun eased(
    easing: Easing,
    elapsedMs: Long,
    at: Long,
    duration: Long,
): Float = easing.transform(((elapsedMs - at).toFloat() / duration.toFloat()).coerceIn(0f, 1f))

private fun splashFrameAt(elapsedMs: Long): SplashFrameState {
    val e = max(0L, elapsedMs - SplashLeadMs)
    return SplashFrameState(
        draw = eased(SplashEasing, e, 0L, SplashStrokeMs.toLong()),
        fill = eased(SplashEasing, e, SplashFillAtMs, SplashFillMs.toLong()),
        sub = eased(SplashEasing, e, SplashSubAtMs, SplashSubMs.toLong()),
        veil = 1f - eased(SplashEasing, e, SplashOutAtMs, SplashOutMs.toLong()),
        bob = eased(SplashFloatEasing, e, 0L, SplashFloatMs.toLong()),
    )
}

private fun glyphPaint(
    color: Int,
    sizePx: Float,
    trackingEm: Float,
    family: String,
): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(family, Typeface.NORMAL)
        textSize = sizePx
        letterSpacing = trackingEm
        this.color = color
    }

/**
 * The wordmark and its subtitle, laid out once into a bitmap per animated layer.
 *
 * Laying text out is the expensive half of a frame — glyph placement for a 200px black weight, plus
 * eight stroked copies of it for the neon — and none of it changes, so it happens here rather than on
 * the render thread's clock. The three wordmark layers share one geometry and blit into the same
 * rect, which is what keeps the stroke, the fill and the glow in register.
 */
private class SplashAssets(
    val spec: SplashSpec,
) {
    val wordW: Float
    val wordLineH: Float
    val subW: Float
    val subLineH: Float
    val padWord: Float
    val padSub: Float
    val glow: Bitmap
    val stroke: Bitmap
    val fill: Bitmap
    val subtitle: Bitmap

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    init {
        val apx = spec.apx
        val word = glyphPaint(spec.primaryArgb, spec.wordPx, SplashWordTrackingEm, "sans-serif-black")
        val sub = glyphPaint(spec.mutedArgb, spec.subPx, SplashSubTrackingEm, "sans-serif-medium")
        val fm = Paint.FontMetrics()
        val subFm = Paint.FontMetrics()

        wordW = ceil(word.measureText(Word))
        word.getFontMetrics(fm)
        wordLineH = ceil(fm.descent - fm.ascent)
        // The widest neon pass reaches half its width outside the letterforms. Roboto's ascent sits
        // well above the cap height, so the line box itself needs no extra room.
        padWord = ceil(SplashGlowPasses.last().first * apx / 2f + 8f * apx)

        subW = ceil(sub.measureText(Sub))
        sub.getFontMetrics(subFm)
        subLineH = ceil(subFm.descent - subFm.ascent)
        padSub = ceil(8f * apx)

        val w = (wordW + 2f * padWord).toInt()
        val h = (wordLineH + 2f * padWord).toInt()
        val left = padWord
        val baseline = padWord - fm.ascent

        glow = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pass = glyphPaint(spec.primaryArgb, spec.wordPx, SplashWordTrackingEm, "sans-serif-black")
        pass.style = Paint.Style.STROKE
        pass.strokeCap = Paint.Cap.ROUND
        pass.strokeJoin = Paint.Join.ROUND
        Canvas(glow).let { canvas ->
            SplashGlowPasses.forEach { (widthPx, alpha) ->
                pass.strokeWidth = widthPx * apx
                pass.alpha = (alpha * 255f).toInt()
                canvas.drawText(Word, left, baseline, pass)
            }
        }

        pass.alpha = 255
        pass.strokeWidth = SplashStrokePx * apx
        stroke = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(stroke).drawText(Word, left, baseline, pass)

        fill = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(fill).drawText(Word, left, baseline, word)

        subtitle = Bitmap.createBitmap(
            (subW + 2f * padSub).toInt(),
            (subLineH + 2f * padSub).toInt(),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(subtitle).drawText(Sub, padSub, padSub - subFm.ascent, sub)
    }

    fun recycle() {
        glow.recycle()
        stroke.recycle()
        fill.recycle()
        subtitle.recycle()
    }

    /**
     * Paint one frame over the whole surface. Everything below is a blit: the group's float, the
     * wordmark's grow and the stroke's left-to-right reveal are a layer transform and a clip, which
     * is what lets this run on a core that is otherwise busy booting the app.
     *
     * The full-surface page fill is not the cost and dirty-rect updates were measured not to be
     * either: on a 1920x1080 buffer the fill is a few ms of memset, while the four scaled, alpha-
     * blended glyph blits are ~29ms of CPU. Painting only the mark's box traded that fill for the
     * compositor having to merge each buffer with the last, and the frame rate did not move.
     */
    fun draw(
        canvas: Canvas,
        state: SplashFrameState,
    ) {
        canvas.drawColor(spec.pageArgb)
        val apx = spec.apx
        val cx = canvas.width / 2f
        val cy = canvas.height / 2f
        val contentH = wordLineH + subLineH - SplashOverlapPx * apx
        val wordTop = cy - contentH / 2f
        val subTop = wordTop + wordLineH - SplashOverlapPx * apx
        val wordLeft = cx - wordW / 2f

        canvas.save()
        canvas.scale(1f + SplashBobScale * state.bob, 1f + SplashBobScale * state.bob, cx, cy)
        canvas.translate(0f, -SplashBobPx * apx * state.bob)

        val wordCenterY = wordTop + wordLineH / 2f
        canvas.save()
        canvas.scale(0.95f + 0.05f * state.draw, 0.95f + 0.05f * state.draw, cx, wordCenterY)
        blit(canvas, glow, wordLeft, wordTop, padWord, state.fill * state.veil)
        blit(canvas, fill, wordLeft, wordTop, padWord, state.fill * state.veil)
        val strokeAlpha = (1f - state.fill) * state.veil
        val revealed = stroke.width * state.draw
        if (strokeAlpha > 0f && revealed > 0f) {
            canvas.save()
            canvas.clipRect(
                wordLeft - padWord,
                wordTop - padWord,
                wordLeft - padWord + revealed,
                wordTop - padWord + stroke.height,
            )
            blit(canvas, stroke, wordLeft, wordTop, padWord, strokeAlpha)
            canvas.restore()
        }
        canvas.restore()

        blit(
            canvas,
            subtitle,
            cx - subW / 2f,
            subTop + SplashSubRisePx * apx * (1f - state.sub),
            padSub,
            state.sub * state.veil,
        )
        canvas.restore()
    }

    private fun blit(
        canvas: Canvas,
        bitmap: Bitmap,
        textLeft: Float,
        textTop: Float,
        pad: Float,
        alpha: Float,
    ) {
        if (alpha <= 0f) return
        paint.alpha = min(255, (alpha * 255f).toInt())
        val left = textLeft - pad
        val top = textTop - pad
        dst.set(left, top, left + bitmap.width, top + bitmap.height)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }
}

/**
 * The panel. Its only job is to hand [SplashRevealThread] a surface and take the thread down with the
 * view, including when the activity goes away mid-reveal.
 */
private class SplashRevealView(
    context: Context,
    private val spec: SplashSpec,
    private val onFinish: () -> Unit,
    private val onFailed: () -> Unit,
) : SurfaceView(context),
    SurfaceHolder.Callback {
    private val main = Handler(Looper.getMainLooper())
    private var thread: SplashRevealThread? = null

    init {
        // The reveal paints its own opaque page, and the shell underneath is what the surface hole
        // hides, so the buffer carries no alpha channel.
        holder.setFormat(PixelFormat.OPAQUE)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        if (thread != null) return
        SplashRevealThread(holder, spec, onFinish, onFailed, main).also {
            thread = it
            it.start()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.cancel()
        thread = null
    }

    fun shutdown() {
        holder.removeCallback(this)
        thread?.cancel()
        thread = null
    }
}

/**
 * The clock, the buffers and the frames.
 *
 * `lockCanvas` hands back a buffer this thread writes into directly, so the cadence is set by how
 * fast the blits go and not by how busy the main thread is — which is the entire reason for doing it
 * this way. Nothing here touches a Compose snapshot.
 */
private class SplashRevealThread(
    private val holder: SurfaceHolder,
    private val spec: SplashSpec,
    private val onFinish: () -> Unit,
    private val onFailed: () -> Unit,
    private val main: Handler,
) : Thread("AuraSplashReveal") {
    @Volatile
    private var cancelled = false

    fun cancel() {
        cancelled = true
        interrupt()
    }

    override fun run() {
        // Cold start is a CPU fight, and the main thread is the heavier fighter. At default priority
        // this thread was scheduled for ~10 slices a second, which is a slideshow no matter how fast
        // the blits are. URGENT_DISPLAY is what the system's own surface animation threads ask for.
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
        } catch (_: Throwable) {
        }
        var assets: SplashAssets? = null
        var frames = 0
        val startedAt = System.nanoTime()
        try {
            // Flat page first, so the panel is never empty while the glyphs are laid out.
            val opener = lock() ?: return report(failed = true)
            opener.drawColor(spec.pageArgb)
            if (!post(opener)) return report(failed = true)

            val built = SplashAssets(spec)
            assets = built
            val t0 = System.nanoTime()
            while (!cancelled) {
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000L
                val canvas = lock() ?: break
                try {
                    built.draw(canvas, splashFrameAt(elapsedMs))
                } finally {
                    if (!post(canvas)) break
                }
                frames++
                if (elapsedMs >= SplashTotalMs) break
                val sleepMs = frames * SplashFrameMs - (System.nanoTime() - t0) / 1_000_000L
                if (sleepMs > 0) {
                    try {
                        sleep(sleepMs)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            // The one number that says whether this design is still doing its job: frames delivered
            // inside the timeline, on a box where the main thread gets two frames in six seconds.
            val elapsed = (System.nanoTime() - startedAt) / 1_000_000L
            Log.i(SplashTag, "reveal painted $frames frames in ${elapsed}ms off the main thread")
            report(failed = frames == 0)
        } catch (_: Throwable) {
            if (!cancelled) report(failed = true)
        } finally {
            assets?.recycle()
        }
    }

    private fun lock(): Canvas? =
        if (!holder.surface.isValid || cancelled) {
            null
        } else {
            try {
                holder.lockCanvas()
            } catch (_: Throwable) {
                null
            }
        }

    private fun post(canvas: Canvas): Boolean =
        try {
            holder.unlockCanvasAndPost(canvas)
            true
        } catch (_: Throwable) {
            false
        }

    /**
     * No pixels at all is the one case where the Compose implementation is worth its cost: it replays
     * the timeline a beat late, but it plays.
     */
    private fun report(failed: Boolean) {
        main.post {
            if (failed) onFailed() else onFinish()
        }
    }
}

// ------------------------------------------------------------- compose path ---

/**
 * The original implementation: the same timeline driven by [Animatable]s and drawn by Compose. It is
 * the fallback for a surface that yields nothing, and it is also the readable statement of what the
 * reveal is — every property the blit path animates is named here.
 */
@Composable
private fun AuraSplashCompose(onFinished: () -> Unit) {
    val draw = remember { Animatable(0f) }
    val fill = remember { Animatable(0f) }
    val sub = remember { Animatable(0f) }
    val veil = remember { Animatable(1f) }
    val started = remember { mutableStateOf(false) }
    // Flipped once, at the moment the fill begins, so the glow passes are not composed at all
    // during the stroke phase. Testing `fill.value` here would recompose the group every frame.
    val glowOn = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(SplashLeadMs)
        // No wait for the shell to settle: the reveal starts on the clock, and the panel is opaque
        // underneath it either way.
        started.value = true
        coroutineScope {
            launch { draw.animateTo(1f, tween(SplashStrokeMs, easing = SplashEasing)) }
            launch {
                delay(SplashFillAtMs)
                glowOn.value = true
                fill.animateTo(1f, tween(SplashFillMs, easing = SplashEasing))
            }
            launch {
                delay(SplashSubAtMs)
                sub.animateTo(1f, tween(SplashSubMs, easing = SplashEasing))
            }
        }
        delay(SplashHoldMs)
        veil.animateTo(0f, tween(SplashOutMs, easing = SplashEasing))
        onFinished()
    }

    val oneApx = with(LocalDensity.current) { 1.apx().toPx() }
    val wordStyle = auraType(FontWeight.Black, SplashWordPx, SplashWordTrackingEm)
    val strokeStyle = wordStyle.copy(
        color = AuraMock.Primary,
        drawStyle = Stroke(
            width = SplashStrokePx * oneApx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            // Opaque for the whole reveal, so the shell composing underneath costs nothing here.
            // Only the mark group fades: a full-screen alpha layer would put 1920x1080 into an
            // offscreen buffer for every frame of the exit.
            .background(AuraMock.Page),
    ) {
        // Composed one lead after the panel rather than at once, so the mark's infinite float does not
        // register a frame callback before the first reveal step is due.
        if (started.value) {
            val float = rememberInfiniteTransition(label = "floatMove")
            val bob by float.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(SplashFloatMs, easing = SplashFloatEasing), RepeatMode.Reverse),
                label = "translateY",
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = veil.value
                    translationY = -SplashBobPx * oneApx * bob
                    scaleX = 1f + SplashBobScale * bob
                    scaleY = 1f + SplashBobScale * bob
                },
            ) {
                // `drawStroke` grows the whole mark from scale(0.95) to scale(1) as it draws.
                Box(modifier = Modifier.graphicsLayer {
                    val s = 0.95f + 0.05f * draw.value
                    scaleX = s
                    scaleY = s
                }) {
                    // The neon the fill lands with, stacked behind the wordmark so the fill sits on
                    // top of it. The ramp fades as one group rather than per pass.
                    if (glowOn.value) {
                        Box(modifier = Modifier.graphicsLayer { alpha = fill.value }) {
                            SplashGlowPasses.forEach { (widthPx, passAlpha) ->
                                Text(
                                    text = Word,
                                    style = wordStyle.copy(
                                        color = AuraMock.Primary.copy(alpha = passAlpha),
                                        drawStyle = Stroke(
                                            width = widthPx * oneApx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                    // The stroke, revealed left to right as the dash offset unwinds, and thinned
                    // away (`stroke-width: 4px -> 0`) by the fill that replaces it.
                    Text(
                        text = Word,
                        style = strokeStyle,
                        modifier = Modifier
                            .graphicsLayer { alpha = 1f - fill.value }
                            .drawWithContent {
                                clipRect(right = size.width * draw.value) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                    )
                    // The fill, which only exists once fillAndGlow has begun, and the stroke only
                    // disappears as it arrives.
                    Text(
                        text = Word,
                        style = wordStyle,
                        color = AuraMock.Primary,
                        modifier = Modifier.graphicsLayer { alpha = fill.value },
                    )
                }
                Text(
                    text = Sub,
                    style = auraType(FontWeight.Medium, SplashSubPx, SplashSubTrackingEm),
                    color = AuraMock.Muted,
                    modifier = Modifier
                        // `-mt-10` parks it in the wordmark's descenders.
                        .offset(y = (-SplashOverlapPx).apx())
                        // `slideUpFade` lifts it the last 20px, as a layer move: read during
                        // composition it would re-run layout for the group on every frame.
                        .graphicsLayer {
                            alpha = sub.value
                            translationY = SplashSubRisePx * (1f - sub.value) * oneApx
                        },
                )
            }
        }
    }
}
