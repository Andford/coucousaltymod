// Mochi on the wrist: Mochi himself, the tasks (check them off, dictate new
// ones), breathing with a gentle buzz on each turn and your pulse, and the
// batteries. Swipe right to go back.

package mx.loperstack.mochi.wear

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.wellness.BREATHE_MINUTES
import mx.loperstack.mochi.wellness.BreathSession
import mx.loperstack.mochi.wellness.Breathing
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.Snapshot
import mx.loperstack.mochi.wellness.breathFrame
import mx.loperstack.mochi.wellness.clock
import mx.loperstack.mochi.wellness.left
import mx.loperstack.mochi.wellness.minutesUntil
import mx.loperstack.mochi.wellness.nextEvents
import mx.loperstack.mochi.wellness.orderedTasks
import mx.loperstack.mochi.wellness.pendingCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Violet = Color(0xFFA78BFA)
private val VioletSoft = Color(0xFFC4B5FD)
private val Green = Color(0xFF4ADE80)
private val Dim = Color(0xFFA5A9B1)
private val Card = Color(0xFF17181C)

class WearActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        Sounds.init(this)
        setContent { WearApp() }
    }

    override fun onResume() {
        super.onResume()
        WatchStore.pull()
    }
}

@Composable
fun WearApp() {
    val nav = rememberSwipeDismissableNavController()
    val snap by WatchStore.state.collectAsState()
    var jump by remember { mutableIntStateOf(0) }
    MaterialTheme(colors = Colors(primary = Violet, onPrimary = Color(0xFF14102A), secondary = Green)) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") { Home(snap, jump, onPoke = { jump++; Sounds.play(listOf("wink", "pop", "love").random()) }, go = { nav.navigate(it) }) }
            composable("tasks") { Tasks(snap) { jump++ } }
            composable("breathe") { Breathe(snap) }
            composable("battery") { Batteries(snap) }
        }
    }
}

// ── Mochi ─────────────────────────────────────────────────────────────────────

@Composable
fun rememberNow(periodMs: Long = 1000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(periodMs - now % periodMs)
        }
    }
    return now
}

private fun hm(ms: Long) = SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(ms))

@Composable
fun Home(snap: Snapshot, jump: Int, onPoke: () -> Unit, go: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val now = rememberNow()
    val next = nextEvents(snap.events, now).firstOrNull { !it.allDay }
    val focusing = snap.pomodoro.phase == Phase.FOCUS
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Mochi(
                    Modifier.width(130.dp).height(108.dp).clickable(onClick = onPoke),
                    color = Color(snap.mochiColor),
                    mood = if (focusing) Mood.FOCUS else Mood.IDLE,
                    jump = jump,
                )
            }
            item {
                if (next != null) {
                    val min = minutesUntil(next.begin, now)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (min <= 0) "Ahora" else if (min < 60) "En $min min" else hm(next.begin),
                            color = VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(next.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                } else {
                    Text(if (snap.at == 0L) "Abre Mochi en tu teléfono" else "Nada más por hoy", color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
            if (snap.pomodoro.phase != Phase.IDLE) {
                item {
                    Chip(
                        onClick = { WatchStore.pomodoro() },
                        label = { Text(if (focusing) "Enfoque · ${clock(snap.pomodoro.left(now))}" else "Descanso · ${clock(snap.pomodoro.left(now))}") },
                        secondaryLabel = { Text("Toca para pausar o seguir") },
                        colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF2D2547)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = { go("tasks") },
                    label = { Text("Pendientes · ${pendingCount(snap.tasks)}") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { go("breathe") },
                    label = { Text("Respirar") },
                    secondaryLabel = { Text("Con vibración y pulso") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { go("battery") },
                    label = { Text("Baterías") },
                    secondaryLabel = { Text(if (snap.phoneBattery >= 0) "Teléfono ${snap.phoneBattery}%" else "Sin datos del teléfono") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (snap.pomodoro.phase == Phase.IDLE) {
                item {
                    Chip(
                        onClick = { WatchStore.pomodoro() },
                        label = { Text("Iniciar Pomodoro") },
                        secondaryLabel = { Text("25 min de enfoque") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ── Pendientes ────────────────────────────────────────────────────────────────

@Composable
fun Tasks(snap: Snapshot, onDone: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { WatchStore.addTask(it) }
    }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text("Pendientes · ${pendingCount(snap.tasks)}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            items(orderedTasks(snap.tasks).take(30)) { t ->
                ToggleChip(
                    checked = t.done,
                    onCheckedChange = {
                        WatchStore.toggleTask(t.id)
                        if (!t.done) onDone()
                    },
                    label = { Text(t.text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    toggleControl = { Icon(imageVector = ToggleChipDefaults.checkboxIcon(checked = t.done), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = {
                        dictate.launch(
                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                                .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué hay que hacer?"),
                        )
                    },
                    label = { Text("Dictar tarea") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Respirar ──────────────────────────────────────────────────────────────────

/** Live heart rate from the watch's sensor while `active`; 0 until it reads. */
@Composable
fun rememberHeartRate(active: Boolean): Int {
    val context = LocalContext.current
    var bpm by remember { mutableIntStateOf(0) }
    DisposableEffect(active) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val v = e.values.firstOrNull()?.toInt() ?: 0
                if (v in 30..220) bpm = v
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (active && granted && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(listener) }
    }
    return bpm
}

private fun buzz(context: Context, inhale: Boolean) {
    val vm = context.getSystemService(VibratorManager::class.java) ?: return
    val effect = if (inhale) VibrationEffect.createOneShot(70, 120)
    else VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), intArrayOf(0, 90, 0, 90), -1)
    runCatching { vm.defaultVibrator.vibrate(effect) }
}

@Composable
fun Breathe(snap: Snapshot) {
    val context = LocalContext.current
    var b by remember { mutableStateOf(Breathing()) }
    var summary by remember { mutableStateOf<BreathSession?>(null) }
    val active = b.startedAt != null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) withFrameMillis { now = System.currentTimeMillis() } }
    val f = breathFrame(b, now)
    val bpm = rememberHeartRate(active)
    var hrStart by remember { mutableIntStateOf(0) }
    var hrMin by remember { mutableIntStateOf(0) }
    LaunchedEffect(bpm) {
        if (!active || bpm <= 0) return@LaunchedEffect
        if (hrStart == 0) hrStart = bpm
        hrMin = if (hrMin == 0) bpm else minOf(hrMin, bpm)
    }
    // A gentle buzz on each turn: one for in, two for out.
    var lastInhale by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(f.inhaling, active) {
        if (!active) { lastInhale = null; return@LaunchedEffect }
        if (lastInhale != f.inhaling) {
            lastInhale = f.inhaling
            buzz(context, f.inhaling)
        }
    }
    LaunchedEffect(f.done) {
        val start = b.startedAt
        if (f.done && start != null) {
            val s = BreathSession(start, b.minutes, f.breaths, hrStart, bpm, hrMin, device = "watch")
            WatchStore.session(s)
            summary = s
            b = Breathing()
            Sounds.play("love")
        }
    }
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    val askSensors = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    if (active) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = f.progress, modifier = Modifier.fillMaxSize().padding(4.dp),
                startAngle = 290f, endAngle = 250f, indicatorColor = Violet, trackColor = Color(0x14FFFFFF), strokeWidth = 5.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Quedan ${clock(f.left)}", color = Dim, fontSize = 12.sp)
                Mochi(Modifier.width(120.dp).height(98.dp), color = Color(snap.mochiColor), mood = Mood.BREATHING, breathFill = f.fill)
                Text(if (f.inhaling) "Inhala…" else "Exhala…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (bpm > 0) "♥ $bpm lpm" else "Midiendo pulso…", color = if (bpm > 0) Color(0xFFFF7896) else Dim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Terminar", color = Dim, fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).clickable { b = Breathing(); Sounds.play("close") }.padding(8.dp),
                )
            }
        }
        return
    }

    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            summary?.let { s ->
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("¡Bien hecho!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${s.minutes} min · ${s.breaths} respiraciones", color = Dim, fontSize = 13.sp)
                        if (s.hrStart > 0 && s.hrEnd > 0) {
                            Text("♥ ${s.hrStart} → ${s.hrEnd} lpm", color = Color(0xFFFF7896), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Guardado en tu teléfono", color = Dim, fontSize = 11.sp)
                    }
                }
            }
            item { Text("Respira con Mochi", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            items(BREATHE_MINUTES) { m ->
                Chip(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
                            askSensors.launch(Manifest.permission.BODY_SENSORS)
                        }
                        summary = null
                        hrStart = 0
                        hrMin = 0
                        b = Breathing(System.currentTimeMillis(), m)
                        Sounds.play("open")
                    },
                    label = { Text("$m min") },
                    secondaryLabel = { Text("4 s inhalar · 6 s exhalar") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Baterías ──────────────────────────────────────────────────────────────────

@Composable
fun Batteries(snap: Snapshot) {
    val context = LocalContext.current
    val now = rememberNow(30_000)
    val watch = remember(now) { context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Baterías", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BatteryRing(if (snap.phoneBattery >= 0) snap.phoneBattery else null, if (snap.phoneCharging) "Teléfono ⚡" else "Teléfono", Green)
                BatteryRing(watch, "Reloj", Violet)
            }
            Text("Tablet y PC llegan con la Fase 2", color = Color(0xFF6B7079), fontSize = 10.sp)
        }
    }
}

@Composable
fun BatteryRing(level: Int?, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = (level ?: 0) / 100f, modifier = Modifier.fillMaxSize(),
                indicatorColor = if ((level ?: 100) <= 15) Color(0xFFF4505E) else color,
                trackColor = Color(0x14FFFFFF), strokeWidth = 5.dp,
            )
            Text(level?.toString() ?: "–", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(label, color = Dim, fontSize = 11.sp)
    }
}
