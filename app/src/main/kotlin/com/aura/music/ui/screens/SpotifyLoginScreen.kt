/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Spotify login using an embedded WebView.
 * Loads Spotify's web login page, which supports all auth methods
 * (email/password, Facebook, Google, Apple). After successful login,
 * the WebView redirect to open.spotify.com is intercepted and the
 * sp_dc cookie is extracted to fetch an access token.
 *
 * Token acquisition uses TOTP (Time-based One-Time Password) generated
 * from a community-maintained shared secret, following the approach used
 * by the Spotube Spotify plugin. The token is fetched entirely in the
 * background using HttpURLConnection — no web player, no rate limit issues.
 *
 * Reference: https://github.com/sonic-liberation/spotube-plugin-spotify
 */

package com.aura.music.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.aura.music.utils.BrokenLogin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.aura.music.ui.component.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.constants.SpotifyAccessTokenKey
import com.aura.music.constants.SpotifyAnonymousHomeKey
import com.aura.music.constants.SpotifySpDcKey
import com.aura.music.constants.SpotifySpKeyKey
import com.aura.music.constants.SpotifyTokenExpiryKey
import com.aura.music.constants.SpotifyUserIdKey
import com.aura.music.constants.SpotifyUsernameKey
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.LocalAuraPageFocus
import com.aura.music.ui.aura.apx
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.utils.backToMain
import com.aura.music.utils.dataStore
import com.aura.spotify.Spotify
import com.aura.spotify.SpotifyAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    val tokenFetchStarted = remember { AtomicBoolean(false) }

    // Once the token exchange fails, the retry pill is the only thing on this page a remote can act
    // on - the web view behind the panel still holds the selector, and a page whose only answer is
    // BACK reads as a hang. The pill offers itself to the rail like any shell control; the page aims
    // at the offer the moment the error lands.
    val pageAnchors = LocalAuraPageFocus.current
    LaunchedEffect(hasError, isProcessing) {
        val anchors = pageAnchors ?: return@LaunchedEffect
        if (!hasError || !isProcessing) return@LaunchedEffect
        var tries = 0
        while (tries < 8 && !anchors.anchorHoldsFocus) {
            anchors.requestFirstFocus()
            delay(140L)
            tries++
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.spotify_login)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
        )

        if (isLoading || isProcessing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = AuraMock.Primary,
                trackColor = AuraMock.Panel,
            )
        }

        // The mini-player is drawn over this screen, so the WebView has to stop above it or the
        // bottom of the sign-in form ends up unreachable.
        Box(
            modifier =
                Modifier
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    )
                    .fillMaxSize(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val bl = BrokenLogin.nextId("login")
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    // This used to wipe every cookie before loading the page. Signing out already
                    // clears them (App.forgetAccount, AccountSettings, SpotifySettings), so all this
                    // did was throw away the `sp_t`/`dt` device identity Spotify's login checks
                    // expect to already be there, and force a retyping on every visit.

                    WebView(ctx).apply {
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        /*
                         * No User-Agent override for Spotify's own pages.
                         *
                         * Chromium sends the low-entropy Client Hints — `Sec-CH-UA`,
                         * `Sec-CH-UA-Mobile`, `Sec-CH-UA-Platform` — on every HTTPS request whether
                         * or not the server asked for them with `Accept-CH` (Spotify never does), and
                         * an app cannot rewrite them before API 35. So overriding only the UA made
                         * this WebView say Windows in one header and Android in the next: the
                         * textbook signature of a WebView pretending to be a desktop browser, and
                         * exactly the inconsistency a login risk check scores. The measured proof is
                         * in the trace — the submit carried a complete 2446-character reCAPTCHA token
                         * and every cookie, and the server still answered `internal_error`, while the
                         * hints contradicted the UA on the very first document request.
                         *
                         * The engine's own string is self-consistent, so it is left alone. Facebook
                         * is the single exception, in [userAgentFor].
                         */
                        val nativeUserAgent = settings.userAgentString

                        /*
                         * The WebView implementation is a separate, auto-updating system package.
                         * "It worked days ago and now the page is black" is very often that package
                         * updating underneath us, so its version is the first thing worth knowing —
                         * and it is not recoverable from any other line in a logcat.
                         */
                        BrokenLogin.i(
                            bl, "webview.create",
                            BrokenLogin.kv(
                                "webViewPackage" to BrokenLogin.trap(bl, "webview.package") {
                                    WebView.getCurrentWebViewPackage()?.let { "${it.packageName}/${it.versionName}" }
                                },
                                "userAgent" to BrokenLogin.shortUrl(settings.userAgentString, 110),
                                "javaScript" to settings.javaScriptEnabled,
                                "domStorage" to settings.domStorageEnabled,
                                "thirdPartyCookies" to cookieManager.acceptThirdPartyCookies(this),
                                "acceptCookie" to cookieManager.acceptCookie(),
                                "mixedContent" to settings.mixedContentMode,
                                // Platform dark inversion. Applied on top of a page that is
                                // already dark, this turns text dark-on-dark — a WebView that
                                // reports everything painted while showing the user black.
                                "algorithmicDarkening" to BrokenLogin.trap(bl, "webview.darkening") {
                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                        settings.isAlgorithmicDarkeningAllowed
                                    } else {
                                        "n/a<33"
                                    }
                                },
                                "uiNightMode" to BrokenLogin.trap(bl, "webview.nightMode") {
                                    ctx.resources.configuration.uiMode and
                                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                                },
                                "loginUrl" to BrokenLogin.shortUrl(SpotifyAuth.LOGIN_URL),
                            ),
                        )

                        /*
                         * Surfaces what the page itself reports. Without a WebChromeClient a page
                         * that fails entirely in JavaScript looks identical to one that rendered
                         * fine — which is exactly the black-screen case being chased here.
                         */
                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                if (message != null) {
                                    val op = when (message.messageLevel()) {
                                        ConsoleMessage.MessageLevel.ERROR -> "console.error"
                                        ConsoleMessage.MessageLevel.WARNING -> "console.warn"
                                        else -> "console"
                                    }
                                    val details = BrokenLogin.kv(
                                        "level" to message.messageLevel(),
                                        "line" to message.lineNumber(),
                                        "source" to BrokenLogin.shortUrl(message.sourceId(), 90),
                                        "msg" to message.message(),
                                    )
                                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                                        BrokenLogin.e(bl, op, details)
                                    } else {
                                        BrokenLogin.d(bl, op, details)
                                    }
                                }
                                return true
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                // A page that stalls partway shows up here as a progress that
                                // never reaches 100 despite onPageFinished having fired.
                                if (newProgress % 25 == 0) {
                                    BrokenLogin.d(bl, "progress", BrokenLogin.kv("percent" to newProgress))
                                }
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                // An empty or error title on a "finished" page is a strong signal
                                // the document is not the login form.
                                BrokenLogin.i(bl, "title", BrokenLogin.kv("title" to title))
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                Timber.d("SpotifyLogin: page started: $url")
                                BrokenLogin.i(bl, "page.started", BrokenLogin.kv("url" to BrokenLogin.shortUrl(url)))
                                // Spotify's own submit call is the one that fails, and the WebView
                                // reports only its status code. This hook makes the page echo the
                                // response body into the console, which the WebChromeClient above
                                // already logs — the difference between "400" and "400 errorCSRF".
                                BrokenLogin.trap(bl, "net.hook") {
                                    view?.evaluateJavascript(NETWORK_HOOK_JS, null)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                // Not overridden before, so every network-level failure — including
                                // one on the main frame, which paints a blank view — was silent.
                                val isMainFrame = request?.isForMainFrame == true
                                val details = BrokenLogin.kv(
                                    "mainFrame" to isMainFrame,
                                    "code" to BrokenLogin.trap(bl, "error.code") { error?.errorCode },
                                    "desc" to BrokenLogin.trap(bl, "error.desc") { error?.description?.toString() },
                                    "method" to request?.method,
                                    "url" to BrokenLogin.shortUrl(request?.url?.toString()),
                                )
                                if (isMainFrame) BrokenLogin.e(bl, "page.error", details)
                                else BrokenLogin.w(bl, "resource.error", details)
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?,
                            ) {
                                val isMainFrame = request?.isForMainFrame == true
                                val details = BrokenLogin.kv(
                                    "mainFrame" to isMainFrame,
                                    "status" to errorResponse?.statusCode,
                                    "reason" to errorResponse?.reasonPhrase,
                                    "mime" to errorResponse?.mimeType,
                                    "url" to BrokenLogin.shortUrl(request?.url?.toString()),
                                )
                                if (isMainFrame) BrokenLogin.e(bl, "page.httpError", details)
                                else BrokenLogin.w(bl, "resource.httpError", details)
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?,
                            ) {
                                BrokenLogin.e(
                                    bl, "page.sslError",
                                    BrokenLogin.kv(
                                        "primaryError" to error?.primaryError,
                                        "url" to BrokenLogin.shortUrl(error?.url),
                                    ),
                                )
                                // Preserve the default (cancel): never weaken TLS to get a login through.
                                super.onReceivedSslError(view, handler, error)
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                /*
                                 * The prime suspect for a black WebView. When the renderer dies the
                                 * view keeps its surface and simply paints nothing; returning false
                                 * (the default) also kills the hosting app process. Log it and
                                 * return true so the app survives and the trace makes it to logcat.
                                 */
                                BrokenLogin.e(
                                    bl, "renderer.gone",
                                    BrokenLogin.kv(
                                        "didCrash" to detail?.didCrash(),
                                        "rendererPriorityAtExit" to detail?.rendererPriorityAtExit(),
                                    ),
                                )
                                BrokenLogin.trap(bl, "renderer.destroy") { view?.destroy() }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                Timber.d("SpotifyLogin: page finished: $url")
                                BrokenLogin.i(
                                    bl, "page.finished",
                                    BrokenLogin.kv(
                                        "url" to BrokenLogin.shortUrl(url),
                                        "progress" to BrokenLogin.trap(bl, "page.progress") { view?.progress },
                                        "contentHeight" to BrokenLogin.trap(bl, "page.height") { view?.contentHeight },
                                        "title" to BrokenLogin.trap(bl, "page.title") { view?.title },
                                    ) + " " + BrokenLogin.describeCookies(
                                        // The login page is served from accounts.spotify.com and its
                                        // CSRF cookies live there; reading only open.spotify.com made
                                        // every trace say `names=sp_t` while telling us nothing about
                                        // the cookies the submit actually needs.
                                        BrokenLogin.trap(bl, "page.cookies.accounts") {
                                            CookieManager.getInstance().getCookie("https://accounts.spotify.com")
                                        },
                                    ) + " " + BrokenLogin.describeCookies(
                                        BrokenLogin.trap(bl, "page.cookies.open") {
                                            CookieManager.getInstance().getCookie("https://open.spotify.com")
                                        },
                                    ),
                                )
                                // Runs on every page: the collapsed layout is re-created by each
                                // navigation within the login flow, not just the first load.
                                applyLoginLayoutFix(bl, view)
                                probeDom(bl, view)

                                if (url?.startsWith("https://open.spotify.com") == true &&
                                    tokenFetchStarted.compareAndSet(false, true)
                                ) {
                                    Timber.d("SpotifyLogin: extracting token from onPageFinished")
                                    extractAndFetchToken(
                                        view = view,
                                        context = context,
                                        scope = scope,
                                        navController = navController,
                                        setProcessing = { isProcessing = it },
                                        setStatus = { statusMessage = it },
                                        setError = { hasError = it },
                                        tokenFetchStarted = tokenFetchStarted,
                                    )
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val requestUrl = request?.url?.toString() ?: return false
                                Timber.d("SpotifyLogin: navigating to: $requestUrl")
                                BrokenLogin.i(
                                    bl, "navigate",
                                    BrokenLogin.kv(
                                        "url" to BrokenLogin.shortUrl(requestUrl),
                                        "mainFrame" to request.isForMainFrame,
                                        "redirect" to request.isRedirect,
                                    ),
                                )

                                /*
                                 * The UA is chosen per navigation, here, because this is the last
                                 * point before the document request leaves — and the document
                                 * request is where Spotify's login check reads it.
                                 */
                                BrokenLogin.trap(bl, "ua.apply") {
                                    if (view != null) {
                                        view.settings.userAgentString =
                                            userAgentFor(requestUrl, nativeUserAgent)
                                    }
                                }

                                if (requestUrl.startsWith("https://open.spotify.com")) {
                                    val spDc = extractSpDcCookie()
                                    BrokenLogin.i(
                                        bl, "redirect.toOpenSpotify",
                                        BrokenLogin.kv("spDc" to BrokenLogin.redact(spDc)),
                                    )
                                    if (spDc != null && tokenFetchStarted.compareAndSet(false, true)) {
                                        Timber.d("SpotifyLogin: sp_dc available at redirect, processing immediately")
                                        extractAndFetchToken(
                                            view = view,
                                            context = context,
                                            scope = scope,
                                            navController = navController,
                                            setProcessing = { isProcessing = it },
                                            setStatus = { statusMessage = it },
                                            setError = { hasError = it },
                                            tokenFetchStarted = tokenFetchStarted,
                                        )
                                        return true
                                    }
                                    // sp_dc not ready yet — let the page load so
                                    // onPageFinished can pick up the cookie later
                                    Timber.d("SpotifyLogin: sp_dc not ready at redirect, deferring to onPageFinished")
                                    return false
                                }

                                return false
                            }
                        }

                        loadUrl(SpotifyAuth.LOGIN_URL)
                    }
                },
            )

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AuraMock.Page),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(32.apx()),
                        modifier = Modifier.padding(horizontal = 64.apx()),
                    ) {
                        if (!hasError) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(72.apx()),
                                strokeWidth = 4.apx(),
                                color = AuraMock.Primary,
                            )
                        }
                        AuraText(
                            value = statusMessage.ifEmpty {
                                stringResource(R.string.spotify_logging_in)
                            },
                            px = 28,
                            weight = if (hasError) FontWeight.Bold else FontWeight.Medium,
                            color =
                                if (hasError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    AuraMock.Muted
                                },
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasError) {
                            AuraPillButton(
                                label = stringResource(R.string.retry),
                                icon = R.drawable.refresh,
                                filled = true,
                                onClick = {
                                    hasError = false
                                    isProcessing = false
                                    statusMessage = ""
                                    tokenFetchStarted.set(false)
                                    retryCount++
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Asks the loaded document what it actually contains.
 *
 * `onPageFinished` with `progress=100` and a non-zero `contentHeight` only proves a document
 * exists — not that the login form is in it. Spotify's login is a JavaScript app on a dark
 * background, so a shell that mounted without its form is indistinguishable from a correctly
 * rendered page from the outside, and looks like a black screen to the user. This reports whether
 * the inputs are actually there, which is the difference between "Spotify refused us" and
 * "the page is fine but something failed to mount".
 *
 * Reads structure only — element counts, sizes, readyState. No field values are touched.
 */
private fun probeDom(bl: String, view: WebView?) {
    if (!BrokenLogin.ENABLED || view == null) return

    /*
     * The page is measured in CSS pixels by Chromium; this is the Android View that has to put
     * those pixels on screen. Chromium can report a perfectly laid-out, high-contrast document
     * while the View itself is zero-sized, transparent, detached or not shown — in which case
     * nothing about the page matters.
     */
    BrokenLogin.i(
        bl, "viewState",
        BrokenLogin.kv(
            "width" to view.width,
            "height" to view.height,
            "alpha" to view.alpha,
            "visibility" to view.visibility,
            "isShown" to view.isShown,
            "attached" to view.isAttachedToWindow,
            "hwAccelerated" to view.isHardwareAccelerated,
            "layerType" to view.layerType,
            "scaleX" to view.scaleX,
            "scaleY" to view.scaleY,
            "translationY" to view.translationY,
            "parent" to (view.parent?.javaClass?.simpleName ?: "NONE"),
        ),
    )
    val js = """
        (function () {
          try {
            var b = document.body;
            var vis = 0, painted = 0, all = document.querySelectorAll('*');
            for (var i = 0; i < all.length; i++) {
              var el = all[i];
              var r = el.getBoundingClientRect();
              if (r.width > 0 && r.height > 0) {
                vis++;
                // Size alone is not visibility: an element can be laid out and still be
                // invisible. This is what separates "rendered" from "merely present".
                var cs = getComputedStyle(el);
                if (cs.visibility !== 'hidden' && cs.display !== 'none' && parseFloat(cs.opacity) > 0.01) painted++;
              }
            }
            // What is actually on top at the centre of the viewport. If this is the body or an
            // overlay rather than the form, something is covering the page.
            var mid = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
            var midDesc = mid ? (mid.tagName + (mid.id ? '#' + mid.id : '') +
                (mid.className && typeof mid.className === 'string'
                  ? '.' + mid.className.trim().split(/\s+/).slice(0, 2).join('.') : '')) : 'null';
            var inp = document.querySelector('input');
            var inpDesc = '-';
            if (inp) {
              var ir = inp.getBoundingClientRect();
              var ics = getComputedStyle(inp);
              inpDesc = Math.round(ir.x) + ',' + Math.round(ir.y) + ' ' +
                Math.round(ir.width) + 'x' + Math.round(ir.height) +
                ' vis=' + ics.visibility + ' op=' + ics.opacity + ' disp=' + ics.display;
            }
            /*
             * Contrast, not presence. Everything above says the page is painted, yet the user
             * sees black — which is exactly what an already-dark page looks like after a second,
             * platform-level dark inversion is applied on top of it: dark text on dark background.
             * If these colours collapse onto each other, that is the bug; if they are contrasting,
             * the page really is fine and the failure is in compositing to the screen.
             */
            function col(el, prop) {
              return el ? getComputedStyle(el)[prop] : '-';
            }
            /*
             * Walk from the login field up to the root. The page background composites but the
             * content does not, and body.scrollHeight is 0 — the signature of an ancestor that is
             * zero-height (or otherwise collapsed) and clipping everything inside it. Clipping is
             * invisible to every check above: it changes neither getBoundingClientRect nor
             * visibility/opacity, so an element can report "painted" while being cropped away.
             * The first ancestor with height 0 and a non-visible overflow is the culprit.
             */
            var chain = [];
            var node = inp;
            for (var depth = 0; node && depth < 10; depth++) {
              var ncs = getComputedStyle(node);
              chain.push({
                t: node.tagName +
                   (node.className && typeof node.className === 'string'
                     ? '.' + node.className.trim().split(/\s+/).slice(0, 1).join('') : ''),
                h: ncs.height,
                ch: node.clientHeight,
                oh: node.offsetHeight,
                ov: ncs.overflow,
                pos: ncs.position,
                disp: ncs.display,
                tr: ncs.transform === 'none' ? '-' : 'set',
                cont: ncs.contain
              });
              node = node.parentElement;
            }
            var btn = document.querySelector('button');
            var colours = {
              htmlBg: col(document.documentElement, 'backgroundColor'),
              bodyColor: col(b, 'color'),
              inputColor: col(inp, 'color'),
              inputBg: col(inp, 'backgroundColor'),
              buttonColor: col(btn, 'color'),
              buttonBg: col(btn, 'backgroundColor'),
              colorScheme: col(document.documentElement, 'colorScheme'),
              prefersDark: window.matchMedia &&
                window.matchMedia('(prefers-color-scheme: dark)').matches
            };
            return JSON.stringify({
              readyState: document.readyState,
              bodyChildren: b ? b.children.length : -1,
              htmlLen: b ? b.innerHTML.length : -1,
              textLen: b ? (b.innerText || '').trim().length : -1,
              /*
               * Spotify renders a refusal as a page of its own, so its wording is the diagnosis —
               * "Something went wrong" from a captcha rejection and from a dead backend look
               * identical from the outside. The login page can echo the account name, so anything
               * address-shaped is masked before it reaches logcat: a capture of this tag has to
               * stay safe to paste into a public issue.
               */
              pageText: (function () {
                var t = b ? (b.innerText || '') : '';
                return t.replace(/[\w.+-]+@[\w.-]+\.\w+/g, '<email>')
                        .replace(/\s+/g, ' ')
                        .trim()
                        .slice(0, 240);
              })(),
              elements: all.length,
              visibleElements: vis,
              paintedElements: painted,
              centreElement: midDesc,
              firstInput: inpDesc,
              inputs: document.querySelectorAll('input').length,
              passwordInputs: document.querySelectorAll('input[type=password]').length,
              buttons: document.querySelectorAll('button').length,
              iframes: document.querySelectorAll('iframe').length,
              forms: document.querySelectorAll('form').length,
              innerW: window.innerWidth,
              innerH: window.innerHeight,
              bodyH: b ? b.scrollHeight : -1,
              bg: b ? getComputedStyle(b).backgroundColor : '-',
              htmlH: document.documentElement.clientHeight,
              htmlScrollH: document.documentElement.scrollHeight,
              bodyOverflow: b ? getComputedStyle(b).overflow : '-',
              htmlOverflow: getComputedStyle(document.documentElement).overflow,
              /*
               * What the page's own Client Hints API reports is what Chromium writes into the
               * `Sec-CH-UA`, `Sec-CH-UA-Mobile` and `Sec-CH-UA-Platform` request headers — and
               * those go out on every request whether or not the server asked for them with
               * `Accept-CH` (Spotify never does). `settings.userAgentString` is the one part of
               * that set an app can rewrite, so a UA claiming Windows next to hints saying Android
               * is the signature of a spoofing WebView. This is the measurement that decides
               * whether our own UA override was what Spotify refused.
               */
              clientHints: (function () {
                var ud = navigator.userAgentData;
                if (!ud) return 'none';
                var brands = (ud.brands || []).map(function (x) {
                  return x.brand + '/' + x.version;
                }).join(',');
                return 'platform=' + ud.platform + ' mobile=' + ud.mobile + ' brands=' + brands;
              })(),
              colours: colours,
              ancestors: chain
            });
          } catch (e) {
            return JSON.stringify({ probeError: String(e) });
          }
        })();
    """.trimIndent()
    BrokenLogin.trap(bl, "dom.probe") {
        view.evaluateJavascript(js) { result ->
            BrokenLogin.i(bl, "dom", result?.trim('"')?.replace("\\\"", "\"") ?: "null")
        }
    }

    /*
     * The WebView is created and told to load inside AndroidView's factory — before Compose has
     * measured it. If the page lays out while the view still has zero height, JavaScript that
     * computes sizes once (Spotify's <main> is position:absolute and ends up 48px tall around
     * 729px of content) keeps those numbers forever, even though the viewport is 375x703 by the
     * time anything is measured.
     *
     * Telling the page the viewport changed is the cheapest way to find out. If <main> grows on a
     * resize, the bug is the load racing the layout and the fix is to load after measurement — not
     * to patch anyone's CSS. If it stays 48px, the collapse is intrinsic to the page and an
     * injected override is the only route left.
     */
}

/**
 * Undoes the collapsed height chain on Spotify's login page.
 *
 * Measured on the live page: `<main>` is `position: absolute`, `overflow: auto` and **48 px tall**
 * around a 729 px `<section>`, with `div`, `body` and `html` above it all computing to `0px`. The
 * form is fully laid out, opaque, white-on-dark and simply cropped away, which is why every
 * presence check reported it as painted while the screen stayed black.
 *
 * Ruled out by measurement before resorting to this: page load failures, HTTP/SSL errors, a dead
 * renderer, reCAPTCHA blocking the form, View size/alpha/attachment, dark inversion, contrast,
 * WebView compositing, the desktop User-Agent, and a load-versus-measure race (a synthetic
 * `resize` leaves `main` at 48 px).
 *
 * This is a workaround on someone else's stylesheet, so it is written to age as well as such a
 * thing can:
 *  - selectors are element names, never the generated class names (`sc-hLBbgQ` is a build hash and
 *    changes on every Spotify deploy)
 *  - it only relaxes heights and overflow; nothing is repositioned, hidden or restyled
 *  - if Spotify fixes the page, `height: auto` and `overflow: visible` are what the page would
 *    compute anyway, so the override becomes a no-op rather than a new bug
 */
private fun applyLoginLayoutFix(bl: String, view: WebView?) {
    if (view == null) return
    val js = """
        (function () {
          try {
            var m = document.querySelector('main');
            var before = m ? getComputedStyle(m).height : '-';
            var box = '-';
            if (m) {
              var s = getComputedStyle(m);
              box = 'pos=' + s.position + ' top=' + s.top + ' bottom=' + s.bottom +
                    ' minH=' + s.minHeight + ' maxH=' + s.maxHeight + ' flex=' + s.flex +
                    ' basis=' + s.flexBasis + ' align=' + s.alignSelf + ' box=' + s.boxSizing;
            }
            var id = 'meld-login-layout-fix';
            if (!document.getElementById(id)) {
              var st = document.createElement('style');
              st.id = id;
              st.textContent =
                'html, body { height: auto !important; min-height: 100% !important; overflow: visible !important; }' +
                'body > div { height: auto !important; min-height: 100% !important; }' +
                'main { position: static !important; height: auto !important;' +
                '       min-height: 100dvh !important; max-height: none !important;' +
                '       overflow: visible !important; }';
              document.head.appendChild(st);
            }
            var after = m ? getComputedStyle(m).height : '-';
            return JSON.stringify({
              mainBefore: before,
              mainAfter: after,
              mainBox: box,
              bodyH: document.body ? document.body.scrollHeight : -1,
              innerH: window.innerHeight,
              applied: !!document.getElementById(id)
            });
          } catch (e) {
            return JSON.stringify({ probeError: String(e) });
          }
        })();
    """.trimIndent()
    BrokenLogin.trap(bl, "layoutFix") {
        view.evaluateJavascript(js) { result ->
            BrokenLogin.i(bl, "layoutFix", result?.trim('"')?.replace("\\\"", "\"") ?: "null")
        }
    }
}

/**
 * Attempts to read the sp_dc cookie from the CookieManager.
 * Returns null if the cookie is not yet available.
 */
private fun extractSpDcCookie(): String? {
    val allCookies = CookieManager.getInstance().getCookie("https://open.spotify.com")
    if (allCookies.isNullOrBlank()) return null

    return allCookies.split(";")
        .mapNotNull { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
        .firstOrNull { it.first == "sp_dc" && it.second.isNotBlank() }
        ?.second
}

/**
 * Extracts sp_dc/sp_key cookies, stops the WebView, and fetches the access
 * token in the background using [SpotifyAuth.fetchAccessToken].
 *
 * Uses [tokenFetchStarted] as an atomic guard so only one invocation ever runs,
 * preventing the race between shouldOverrideUrlLoading and onPageFinished.
 */
private fun extractAndFetchToken(
    view: WebView?,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    navController: NavController,
    setProcessing: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setError: (Boolean) -> Unit,
    tokenFetchStarted: AtomicBoolean,
) {
    val bl = BrokenLogin.nextId("token")
    val cookieManager = CookieManager.getInstance()
    val allCookies = cookieManager.getCookie("https://open.spotify.com")
    Timber.d("SpotifyLogin: cookies present: ${!allCookies.isNullOrBlank()}")
    BrokenLogin.i(bl, "token.begin", BrokenLogin.describeCookies(allCookies))

    val cookieMap = allCookies?.split(";")
        ?.mapNotNull { cookie ->
            val parts = cookie.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0].trim().isNotEmpty()) {
                parts[0].trim() to parts[1].trim()
            } else {
                null
            }
        }?.toMap() ?: emptyMap()

    val spDc = cookieMap["sp_dc"]
    if (spDc.isNullOrBlank()) {
        Timber.w("SpotifyLogin: sp_dc not found in cookies (keys: ${cookieMap.keys})")
        // The login page completed but never produced the session cookie. Whether any cookie at
        // all arrived separates "login never actually happened" from "cookie was dropped".
        BrokenLogin.e(
            bl, "token.noCookie",
            BrokenLogin.kv("cookieNames" to cookieMap.keys.joinToString(",").ifEmpty { "NONE" }),
        )
        setProcessing(true)
        setStatus(context.getString(R.string.spotify_login_error_no_cookie))
        setError(true)
        tokenFetchStarted.set(false)
        return
    }

    val spKey = cookieMap["sp_key"] ?: ""
    Timber.d("SpotifyLogin: sp_dc found (${spDc.take(8)}...), starting token fetch")

    setProcessing(true)
    setError(false)
    setStatus(context.getString(R.string.spotify_status_verifying))

    view?.stopLoading()
    view?.loadUrl("about:blank")

    scope.launch(Dispatchers.IO) {
        try {
            context.dataStore.edit { prefs ->
                prefs[SpotifySpDcKey] = spDc
                prefs[SpotifySpKeyKey] = spKey
                prefs.remove(SpotifyAnonymousHomeKey)
            }

            withContext(Dispatchers.Main) {
                setStatus(context.getString(R.string.spotify_status_connecting))
            }
            Timber.d("SpotifyLogin: fetching access token via SpotifyAuth (with TOTP)...")

            // SpotifyAuth chains three remote dependencies — the TOTP secret gist, Spotify's
            // server-time endpoint and /api/token — and collapses them into one Result. Logging
            // the failure here is the only place the distinction survives.
            val tokenResult = SpotifyAuth.fetchAccessToken(spDc, spKey)
            tokenResult.onFailure { BrokenLogin.fail(bl, "token.fetch", it) }
            val token = tokenResult.getOrThrow()
            Timber.d("SpotifyLogin: token obtained (anonymous=${token.isAnonymous})")
            BrokenLogin.i(
                bl, "token.ok",
                BrokenLogin.kv(
                    "anonymous" to token.isAnonymous,
                    "accessToken" to BrokenLogin.redact(token.accessToken),
                    "expiresAt" to token.accessTokenExpirationTimestampMs,
                ),
            )
            Spotify.accessToken = token.accessToken

            withContext(Dispatchers.Main) {
                setStatus(context.getString(R.string.spotify_status_loading_profile))
            }
            Timber.d("SpotifyLogin: fetching user profile...")

            Spotify.me().onSuccess { user ->
                Timber.d("SpotifyLogin: logged in as ${user.displayName} (${user.id})")
                context.dataStore.edit { prefs ->
                    prefs[SpotifyUsernameKey] = user.displayName ?: user.id
                    prefs[SpotifyUserIdKey] = user.id
                }
            }.onFailure { e ->
                Timber.w(e, "SpotifyLogin: could not fetch profile (non-fatal)")
            }

            context.dataStore.edit { prefs ->
                prefs[SpotifyAccessTokenKey] = token.accessToken
                prefs[SpotifyTokenExpiryKey] = token.accessTokenExpirationTimestampMs
            }

            withContext(Dispatchers.Main) {
                setStatus(context.getString(R.string.spotify_login_success))
            }
            Timber.d("SpotifyLogin: login complete, navigating back")

            delay(300)

            withContext(Dispatchers.Main) {
                navController.navigateUp()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SpotifyLogin: login failed — ${e.message}")
            BrokenLogin.fail(bl, "login.failed", e)
            val errorMsg = classifyLoginError(context, e)
            withContext(Dispatchers.Main) {
                setStatus(errorMsg)
                setError(true)
            }
            tokenFetchStarted.set(false)
        }
    }
}

/**
 * Maps authentication exceptions to user-friendly error messages.
 */
private fun classifyLoginError(context: Context, e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        "anonymous" in msg || "expired" in msg ->
            context.getString(R.string.spotify_login_error_expired)
        "HTTP 403" in msg || "HTTP 401" in msg ->
            context.getString(R.string.spotify_login_error_rejected)
        "gist" in msg.lowercase() || "nuance" in msg.lowercase() ->
            context.getString(R.string.spotify_login_error_network)
        "UnknownHostException" in msg || "timeout" in msg.lowercase() ||
            "SocketTimeoutException" in e.javaClass.simpleName ->
            context.getString(R.string.spotify_login_error_network)
        else ->
            context.getString(R.string.spotify_login_error)
    }
}

/**
 * Wraps `fetch` and `XMLHttpRequest` so Spotify's own login requests print their response body into
 * the console, which the `WebChromeClient` above forwards to [BrokenLogin].
 *
 * `onReceivedHttpError` can only report a status: the WebView gave us `400` on
 * `accounts.spotify.com/login/code/send` and nothing else, while the JSON behind that 400 names the
 * check that failed. Matching is by path, only the request's *field names* are printed, and the body
 * is masked — addresses and long token-shaped runs replaced — so a capture stays safe to paste into
 * a public issue.
 */
private const val NETWORK_HOOK_JS = """
(function () {
  if (window.__auraNet) return;
  window.__auraNet = true;
  function mask(v) {
    try {
      return String(v)
        .replace(/[\w.+-]+@[\w.-]+\.\w+/g, '<email>')
        .replace(/[A-Za-z0-9_\-.]{60,}/g, '<opaque>')
        .replace(/\s+/g, ' ')
        .slice(0, 300);
    } catch (e) { return 'unprintable'; }
  }
  /*
   * Query strings are where Spotify's own one-time ids travel — `flow_ctx` rode in plain text on the
   * OTP request line. Only host + path are worth reading in a trace, so named values are replaced and
   * any long opaque run left in a path is shortened.
   */
  function safeUrl(u) {
    try {
      return String(u)
        .replace(/([?&])(flow_ctx|flowCtx|flow_context|request_context|requestContext|recaptchaToken|code|otp|totp|pin|password|email|username|login_hint|sp_dc|sid|access_token|authorization_code|token)=([^&#]*)/gi,
                  '$1$2=<redacted>')
        .replace(/[A-Za-z0-9_\-.]{60,}/g, '<opaque>')
        .slice(0, 220);
    } catch (e) { return 'unprintable'; }
  }
  function fieldNames(body) {
    try { return Object.keys(JSON.parse(body)).sort().join(','); } catch (e) { return 'non-json'; }
  }
  /*
   * The two anti-automation signals are opaque strings, so their *lengths* are the whole
   * measurement: a 400 `internal_error` sent with a 500-character reCAPTCHA token means Spotify
   * received a complete request and still refused the environment, while a zero-length one means
   * reCAPTCHA never ran in this WebView at all. Those two answers lead to different decisions, and
   * only the lengths go into the log — never the values.
   */
  function signals(body) {
    try {
      var o = JSON.parse(body);
      function len(v) { return (typeof v === 'string') ? v.length : String(v); }
      return 'recaptchaTokenLen=' + len(o.recaptchaToken) + ' automationKeyLen=' + len(o.automationKey);
    } catch (e) { return 'signals=-'; }
  }
  function emit(kind, url, status, body, text) {
    try {
      console.error('AURANET ' + kind + ' ' + status + ' ' + safeUrl(url) +
        ' sentFields=' + (body ? fieldNames(body) : '-') + ' ' + signals(body) +
        ' body=' + mask(text));
    } catch (e) {}
  }
  function interesting(u) {
    return u.indexOf('/login/') >= 0 || u.indexOf('/authenticate/') >= 0 || u.indexOf('/device') >= 0;
  }
  var of = window.fetch;
  if (of) {
    window.fetch = function (input, init) {
      var url = (typeof input === 'string') ? input : (input && input.url) || '?';
      var body = (init && init.body) || (input && input.body) || null;
      var result = of.apply(this, arguments);
      if (interesting(url)) {
        result.then(function (r) {
          try { r.clone().text().then(function (t) { emit('fetch', url, r.status, body, t); }); } catch (e) {}
        });
      }
      return result;
    };
  }
  var XO = XMLHttpRequest.prototype.open;
  var XS = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) {
    this.__auraUrl = u; this.__auraMethod = m;
    return XO.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function (b) {
    var self = this;
    var u = self.__auraUrl || '?';
    if (interesting(u)) {
      self.addEventListener('loadend', function () {
        emit('xhr-' + (self.__auraMethod || '?'), u, self.status, b, self.responseText);
      });
    }
    return XS.apply(this, arguments);
  };
})();
"""

/**
 * Desktop Chrome User-Agent, used only for Facebook — see [userAgentFor].
 *
 * It is *not* used for Spotify any more. Two claims in the older version of this comment turned out
 * to be wrong. First, the desktop UA never fixed the black login screen: Spotify's `<main>` still
 * collapsed to 48 px around 729 px of content under both user agents, so the page is responsive
 * rather than desktop-vs-mobile, and the collapse is handled where it actually occurs, in
 * [applyLoginLayoutFix]. Second, "a desktop UA makes the login more stable" was the opposite of
 * true: Chromium sends the low-entropy Client Hints on every HTTPS request and they cannot be
 * rewritten before API 35, so claiming Windows from an Android WebView put two contradictory
 * environment headers on the wire — and the login submit that carried a complete reCAPTCHA token and
 * every cookie still came back `internal_error`.
 */
private const val USER_AGENT_DESKTOP =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/**
 * The User-Agent for one navigation of the login flow.
 *
 * Spotify's own hosts get [nativeUserAgent] — the string this WebView really is — so the UA header
 * and the `Sec-CH-UA-*` Client Hints that go out beside it describe the same browser. Facebook is
 * the one exception that still needs the desktop UA: its *mobile* bundle throws
 * `Uncaught TypeError: Cannot read properties of undefined (reading 'getElementsByTagName')` and
 * leaves the page dead, which is a defect in Facebook's own JavaScript that nothing here can fix.
 */
private fun userAgentFor(url: String?, nativeUserAgent: String?): String? =
    if (url?.contains("facebook.com") == true) desktopUaOf(nativeUserAgent) else nativeUserAgent

/**
 * [USER_AGENT_DESKTOP] with the Chrome major taken from [webViewUa], so the page gets the desktop
 * layout it expects while the version still describes the engine actually rendering it. Falls back
 * to the constant when the WebView reports no Chrome version at all.
 */
private fun desktopUaOf(webViewUa: String?): String {
    val chromeMajor = Regex("Chrome/(\\d+)").find(webViewUa ?: "")?.groupValues?.get(1)
        ?: return USER_AGENT_DESKTOP
    return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/$chromeMajor.0.0.0 Safari/537.36"
}
