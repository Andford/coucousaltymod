// Mochi on the wrist: Mochi himself (tap him, as on the PC), the tasks (check
// them off, dictate new ones), breathing with a gentle buzz on each turn and
// your pulse — started here or from the phone — and the batteries.
//
// Kept light for the watch: Mochi animates on his own Canvas only while he
// moves, clocks tick only where a number shows, and the breathing loop
// recomposes on each inhale/exhale and once a second, not every frame.

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
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.delay
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.mochi.Haptics
import mx.loperstack.mochi.mochi.rememberMusicPlaying
import mx.loperstack.mochi.wellness.MOODS
import mx.loperstack.mochi.wellness.moodOf
import mx.loperstack.mochi.wellness.BREATHE_MINUTES
import mx.loperstack.mochi.wellness.BreathSession
import mx.loperstack.mochi.wellness.Breathing
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.Pomodoro
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
private val Pink = Color(0xFFFF7896)
private val HM = SimpleDateFormat("HH:mm", Locale("es", "MX"))

class WearActivity : ComponentActivity() {
    companion object {
        /** A screen to open: "mood" (from the mood complication). */
        const val EXTRA_SCREEN = "screen"
    }

    private val askOnce = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchStore.init(this)
        Sounds.init(this)
        handle(intent)
        val missing = buildList {
            add(Manifest.permission.BODY_SENSORS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty() && savedInstanceState == null) askOnce.launch(missing.toTypedArray())
        setContent { WearApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** mochi://breathe?at=…&min=… — the phone started a session. */
    private fun handle(intent: Intent?) {
        intent?.getStringExtra(EXTRA_SCREEN)?.let { WatchStore.openScreen.value = it }
        val uri = intent?.data ?: return
        if (uri.scheme != "mochi" || uri.host != "breathe") return
        val at = uri.getQueryParameter("at")?.toLongOrNull() ?: return
        val min = uri.getQueryParameter("min")?.toIntOrNull() ?: return
        if (System.currentTimeMillis() - at < min * 60_000L) WatchStore.breathRequest.value = Breathing(at, min)
        WatchStore.clearBreatheNotice(this)
    }

    override fun onResume() {
        super.onResume()
        WatchStore.foreground = true
        WatchStore.pull()
    }

    override fun onPause() {
        WatchStore.foreground = false
        super.onPause()
    }
}

@Composable
fun WearApp() {
    val nav = rememberSwipeDismissableNavController()
    val snap by WatchStore.state.collectAsState()
    val request by WatchStore.breathRequest.collectAsState()
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(request) {
        if (request != null && nav.currentDestination?.route != "breathe") nav.navigate("breathe")
    }
    val open by WatchStore.openScreen.collectAsState()
    LaunchedEffect(open) {
        val screen = open ?: return@LaunchedEffect
        if (nav.currentDestination?.route != screen) nav.navigate(screen)
        WatchStore.openScreen.value = null
    }
    MaterialTheme(colors = Colors(primary = Violet, onPrimary = Color(0xFF14102A), secondary = Green)) {
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") { Home(snap, jump) { nav.navigate(it) } }
            composable("tasks") { Tasks(snap) { jump++ } }
            composable("breathe") { Breathe(snap) { nav.navigate(it) } }
            composable("history") { BreathHistory(snap) { nav.navigate("session/$it") } }
            composable("session/{at}") { entry -> SessionScreen(entry.arguments?.getString("at")?.toLongOrNull() ?: 0L, snap) }
            composable("sleep") { SleepReport(snap) }
            composable("battery") { Batteries(snap) }
            composable("mood") { MoodPicker(snap) { nav.popBackStack() } }
        }
    }
}

// ── Clocks ────────────────────────────────────────────────────────────────────

/** The time, refreshed on each whole `periodMs`. */
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

/** Only this text ticks each second, not the screen around it. */
@Composable
fun PomodoroLabel(p: Pomodoro) {
    val now = rememberNow(1000)
    Text("${if (p.phase == Phase.FOCUS) "Enfoque" else "Descanso"} · ${clock(p.left(now))}${if (p.endsAt == null) " ⏸" else ""}")
}

// ── Mochi ─────────────────────────────────────────────────────────────────────

@Composable
fun Home(snap: Snapshot, jump: Int, go: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val focusing = snap.pomodoro.phase == Phase.FOCUS
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Mochi(
                    Modifier.width(130.dp).height(108.dp),
                    color = Color(snap.mochiColor),
                    mood = if (focusing) Mood.FOCUS else Mood.IDLE,
                    jump = jump,
                    interactive = true,
                    greet = true,
                    dancing = rememberMusicPlaying(),
                )
            }
            if (snap.name.isNotBlank()) {
                item { Text("Hola, ${snap.name}", color = VioletSoft, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
            item { NextEvent(snap) }
            if (snap.pomodoro.phase != Phase.IDLE) {
                item {
                    Chip(
                        onClick = { WatchStore.pomodoro() },
                        label = { PomodoroLabel(snap.pomodoro) },
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
            if (snap.sleepMin > 0) {
                item {
                    val h = snap.sleepMin / 60
                    val m = snap.sleepMin % 60
                    Chip(
                        onClick = { go("sleep") },
                        label = { Text("Dormiste $h h ${"%02d".format(m)}") },
                        secondaryLabel = { Text(if (snap.sleepNight == java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())) "Anoche" else "Última noche registrada") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = { go("mood") },
                    label = { Text("¿Cómo te sientes?") },
                    secondaryLabel = { Text(moodOf(WatchStore.todayMood())?.let { "Hoy: ${it.label}" } ?: "Anótalo en tu diario") },
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

@Composable
fun NextEvent(snap: Snapshot) {
    val now = rememberNow(30_000)
    val next = remember(snap.events, now / 60_000) { nextEvents(snap.events, now).firstOrNull { !it.allDay } }
    if (next != null) {
        val min = minutesUntil(next.begin, now)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (min <= 0) "Ahora" else if (min < 60) "En $min min" else HM.format(Date(next.begin)),
                color = VioletSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            Text(next.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    } else {
        Text(if (snap.at == 0L) "Abre Mochi en tu teléfono" else "Nada más por hoy", color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ── Pendientes ────────────────────────────────────────────────────────────────

@Composable
fun Tasks(snap: Snapshot, onDone: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { WatchStore.addTask(it) }
    }
    val tasks = remember(snap.tasks) { orderedTasks(snap.tasks).take(30) }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text("Pendientes · ${pendingCount(snap.tasks)}", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            items(tasks, key = { it.id }) { t ->
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
                if (v in 30..220 && v != bpm) bpm = v
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (!active) bpm = 0
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
fun Breathe(snap: Snapshot, go: (String) -> Unit = {}) {
    val context = LocalContext.current
    var b by remember { mutableStateOf(Breathing()) }
    var linked by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<BreathSession?>(null) }
    val active = b.startedAt != null
    val bpm = rememberHeartRate(active)
    val bpmNow by rememberUpdatedState(bpm)
    val beats = rememberHeartBeats(active)
    var inhaling by remember { mutableStateOf(true) }
    var left by remember { mutableIntStateOf(0) }
    val askSensors = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun start(session: Breathing, fromPhone: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            askSensors.launch(Manifest.permission.BODY_SENSORS)
        }
        summary = null
        linked = fromPhone
        b = session
        Sounds.play("open")
    }

    // The phone asks: open its session, in step with it.
    val request by WatchStore.breathRequest.collectAsState()
    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        if (r.startedAt != b.startedAt) start(r, fromPhone = true)
        WatchStore.breathRequest.value = null
    }
    val stop by WatchStore.stopRequest.collectAsState()
    LaunchedEffect(stop) {
        if (stop != null && linked && active) { Haptics.stop(context); b = Breathing(); linked = false; Sounds.play("close") }
    }

    // The session's clock: buzz on each turn, the pulse to the phone, the end.
    LaunchedEffect(b) {
        val start = b.startedAt ?: return@LaunchedEffect
        var hrStart = 0
        var hrMin = 0
        var lastSent = 0L
        val samples = ArrayList<Int>()
        var nextSample = start
        var lastInhale: Boolean? = null
        while (true) {
            val now = System.currentTimeMillis()
            val f = breathFrame(b, now)
            if (f.inhaling != lastInhale && !f.done) {
                lastInhale = f.inhaling
                inhaling = f.inhaling
                Haptics.breath(
                    context, f.inhaling,
                    if (f.inhaling) mx.loperstack.mochi.wellness.INHALE_S else mx.loperstack.mochi.wellness.EXHALE_S,
                    guide = WatchStore.breathGuide,
                )
            }
            if (f.left != left) left = f.left
            val hr = bpmNow
            while (now >= nextSample && !f.done) {
                samples += hr
                nextSample += mx.loperstack.mochi.wellness.HR_STEP_S * 1000L
            }
            if (hr > 0) {
                if (hrStart == 0) hrStart = hr
                hrMin = if (hrMin == 0) hr else minOf(hrMin, hr)
                if (now - lastSent >= 3000) { lastSent = now; WatchStore.heartRate(hr) }
            }
            if (f.done) {
                Haptics.stop(context)
                val s = BreathSession(
                    start, b.minutes, f.breaths, hrStart, hr, hrMin, device = "watch",
                    hr = samples.toList(), rr = synchronized(beats) { beats.toList() },
                )
                WatchStore.session(s)
                summary = s
                b = Breathing()
                linked = false
                Sounds.play("love")
                break
            }
            delay(100)
        }
    }

    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }

    if (active) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            BreathRing(b, Modifier.fillMaxSize().padding(4.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${if (linked) "Con tu teléfono · " else ""}quedan ${clock(left)}", color = Dim, fontSize = 11.sp)
                Mochi(Modifier.width(120.dp).height(98.dp), color = Color(snap.mochiColor), breathing = b)
                Text(if (inhaling) "Inhala…" else "Exhala…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(if (bpm > 0) "♥ $bpm lpm" else "Midiendo pulso…", color = if (bpm > 0) Pink else Dim, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Terminar", color = Dim, fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).clickable { Haptics.stop(context); b = Breathing(); linked = false; Sounds.play("close") }.padding(8.dp),
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
                            Text("♥ ${s.hrStart} → ${s.hrEnd} lpm", color = Pink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("Guardado en tu teléfono", color = Dim, fontSize = 11.sp)
                    }
                }
                if (s.hr.any { it > 0 }) {
                    item {
                        Chip(
                            onClick = { go("session/${s.at}") },
                            label = { Text("Ver cardiograma") },
                            secondaryLabel = { Text("Pulso y variabilidad") },
                            colors = ChipDefaults.primaryChipColors(backgroundColor = Color(0xFF14361F)),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { Text("Respira con Mochi", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item {
                Chip(
                    onClick = { go("history") },
                    label = { Text("Historial") },
                    secondaryLabel = { Text("Sesiones, pulso y variabilidad") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                var guide by remember { mutableStateOf(WatchStore.breathGuide) }
                ToggleChip(
                    checked = guide,
                    onCheckedChange = {
                        guide = it
                        WatchStore.breathGuide = it
                        Haptics.breath(context, true, 2.0, guide = it)
                    },
                    label = { Text("Vibración guía") },
                    secondaryLabel = { Text(if (guide) "Pulsos al inhalar y exhalar" else "Solo en cada cambio") },
                    toggleControl = { Icon(imageVector = ToggleChipDefaults.switchIcon(checked = guide), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(BREATHE_MINUTES) { m ->
                Chip(
                    onClick = { start(Breathing(System.currentTimeMillis(), m), fromPhone = false) },
                    label = { Text("$m min") },
                    secondaryLabel = { Text("4 s inhalar · 6 s exhalar") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The session's ring, smooth: it redraws each frame without recomposing. */
@Composable
fun BreathRing(b: Breathing, modifier: Modifier) {
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(b) { while (true) withFrameMillis { frame = it } }
    Canvas(modifier) {
        if (frame < 0) return@Canvas
        val p = breathFrame(b, System.currentTimeMillis()).progress
        val stroke = 5.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Color(0x14FFFFFF), 290f, 320f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(Violet, 290f, 320f * p, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
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

// ── Ánimo ─────────────────────────────────────────────────────────────────────

@Composable
fun MoodPicker(snap: Snapshot, done: () -> Unit) {
    val listState = rememberScalingLazyListState()
    val today = remember(snap) { WatchStore.todayMood() }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { Text("¿Cómo te sientes?", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            items(MOODS) { m ->
                Chip(
                    onClick = { WatchStore.mood(m.value); done() },
                    label = { Text(m.label, fontWeight = if (today == m.value) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Mochi(Modifier.width(34.dp).height(28.dp), color = Color(m.color), face = m.face) },
                    colors = if (today == m.value) ChipDefaults.primaryChipColors(backgroundColor = Color(m.color).copy(alpha = 0.35f))
                    else ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ── Latidos (intervalos RR) ───────────────────────────────────────────────────

/**
 * Beat-to-beat intervals (ms) from the heartbeat sensor while `active`, when
 * the watch offers one; empty otherwise (the variability is then estimated).
 */
@Composable
fun rememberHeartBeats(active: Boolean): MutableList<Int> {
    val context = LocalContext.current
    val beats = remember { ArrayList<Int>() }
    DisposableEffect(active) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HEART_BEAT)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.values.firstOrNull()?.let { it < 0.5f } == true) return // low confidence
                if (last > 0) {
                    val rr = ((e.timestamp - last) / 1_000_000L).toInt()
                    if (rr in 300..2000) synchronized(beats) { beats += rr }
                }
                last = e.timestamp
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (active) synchronized(beats) { beats.clear() }
        if (active && granted && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        onDispose { sm?.unregisterListener(listener) }
    }
    return beats
}

// ── Historial de respiración ──────────────────────────────────────────────────

private val DayHM = SimpleDateFormat("EEE d · HH:mm", Locale("es", "MX"))

@Composable
fun BreathHistory(snap: Snapshot, open: (Long) -> Unit) {
    val listState = rememberScalingLazyListState()
    val list = remember(snap) { WatchStore.allSessions().take(10) }
    val weekStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -((get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7))
        }.timeInMillis
    }
    val weekMin = list.filter { it.at >= weekStart }.sumOf { it.minutes }
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("Respiración", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            item { Text("$weekMin min esta semana", color = VioletSoft, fontSize = 13.sp) }
            if (list.isEmpty()) item { Text("Aún no hay sesiones", color = Dim, fontSize = 13.sp) }
            items(list, key = { it.at }) { s ->
                Chip(
                    onClick = { open(s.at) },
                    label = { Text("${s.minutes} min · ${DayHM.format(Date(s.at))}", maxLines = 1) },
                    secondaryLabel = {
                        Text(
                            if (s.hrStart > 0 && s.hrEnd > 0) "♥ ${s.hrStart} → ${s.hrEnd} lpm" else "Sin pulso",
                            color = if (s.hrEnd > 0) Pink else Dim,
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One session: the trace, the pulse curve, the variability and the minutes. */
@Composable
fun SessionScreen(at: Long, snap: Snapshot) {
    val s = remember(at, snap) { WatchStore.allSessions().firstOrNull { it.at == at } }
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s == null) {
                item { Text("Sesión no encontrada", color = Dim) }
                return@ScalingLazyColumn
            }
            val stats = mx.loperstack.mochi.wellness.hrvStats(s)
            item { Text(DayHM.format(Date(s.at)), fontSize = 13.sp, color = Dim) }
            if (stats == null) {
                item { Text("Esta sesión no tiene pulso", fontSize = 14.sp, textAlign = TextAlign.Center) }
                return@ScalingLazyColumn
            }
            item {
                Box(Modifier.fillMaxWidth().height(70.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(Color(0xFF07110B))) {
                    mx.loperstack.mochi.charts.Cardiogram(mx.loperstack.mochi.charts.beatIntervals(s), Modifier.fillMaxSize().padding(vertical = 6.dp), windowMs = 3000)
                }
            }
            item { Text("♥ ${stats.avgBpm} lpm · ${stats.minBpm}–${stats.maxBpm}", color = Pink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            item {
                mx.loperstack.mochi.charts.PulseCurve(s, Modifier.fillMaxWidth().height(48.dp), Pink, Color(0x22FFFFFF))
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Variabilidad", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "RMSSD ${stats.rmssd} ms · ${mx.loperstack.mochi.wellness.rmssdLabel(stats.rmssd)}",
                        color = VioletSoft, fontSize = 13.sp,
                    )
                    Text("SDNN ${stats.sdnn} ms", color = Dim, fontSize = 12.sp)
                    Text(if (stats.beatToBeat) "Latido a latido" else "Estimada del pulso", color = Dim, fontSize = 10.sp)
                }
            }
            item { Text("Min · prom · mín–máx", color = Dim, fontSize = 11.sp) }
            items(mx.loperstack.mochi.wellness.minuteTable(s)) { r ->
                Text("${r.minute}  ·  ${r.avg}  ·  ${r.min}–${r.max}", fontSize = 13.sp)
            }
            item { Text("Trazo ilustrativo, no es un electrocardiograma", color = Dim, fontSize = 10.sp, textAlign = TextAlign.Center) }
        }
    }
}

// ── Sueño ─────────────────────────────────────────────────────────────────────

@Composable
fun SleepReport(snap: Snapshot) {
    val listState = rememberScalingLazyListState()
    val nights = snap.nights
    Scaffold(timeText = { TimeText() }, positionIndicator = { PositionIndicator(scalingLazyListState = listState) }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item { Text("Sueño", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (nights.isEmpty()) {
                item { Text("Registra tus noches en el teléfono", color = Dim, fontSize = 13.sp, textAlign = TextAlign.Center) }
                return@ScalingLazyColumn
            }
            val last = nights.last()
            val avg = nights.map { it.minutes }.average().toInt()
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${last.minutes / 60} h ${"%02d".format(last.minutes % 60)}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("${HM.format(Date(last.bed))} → ${HM.format(Date(last.wake))}", color = VioletSoft, fontSize = 13.sp)
                    moodOf(last.quality)?.let { Text(it.label, color = Color(it.color), fontSize = 12.sp) }
                }
            }
            item {
                val maxMin = maxOf(9 * 60, nights.maxOf { it.minutes })
                Canvas(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp)) {
                    val n = nights.size
                    val slot = size.width / maxOf(n, 7)
                    val bw = slot * 0.6f
                    val goalY = size.height * (1 - 8 * 60f / maxMin)
                    drawLine(Color(0x55FFFFFF), Offset(0f, goalY), Offset(size.width, goalY), 1.5f)
                    nights.forEachIndexed { i, e ->
                        val h = size.height * e.minutes / maxMin
                        val c = moodOf(e.quality)?.let { Color(it.color) } ?: Violet
                        val x = slot * (i + (maxOf(n, 7) - n)) + (slot - bw) / 2
                        drawRoundRect(c, Offset(x, size.height - h), Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                    }
                }
            }
            item { Text("Promedio ${avg / 60} h ${"%02d".format(avg % 60)} · ${nights.size} noches", color = Dim, fontSize = 12.sp) }
            items(nights.reversed()) { e ->
                Text(
                    "${e.night.takeLast(5).replace('-', '/')}  ${e.minutes / 60} h ${"%02d".format(e.minutes % 60)}",
                    fontSize = 13.sp, color = moodOf(e.quality)?.let { Color(it.color) } ?: Color.White,
                )
            }
        }
    }
}
