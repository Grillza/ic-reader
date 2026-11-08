package io.github.grillza.icreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF2B5BA8),
    secondary = Color(0xFF1E9E4A),
    tertiary = Color(0xFFE8338A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    secondary = Color(0xFF7FD99A),
    tertiary = Color(0xFFFFB0CB),
)

/** Colour for money coming in (charges); spending uses the normal text colour. */
val ChargeGreen = Color(0xFF1E9E4A)
val ChargeGreenDark = Color(0xFF7FD99A)

@Composable
fun IcReaderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        Dark
    } else {
        Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
