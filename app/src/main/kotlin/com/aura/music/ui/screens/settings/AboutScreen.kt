/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.aura.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.aura.music.BuildConfig
import com.aura.music.LocalPlayerAwareWindowInsets
import com.aura.music.R
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.auraType
import com.aura.music.ui.component.IconButton
import com.aura.music.ui.component.TopAppBar
import com.aura.music.ui.utils.backToMain
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scrollState = rememberScrollState()
    val scrollScope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            ),
    ) {
        // The shell gives this destination an exact page box, so a bar emitted after the card
        // becomes a sibling of it and paints over the card. It is the page heading: first child,
        // pinned, with the card scrolling under it.
        TopAppBar(
            title = { Text(stringResource(R.string.about)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )

        // The body is prose, so it hands the remote nothing to move: a plain Column takes no focus
        // and verticalScroll only follows a focused child, which is why this page never scrolled.
        // The container becomes the D-pad stop instead and spends up/down on the scroll. It draws no
        // ring because there is nothing here to select, and it hands a press back at either end, so
        // UP at the top still reaches the header arrow and DOWN at the foot still reaches the rail.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = when (event.key) {
                        Key.DirectionDown -> 1
                        Key.DirectionUp -> -1
                        else -> return@onPreviewKeyEvent false
                    }
                    val band = (scrollState.viewportSize * 0.8f).toInt().coerceAtLeast(1)
                    val target = (scrollState.value + direction * band)
                        .coerceIn(0, scrollState.maxValue)
                    if (target == scrollState.value) return@onPreviewKeyEvent false
                    scrollScope.launch { scrollState.animateScrollTo(target) }
                    true
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(AuraMock.Panel)
                    .padding(vertical = 44.dp, horizontal = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(AuraMock.FocusFill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.aura_ic_logo_note),
                        contentDescription = null,
                        tint = AuraMock.Primary,
                        modifier = Modifier.size(56.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                AuraText(
                    value = stringResource(R.string.about_app_name),
                    px = 46,
                    weight = FontWeight.Bold,
                    color = AuraMock.Text,
                )

                Spacer(Modifier.height(10.dp))

                AuraText(
                    value = stringResource(
                        R.string.app_version_info,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.ARCHITECTURE.uppercase(),
                    ),
                    px = 24,
                    color = AuraMock.Muted,
                    maxLines = 2,
                )

                Spacer(Modifier.height(36.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AuraMock.Border),
                )

                Spacer(Modifier.height(36.dp))

                // The page is prose with one title and two named sections, so each heading is its
                // own resource and the paragraph under it is another: a translator can move the copy
                // without touching the layout.
                AboutSectionHeading(stringResource(R.string.about_heading), 34)

                Spacer(Modifier.height(18.dp))

                AboutParagraph(stringResource(R.string.about_intro))

                Spacer(Modifier.height(34.dp))

                AboutSectionHeading(stringResource(R.string.about_section_living_room))

                Spacer(Modifier.height(16.dp))

                AboutParagraph(stringResource(R.string.about_living_room))

                Spacer(Modifier.height(40.dp))

                AboutSectionHeading(stringResource(R.string.about_features))

                Spacer(Modifier.height(22.dp))

                listOf(
                    R.string.about_feature_spotify_lead to R.string.about_feature_spotify_text,
                    R.string.about_feature_tv_lead to R.string.about_feature_tv_text,
                    R.string.about_feature_material_lead to R.string.about_feature_material_text,
                    R.string.about_feature_setup_lead to R.string.about_feature_setup_text,
                    R.string.about_feature_queue_lead to R.string.about_feature_queue_text,
                    R.string.about_feature_sync_lead to R.string.about_feature_sync_text,
                ).forEachIndexed { index, feature ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    AboutBullet(stringResource(feature.first), stringResource(feature.second))
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** One body paragraph. Left-aligned: the card centres its own header, but prose set to centre
 *  reads as a column of ragged line-starts and is the first thing the eye cannot follow. */
@Composable
private fun AboutParagraph(text: String) {
    Text(
        text = text,
        style = auraType(FontWeight.Normal, 24),
        color = AuraMock.Muted,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AboutSectionHeading(text: String, px: Int = 28) {
    AuraText(
        value = text,
        px = px,
        weight = FontWeight.Bold,
        color = AuraMock.Text,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * One line of the feature list: the brand dot carries the bullet, so no glyph font is needed, and
 * the lead-in is bold inside the same paragraph rather than a second Text, so it wraps with the
 * sentence instead of dropping onto its own line.
 */
@Composable
private fun AboutBullet(lead: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 11.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(AuraMock.Primary),
        )
        Text(
            text = remember(lead, text) {
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AuraMock.Text)) {
                        append(lead)
                    }
                    append(text)
                }
            },
            style = auraType(FontWeight.Normal, 24),
            color = AuraMock.Muted,
            modifier = Modifier.weight(1f),
        )
    }
}
