/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.utils

import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/**
 * Diagnostic instrumentation for the Spotify WebView login.
 *
 * **Every line carries the logcat tag [TAG] (`brokenLogin`)**, so a capture reduces to just this
 * with `adb logcat -s brokenLogin`.
 *
 * ### Why this exists
 * The login WebView renders black while still reporting `onPageFinished`. That combination is
 * invisible to the current code: the `WebViewClient` only implements `onPageStarted`,
 * `onPageFinished` and `shouldOverrideUrlLoading`, so HTTP errors on the main frame, SSL failures
 * and — most importantly — a dead renderer process all pass unnoticed, and there is no
 * `WebChromeClient` at all, so the page's own JavaScript errors never surface. A black view with
 * no diagnostics is unfalsifiable; this makes the failure state observable.
 *
 * ### Redaction
 * Logs must stay safe to paste into a public issue. Cookie values, tokens and anything that
 * authenticates the user go through [redact], which prints a length and a short SHA-1 prefix —
 * enough to tell present/absent/changed apart without disclosing the secret. Cookie *names* are
 * printed in full, because "which cookies exist" is the diagnosis and the names are not secret.
 */
object BrokenLogin {
    const val TAG = "brokenLogin"

    /**
     * Always on, including in release builds.
     *
     * Login failures happen on the TV, in a signed release, and the only way to see one is through
     * logcat — so a debug-only gate meant the traces that would have explained the black screen and
     * the "Something went wrong" page were never emitted on the device where they occurred. Nothing
     * here is safe to publish otherwise, which is why every secret goes through [redact] and only
     * cookie *names* are printed in full; a capture is still fine to paste into an issue.
     */
    @JvmField
    val ENABLED: Boolean = true

    private val seq = AtomicLong(0)

    /** Short correlation id, e.g. `login-3`. Prefix every line of one login attempt with it. */
    fun nextId(prefix: String): String = "$prefix-${seq.incrementAndGet()}"

    // ── Emit ─────────────────────────────────────────────────────────────────────────────

    fun d(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).d(line(id, op, details))
    }

    fun i(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).i(line(id, op, details))
    }

    fun w(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).w(line(id, op, details))
    }

    fun e(id: String, op: String, details: String = "") {
        if (ENABLED) Timber.tag(TAG).e(line(id, op, details))
    }

    /**
     * Log a throwable with its full cause chain **and** stack trace. Use at every catch site,
     * including ones that deliberately continue — a swallowed exception is precisely what makes a
     * black screen impossible to diagnose.
     */
    fun fail(id: String, op: String, t: Throwable, details: String = "") {
        if (!ENABLED) return
        Timber.tag(TAG).e(line(id, op, "$details chain=${chain(t)}"))
        Timber.tag(TAG).e(t, line(id, "$op.stack", ""))
    }

    private fun line(id: String, op: String, details: String): String =
        if (details.isEmpty()) "$id $op" else "$id $op $details"

    // ── Formatting ───────────────────────────────────────────────────────────────────────

    fun kv(vararg pairs: Pair<String, Any?>): String =
        pairs.joinToString(" ") { (k, v) -> "$k=${render(v)}" }

    private fun render(v: Any?): String = when (v) {
        null -> "-"
        is String -> if (v.isEmpty()) "\"\"" else if (' ' in v) "\"$v\"" else v
        else -> v.toString()
    }

    fun chain(t: Throwable?): String =
        generateSequence(t) { it.cause.takeIf { c -> c !== it } }
            .take(12)
            .joinToString(" <- ") { "${it.javaClass.simpleName}:${it.message ?: "-"}" }

    fun redact(secret: String?): String {
        if (secret == null) return "absent"
        if (secret.isEmpty()) return "EMPTY(len=0)"
        return try {
            val digest = MessageDigest.getInstance("SHA-1").digest(secret.toByteArray())
            val hex = digest.take(4).joinToString("") { "%02x".format(it) }
            "len=${secret.length},sha1=$hex"
        } catch (t: Throwable) {
            "len=${secret.length},sha1=unavailable"
        }
    }

    /**
     * Cookie *names* only, with each value redacted. The question a login trace has to answer is
     * "did `sp_dc` ever arrive", and that is a question about names.
     */
    fun describeCookies(raw: String?): String {
        if (raw.isNullOrBlank()) return "cookies=NONE"
        val pairs = raw.split(";").mapNotNull {
            val parts = it.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        val names = pairs.joinToString(",") { it.first }
        val spDc = pairs.firstOrNull { it.first == "sp_dc" }?.second
        return kv("cookieCount" to pairs.size, "names" to names, "sp_dc" to redact(spDc))
    }

    /**
     * Truncate a URL for logging while keeping host and path intact — and with the values of any
     * credential-shaped query parameter gone.
     *
     * This tag stays on in release, where the only reader is `adb logcat` on someone else's TV, and
     * the navigation URLs are exactly the ones that carry secrets: the redirect back to
     * `open.spotify.com` puts a one-time authorization `code` in its query, and the login SPA puts
     * Spotify's `flow_ctx` there. Host and path are the diagnosis; the values never are.
     */
    fun shortUrl(url: String?, max: Int = 120): String {
        if (url == null) return "-"
        val safe = SENSITIVE_QUERY.replace(url) { it.groupValues[1] + it.groupValues[2] + "=<redacted>" }
        return if (safe.length <= max) safe else safe.take(max) + "...(${safe.length})"
    }

    private val SENSITIVE_QUERY = Regex(
        "([?&])(flow_ctx|flowCtx|flow_context|request_context|requestContext|recaptchaToken|code|otp|totp|pin|" +
            "password|email|username|login_hint|sp_dc|sid|state|token|access_token|authorization_code)=([^&#]*)",
        RegexOption.IGNORE_CASE,
    )

    // ── Trapping ─────────────────────────────────────────────────────────────────────────

    inline fun <T> trap(id: String, op: String, block: () -> T): T? =
        try {
            block()
        } catch (t: Throwable) {
            fail(id, op, t)
            null
        }

    inline fun <T> timed(id: String, op: String, block: () -> T): T {
        val startedAt = System.nanoTime()
        try {
            val result = block()
            d(id, "$op.done", kv("ms" to (System.nanoTime() - startedAt) / 1_000_000))
            return result
        } catch (t: Throwable) {
            fail(id, "$op.threw", t, kv("ms" to (System.nanoTime() - startedAt) / 1_000_000))
            throw t
        }
    }
}
