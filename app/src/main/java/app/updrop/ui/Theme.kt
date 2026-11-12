package app.updrop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** OLED black + Xbox green. Pure #000 background so the panel's pixels stay off. */
object C {
    val Black = Color(0xFF000000)
    val Card = Color(0xFF070A07)
    val CardHi = Color(0xFF0D120D)
    val Stroke = Color(0xFF172117)
    val Green = Color(0xFF107C10)       // Xbox green
    val Glow = Color(0xFF3DDC3D)        // highlight green
    val Lime = Color(0xFF9BF00B)
    val Text = Color(0xFFE6F0E6)
    val Dim = Color(0xFF7A887A)
    val Faint = Color(0xFF3A463A)
    val Warn = Color(0xFFF2B33D)
    val Bad = Color(0xFFFF5A4E)
}

@Composable
fun UpdropTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Glow, onPrimary = C.Black,
            secondary = C.Green, onSecondary = C.Text,
            background = C.Black, onBackground = C.Text,
            surface = C.Black, onSurface = C.Text,
            surfaceVariant = C.CardHi, onSurfaceVariant = C.Dim,
            surfaceContainer = C.Card, surfaceContainerHigh = C.CardHi, surfaceContainerLow = C.Card,
            surfaceContainerHighest = C.CardHi, surfaceContainerLowest = C.Black,
            outline = C.Stroke, outlineVariant = C.Stroke,
            error = C.Bad,
        ),
        content = content,
    )
}
