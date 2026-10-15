package com.extforge.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spine {
    val Black = Color(0xFF000000)
    val Text = Color(0xFF00FF66)
    val Muted = Color(0xFF1F8A45)
    val Line = Color(0xFF145C32)
    val Accent = Color(0xFF00FF66)
    val Warn = Color(0xFFFF4455)
}

@Composable
fun EightSpineTheme(content: @Composable () -> Unit) {
    val mono = FontFamily.Monospace
    val scheme = darkColorScheme(
        primary = Spine.Accent,
        onPrimary = Spine.Black,
        background = Spine.Black,
        onBackground = Spine.Text,
        surface = Spine.Black,
        onSurface = Spine.Text,
        surfaceVariant = Spine.Black,
        onSurfaceVariant = Spine.Muted,
        outline = Spine.Accent,
        error = Spine.Warn,
        onError = Spine.Black,
    )
    val type = Typography(
        bodyLarge = TextStyle(fontFamily = mono, fontSize = 14.sp, lineHeight = 18.sp),
        bodyMedium = TextStyle(fontFamily = mono, fontSize = 13.sp, lineHeight = 17.sp),
        bodySmall = TextStyle(fontFamily = mono, fontSize = 11.sp, lineHeight = 14.sp),
        labelLarge = TextStyle(fontFamily = mono, fontSize = 12.sp, lineHeight = 16.sp),
        titleLarge = TextStyle(fontFamily = mono, fontSize = 18.sp, lineHeight = 22.sp),
        titleMedium = TextStyle(fontFamily = mono, fontSize = 16.sp, lineHeight = 20.sp),
        titleSmall = TextStyle(fontFamily = mono, fontSize = 13.sp, lineHeight = 17.sp),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp),
    )
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes) {
        CompositionLocalProvider(LocalContentColor provides Spine.Text) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Spine.Black),
            ) {
                content()
            }
        }
    }
}
