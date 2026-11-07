/**
 * Aura Music — the reference's dialogs.
 *
 * The mock never shows a Material card: a raised surface is a flat #211F26 panel on a dimmed page,
 * its rows are the same full-round strips the settings rail uses, and its actions are `.pill-btn`.
 * Every dialog in the app funnels through DefaultDialog or ListDialog, so the treatment lives here
 * once and both of those branch onto it.
 */

package com.aura.music.ui.aura

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aura.music.LocalAuraTvShell
import com.aura.music.R
import com.aura.music.ui.component.AuraSwitch

/** The panel's corner in the reference's own scale. */
val AuraDialogRadius = 32

/**
 * The reference's raised surface: a dimmed page behind a flat, centred #211F26 panel.
 *
 * The panel swallows its own taps — the scrim's dismiss gesture is on the parent, and the panel is
 * not itself clickable, so without this a tap inside the dialog would close it.
 */
@Composable
fun AuraDialogScaffold(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidthPx: Int = 1200,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AuraMock.Scrim)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = modifier
                    .widthIn(max = maxWidthPx.apx())
                    .heightIn(max = 900.apx())
                    .padding(horizontal = 48.apx())
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                    .clip(RoundedCornerShape(AuraDialogRadius.apx()))
                    .background(AuraMock.Panel)
                    .padding(40.apx()),
                content = content,
            )
        }
    }
}

/** The panel heading, sized like the reference's `text-3xl font-bold`. */
@Composable
fun AuraDialogTitle(
    value: String,
    modifier: Modifier = Modifier,
) {
    AuraText(
        value = value,
        px = 34,
        weight = FontWeight.Bold,
        modifier = modifier,
    )
}

/**
 * One choice inside a dialog: the same flat, full-round strip as a settings row, with the reference's
 * hollow ring standing in for a Material radio.
 */
@Composable
fun AuraDialogOption(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    selected: Boolean = false,
    @DrawableRes icon: Int? = null,
    showRadio: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .auraFocused(corner = FullRound, fill = false, onFocusChanged = { focused = it })
            .clip(RoundedCornerShape(FullRound))
            .background(if (focused) AuraMock.FocusFill else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.apx(), vertical = 18.apx()),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = when {
                    focused -> AuraMock.Primary
                    selected -> AuraMock.Primary
                    else -> AuraMock.Muted
                },
                modifier = Modifier.size(28.apx()),
            )
            Spacer(Modifier.width(20.apx()))
        }
        if (showRadio) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.apx())
                    .border(
                        width = 2.apx(),
                        color = if (selected) AuraMock.Primary else AuraMock.Border,
                        shape = CircleShape,
                    ),
            ) {
                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(16.apx())
                            .background(AuraMock.Primary, CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(20.apx()))
        }
        Column(modifier = Modifier.weight(1f)) {
            AuraText(
                value = label,
                px = 24,
                weight = FontWeight.Medium,
                color = when {
                    selected -> AuraMock.Primary
                    focused -> AuraMock.Text
                    else -> AuraMock.Muted
                },
            )
            if (description != null) {
                Spacer(Modifier.height(4.apx()))
                AuraText(
                    value = description,
                    px = 18,
                    color = AuraMock.Muted,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * A labelled switch inside a dialog.
 *
 * On the phone these are the plain rows Metrolist always had; on the TV shell they are the same
 * focusable strip as every other dialog choice — a bare Row cannot receive D-pad focus, so an
 * un-focused switch row would be reachable only by touch and its setting unreachable by remote.
 */
@Composable
fun AuraDialogSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    if (!LocalAuraTvShell.current) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label)
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AuraSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(if (checked) R.drawable.check else R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        }
        return
    }

    AuraDialogOption(
        label = label,
        description = description,
        modifier = modifier,
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            AuraSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                thumbContent = {
                    Icon(
                        painter = painterResource(if (checked) R.drawable.check else R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
    )
}

/** The actions line: right-aligned `.pill-btn`s, the primary one filled. */
@Composable
fun AuraDialogActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            space = 16.apx(),
            alignment = Alignment.End,
        ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The reference's input well: a darker inset than the panel it sits in, outlined in border grey and
 * flipped to primary by focus. [fieldFocus] belongs on the text field itself, not the surrounding
 * box — a [FocusRequester] on a non-focusable container reports a miss rather than taking focus.
 */
@Composable
fun AuraDialogTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldFocus: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(28.apx())
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = auraType(FontWeight.Normal, 24).copy(color = AuraMock.Text),
        cursorBrush = SolidColor(AuraMock.Primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
            .fillMaxWidth()
            .then(fieldFocus?.let { Modifier.focusRequester(it) } ?: Modifier)
            .auraFocused(corner = 28.apx(), fill = false, grow = false, onFocusChanged = { focused = it })
            .clip(shape)
            .background(AuraMock.Page, shape)
            .border(2.apx(), if (focused) AuraMock.Primary else AuraMock.Border, shape)
            .padding(horizontal = 24.apx(), vertical = 20.apx()),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty() && placeholder != null) {
                    CompositionLocalProvider(LocalContentColor provides AuraMock.Muted) {
                        placeholder()
                    }
                }
                inner()
            }
        },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
    )
}

/** The caption above an input in the reference — smaller, muted, sitting on the panel. */
@Composable
fun AuraFieldLabel(
    value: String,
    modifier: Modifier = Modifier,
) {
    AuraText(
        value = value,
        px = 18,
        weight = FontWeight.Medium,
        color = AuraMock.Muted,
        modifier = modifier.padding(start = 8.apx(), bottom = 8.apx()),
    )
}

/**
 * [AuraDialogTextField] with a plain string binding and the reference's caption above it, so a
 * settings dialog can swap an `OutlinedTextField` without carrying a [TextFieldValue] at the call site.
 */
@Composable
fun AuraDialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    fieldFocus: FocusRequester? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // The caller owns the text; re-seed when it changes underneath us (a cleared field, an autosave).
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        AuraFieldLabel(label)
        AuraDialogTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            singleLine = singleLine,
            isPassword = isPassword,
            keyboardOptions = keyboardOptions,
            fieldFocus = fieldFocus,
        )
    }
}

/**
 * A choice list the remote can actually drive.
 *
 * The reference has no popup menu, and an `ExposedDropdownMenu` opens a separate window the D-pad
 * cannot leave the dialog into — so the options expand in place, below the closed control, as the
 * same full-round rows used everywhere else in the panel.
 */
@Composable
fun AuraDropdownField(
    label: String,
    options: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        AuraFieldLabel(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .auraFocused(corner = 28.apx(), fill = false, grow = false, onFocusChanged = { focused = it })
                .clip(RoundedCornerShape(28.apx()))
                .background(AuraMock.Page)
                .border(2.apx(), if (focused) AuraMock.Primary else AuraMock.Border, RoundedCornerShape(28.apx()))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(horizontal = 24.apx(), vertical = 20.apx()),
        ) {
            AuraText(
                value = current,
                px = 24,
                weight = FontWeight.Medium,
                color = if (focused) AuraMock.Text else AuraMock.Muted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.expand_more),
                contentDescription = null,
                tint = if (focused) AuraMock.Primary else AuraMock.Muted,
                modifier = Modifier.size(24.apx()),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.apx()),
            ) {
                options.forEach { option ->
                    AuraDialogOption(
                        label = option,
                        selected = option == current,
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
