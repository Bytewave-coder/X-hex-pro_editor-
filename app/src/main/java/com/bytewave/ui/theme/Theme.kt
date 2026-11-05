package com.bytewave.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
  isDarkMode: Boolean = true,
  appThemeColor: com.bytewave.AppThemeColor = com.bytewave.AppThemeColor.CYBERPUNK,
  content: @Composable () -> Unit,
) {
  val colorScheme = when (appThemeColor) {
      com.bytewave.AppThemeColor.CYBERPUNK -> if (isDarkMode) CyberpunkColorScheme else LightCyberpunkColorScheme
      com.bytewave.AppThemeColor.MONOKAI -> if (isDarkMode) MonokaiColorScheme else LightMonokaiColorScheme
      com.bytewave.AppThemeColor.TERMINAL -> if (isDarkMode) TerminalColorScheme else LightTerminalColorScheme
      com.bytewave.AppThemeColor.DRACULA -> if (isDarkMode) DraculaColorScheme else LightDraculaColorScheme
      com.bytewave.AppThemeColor.NORD -> if (isDarkMode) NordColorScheme else LightNordColorScheme
      com.bytewave.AppThemeColor.AMOLED -> if (isDarkMode) AmoledColorScheme else LightAmoledColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
