package com.extforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.extforge.app.ui.theme.Spine

@Composable
fun SpineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Spine.Text,
    size: TextUnit = 13.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = size,
        lineHeight = (size.value + 4).sp,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
fun SpineScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Spine.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
        content = content,
    )
}

@Composable
fun SpineHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Spine.Accent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        if (onBack != null) {
            SpineText(
                text = "<",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onBack,
                    ),
            )
        }
        SpineText(text = title, modifier = Modifier.weight(1f), size = 16.sp, maxLines = 1)
        trailing()
    }
}

@Composable
fun SpineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    val bg = if (active) Spine.Accent else Spine.Black
    val fg = if (active) Spine.Black else Spine.Text
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (active) Spine.Accent else Spine.Line)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        SpineText(text = label, color = fg, size = 12.sp, maxLines = 1)
    }
}

@Composable
fun SpineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(
            color = Spine.Text,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        ),
        cursorBrush = SolidColor(Spine.Accent),
        keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (singleLine) 0.dp else 96.dp)
            .border(1.dp, if (focused) Spine.Accent else Spine.Line)
            .padding(8.dp)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty() && hint.isNotEmpty()) {
                    SpineText(text = hint, color = Spine.Muted, size = 13.sp)
                }
                inner()
            }
        },
    )
}
