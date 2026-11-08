/**
 * Aura Music — the update prompt on a television.
 *
 * The phone gets Metrolist's Material alert, whose buttons sit in the dialog's own action row. On a
 * remote-driven screen that row is unreachable: the two buttons are laid out but never focusable, and
 * the changelog column is tall enough to paint over them, so the prompt shows a wall of raw markdown
 * with two invisible, dead targets. This is the same panel the rest of the app uses — a scroll body the
 * D-pad can move through, the release notes rendered rather than dumped, and the actions as pills the
 * selector can actually stop on.
 */

package com.aura.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.aura.music.R
import com.aura.music.ui.aura.AuraDialogActions
import com.aura.music.ui.aura.AuraDialogScaffold
import com.aura.music.ui.aura.AuraDialogTitle
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.AuraPillButton
import com.aura.music.ui.aura.AuraPx
import com.aura.music.ui.aura.AuraText
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraFocused
import com.aura.music.ui.aura.requestFocusSafely
import com.aura.music.ui.screens.settings.MarkdownText
import kotlinx.coroutines.delay

@Composable
fun AuraUpdatePrompt(
    versionName: String,
    changelog: String,
    warning: String?,
    updateState: ApkUpdateState,
    onDismiss: () -> Unit,
) {
    val actionFocus = remember { FocusRequester() }

    // A dialog window opens with no selection of its own, and the pill only exists once this
    // composition lands, so aim at the action the user came here to take.
    LaunchedEffect(Unit) {
        var tries = 0
        while (tries < 10 && !actionFocus.requestFocusSafely()) {
            delay(120)
            tries++
        }
    }

    AuraDialogScaffold(onDismiss = onDismiss) {
        AuraDialogTitle(value = stringResource(R.string.update_available_title))

        Spacer(modifier = Modifier.height(20.apx()))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.apx())
                .background(AuraMock.Border, RoundedCornerShape(AuraPx.PickCardRadius.apx())),
        )

        Spacer(modifier = Modifier.height(24.apx()))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.apx()),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .auraFocused(
                    corner = AuraPx.PickCardRadius.apx(),
                    fill = false,
                    grow = false,
                    claim = false,
                )
                .verticalScroll(rememberScrollState())
                .focusable(),
        ) {
            AuraText(
                value = stringResource(R.string.update_available_message, versionName),
                px = 24,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )

            if (warning != null) {
                AuraText(
                    value = warning,
                    px = 22,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            }

            // Above the notes, not under them: a download that starts has to be visible without
            // scrolling a panel that is already full.
            ApkUpdateStatus(updateState)

            AuraText(
                value = stringResource(R.string.changelog),
                px = 22,
                weight = FontWeight.Bold,
                color = AuraMock.Text,
            )

            MarkdownText(changelog.ifBlank { stringResource(R.string.changelog_empty) })
        }

        Spacer(modifier = Modifier.height(28.apx()))

        AuraDialogActions {
            AuraPillButton(
                label = stringResource(R.string.update_not_now),
                onClick = onDismiss,
            )
            AuraPillButton(
                label = stringResource(updateState.actionLabel),
                filled = true,
                focusRequester = actionFocus,
                onClick = updateState::act,
            )
        }
    }
}
