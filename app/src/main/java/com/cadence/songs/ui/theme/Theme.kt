package com.cadence.songs.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E3FF),
    onPrimaryContainer = Color(0xFF15124F),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0F5EE),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9C7),
    onTertiaryContainer = Color(0xFF3A2400),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1B22),
    surface = Color(0xFFFAF8F5),
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFEBE8F1),
    onSurfaceVariant = Color(0xFF4A4953),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F1ED),
    surfaceContainer = Color(0xFFEFECE7),
    surfaceContainerHigh = Color(0xFFE9E6E1),
    surfaceContainerHighest = Color(0xFFE3E0DB),
    outline = Color(0xFF7B7A85),
    outlineVariant = Color(0xFFD0CDD6),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9ACFF),
    onPrimary = Color(0xFF1B1A6B),
    primaryContainer = Color(0xFF30338F),
    onPrimaryContainer = Color(0xFFE2E1FF),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B4F47),
    onSecondaryContainer = Color(0xFFB9F5EC),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF3A2800),
    tertiaryContainer = Color(0xFF5A3F00),
    onTertiaryContainer = Color(0xFFFFE2A0),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFE7E5EC),
    surface = Color(0xFF0E1015),
    onSurface = Color(0xFFE7E5EC),
    surfaceVariant = Color(0xFF2A2C36),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceContainerLowest = Color(0xFF090A0E),
    surfaceContainerLow = Color(0xFF14161D),
    surfaceContainer = Color(0xFF181B23),
    surfaceContainerHigh = Color(0xFF1F232C),
    surfaceContainerHighest = Color(0xFF272B36),
    outline = Color(0xFF908F9A),
    outlineVariant = Color(0xFF3A3C47),
    error = Color(0xFFFFB4AB)
)

/** Thème « scène » : noir pur, fort contraste, pour jouer dans le noir. */
private val StageColors = darkColorScheme(
    primary = Color(0xFFFFD60A),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A3200),
    onPrimaryContainer = Color(0xFFFFE680),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF06302B),
    onSecondaryContainer = Color(0xFFB9F5EC),
    tertiary = Color(0xFFFFD60A),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFD1D1D6),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0C),
    surfaceContainer = Color(0xFF111113),
    surfaceContainerHigh = Color(0xFF1A1A1C),
    surfaceContainerHighest = Color(0xFF232326),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF6B6B)
)

private val CadenceShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val CadenceTypography = Typography(
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
)

/** Palette des couleurs d'accords proposées dans les réglages d'affichage. */
val ChordColorNames = listOf("Ambre", "Indigo", "Menthe", "Rose", "Bleu")

fun chordPalette(dark: Boolean): List<Color> =
    if (dark) {
        listOf(Color(0xFFFBBF24), Color(0xFFA5B4FC), Color(0xFF5EEAD4), Color(0xFFF9A8D4), Color(0xFF93C5FD))
    } else {
        listOf(Color(0xFFC2410C), Color(0xFF4F46E5), Color(0xFF0F766E), Color(0xFFBE185D), Color(0xFF1D4ED8))
    }

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Palette proposée pour la couleur de l'application (ARGB). */
val AccentChoices = listOf(
    0xFF4F46E5.toInt() to "Indigo", 0xFF0D9488.toInt() to "Turquoise", 0xFFD97706.toInt() to "Orange",
    0xFFDB2777.toInt() to "Rose", 0xFF2563EB.toInt() to "Bleu", 0xFF16A34A.toInt() to "Vert",
    0xFFDC2626.toInt() to "Rouge", 0xFF7C3AED.toInt() to "Violet", 0xFF0891B2.toInt() to "Cyan",
    0xFF78716C.toInt() to "Pierre"
)

/** Fonds proposés (ARGB) : le nom suffit à l'utilisateur ; 0 = fond du thème. */
val BackgroundChoices = listOf(
    0xFFFFFFFF.toInt() to "Blanc", 0xFFFAF8F5.toInt() to "Papier", 0xFFFFF7D6.toInt() to "Crème",
    0xFFE8F1FF.toInt() to "Ciel", 0xFF1E293B.toInt() to "Ardoise", 0xFF0E1015.toInt() to "Nuit",
    0xFF000000.toInt() to "Noir"
)

val TextChoices = listOf(
    0xFF1C1B22.toInt() to "Encre", 0xFF000000.toInt() to "Noir", 0xFFFFFFFF.toInt() to "Blanc",
    0xFFE7E5EC.toInt() to "Gris clair", 0xFFFFE680.toInt() to "Jaune pâle"
)

private fun readableOn(c: Color): Color = if (c.luminance() > 0.5f) Color.Black else Color.White

private fun withCustomColors(base: ColorScheme, accent: Int, background: Int, text: Int): ColorScheme {
    var s = base
    if (accent != 0) {
        val a = Color(accent)
        val container = androidx.compose.ui.graphics.lerp(a, s.surface, if (s.surface.luminance() < 0.5f) 0.65f else 0.8f)
        s = s.copy(primary = a, onPrimary = readableOn(a), primaryContainer = container, onPrimaryContainer = readableOn(container))
    }
    if (background != 0) {
        val b = Color(background)
        val on = if (text != 0) Color(text) else readableOn(b)
        s = s.copy(
            background = b, surface = b, onBackground = on, onSurface = on,
            surfaceContainerLowest = b,
            surfaceContainerLow = androidx.compose.ui.graphics.lerp(b, on, 0.04f),
            surfaceContainer = androidx.compose.ui.graphics.lerp(b, on, 0.07f),
            surfaceContainerHigh = androidx.compose.ui.graphics.lerp(b, on, 0.10f),
            surfaceContainerHighest = androidx.compose.ui.graphics.lerp(b, on, 0.14f),
            surfaceVariant = androidx.compose.ui.graphics.lerp(b, on, 0.12f),
            onSurfaceVariant = on.copy(alpha = 0.72f)
        )
    } else if (text != 0) {
        val on = Color(text)
        s = s.copy(onBackground = on, onSurface = on)
    }
    return s
}

@Composable
fun CadenceTheme(
    themeMode: Int,
    accentColor: Int = 0,
    backgroundColor: Int = 0,
    textColor: Int = 0,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemInDarkTheme()
    }
    val baseScheme: ColorScheme = when {
        themeMode == 3 -> StageColors
        dark -> DarkColors
        else -> LightColors
    }
    val scheme = withCustomColors(baseScheme, accentColor, backgroundColor, textColor)
    // Barres d'état : icônes sombres si le fond est clair, quel que soit le thème.
    val darkBars = scheme.background.luminance() < 0.5f
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkBars
                controller.isAppearanceLightNavigationBars = !darkBars
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = CadenceTypography,
        shapes = CadenceShapes,
        content = content
    )
}
