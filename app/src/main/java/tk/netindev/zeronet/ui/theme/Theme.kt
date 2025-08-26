package tk.netindev.zeronet.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val AmoledDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentGreen,
    tertiary = LightGray,
    background = AmoledBlack,
    surface = DarkGray,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = AccentGreen,
    tertiary = LightGray,
    background = AmoledBlack,
    surface = DarkGray,
    onPrimary = TextWhite,
    onSecondary = TextWhite,
    onTertiary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

@Composable
fun ZeronetTheme(
    darkTheme: Boolean = true, // Force dark theme for AMOLED
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic colors for consistent AMOLED theme
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> AmoledDarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}