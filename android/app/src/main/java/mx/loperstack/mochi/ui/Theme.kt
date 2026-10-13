package mx.loperstack.mochi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The island's palette (windows/src/style.css), so the phone feels like the PC. */
object Ink {
    val Ground = Color(0xFF0B0C0E)
    val Card = Color(0xFF141518)
    val CardHigh = Color(0xFF17181C)
    val Hairline = Color(0xFF1D1F24)
    val Text = Color(0xFFF5F6F8)
    val Dim = Color(0xFF8E939C)
    val Dim2 = Color(0xFF6B7079)
    val Violet = Color(0xFFA78BFA)
    val VioletSoft = Color(0xFFC4B5FD)
    val VioletDeep = Color(0xFF1A1726)
    val VioletLine = Color(0xFF2A2440)
    val Green = Color(0xFF4ADE80)
    val GreenDeep = Color(0xFF06260F)
    val Amber = Color(0xFFF5A524)
    val Red = Color(0xFFF4505E)
}

@Composable
fun MochiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.Violet,
            onPrimary = Color(0xFF14102A),
            secondary = Ink.Green,
            background = Ink.Ground,
            onBackground = Ink.Text,
            surface = Ink.Card,
            onSurface = Ink.Text,
            surfaceVariant = Ink.CardHigh,
            onSurfaceVariant = Ink.Dim,
            outline = Ink.Hairline,
        ),
        content = content,
    )
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
