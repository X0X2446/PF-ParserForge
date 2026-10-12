package com.parserforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parserforge.app.ui.theme.Spine

@Composable
fun SpineText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = Spine.White,
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
            .border(1.dp, Spine.White)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            SpineText(
                text = "BACK",
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(end = 12.dp),
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            SpineText(
                text = title,
                size = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
fun SpineAction(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val border = if (active) Spine.Accent else Spine.White
    val foreground = if (active) Spine.Black else Spine.White
    val background = if (active) Spine.Accent else Spine.Black
    Box(
        modifier = modifier
            .border(1.dp, border)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        SpineText(text = label, color = foreground, size = 12.sp)
    }
}

@Composable
fun SpineField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onFocus: (Boolean) -> Unit = {},
    onDone: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .border(1.dp, Spine.Grey)
            .onFocusChanged { onFocus(it.isFocused) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
        textStyle = TextStyle(
            color = Spine.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 17.sp,
        ),
        cursorBrush = SolidColor(Spine.Accent),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onDone != null && singleLine) ImeAction.Go else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(
            onGo = { onDone?.invoke() },
            onDone = { onDone?.invoke() },
        ),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    SpineText(text = hint, color = Spine.Muted, size = 13.sp)
                }
                inner()
            }
        },
    )
}
