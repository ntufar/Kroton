package io.github.ntufar.kroton.designsystem

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val KrotonDarkColorScheme =
    darkColorScheme(
        primary = Ground,
        onPrimary = Mark,
        background = Slate,
        onBackground = Mark,
        surface = Slate,
        onSurface = Mark,
    )

private val KrotonLightColorScheme =
    lightColorScheme(
        primary = Ground,
        onPrimary = Mark,
        background = Mark,
        onBackground = Ink,
        surface = Mark,
        onSurface = Ink,
    )

@Composable
fun KrotonTheme(
    // Dark by default (spec §8 "Theming"); callers override once a user preference exists.
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> KrotonDarkColorScheme
            else -> KrotonLightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
