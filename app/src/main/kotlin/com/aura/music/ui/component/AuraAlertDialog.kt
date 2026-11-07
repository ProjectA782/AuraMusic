/**
 * Aura Music — the reference's alert dialog.
 *
 * Material's `AlertDialog` is still used directly by a dozen screens, and its surface is a tonally
 * elevated M3 card with a ripple button row — none of which exists in the reference. This keeps the
 * exact same signature so call sites only change their import, and paints the TV shell with the flat
 * #211F26 panel, the reference's heading ramp and `.pill-btn`-coloured actions.
 */

package com.aura.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraDialogActions
import com.aura.music.ui.aura.AuraDialogScaffold
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraType

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    if (!LocalAuraTvShell.current) {
        // Hand the phone layout back to Material untouched, including any explicit colours.
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = if (containerColor == Color.Unspecified) {
                AlertDialogDefaults.containerColor
            } else {
                containerColor
            },
            iconContentColor = if (iconContentColor == Color.Unspecified) {
                AlertDialogDefaults.iconContentColor
            } else {
                iconContentColor
            },
            titleContentColor = if (titleContentColor == Color.Unspecified) {
                AlertDialogDefaults.titleContentColor
            } else {
                titleContentColor
            },
            textContentColor = if (textContentColor == Color.Unspecified) {
                AlertDialogDefaults.textContentColor
            } else {
                textContentColor
            },
            tonalElevation = tonalElevation,
            properties = properties,
        )
        return
    }

    AuraDialogScaffold(onDismiss = onDismissRequest, modifier = modifier) {
        if (icon != null) {
            CompositionLocalProvider(LocalContentColor provides AuraMock.Primary) {
                Box(modifier = Modifier.align(Alignment.CenterHorizontally)) { icon() }
            }
            Spacer(Modifier.height(16.apx()))
        }
        if (title != null) {
            ProvideTextStyle(auraType(FontWeight.Bold, 32)) {
                CompositionLocalProvider(LocalContentColor provides AuraMock.Text) {
                    Box(modifier = Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)) { title() }
                }
            }
            Spacer(Modifier.height(16.apx()))
        }
        if (text != null) {
            ProvideTextStyle(auraType(FontWeight.Normal, 22)) {
                CompositionLocalProvider(LocalContentColor provides AuraMock.Muted) {
                    Column(modifier = Modifier.fillMaxWidth()) { text() }
                }
            }
            Spacer(Modifier.height(16.apx()))
        }
        AuraDialogActions {
            // The call sites hand over TextButtons; the mock has no container for them, so they read
            // as flat primary labels — recoloured here rather than at a dozen call sites.
            CompositionLocalProvider(LocalContentColor provides AuraMock.Primary) {
                ProvideTextStyle(auraType(FontWeight.Bold, 22)) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
