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
    primary = Color(0xFF5B6CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E7FF),
    onPrimaryContainer = Color(0xFF1B2470),
    secondary = Color(0xFF0FAFA0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F7F2),
    onSecondaryContainer = Color(0xFF00403B),
    tertiary = Color(0xFFE67E22),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = Color(0xFF5C6070),
    outline = Color(0xFFD5D8E0),
    outlineVariant = Color(0xFFE8EAF0),
    error = Color(0xFFD93036),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E5),
    onErrorContainer = Color(0xFF5F0010)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA7FF),
    onPrimary = Color(0xFF1B2470),
    primaryContainer = Color(0xFF343E86),
    onPrimaryContainer = Color(0xFFE4E7FF),
    secondary = Color(0xFF5EE0D0),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFD6F7F2),
    tertiary = Color(0xFFF2A65A),
    background = Color(0xFF111216),
    onBackground = Color(0xFFE8E9EE),
    surface = Color(0xFF191A20),
    onSurface = Color(0xFFE8E9EE),
    surfaceVariant = Color(0xFF232530),
    onSurfaceVariant = Color(0xFFA9ADBA),
    outline = Color(0xFF3A3D4A),
    outlineVariant = Color(0xFF2B2E38),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val StudioTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium)
    )
}

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
