// Ajustes: you (your name), Mochi's colour, the theme, sounds, Google Tasks,
// your devices — the little ecosystem — and the welcome again. Also the
// pieces the welcome reuses (theme cards, the devices list), and the aurora
// that softly moves behind Mochi.

package mx.loperstack.mochi.ui

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.loperstack.mochi.data.AppData
import mx.loperstack.mochi.data.GoogleTasks
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.mochi.Sounds
import kotlin.math.cos
import kotlin.math.sin

// ── Aurora ────────────────────────────────────────────────────────────────────

/** Three soft lights in Mochi's colour and the theme's, drifting slowly. */
@Composable
fun Aurora(color: Color, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, (Math.PI * 2).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    val accent = Ink.Violet
    val alpha = if (Ink.Dark) 0.30f else 0.22f
    Canvas(modifier.blur(40.dp)) {
        val w = size.width
        val h = size.height
        val lights = listOf(
            Triple(color, Offset(w * (0.30f + 0.12f * cos(t)), h * (0.35f + 0.10f * sin(t))), w * 0.55f),
            Triple(accent, Offset(w * (0.75f + 0.10f * sin(t * 1.3f)), h * (0.30f + 0.12f * cos(t))), w * 0.45f),
            Triple(color, Offset(w * (0.55f + 0.15f * sin(t + 2f)), h * (0.80f + 0.08f * cos(t * 0.7f))), w * 0.50f),
        )
        for ((c, center, r) in lights) {
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = alpha), Color.Transparent), center, r), r, center)
        }
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink.Dim2, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
}

@Composable
fun NameField(name: String, onName: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = name, onValueChange = { onName(it.take(30)) }, modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Tu nombre", color = Ink.Dim2) }, singleLine = true, shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
            focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
        ),
    )
}

/** Three cards, each painted in the colours it would give the app. */
@Composable
fun ThemeCards(current: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    val light = remember { dynamicLightColorScheme(context) }
    val dark = remember { dynamicDarkColorScheme(context) }
    val options = listOf(
        Triple("pixel", "Como mi Pixel", listOf(light.surface, light.primary, dark.surface)),
        Triple("pixelDark", "Pixel oscuro", listOf(dark.surface, dark.primary, dark.primaryContainer)),
        Triple("mochi", "Mochi clásico", listOf(Color(0xFF0B0C0E), Color(0xFFA78BFA), Color(0xFF17181C))),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for ((id, label, colors) in options) {
            val on = id == current
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
                    .border(if (on) 2.dp else 1.dp, if (on) Ink.Violet else Ink.Hairline, RoundedCornerShape(20.dp))
                    .clickable { onPick(id); Sounds.play("pop") }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(colors[0], colors[2]))),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(22.dp).clip(CircleShape).background(colors[1])) }
                Spacer(Modifier.height(8.dp))
                Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) Ink.Text else Ink.Dim)
            }
        }
    }
}

/** The watch's name if one is linked right now, null if not. */
suspend fun linkedWatch(context: Context): String? = withContext(Dispatchers.IO) {
    runCatching { Tasks.await(Wearable.getNodeClient(context).connectedNodes).firstOrNull()?.displayName }.getOrNull()
}

@Composable
fun DeviceRow(icon: String, title: String, status: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(Ink.Card)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 22.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Label(status, Ink.Dim, 12.sp)
        }
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Ink.Green else Ink.Dim2))
    }
}

/** Mochi on all your devices: the watch, this phone, the tablet and the laptop. */
@Composable
fun Devices(google: GoogleTasks.Status) {
    val context = LocalContext.current
    var watch by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { watch = linkedWatch(context); checked = true }
    val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
    DeviceRow(
        "⌚", watch ?: "Reloj",
        when { !checked -> "Buscando…"; watch != null -> "Conectado · pendientes, respirar, ánimo y pulso"; else -> "Sin reloj ahora · abre Mochi en tu Pixel Watch" },
        watch != null,
    )
    DeviceRow(
        if (tablet) "📟" else "📱", if (tablet) "Esta tablet" else "Este teléfono",
        "Aquí vive tu diario, tu historial y tus widgets", true,
    )
    DeviceRow(
        if (tablet) "📱" else "📟", if (tablet) "Tu teléfono" else "Tu tablet",
        if (google.connected) "Las mismas tareas por Google Tasks" else "Conecta Google Tasks para compartir tareas", google.connected,
    )
    DeviceRow(
        "💻", "Tu laptop (Coucou)",
        if (google.connected) "Mismas tareas que la isla · el puente directo llega pronto" else "Con Google Tasks, la isla y Mochi comparten tareas",
        google.connected,
    )
}

// ── Ajustes ───────────────────────────────────────────────────────────────────

@Composable
fun SettingsSheet(
    data: AppData, color: Color, google: GoogleTasks.Status,
    connectGoogle: () -> Unit, syncGoogle: () -> Unit, disconnectGoogle: () -> Unit, replayWelcome: () -> Unit,
) {
    var name by remember { mutableStateOf(data.name) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Ajustes", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            SectionTitle("Tú")
            NameField(name, { name = it; Store.setName(it) })
        }
        Spacer(Modifier.height(18.dp))
        ColorPicker(data.mochiColor, color) { Store.setColor(it) }
        Column(Modifier.padding(horizontal = 24.dp)) {
            SectionTitle("Tema")
            ThemeCards(data.theme) { Store.setTheme(it) }
            SectionTitle("Sonidos")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sonidos de Mochi", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Label("Los mismos de la PC, en el teléfono y el reloj", Ink.Dim2)
                }
                Switch(
                    checked = data.soundOn, onCheckedChange = { Store.setSound(it); if (it) Sounds.play("pop") },
                    colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet),
                )
            }
            SectionTitle("Tus dispositivos")
            Devices(google)
        }
        Spacer(Modifier.height(10.dp))
        GoogleTasksRow(google, connectGoogle, syncGoogle, disconnectGoogle)
        Text(
            "Ver la bienvenida otra vez", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 28.dp).clickable(onClick = replayWelcome),
        )
    }
}
