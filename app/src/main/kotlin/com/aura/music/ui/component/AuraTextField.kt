/**
 * Aura Music — the settings and integration text input.
 *
 * Phones keep Material's outlined field. The TV shell has no outlined field anywhere in the
 * reference: an input is the inset well used by the dialogs, with the caption painted above it.
 * The signature mirrors the subset of `OutlinedTextField` these screens use, so a screen converts
 * by swapping the import rather than rewriting every call.
 */

package com.aura.music.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.music.LocalAuraTvShell
import com.aura.music.ui.aura.AuraDialogTextField
import com.aura.music.ui.aura.AuraMock
import com.aura.music.ui.aura.apx
import com.aura.music.ui.aura.auraType
import androidx.compose.ui.text.input.TextFieldValue

@Composable
fun AuraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    fieldFocus: FocusRequester? = null,
) {
    AuraTextField(
        value = TextFieldValue(value),
        onValueChange = { onValueChange(it.text) },
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        singleLine = singleLine,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = textStyle,
        shape = shape,
        colors = colors,
        fieldFocus = fieldFocus,
    )
}

@Composable
fun AuraTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    fieldFocus: FocusRequester? = null,
) {
    // The requester has to sit on the editable node: the outer column is not a focus target, so a
    // caller that aims at the modifier it is handed would never move the caret in.
    val innerFocus = fieldFocus?.let { Modifier.focusRequester(it) } ?: Modifier

    if (!LocalAuraTvShell.current) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.then(innerFocus),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            supportingText = supportingText,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            shape = shape,
            colors = colors,
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            // Call sites pass a bare `Text(...)`; the reference's caption style is supplied through
            // the text-style local instead of parsing the composable apart.
            CompositionLocalProvider(
                LocalContentColor provides AuraMock.Muted,
                LocalTextStyle provides auraType(FontWeight.Medium, 18),
                content = {
                    Column(modifier = Modifier.padding(start = 8.apx(), bottom = 10.apx())) {
                        label()
                    }
                },
            )
        }
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.apx()),
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides AuraMock.Muted, content = leadingIcon)
            }
            AuraDialogTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = placeholder,
                singleLine = singleLine,
                maxLines = maxLines,
                isPassword = visualTransformation is PasswordVisualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                fieldFocus = fieldFocus,
            )
            if (trailingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides AuraMock.Muted, content = trailingIcon)
            }
        }
        if (supportingText != null) {
            CompositionLocalProvider(LocalContentColor provides AuraMock.Muted, content = supportingText)
        }
    }
}
