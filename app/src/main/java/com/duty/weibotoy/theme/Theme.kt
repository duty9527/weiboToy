package com.duty.weibotoy.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val WeiboDarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    background = DarkBg,
    surface = BubbleBg,
    onBackground = TextWhite,
    onSurface = TextWhite,
    secondary = TextGrey
)

@Composable
fun WeiboChatTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WeiboDarkColorScheme,
        typography = Typography,
        content = content
    )
}
