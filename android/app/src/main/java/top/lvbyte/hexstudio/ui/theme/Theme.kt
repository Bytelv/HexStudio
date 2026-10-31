package top.lvbyte.hexstudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F63F5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E9FF),
    onPrimaryContainer = Color(0xFF101B5C),
    secondary = Color(0xFF0E9F94),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5F5F1),
    onSecondaryContainer = Color(0xFF003D38),
    tertiary = Color(0xFFE08A2E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBD8),
    onTertiaryContainer = Color(0xFF4A2800),
    background = Color(0xFFF4F5F8),
    onBackground = Color(0xFF171922),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171922),
    surfaceVariant = Color(0xFFEEEFF3),
    onSurfaceVariant = Color(0xFF5A5F6E),
    outline = Color(0xFFD7DAE2),
    outlineVariant = Color(0xFFE7E9EF),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E6),
    onErrorContainer = Color(0xFF5C0A05)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA7B3FF),
    onPrimary = Color(0xFF101B5C),
    primaryContainer = Color(0xFF33418E),
    onPrimaryContainer = Color(0xFFE6E9FF),
    secondary = Color(0xFF5EE0D0),
    onSecondary = Color(0xFF003D38),
    secondaryContainer = Color(0xFF005149),
    onSecondaryContainer = Color(0xFFD5F5F1),
    tertiary = Color(0xFFF3B15B),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6B3D00),
    onTertiaryContainer = Color(0xFFFFEBD8),
    background = Color(0xFF0E1016),
    onBackground = Color(0xFFE9EAF0),
    surface = Color(0xFF171A22),
    onSurface = Color(0xFFE9EAF0),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFA6ACBC),
    outline = Color(0xFF3A3F4C),
    outlineVariant = Color(0xFF2A2E3A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val StudioTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp)
    )
}

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun StudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
