package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = EcoGreen, 
    secondary = LightGreyText, 
    tertiary = EcoGreen,
    background = TrueBlack,
    surface = DarkGrey,
    onBackground = LightGreyText,
    onSurface = LightGreyText
  )

private val LightColorScheme = DarkColorScheme // Force dark for battery saving

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force true dark 
  dynamicColor: Boolean = false, // Disable dynamic colors to ensure pure black is used
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
