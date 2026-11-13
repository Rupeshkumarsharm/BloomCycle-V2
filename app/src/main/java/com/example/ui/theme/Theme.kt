package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BloomLightColorScheme = lightColorScheme(
  primary = TerracottaRose,
  onPrimary = Color.White,
  primaryContainer = SoftBlushContainer,
  onPrimaryContainer = OnBlushContainer,
  secondary = BotanicalSage,
  onSecondary = Color.White,
  secondaryContainer = SoftSageContainer,
  onSecondaryContainer = OnSageContainer,
  tertiary = WarmAmber,
  onTertiary = Color.White,
  tertiaryContainer = SoftAmberContainer,
  onTertiaryContainer = OnAmberContainer,
  background = CreamBackground,
  onBackground = PlumEspresso,
  surface = CreamSurface,
  onSurface = PlumEspresso,
  surfaceVariant = PetalCard,
  onSurfaceVariant = MutedTaupe,
  surfaceContainerLow = CreamSurface,
  surfaceContainer = PetalCard,
  surfaceContainerHigh = PetalCardHigh,
  outline = SoftClayOutline,
  outlineVariant = SoftClayOutline
)

private val BloomDarkColorScheme = darkColorScheme(
  primary = SoftCoralRose,
  onPrimary = Color(0xFF3E100E),
  primaryContainer = DarkTerracottaContainer,
  onPrimaryContainer = DarkOnRoseContainer,
  secondary = DarkMutedSage,
  onSecondary = Color(0xFF15291B),
  secondaryContainer = DarkSageContainer,
  onSecondaryContainer = DarkOnSageContainer,
  tertiary = DarkWarmAmber,
  onTertiary = Color(0xFF382008),
  tertiaryContainer = DarkAmberContainer,
  onTertiaryContainer = DarkOnAmberContainer,
  background = DarkCocoaBackground,
  onBackground = WarmIvory,
  surface = DarkEspressoSurface,
  onSurface = WarmIvory,
  surfaceVariant = DarkEspressoCard,
  onSurfaceVariant = SoftRoseGray,
  surfaceContainerLow = DarkEspressoSurface,
  surfaceContainer = DarkEspressoCard,
  surfaceContainerHigh = Color(0xFF3A292E),
  outline = DarkClayOutline,
  outlineVariant = DarkClayOutline
)

private val BloomShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(18.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun BloomCycleTheme(
  darkThemePreference: String = "SYSTEM",
  content: @Composable () -> Unit
) {
  val useDark = when (darkThemePreference) {
    "DARK" -> true
    "LIGHT" -> false
    else -> isSystemInDarkTheme()
  }

  MaterialTheme(
    colorScheme = if (useDark) BloomDarkColorScheme else BloomLightColorScheme,
    typography = Typography,
    shapes = BloomShapes,
    content = content
  )
}
