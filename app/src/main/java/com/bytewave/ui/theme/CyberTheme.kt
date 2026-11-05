package com.bytewave.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val CyberBlack = Color(0xFF050505)
val CyberGray = Color(0xFF0A0A0A)
val CyberBorder = Color(0x19FFFFFF) // white/10
val NeonCyan = Color(0xFF06B6D4)
val OffsetColor = Color(0xFF155E75)
val NeonPurple = Color(0xFFEAB308) // Changed to yellow-400 for modified text
val TerminalGreen = Color(0xFF22C55E)
val TextPrimary = Color(0xFFD4D4D8)
val TextSecondary = Color(0x66FFFFFF) // white/40

val CyberpunkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = TerminalGreen,
    background = CyberBlack,
    surface = CyberGray,
    onPrimary = CyberBlack,
    onSecondary = Color.White,
    onTertiary = CyberBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF16161A),
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder
)

val LightCyberpunkColorScheme = lightColorScheme(
    primary = Color(0xFF0891B2), // cyan-600
    secondary = Color(0xFFD97706), // amber-600
    tertiary = Color(0xFF16A34A), // green-600
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFF1F5F9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

val MonokaiColorScheme = darkColorScheme(
    primary = Color(0xFFFD971F), // Orange
    secondary = Color(0xFFF92672), // Pink
    tertiary = Color(0xFFA6E22E), // Green
    background = Color(0xFF272822),
    surface = Color(0xFF1E1F1A),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF8F8F2),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF2C2D27),
    onSurfaceVariant = Color(0xFF75715E),
    outline = Color(0x40FFFFFF)
)

val LightMonokaiColorScheme = lightColorScheme(
    primary = Color(0xFFFD971F),
    secondary = Color(0xFFF92672),
    tertiary = Color(0xFFA6E22E),
    background = Color(0xFFF8F8F2),
    surface = Color(0xFFFFFFFF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFF272822),
    onSurface = Color(0xFF272822),
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color(0xFF75715E),
    outline = Color(0xFFCCCCCC)
)

val TerminalColorScheme = darkColorScheme(
    primary = Color(0xFF00FF00), // Lime green
    secondary = Color(0xFF00FF00),
    tertiary = Color(0xFF00FF00),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFF00FF00),
    onSurface = Color(0xFF00FF00),
    surfaceVariant = Color(0xFF151515),
    onSurfaceVariant = Color(0x8000FF00),
    outline = Color(0x4000FF00)
)

val LightTerminalColorScheme = lightColorScheme(
    primary = Color(0xFF008000),
    secondary = Color(0xFF008000),
    tertiary = Color(0xFF008000),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF0F0F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFCCCCCC),
    onSurfaceVariant = Color(0xFF008000),
    outline = Color(0xFFB3B3B3)
)

val DraculaColorScheme = darkColorScheme(
    primary = Color(0xFFBD93F9), // Purple
    secondary = Color(0xFFFF79C6), // Pink
    tertiary = Color(0xFF50FA7B), // Green
    background = Color(0xFF282A36),
    surface = Color(0xFF1E1F29),
    onPrimary = Color(0xFF282A36),
    onSecondary = Color(0xFF282A36),
    onTertiary = Color(0xFF282A36),
    onBackground = Color(0xFFF8F8F2),
    onSurface = Color(0xFFF8F8F2),
    surfaceVariant = Color(0xFF44475A),
    onSurfaceVariant = Color(0xFF6272A4),
    outline = Color(0x40FFFFFF)
)

val LightDraculaColorScheme = lightColorScheme(
    primary = Color(0xFF7C4DFF), // Purple
    secondary = Color(0xFFE040FB), // Pink
    tertiary = Color(0xFF00C853), // Green
    background = Color(0xFFF8F9FA), // Clean light slate background
    surface = Color(0xFFECEFF1),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF282A36),
    onSurface = Color(0xFF282A36),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF6272A4),
    outline = Color(0xFFD1D5DB)
)

val NordColorScheme = darkColorScheme(
    primary = Color(0xFF88C0D0), // Frost Blue
    secondary = Color(0xFF81A1C1),
    tertiary = Color(0xFFA3BE8C), // Green
    background = Color(0xFF2E3440),
    surface = Color(0xFF3B4252),
    onPrimary = Color(0xFF2E3440),
    onSecondary = Color(0xFF2E3440),
    onTertiary = Color(0xFF2E3440),
    onBackground = Color(0xFFD8DEE9),
    onSurface = Color(0xFFE5E9F0),
    surfaceVariant = Color(0xFF434C5E),
    onSurfaceVariant = Color(0xFFD8DEE9).copy(alpha=0.6f),
    outline = Color(0xFF4C566A)
)

val LightNordColorScheme = lightColorScheme(
    primary = Color(0xFF5E81AC), // Frost Blue
    secondary = Color(0xFF81A1C1),
    tertiary = Color(0xFF4C566A),
    background = Color(0xFFECEFF4), // Snow Storm Light background
    surface = Color(0xFFE5E9F0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF2E3440),
    onSurface = Color(0xFF3B4252),
    surfaceVariant = Color(0xFFD8DEE9),
    onSurfaceVariant = Color(0xFF4C566A),
    outline = Color(0xFFD8DEE9)
)

val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFFE2E2E2), // White 
    secondary = Color(0xFF999999), 
    tertiary = Color(0xFFE2E2E2), 
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0F0F0F),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF333333)
)

val LightAmoledColorScheme = lightColorScheme(
    primary = Color(0xFF000000), // Pure Black high contrast
    secondary = Color(0xFF666666),
    tertiary = Color(0xFF000000),
    background = Color(0xFFFFFFFF), // Pure White
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF333333),
    outline = Color(0xFFCCCCCC)
)

