package mx.loperstack.mochi.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The app's colours. They follow the theme you pick: your Pixel's wallpaper
 * colours (Material You, light or dark as the phone is), or the island's own
 * dark palette (windows/src/style.css). State-backed, so a change of wallpaper
 * or theme repaints everything, Canvas drawings included.
 */
object Ink {
    var Ground by mutableStateOf(Color(0xFF0B0C0E))
    var Card by mutableStateOf(Color(0xFF141518))
    var CardHigh by mutableStateOf(Color(0xFF17181C))
    var Hairline by mutableStateOf(Color(0xFF1D1F24))
    var Text by mutableStateOf(Color(0xFFF5F6F8))
    var Dim by mutableStateOf(Color(0xFF8E939C))
    var Dim2 by mutableStateOf(Color(0xFF6B7079))
    var Violet by mutableStateOf(Color(0xFFA78BFA))
    var VioletSoft by mutableStateOf(Color(0xFFC4B5FD))
    var VioletDeep by mutableStateOf(Color(0xFF1A1726))
    var VioletLine by mutableStateOf(Color(0xFF2A2440))
    var Green by mutableStateOf(Color(0xFF4ADE80))
    var GreenSoft by mutableStateOf(Color(0xFF86EFAC))
    var GreenDeep by mutableStateOf(Color(0xFF06260F))
    var Amber by mutableStateOf(Color(0xFFF5A524))
    var Red by mutableStateOf(Color(0xFFF4505E))
    /** Empty tracks and faint fills: the text colour, barely there. */
    var Track by mutableStateOf(Color(0x14FFFFFF))
    var Dark by mutableStateOf(true)

    internal fun classic() {
        Ground = Color(0xFF0B0C0E); Card = Color(0xFF141518); CardHigh = Color(0xFF17181C); Hairline = Color(0xFF1D1F24)
        Text = Color(0xFFF5F6F8); Dim = Color(0xFF8E939C); Dim2 = Color(0xFF6B7079)
        Violet = Color(0xFFA78BFA); VioletSoft = Color(0xFFC4B5FD); VioletDeep = Color(0xFF1A1726); VioletLine = Color(0xFF2A2440)
        Green = Color(0xFF4ADE80); GreenSoft = Color(0xFF86EFAC); GreenDeep = Color(0xFF06260F); Red = Color(0xFFF4505E)
        Track = Color(0x14FFFFFF); Dark = true
    }

    internal fun from(cs: ColorScheme, dark: Boolean) {
        Ground = cs.surface; Card = cs.surfaceContainer; CardHigh = cs.surfaceContainerHigh; Hairline = cs.outlineVariant
        Text = cs.onSurface; Dim = cs.onSurfaceVariant; Dim2 = cs.outline
        Violet = cs.primary; VioletSoft = cs.primary; VioletDeep = cs.primaryContainer; VioletLine = cs.primaryContainer
        Green = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
        GreenSoft = if (dark) Color(0xFF86EFAC) else Color(0xFF15803D)
        GreenDeep = if (dark) Color(0xFF06260F) else Color.White
        Red = cs.error
        Track = cs.onSurface.copy(alpha = 0.08f); Dark = dark
    }
}

private val ClassicScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF14102A),
    secondary = Color(0xFF4ADE80),
    background = Color(0xFF0B0C0E),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF17181C),
    onSurfaceVariant = Color(0xFF8E939C),
    outline = Color(0xFF1D1F24),
    surfaceContainer = Color(0xFF141518),
    surfaceContainerHigh = Color(0xFF17181C),
)

/**
 * Google Sans, the Pixel's own typeface (the Diario/Journal look), when the
 * phone has it — every Pixel does; elsewhere the system font.
 */
private fun pixelFont(weight: Int): android.graphics.Typeface {
    for (family in listOf("google-sans-flex", "google-sans", "google-sans-text")) {
        val base = android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
        if (base != android.graphics.Typeface.DEFAULT) return android.graphics.Typeface.create(base, weight, false)
    }
    return android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, weight, false)
}

private val PixelSans = androidx.compose.ui.text.font.FontFamily(pixelFont(400))

private val MochiType = androidx.compose.material3.Typography().let { t ->
    fun androidx.compose.ui.text.TextStyle.p() = copy(fontFamily = PixelSans)
    t.copy(
        displayLarge = t.displayLarge.p(), displayMedium = t.displayMedium.p(), displaySmall = t.displaySmall.p(),
        headlineLarge = t.headlineLarge.p(), headlineMedium = t.headlineMedium.p(), headlineSmall = t.headlineSmall.p(),
        titleLarge = t.titleLarge.p(), titleMedium = t.titleMedium.p(), titleSmall = t.titleSmall.p(),
        bodyLarge = t.bodyLarge.p(), bodyMedium = t.bodyMedium.p(), bodySmall = t.bodySmall.p(),
        labelLarge = t.labelLarge.p(), labelMedium = t.labelMedium.p(), labelSmall = t.labelSmall.p(),
    )
}

/** Material 3 Expressive: generous, friendly corners. */
private val MochiShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

/**
 * @param theme "pixel" (wallpaper colours, follows the phone's light/dark),
 *   "pixelDark" (wallpaper colours, always dark) or "mochi" (the island's).
 */
@Composable
fun MochiTheme(theme: String = "pixel", content: @Composable () -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = theme != "pixel" || systemDark
    val scheme = remember(theme, dark, context.resources.configuration) {
        when {
            theme == "mochi" -> ClassicScheme
            dark -> dynamicDarkColorScheme(context)
            else -> dynamicLightColorScheme(context)
        }
    }
    if (theme == "mochi") Ink.classic() else Ink.from(scheme, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val bars = WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = !dark
            bars.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(colorScheme = scheme, typography = MochiType, shapes = MochiShapes, content = content)
}

/** Stroke icons from the mockup, on a 24×24 grid. */
object Glyph {
    private fun stroke(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Home = stroke("home", "M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Tasks = stroke("tasks", "M9 11l3 3 8-8", "M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h9")
    val Focus = stroke("focus", "M12 5a8 8 0 1 0 0.01 0z", "M12 9v4l2 2", "M9 2h6")
    val Breathe = stroke("breathe", "M12 4a8 8 0 1 0 0.01 0z", "M12 8.5a3.5 3.5 0 1 0 0.01 0z")
    val Diary = stroke("diary", "M6 3h11a2 2 0 0 1 2 2v16H8a2 2 0 0 1-2-2z", "M6 17a2 2 0 0 1 2-2h11", "M10 7h5M10 10h3")
    val Heart = stroke("heart", "M12 20s-7-4.5-9.3-9A4.8 4.8 0 0 1 12 6a4.8 4.8 0 0 1 9.3 5c-2.3 4.5-9.3 9-9.3 9z")
    val Moon = stroke("moon", "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z")
    val Pc = stroke("pc", "M5 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M8 20h8", "M12 16v4")
    val Gear = stroke("gear", "M12 9a3 3 0 1 0 0.01 0z", "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val Video = stroke("video", "M15 10l5-3v10l-5-3z", "M5 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z")
    val Back = stroke("back", "M15 18l-6-6 6-6")
    val Skip = stroke("skip", "M5 6l7 6-7 6z", "M13 6l7 6-7 6z")
    val Reset = stroke("reset", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5")
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Close = stroke("close", "M6 6l12 12", "M18 6L6 18")
    val Calendar = stroke("calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M8 3v4M16 3v4M3 10h18")
}
