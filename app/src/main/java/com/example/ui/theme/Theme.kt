package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

@Immutable
data class ElectricStatusColors(
    val service: Color,
    val serviceContainer: Color,
    val outage: Color,
    val outageContainer: Color,
    val unknown: Color,
    val unknownContainer: Color,
    val favorite: Color,
    val favoriteContainer: Color
)

val LocalElectricStatusColors = staticCompositionLocalOf {
    ElectricStatusColors(
        service = ServiceGreenLight,
        serviceContainer = ServiceGreenBgLight,
        outage = OutageRedLight,
        outageContainer = OutageRedBgLight,
        unknown = UnknownGrayLight,
        unknownContainer = UnknownGrayBgLight,
        favorite = FavoriteGoldLight,
        favoriteContainer = FavoriteGoldBgLight
    )
}

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = TealSecondary,
    onSecondary = OnTealSecondary,
    secondaryContainer = TealContainer,
    onSecondaryContainer = OnTealContainer,
    tertiary = SolarAmberTertiary,
    onTertiary = OnSolarAmber,
    tertiaryContainer = SolarAmberContainer,
    onTertiaryContainer = OnSolarAmberContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF0F5F2),
    surfaceContainerHigh = Color(0xFFE6EEEA),
    outline = LightOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = DarkOnEmeraldPrimary,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = DarkOnEmeraldContainer,
    secondary = DarkTealSecondary,
    onSecondary = DarkOnTealSecondary,
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = DarkOnTealContainer,
    tertiary = DarkAmberTertiary,
    onTertiary = DarkOnAmberTertiary,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = DarkOnAmberContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = Color(0xFF141E1B),
    surfaceContainer = Color(0xFF192521),
    surfaceContainerHigh = Color(0xFF21302B),
    outline = DarkOutline
)

private val LuzIslaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LuzIslaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val statusColors = if (isDark) {
        ElectricStatusColors(
            service = ServiceGreenDark,
            serviceContainer = ServiceGreenBgDark,
            outage = OutageRedDark,
            outageContainer = OutageRedBgDark,
            unknown = UnknownGrayDark,
            unknownContainer = UnknownGrayBgDark,
            favorite = FavoriteGoldDark,
            favoriteContainer = FavoriteGoldBgDark
        )
    } else {
        ElectricStatusColors(
            service = ServiceGreenLight,
            serviceContainer = ServiceGreenBgLight,
            outage = OutageRedLight,
            outageContainer = OutageRedBgLight,
            unknown = UnknownGrayLight,
            unknownContainer = UnknownGrayBgLight,
            favorite = FavoriteGoldLight,
            favoriteContainer = FavoriteGoldBgLight
        )
    }

    CompositionLocalProvider(LocalElectricStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LuzIslaTypography,
            shapes = LuzIslaShapes,
            content = content
        )
    }
}
