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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.SegmentedButton
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
import androidx.compose.runtime.SideEffect
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
import mx.loperstack.mochi.data.breathing
import mx.loperstack.mochi.data.TaskSync
import mx.loperstack.mochi.data.WearSync
import mx.loperstack.mochi.data.isTomorrow
import mx.loperstack.mochi.data.nextEvent
import mx.loperstack.mochi.data.upcoming
import mx.loperstack.mochi.mochi.MOCHI_PALETTE
import mx.loperstack.mochi.mochi.Mochi
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.Haptics
import mx.loperstack.mochi.mochi.rememberMusicPlaying
import mx.loperstack.mochi.data.GoogleTasks
import android.app.Activity
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

enum class Tab(val label: String) { HOME("Inicio"), TASKS("Tareas"), FOCUS("Enfoque"), STREAKS("Rachas"), BREATHE("Respirar"), DIARY("Diario"), SLEEP("Sueño"), PET("Mimos") }

/** The bottom bar's five places (Material 3: three to five); two of them hold two screens each. */
enum class Group(val label: String, val tabs: List<Tab>) {
    HOME("Inicio", listOf(Tab.HOME)),
    TASKS("Tareas", listOf(Tab.TASKS, Tab.FOCUS, Tab.STREAKS)),
    DIARY("Diario", listOf(Tab.DIARY)),
    CALM("Bienestar", listOf(Tab.BREATHE, Tab.SLEEP)),
    PET("Mochi", listOf(Tab.PET));

    companion object {
        fun of(tab: Tab) = entries.first { tab in it.tabs }
    }
}

private val ES = Locale("es", "MX")

// ── Actions ───────────────────────────────────────────────────────────────────

/** Everything the screens can do; Mochi hops through `jump`. */
class Actions(private val context: Context) {
    var jump by mutableIntStateOf(0)
    var breathing by mutableStateOf(Breathing())
    /** Music is playing on the phone: Mochi dances. */
    var music by mutableStateOf(false)

    // The watch's pulse during the session (0 = none yet), and every reading with its time.
    private val hrReadings = mutableListOf<Pair<Long, Int>>()
    var hrStart = 0
    var hrMin = 0
    var hrLast = 0

    fun addTask(text: String): Boolean {
        if (!TaskSync.add(context, text)) return false
        Sounds.play("pop")
        return true
    }

    fun toggle(task: Task) {
        TaskSync.toggle(context, task.id)
        if (!task.done) {
            jump++
            Sounds.play("approve")
            Haptics.success(context)
        } else {
            Sounds.play("blip")
            Haptics.click(context)
        }
    }

    fun remove(task: Task) {
        TaskSync.remove(context, task.id)
        Sounds.play("blip")
    }

    fun clearDone() {
        TaskSync.clearDone(context)
        Sounds.play("blip")
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
        val b = Store.state.value.breathing(System.currentTimeMillis(), minutes)
        breathing = b
        hrStart = 0; hrMin = 0; hrLast = 0
        hrReadings.clear()
        Sounds.play("open")
        WearSync.breatheTogether(context, b)
    }

    fun heartRate(bpm: Int) {
        if (bpm <= 0) return
        if (hrStart == 0) hrStart = bpm
        hrMin = if (hrMin == 0) bpm else minOf(hrMin, bpm)
        hrLast = bpm
        hrReadings += System.currentTimeMillis() to bpm
    }

    /** The readings as the session's pulse: one value every HR_STEP_S seconds, the latest one known. */
    private fun hrSeries(start: Long, minutes: Int): List<Int> {
        if (hrReadings.isEmpty()) return emptyList()
        val n = minutes * 60 / mx.loperstack.mochi.wellness.HR_STEP_S
        return List(n) { i ->
            val t = start + i * mx.loperstack.mochi.wellness.HR_STEP_S * 1000L
            hrReadings.lastOrNull { it.first <= t + 1500 }?.second ?: 0
        }
    }

    fun stopBreathing(completed: Boolean) {
        val b = breathing
        val start = b.startedAt
        breathing = Breathing()
        if (completed && start != null) {
            jump++
            Sounds.play("love")
            val withWatch = hrLast > 0
            Store.addSession(
                BreathSession(
                    start, b.minutes, b.minutes * 6, hrStart, hrLast, hrMin,
                    device = if (withWatch) "watch" else "phone", hr = hrSeries(start, b.minutes),
                ),
            )
            Haptics.stop(context)
            Haptics.success(context)
        } else {
            Sounds.play("close")
            Haptics.stop(context)
            WearSync.stopTogether(context)
        }
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
fun MochiApp(startTab: Tab = Tab.HOME, breatheNow: Pair<Int, Long> = 0 to 0L, composeNow: Long = 0L) {
    val context = LocalContext.current
    val data by Store.state.collectAsState()
    val actions = remember { Actions(context) }
    var tab by remember { mutableStateOf(startTab) }
    // Each place remembers its last screen (Tareas ↔ Enfoque, Respirar ↔ Sueño).
    val lastIn = remember { mutableMapOf<Group, Tab>() }
    SideEffect { lastIn[Group.of(tab)] = tab }
    LaunchedEffect(startTab) { tab = startTab }
    // The Respirar widget's 1 · 5 · 10 buttons.
    LaunchedEffect(breatheNow) {
        val (min, key) = breatheNow
        if (min > 0 && key != 0L) {
            tab = Tab.BREATHE
            if (actions.breathing.startedAt == null) actions.startBreathing(min)
        }
    }
    var showColors by remember { mutableStateOf(false) }
    var showNewEvent by remember { mutableStateOf(false) }
    var agendaKey by remember { mutableIntStateOf(0) }
    val now = rememberNow()
    val music = rememberMusicPlaying()
    SideEffect { actions.music = music }

    // The agenda, with READ_CALENDAR.
    var calendarOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    var events by remember { mutableStateOf(emptyList<CalEvent>()) }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { calendarOk = it }
    val minute = now / 60_000
    LaunchedEffect(calendarOk, minute / 5, agendaKey) { if (calendarOk) events = Agenda.load(context) }

    // New events go to the calendar: READ and WRITE_CALENDAR.
    val askWrite = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        calendarOk = r[Manifest.permission.READ_CALENDAR] == true || calendarOk
        if (r[Manifest.permission.WRITE_CALENDAR] == true) showNewEvent = true
    }
    val newEvent = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) showNewEvent = true
        else askWrite.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    // Google Tasks: Google's consent screen comes back here.
    val googleTasks by GoogleTasks.status.collectAsState()
    val googleConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        (context as? Activity)?.let { GoogleTasks.onResult(it, r.data) }
    }
    val connectGoogle: () -> Unit = {
        (context as? Activity)?.let { a -> GoogleTasks.connect(a) { req -> googleConsent.launch(req) } }
    }

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

    MochiTheme(data.theme) {
        if (!data.onboarded) {
            Welcome(data, googleTasks, actions, connectGoogle) {
                Store.setOnboarded(true)
                calendarOk = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            }
            return@MochiTheme
        }
        if (desk) {
            DeskScreen(data.tasks, data.pomodoro, events, now, color, actions, startPomodoro, calendarOk) { askCalendar.launch(Manifest.permission.READ_CALENDAR) }
        } else {
            Scaffold(
                containerColor = Ink.Ground,
                bottomBar = {
                    NavigationBar(containerColor = Ink.Card, tonalElevation = 0.dp) {
                        val glyphs = mapOf(Group.HOME to Glyph.Home, Group.TASKS to Glyph.Tasks, Group.DIARY to Glyph.Diary, Group.CALM to Glyph.Breathe, Group.PET to Glyph.Heart)
                        val current = Group.of(tab)
                        for (g in Group.entries) {
                            NavigationBarItem(
                                selected = current == g,
                                onClick = {
                                    if (current != g) Haptics.tick(context)
                                    tab = if (current == g) tab else lastIn[g] ?: g.tabs.first()
                                },
                                icon = { Icon(glyphs.getValue(g), contentDescription = null, modifier = Modifier.size(22.dp)) },
                                label = { Text(g.label, fontSize = 12.sp, fontWeight = if (current == g) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ink.VioletSoft,
                                    selectedTextColor = Ink.Text,
                                    indicatorColor = Ink.VioletDeep,
                                    unselectedIconColor = Ink.Dim,
                                    unselectedTextColor = Ink.Dim,
                                ),
                            )
                        }
                    }
                },
            ) { pad ->
              Column(Modifier.padding(pad).fillMaxSize()) {
                val group = Group.of(tab)
                if (group.tabs.size > 1) {
                    SubTabs(group.tabs, tab) { Haptics.tick(context); tab = it }
                }
                AnimatedContent(
                    targetState = tab, modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(240)) + slideInHorizontally(tween(320)) { it / 8 * dir }) togetherWith
                            (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { -it / 8 * dir })
                    },
                    label = "tab",
                ) { shown ->
                    when (shown) {
                        Tab.HOME -> HomeScreen(
                            data.tasks, data.pomodoro, events, now, color, actions, calendarOk,
                            askCalendar = { askCalendar.launch(Manifest.permission.READ_CALENDAR) },
                            openColors = { showColors = true },
                            goFocus = { tab = Tab.FOCUS }, goBreathe = { tab = Tab.BREATHE }, goTasks = { tab = Tab.TASKS },
                            newEvent = newEvent,
                        )
                        Tab.TASKS -> TasksScreen(data.tasks, actions)
                        Tab.FOCUS -> FocusScreen(data.tasks, data.pomodoro, now, color, actions, startPomodoro)
                        Tab.BREATHE -> BreatheScreen(color, actions, data.sessions)
                        Tab.DIARY -> DiaryScreen(data.logs, color, actions, composeNow) { tab = Tab.BREATHE }
                        Tab.SLEEP -> SleepScreen(data, color, actions)
                        Tab.PET -> PetScreen(color, actions)
                        Tab.STREAKS -> StreaksScreen(data.habits, color, actions)
                    }
                }
              }
            }
        }
        if (showNewEvent) {
            ModalBottomSheet(onDismissRequest = { showNewEvent = false }, containerColor = Ink.CardHigh) {
                NewEventSheet(actions) {
                    showNewEvent = false
                    calendarOk = true
                    agendaKey++
                }
            }
        }
        if (showColors) {
            ModalBottomSheet(onDismissRequest = { showColors = false }, containerColor = Ink.CardHigh) {
                SettingsSheet(
                    data, color, googleTasks, connectGoogle, syncGoogle = { TaskSync.refresh(context) },
                    disconnectGoogle = { GoogleTasks.disconnect(context) },
                    replayWelcome = { showColors = false; Store.setOnboarded(false) },
                )
            }
        }
    }
}

// ── Shared pieces ─────────────────────────────────────────────────────────────

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Ink.Card)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = Ink.Dim, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.SemiBold) =
    Text(text, color = color, fontSize = size, fontWeight = weight)

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Ink.Text, textColor: Color = Ink.Ground, onClick: () -> Unit) {
    val context = LocalContext.current
    Button(
        onClick = { Haptics.click(context); onClick() }, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = textColor),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
}

@Composable
fun RoundIcon(glyph: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val context = LocalContext.current
    IconButton(onClick = { Haptics.click(context); onClick() }, modifier = Modifier.size(52.dp).clip(CircleShape).background(Ink.CardHigh)) {
        Icon(glyph, contentDescription = description, tint = Ink.Dim, modifier = Modifier.size(20.dp))
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
            .background(if (highlight) Ink.Violet.copy(alpha = 0.14f) else Color.Transparent)
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
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ink.Green.copy(alpha = 0.18f))
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
    goFocus: () -> Unit, goBreathe: () -> Unit, goTasks: () -> Unit, newEvent: () -> Unit = {},
) {
    val next = nextEvent(events, now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, start = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label(longDate(now))
                val who = Store.state.value.name
                Text(if (who.isBlank()) greeting(now) else "${greeting(now)}, $who", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink.Text)
            }
            RoundIcon(Glyph.Gear, "Color de Mochi", openColors)
        }
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
          Box {
            Aurora(color, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Mochi(
                Modifier.align(Alignment.CenterHorizontally).width(170.dp).height(140.dp),
                color = color,
                mood = if (pomodoro.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE,
                jump = actions.jump,
                interactive = true,
                greet = true,
                dancing = actions.music,
            )
            if (actions.music) Label("♪ Mochi baila con tu música", Ink.VioletSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
            when (mx.loperstack.mochi.mochi.MochiStyle.rest) {
                mx.loperstack.mochi.mochi.Rest.TIRED -> Label("😴 Dormiste poco: Mochi anda con ojeras", Ink.Dim, modifier = Modifier.align(Alignment.CenterHorizontally))
                mx.loperstack.mochi.mochi.Rest.GREAT -> Label("✨ Descansaste: Mochi está de muy buen humor", Ink.GreenSoft, modifier = Modifier.align(Alignment.CenterHorizontally))
                else -> Unit
            }
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
          }
        }
        if (calendarOk) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Agenda", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "+ Evento", color = Ink.VioletSoft, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Ink.VioletDeep).clickable(onClick = newEvent)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Panel(Modifier.fillMaxWidth(), padding = 8.dp) { AgendaList(events, now, actions, max = 5) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Enfoque", if (pomodoro.phase == Phase.IDLE) "25:00" else clock(pomodoro.left(now)), Ink.VioletDeep, Ink.VioletLine, Ink.VioletSoft, Modifier.weight(1f), goFocus)
            Tile("Respirar", "1 · 5 · 10 min", Ink.Green.copy(alpha = 0.10f), Ink.Green.copy(alpha = 0.22f), Ink.GreenSoft, Modifier.weight(1f), goBreathe, small = true)
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
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = modifier)

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
            Text("Tareas", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(tasks: List<Task>, p: mx.loperstack.mochi.wellness.Pomodoro, now: Long, color: Color, actions: Actions, start: () -> Unit) {
    val track = rememberNowPlaying()
    var aod by remember { mutableStateOf(false) }
    var tune by remember { mutableStateOf(false) }
    val cfg = Store.state.collectAsState().value.pomodoroConfig
    if (aod) FocusAod(p, color, track) { aod = false }
    if (tune) {
        ModalBottomSheet(onDismissRequest = { tune = false }, containerColor = Ink.CardHigh) { PomodoroSettings(cfg) }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            Label("Pomodoro · ${p.today} hoy")
            Text(PomodoroScheduler.phaseTitle(p.phase), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        FocusRing(p, now, color, Modifier.size(260.dp), dancing = actions.music)
        val current = orderedTasks(tasks).firstOrNull { !it.done }
        if (current != null && p.phase == Phase.FOCUS) {
            Spacer(Modifier.height(14.dp))
            Text(
                "✓  ${current.text}", color = Ink.VioletSoft, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment("⚙️ Personalizar", false) { tune = true }
            Segment("🌙 Modo AOD", false) { aod = true }
        }
        Spacer(Modifier.height(16.dp))
        NowPlayingCard(track)
        Spacer(Modifier.height(14.dp))
        Label("${cfg.focusMin} min de enfoque · ${cfg.breakMin} de descanso · uno largo de ${cfg.longBreakMin} cada ${cfg.cyclesBeforeLong} bloques. Te avisa aunque la app esté cerrada.", Ink.Dim2)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FocusRing(p: mx.loperstack.mochi.wellness.Pomodoro, now: Long, color: Color, modifier: Modifier, mochi: Boolean = true, dancing: Boolean = false) {
    val ring = if (p.phase == Phase.BREAK || p.phase == Phase.LONG_BREAK) Ink.Green else Ink.Violet
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val inset = stroke / 2
            drawArc(Ink.Track, 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
            drawArc(
                ring, -90f, 360f * p.progress(now), false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (mochi) {
                Mochi(Modifier.width(90.dp).height(72.dp), color = color, mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, dancing = dancing)
            }
            Text(clock(p.left(now)), fontSize = 46.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ── Respirar: BreatheScreen and the history live in Breathe.kt ─────────────

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
            Text("Conexión con la PC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
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
                color = if (level <= 15) Ink.Red else Ink.Green, trackColor = Ink.Track,
            )
            Spacer(Modifier.height(8.dp))
            Label(if (charging) "Cargando" else "Con batería", if (charging) Ink.GreenSoft else Ink.Dim)
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
                    Text(line, fontSize = 15.sp, color = Ink.Dim, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

// ── Color de Mochi: ColorPicker.kt ─────────────────────────────────────────────

// ── Tablet: modo escritorio ───────────────────────────────────────────────────

@Composable
fun DeskScreen(
    tasks: List<Task>, p: mx.loperstack.mochi.wellness.Pomodoro, events: List<CalEvent>, now: Long, color: Color,
    actions: Actions, start: () -> Unit, calendarOk: Boolean, askCalendar: () -> Unit,
) {
    Row(Modifier.fillMaxSize().background(Ink.Ground).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier.weight(1.1f).fillMaxHeight().clip(RoundedCornerShape(34.dp))
                .background(Brush.radialGradient(listOf(Ink.VioletDeep, Ink.Card), radius = 1100f))
                .border(1.dp, Ink.Hairline, RoundedCornerShape(34.dp)).padding(30.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Label(longDate(now), Ink.Dim, 16.sp, FontWeight.SemiBold)
                Text(hourMinute(now), fontSize = 100.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 100.sp)
            }
            Mochi(
                Modifier.fillMaxWidth().aspectRatio(1.4f), color = color,
                mood = if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, jump = actions.jump,
                interactive = true, greet = true, dancing = actions.music,
            )
            Label("Toca a Mochi", Ink.Dim2, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Column(
            Modifier.weight(1.3f).fillMaxHeight().clip(RoundedCornerShape(34.dp)).background(Ink.Card)
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


// ── Google Tasks (in the settings sheet) ──────────────────────────────────────

@Composable
fun GoogleTasksRow(st: GoogleTasks.Status, connect: () -> Unit, sync: () -> Unit, disconnect: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Google Tasks", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Label(
                    when {
                        st.busy -> "Sincronizando…"
                        st.connected -> "Conectado · la misma lista que en tu PC"
                        else -> "Tus tareas, también en Google y en la PC"
                    },
                    if (st.connected) Ink.GreenSoft else Ink.Dim2,
                )
            }
            if (st.connected) {
                OutlinedButton(onClick = sync) { Text("Sincronizar", color = Ink.Text) }
            } else {
                PrimaryButton("Conectar", color = Ink.VioletSoft, onClick = connect)
            }
        }
        if (st.error.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Label(st.error, Ink.Red)
        }
        if (!st.connected) GoogleSetupHelp()
        if (st.connected) {
            Text(
                "Desconectar", color = Ink.Dim2, fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp).clickable(onClick = disconnect),
            )
        }
        Spacer(Modifier.height(10.dp))
        Label("Los eventos nuevos se guardan en tu Google Calendar de este teléfono, sin conectar nada.", Ink.Dim2, 12.sp)
    }
}


/** Material 3 segmented buttons for a place with two screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubTabs(tabs: List<Tab>, current: Tab, pick: (Tab) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            SegmentedButton(
                selected = t == current,
                onClick = { pick(t) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, tabs.size),
                colors = androidx.compose.material3.SegmentedButtonDefaults.colors(
                    activeContainerColor = Ink.VioletDeep, activeContentColor = Ink.Text,
                    inactiveContainerColor = Color.Transparent, inactiveContentColor = Ink.Dim,
                    activeBorderColor = Ink.Hairline, inactiveBorderColor = Ink.Hairline,
                ),
            ) { Text(t.label, fontWeight = FontWeight.SemiBold) }
        }
    }
}
