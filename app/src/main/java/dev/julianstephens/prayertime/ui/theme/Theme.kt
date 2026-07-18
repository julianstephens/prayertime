package dev.julianstephens.prayertime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrayerBlue,
    onPrimary = DarkOnPrayerBlue,
    primaryContainer = DarkPrayerBlueContainer,
    onPrimaryContainer = DarkOnPrayerBlueContainer,

    secondary = DarkQuietSlate,
    onSecondary = DarkOnQuietSlate,
    secondaryContainer = DarkQuietSlateContainer,
    onSecondaryContainer = DarkOnQuietSlateContainer,

    tertiary = DarkWarmGold,
    onTertiary = DarkOnWarmGold,
    tertiaryContainer = DarkWarmGoldContainer,
    onTertiaryContainer = DarkOnWarmGoldContainer,

    background = DarkBackground,
    onBackground = DarkOnSurface,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainer = DarkSurfaceContainer,

    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    primary = PrayerBlue,
    onPrimary = OnPrayerBlue,
    primaryContainer = PrayerBlueContainer,
    onPrimaryContainer = OnPrayerBlueContainer,

    secondary = QuietSlate,
    onSecondary = OnQuietSlate,
    secondaryContainer = QuietSlateContainer,
    onSecondaryContainer = OnQuietSlateContainer,

    tertiary = WarmGold,
    onTertiary = OnWarmGold,
    tertiaryContainer = WarmGoldContainer,
    onTertiaryContainer = OnWarmGoldContainer,

    background = LightBackground,
    onBackground = LightOnSurface,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainer = LightSurfaceContainer,

    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

@Composable
fun PrayerTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current

            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}