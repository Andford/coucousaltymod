// The app: a phone layout with five tabs (Inicio, Tareas, Enfoque, Respirar,
// PC) and, on a wide landscape screen (the Legion Tab), the desk mode.
// Everything reads Store.state; actions go through Actions below.

package mx.loperstack.mochi.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import mx.loperstack.mochi.data.Agenda
import mx.loperstack.mochi.data.CalEvent
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.isTomorrow
import mx.loperstack.mochi.data.nextEvent
import mx.loperstack.mochi.data.upcoming
import mx.loperstack.mochi.mochi.MOCHI_PALETTE
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.wellness.BreathSession
import mx.loperstack.mochi.wellness.calmDrop
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import mx.loperstack.mochi.wellness.BREATHE_MINUTES
import mx.loperstack.mochi.wellness.Breathing
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.PomodoroScheduler
import mx.loperstack.mochi.wellness.Task
import mx.loperstack.mochi.wellness.addTask
import mx.loperstack.mochi.wellness.breathFrame
import mx.loperstack.mochi.wellness.clearDone
import mx.loperstack.mochi.wellness.clock
import mx.loperstack.mochi.wellness.finishPhase
import mx.loperstack.mochi.wellness.isPaused
import mx.loperstack.mochi.wellness.isRunning
import mx.loperstack.mochi.wellness.left
import mx.loperstack.mochi.wellness.orderedTasks
import mx.loperstack.mochi.wellness.pause
import mx.loperstack.mochi.wellness.pendingCount
import mx.loperstack.mochi.wellness.progress
import mx.loperstack.mochi.wellness.removeTask
import mx.loperstack.mochi.wellness.reset
import mx.loperstack.mochi.wellness.resume
import mx.loperstack.mochi.wellness.startFocus
import mx.loperstack.mochi.wellness.toggleTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Tab(val label: String) { HOME("Inicio"), TASKS("Tareas"), FOCUS("Enfoque"), BREATHE("Respirar"), PC("PC") }

private val ES = Locale("es", "MX")

// ── Actions ───────────────────────────────────────────────────────────────────

/** Everything the screens can do; Mochi hops through `jump`. */
class Actions(private val context: Context) {
    var jump by mutableIntStateOf(0)
    var breathing by mutableStateOf(Breathing())

    fun addTask(text: String): Boolean {
        val now = System.currentTimeMillis()
        val before = Store.state.value.tasks
        val after = addTask(before, text, now, "${now.toString(36)}-${(0..9999).random()}")
        if (after.size == before.size) return false
        Store.setTasks(after)
        Sounds.play("pop")
        return true
    }

    fun toggle(task: Task) {
        Store.setTasks(toggleTask(Store.state.value.tasks, task.id))
        if (!task.done) {
            jump++
            Sounds.play("approve")
        } else Sounds.play("blip")
    }

    fun remove(task: Task) {
        Store.setTasks(removeTask(Store.state.value.tasks, task.id))
        Sounds.play("blip")
    }

    fun clearDone() {
        Store.setTasks(clearDone(Store.state.value.tasks))
        Sounds.play("blip")
    }

    /** A poke on Mochi. */
    fun poke() {
        jump++
        Sounds.play(listOf("wink", "pop", "love").random())
    }

    /** Start, pause or resume — the main button. */
    fun playPause() {
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        val next = when {
            p.phase == Phase.IDLE -> p.startFocus(now).also { jump++; Sounds.play("work") }
            p.isPaused() -> p.resume(now)
            else -> p.pause(now)
        }
        if (p.phase != Phase.IDLE) Sounds.play("blip")
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next, now)
    }

    fun skip() {
        val p = Store.state.value.pomodoro
        if (p.phase == Phase.IDLE) return
        val (next, _) = p.finishPhase(System.currentTimeMillis(), counted = false)
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next)
    }

    fun resetPomodoro() {
        val next = Store.state.value.pomodoro.reset()
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next)
    }

    fun startBreathing(minutes: Int) {
        breathing = Breathing(System.currentTimeMillis(), minutes)
        Sounds.play("open")
    }

    fun stopBreathing(completed: Boolean) {
        val b = breathing
        val start = b.startedAt
        breathing = Breathing()
        if (completed && start != null) {
            jump++
            Sounds.play("love")
            Store.addSession(BreathSession(start, b.minutes, b.minutes * 6, device = "phone"))
        } else Sounds.play("close")
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

// ── Clocks ────────────────────────────────────────────────────────────────────

/** The time, refreshed on each whole second. */
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

/** The time on every frame, while `active`. */
@Composable
fun rememberFrameNow(active: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) {
        while (active) {
            withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    return now
}

fun hourMinute(ms: Long): String = SimpleDateFormat("HH:mm", ES).format(Date(ms))

fun greeting(now: Long): String = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}

fun longDate(now: Long): String =
    SimpleDateFormat("EEEE d 'de' MMMM", ES).format(Date(now)).replaceFirstChar { it.uppercase(ES) }

// ── Root ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MochiApp(startTab: Tab = Tab.HOME) {
    val context = LocalContext.current
    val data by Store.state.collectAsState()
    val actions = remember { Actions(context) }
    var tab by remember { mutableStateOf(startTab) }
    LaunchedEffect(startTab) { tab = startTab }
    var showColors by remember { mutableStateOf(false) }
    val now = rememberNow()

    // The agenda, with READ_CALENDAR.
    var calendarOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarOk = it }
    val minute = now / 60_000
    LaunchedEffect(calendarOk, minute / 5) { if (calendarOk) events = Agenda.load(context) }

    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val startPomodoro = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        actions.playPause()
    }

    val color = Color(data.mochiColor)
    val config = LocalConfiguration.current
    val desk = config.screenWidthDp >= 840 && config.orientation == Configuration.ORIENTATION_LANDSCAPE

    MochiTheme {
        if (desk) {
            DeskScreen(data.tasks, data.pomodoro, events, now, color, actions, startPomodoro, calendarOk) { askCalendar.launch(Manifest.permission.READ_CALENDAR) }
        } else {
            Scaffold(
                containerColor = Ink.Ground,
                bottomBar = {
                    NavigationBar(containerColor = Ink.CardHigh, tonalElevation = 0.dp) {
                        val glyphs = mapOf(Tab.HOME to Glyph.Home, Tab.TASKS to Glyph.Tasks, Tab.FOCUS to Glyph.Focus, Tab.BREATHE to Glyph.Breathe, Tab.PC to Glyph.Pc)
                        for (t in Tab.entries) {
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(glyphs.getValue(t), contentDescription = null, modifier = Modifier.size(20.dp)) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ink.VioletSoft,
                                    selectedTextColor = Ink.Text,
                                    indicatorColor = Color(0xFF2D2547),
                                    unselectedIconColor = Ink.Dim,
                                    unselectedTextColor = Ink.Dim,
                                ),
                            )
                        }
                    }
                },
            ) { pad ->
                Box(Modifier.padding(pad).fillMaxSize()) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(
                            data.tasks, data.pomodoro, events, now, color, actions, calendarOk,
                            askCalendar = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                            openColors = { showColors = true },
                            goFocus = { tab = Tab.FOCUS }, goBreathe = { tab = Tab.BREATHE }, goTasks = { tab = Tab.TASKS },
                        )
                        Tab.TASKS -> TasksScreen(data.tasks, actions)
                        Tab.FOCUS -> FocusScreen(data.tasks, data.pomodoro, now, color, actions, startPomodoro)
                        Tab.BREATHE -> BreatheScreen(color, actions, data.sessions)
                        Tab.PC -> PcScreen(now)
                    }
                }
            }
        }
        if (showColors) {
            ModalBottomSheet(onDismissRequest = { showColors = false }, containerColor = Ink.CardHigh) {
                ColorPicker(data.mochiColor, color) { Store.setColor(it); Sounds.play("pop") }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sonidos de Mochi", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Label("Los mismos de la PC, en el teléfono y el reloj", Ink.Dim2)
                    }
                    Switch(
                        checked = data.soundOn, onCheckedChange = { Store.setSound(it); if (it) Sounds.play("pop") },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ink.Violet),
                    )
                }
            }
        }
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(26.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = Ink.Dim, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.Medium) =
    Text(text, color = color, fontSize = size, fontWeight = weight)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Ink.Text, textColor: Color = Ink.Ground, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
fun RoundIcon(glyph: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp).clip(CircleShape).background(Ink.CardHigh)) {
        Icon(glyph, contentDescription = description, tint = Color(0xFFC5C8CD), modifier = Modifier.size(20.dp))
    }
}

@Composable
fun TaskRow(task: Task, actions: Actions, showDelete: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { actions.toggle(task) }.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = task.done, onCheckedChange = { actions.toggle(task) },
            colors = CheckboxDefaults.colors(checkedColor = Ink.Violet, uncheckedColor = Ink.Dim2, checkmarkColor = Ink.Ground),
        )
        Text(
            task.text, modifier = Modifier.weight(1f), fontSize = 15.sp,
            color = if (task.done) Ink.Dim2 else Ink.Text,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        if (showDelete) {
            IconButton(onClick = { actions.remove(task) }) {
                Icon(Glyph.Close, contentDescription = "Eliminar", tint = Ink.Dim2, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun NewTaskField(actions: Actions, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val submit = { if (actions.addTask(text)) text = "" }
    OutlinedTextField(
        value = text, onValueChange = { text = it.take(200) },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Nueva tarea…", color = Ink.Dim2) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(onClick = { submit() }) { Icon(Glyph.Plus, contentDescription = "Agregar", tint = Ink.Violet) }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Violet, unfocusedBorderColor = Ink.Hairline,
            focusedTextColor = Ink.Text, unfocusedTextColor = Ink.Text, cursorColor = Ink.Violet,
        ),
    )
}

@Composable
fun EventRow(e: CalEvent, highlight: Boolean, now: Long, actions: Actions) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (highlight) Color(0x24A78BFA) else Color.Transparent)
            .heightIn(min = 52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(e.color?.let { Color(it) } ?: Ink.Violet))
        val started = !e.allDay && e.begin <= now
        Text(
            if (e.allDay) "Todo el día" else if (started) "Ahora" else hourMinute(e.begin),
            color = if (started) Ink.Green else Ink.VioletSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(76.dp),
        )
        Text(e.title, modifier = Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (e.meetUrl != null) {
            Text(
                "Unirse", color = Ink.Green, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x2E4ADE80))
                    .clickable { actions.open(e.meetUrl) }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun AgendaList(events: List<CalEvent>, now: Long, actions: Actions, max: Int = 20) {
    val list = upcoming(events, now).take(max)
    val next = nextEvent(events, now)
    var tomorrowShown = false
    if (list.isEmpty()) Label("Nada más por hoy — disfrútalo.", Ink.Dim2)
    for (e in list) {
        if (!tomorrowShown && isTomorrow(e, now)) {
            tomorrowShown = true
            Text("MAÑANA", color = Ink.Dim2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp))
        }
        EventRow(e, e == next, now, actions)
    }
}

/** "En 12 min", "En 2 h", or the start time. */
fun untilText(e: CalEvent, now: Long): String {
    val min = ((e.begin - now) / 60_000).toInt()
    return when {
        min < 1 -> "Ahora"
        min < 60 -> "En $min min"
        else -> "A las ${hourMinute(e.begin)}"
    }
}

// ── Inicio ────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    tasks: List<Task>, pomodoro: mx.loperstack.mochi.wellness.Pomodoro, events: List<CalEvent>, now: Long, color: Color,
    actions: Actions, calendarOk: Boolean, askCalendar: () -> Unit, openColors: () -> Unit,
    goFocus: () -> Unit, goBreathe: () -> Unit, goTasks: () -> Unit,
) {
    val next = nextEvent(events, now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label(longDate(now))
                Text(greeting(now), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Ink.Text)
            }
            RoundIcon(Glyph.Gear, "Color de Mochi", openColors)
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
            Mochi(
                Modifier.align(Alignment.CenterHorizontally).width(170.dp).height(140.dp).clickable { actions.poke() },
                color = color,
                mood = if (pomodoro.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE,
                jump = actions.jump,
            )
            Spacer(Modifier.height(10.dp))
            when {
                !calendarOk -> {
                    Text("Tu agenda aquí", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Label("Mochi lee los calendarios de este teléfono, Google Calendar incluido.", Ink.Dim)
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton("Ver mi agenda", Modifier.align(Alignment.CenterHorizontally), onClick = askCalendar)
                }
                next != null -> {
                    Text(
                        "SIGUIENTE · ${untilText(next, now).uppercase(ES)}", color = Ink.VioletSoft, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(next.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Label("${hourMinute(next.begin)} – ${hourMinute(next.end)}", modifier = Modifier.align(Alignment.CenterHorizontally))
                    if (next.meetUrl != null) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Unirse", Modifier.align(Alignment.CenterHorizontally), Ink.Green, Ink.GreenDeep) { actions.open(next.meetUrl) }
                    }
                }
                else -> Text("Nada más en tu agenda hoy", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Enfoque", if (pomodoro.phase == Phase.IDLE) "25:00" else clock(pomodoro.left(now)), Ink.VioletDeep, Ink.VioletLine, Ink.VioletSoft, Modifier.weight(1f), goFocus)
            Tile("Respirar", "1 · 5 · 10 min", Color(0xFF141E1B), Color(0xFF1D2A26), Color(0xFF86EFAC), Modifier.weight(1f), goBreathe, small = true)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("Tareas", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Label("${pendingCount(tasks)} pendientes", modifier = Modifier.clickable(onClick = goTasks))
        }
        Spacer(Modifier.height(8.dp))
        Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
            for (t in orderedTasks(tasks).filter { !it.done }.take(4)) TaskRow(t, actions)
            NewTaskField(actions, Modifier.padding(6.dp))
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun Label(text: String, color: Color = Ink.Dim, modifier: Modifier) =
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = modifier)

@Composable
fun Tile(title: String, value: String, bg: Color, line: Color, accent: Color, modifier: Modifier, onClick: () -> Unit, small: Boolean = false) {
    Column(
        modifier.height(96.dp).clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, line, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = if (small) 15.sp else 28.sp, fontWeight = if (small) FontWeight.SemiBold else FontWeight.ExtraBold, color = Ink.Text)
    }
}

// ── Tareas ────────────────────────────────────────────────────────────────────

@Composable
fun TasksScreen(tasks: List<Task>, actions: Actions) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("Tareas", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            val pending = pendingCount(tasks)
            Label(if (tasks.isEmpty()) "" else if (pending == 0) "¡Todo listo!" else "$pending pendientes")
        }
        Spacer(Modifier.height(12.dp))
        NewTaskField(actions)
        Spacer(Modifier.height(10.dp))
        if (tasks.isEmpty()) {
            Label("Nada pendiente — agrega tu primera tarea.", Ink.Dim2)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(orderedTasks(tasks), key = { it.id }) { t -> TaskRow(t, actions, showDelete = true) }
        }
        if (tasks.any { it.done }) {
            OutlinedButton(onClick = { actions.clearDone() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                Text("Borrar hechas", color = Ink.Dim)
            }
        }
    }
}

// ── Enfoque ───────────────────────────────────────────────────────────────────

@Composable
fun FocusScreen(tasks: List<Task>, p: mx.loperstack.mochi.wellness.Pomodoro, now: Long, color: Color, actions: Actions, start: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Label("Pomodoro · ${p.today} hoy")
            Text(PomodoroScheduler.phaseTitle(p.phase), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(18.dp))
        FocusRing(p, now, color, Modifier.size(260.dp))
        val current = orderedTasks(tasks).firstOrNull { !it.done }
        if (current != null && p.phase == Phase.FOCUS) {
            Spacer(Modifier.height(14.dp))
            Text(
                "✓  ${current.text}", color = Color(0xFFDDD6FE), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(Ink.VioletDeep).border(1.dp, Ink.VioletLine, RoundedCornerShape(17.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(if (p.phase == Phase.IDLE) "Iniciar" else if (p.isPaused()) "Reanudar" else "Pausa", onClick = start)
            if (p.phase != Phase.IDLE) {
                RoundIcon(Glyph.Skip, "Saltar fase") { actions.skip() }
                RoundIcon(Glyph.Reset, "Reiniciar") { actions.resetPomodoro() }
            }
        }
        Spacer(Modifier.height(18.dp))
        Label("25 min de enfoque · 5 de descanso · uno largo de 15 cada 4 bloques. Te avisa aunque la app esté cerrada.", Ink.Dim2)
    }
}

@Composable
fun FocusRing(p: mx.loperstack.mochi.wellness.Pomodoro, now: Long, color: Color, modifier: Modifier, mochi: Boolean = true) {
    val ring = if (p.phase == Phase.BREAK || p.phase == Phase.LONG_BREAK) Ink.Green else Ink.Violet
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2
            drawArc(Color(0x12FFFFFF), 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            drawArc(
                ring, -90f, 360f * p.progress(now), false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (mochi) {
                Mochi(Modifier.width(90.dp).height(72.dp), color = color, mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE)
            }
            Text(clock(p.left(now)), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ── Respirar ──────────────────────────────────────────────────────────────────

@Composable
fun BreatheScreen(color: Color, actions: Actions, sessions: List<BreathSession> = emptyList()) {
    val b = actions.breathing
    val active = b.startedAt != null
    val now = rememberFrameNow(active)
    val f = breathFrame(b, now)
    LaunchedEffect(f.done) { if (f.done) actions.stopBreathing(completed = true) }
    // The screen stays on for the session, as a watch's would.
    val view = LocalView.current
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1C1733), Ink.Ground), center = Offset.Unspecified, radius = 1400f)),
    ) {
        Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(30.dp))
            Text(if (active) "Sesión de ${b.minutes} min" else "Respira con Mochi", color = if (active) Ink.Dim else Ink.Text, fontSize = if (active) 14.sp else 26.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.ExtraBold)
            Spacer(Modifier.weight(0.6f))
            Mochi(Modifier.width(230.dp).height(190.dp), color = color, mood = if (active) Mood.BREATHING else Mood.IDLE, breathFill = f.fill, jump = actions.jump)
            Spacer(Modifier.height(40.dp))
            if (active) {
                Text(if (f.inhaling) "Inhala…" else "Exhala…", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                Label("4 s inhalar · 6 s exhalar")
                Spacer(Modifier.height(28.dp))
                LinearProgressIndicator(progress = { f.progress }, modifier = Modifier.width(280.dp).height(6.dp).clip(CircleShape), color = Ink.Violet, trackColor = Color(0x14FFFFFF))
                Row(Modifier.width(280.dp).padding(top = 8.dp)) {
                    Label("${f.breaths} respiraciones", modifier = Modifier.weight(1f))
                    Label("Quedan ${clock(f.left)}")
                }
            } else {
                Label("Inhala cuando Mochi crece, exhala cuando se encoge.", Ink.Dim)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (m in BREATHE_MINUTES) {
                        OutlinedButton(onClick = { actions.startBreathing(m) }, modifier = Modifier.heightIn(min = 52.dp)) {
                            Text("$m min", color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (sessions.isNotEmpty()) {
                    Spacer(Modifier.height(26.dp))
                    SessionHistory(sessions)
                }
            }
            Spacer(Modifier.weight(1f))
            if (active) PrimaryButton("Terminar") { actions.stopBreathing(completed = false) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── PC ────────────────────────────────────────────────────────────────────────

fun batteryLevel(context: Context): Pair<Int, Boolean> {
    val bm = context.getSystemService(BatteryManager::class.java)
    return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
}

@Composable
fun PcScreen(now: Long) {
    val context = LocalContext.current
    val (level, charging) = remember(now / 30_000) { batteryLevel(context) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Column(Modifier.padding(top = 18.dp, start = 6.dp)) {
            Label("Fase 2 · próximamente")
            Text("Conexión con la PC", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth()) {
            Label("Este teléfono", weight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$level", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Label("%", modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
            }
            LinearProgressIndicator(
                progress = { level / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (level <= 15) Ink.Red else Ink.Green, trackColor = Color(0x14FFFFFF),
            )
            Spacer(Modifier.height(8.dp))
            Label(if (charging) "Cargando" else "Con batería", if (charging) Color(0xFF86EFAC) else Ink.Dim)
        }
        Spacer(Modifier.height(12.dp))
        Panel(Modifier.fillMaxWidth()) {
            Text("Lo que viene", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            for (line in listOf(
                "Ver la batería del teléfono y de la tablet en la isla de la PC",
                "Portapapeles compartido: textos e imágenes entre PC y teléfono",
                "Aprobar los permisos de Claude Code desde aquí",
                "Emparejar con un código QR, cifrado y solo en tu red",
            )) {
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(Ink.Violet))
                    Text(line, fontSize = 15.sp, color = Color(0xFFC5C8CD), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

// ── Color de Mochi ────────────────────────────────────────────────────────────

@Composable
fun ColorPicker(current: Long, color: Color, onPick: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("El color de Mochi", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Mochi(Modifier.width(150.dp).height(124.dp), color = color, mood = Mood.HAPPY)
        Spacer(Modifier.height(16.dp))
        val rows = MOCHI_PALETTE.chunked(6)
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                for (c in row) {
                    val argb = c.value.toLong().let { (it shr 32) and 0xFFFFFFFFL }
                    val on = argb == current
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c)
                            .border(if (on) 3.dp else 1.dp, if (on) Ink.Text else Color(0x33FFFFFF), CircleShape)
                            .clickable { onPick(argb) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Label("Los mismos colores que las pills de Coucou en la PC.", Ink.Dim2)
    }
}

// ── Tablet: modo escritorio ───────────────────────────────────────────────────

@Composable
fun DeskScreen(
    tasks: List<Task>, p: mx.loperstack.mochi.wellness.Pomodoro, events: List<CalEvent>, now: Long, color: Color,
    actions: Actions, start: () -> Unit, calendarOk: Boolean, askCalendar: () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Color(0xFF08090B)).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier.weight(1.1f).fillMaxHeight().clip(RoundedCornerShape(34.dp))
                .background(Brush.radialGradient(listOf(Color(0xFF221B3C), Color(0xFF111117)), radius = 1100f))
                .border(1.dp, Ink.Hairline, RoundedCornerShape(34.dp)).padding(30.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Label(longDate(now), Color(0xFFA5A9B1), 16.sp, FontWeight.SemiBold)
                Text(hourMinute(now), fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 100.sp)
            }
            Mochi(
                Modifier.fillMaxWidth().aspectRatio(1.4f).clickable { actions.poke() }, color = color,
                mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, jump = actions.jump,
            )
            Label("Toca a Mochi", Ink.Dim2, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Column(
            Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(34.dp)).background(Color(0xFF111216))
                .border(1.dp, Ink.Hairline, RoundedCornerShape(34.dp)).padding(26.dp).verticalScroll(rememberScrollState()),
        ) {
            Text("Hoy", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(12.dp))
            if (!calendarOk) PrimaryButton("Ver mi agenda", onClick = askCalendar) else AgendaList(events, now, actions)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.fillMaxWidth(), padding = 20.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusRing(p, now, color, Modifier.size(96.dp), mochi = false)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Label(PomodoroScheduler.phaseTitle(p.phase), Ink.VioletSoft, weight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(if (p.phase == Phase.IDLE) "Iniciar" else if (p.isPaused()) "Reanudar" else "Pausa", onClick = start)
                    }
                }
            }
            Panel(Modifier.fillMaxWidth().weight(1f), padding = 14.dp) {
                Text("Tareas", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    for (t in orderedTasks(tasks)) TaskRow(t, actions)
                }
                NewTaskField(actions)
            }
        }
    }
}

// ── Historial de respiración ──────────────────────────────────────────────────

/** The week's minutes per day, Monday first, and the last sessions. */
@Composable
fun SessionHistory(sessions: List<BreathSession>) {
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        val dow = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0
        add(Calendar.DAY_OF_YEAR, -dow)
    }
    val weekStart = cal.timeInMillis
    val perDay = IntArray(7)
    for (s in sessions) {
        val d = ((s.at - weekStart) / 86_400_000L).toInt()
        if (d in 0..6) perDay[d] += s.minutes
    }
    val maxMin = (perDay.maxOrNull() ?: 0).coerceAtLeast(5)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Label("Esta semana · ${perDay.sum()} min", Ink.Dim, weight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.height(56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            val days = listOf("L", "M", "M", "J", "V", "S", "D")
            for (i in 0..6) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.width(14.dp).height((4 + 36f * perDay[i] / maxMin).dp)
                            .clip(RoundedCornerShape(4.dp)).background(if (perDay[i] > 0) Ink.Violet else Color(0x1AFFFFFF)),
                    )
                    Text(days[i], fontSize = 10.sp, color = Ink.Dim2)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        for (s in sessions.takeLast(3).reversed()) {
            val hr = if (s.hrStart > 0 && s.hrEnd > 0) " · ${s.hrStart}→${s.hrEnd} lpm" else ""
            val calm = s.calmDrop().takeIf { it >= 0.03f }?.let { " · ${(it * 100).toInt()}% más calma" } ?: ""
            val where = if (s.device == "watch") "Reloj" else "Teléfono"
            Label("${SimpleDateFormat("EEE HH:mm", ES).format(Date(s.at))} · ${s.minutes} min$hr$calm · $where", Ink.Dim2)
        }
    }
}
