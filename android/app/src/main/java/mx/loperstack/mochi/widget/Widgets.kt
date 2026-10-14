// Home screen widgets (Glance): Mochi with your day, Respirar (1·5·10 min,
// the watch joins in), the Pomodoro with its live countdown, and Pendientes
// you can tick off without opening the app. They redraw on every Store change.

package mx.loperstack.mochi.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.loperstack.mochi.MainActivity
import mx.loperstack.mochi.R
import mx.loperstack.mochi.data.Agenda
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.nextEvent
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.mochiBitmap
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.PomodoroScheduler
import mx.loperstack.mochi.wellness.clock
import mx.loperstack.mochi.wellness.isPaused
import mx.loperstack.mochi.wellness.left
import mx.loperstack.mochi.wellness.orderedTasks
import mx.loperstack.mochi.wellness.pause
import mx.loperstack.mochi.wellness.pendingCount
import mx.loperstack.mochi.wellness.resume
import mx.loperstack.mochi.wellness.startFocus
import mx.loperstack.mochi.wellness.toggleTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Bg = Color(0xFF141518)
private val Chip = Color(0xFF221C38)
private val White = ColorProvider(Color(0xFFF5F6F8))
private val Dim = ColorProvider(Color(0xFF8E939C))
private val Violet = ColorProvider(Color(0xFFC4B5FD))
private val GreenText = ColorProvider(Color(0xFF86EFAC))

/** Redraws every widget, at most every half second. */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    fun refresh(context: Context) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(500)
            runCatching { MochiWidget().updateAll(app) }
            runCatching { BreatheWidget().updateAll(app) }
            runCatching { PomodoroWidget().updateAll(app) }
            runCatching { TasksWidget().updateAll(app) }
        }
    }
}

private fun openApp(context: Context, tab: String, minutes: Int = 0) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setData(Uri.parse("mochi://widget/$tab/$minutes"))
            .putExtra(MainActivity.EXTRA_TAB, tab)
            .putExtra(MainActivity.EXTRA_BREATHE_MIN, minutes)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )

private fun mochi(context: Context, mood: Mood = Mood.IDLE, w: Int = 300, h: Int = 250) =
    ImageProvider(mochiBitmap(w, h, Color(Store.state.value.mochiColor), mood))

private val Card = GlanceModifier.fillMaxSize().background(Bg).cornerRadius(24.dp)

// ── Mochi ─────────────────────────────────────────────────────────────────────

class MochiWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val now = System.currentTimeMillis()
        val next = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            runCatching { nextEvent(Agenda.load(context, now), now) }.getOrNull()
        } else null
        val data = Store.state.value
        val focusing = data.pomodoro.phase == Phase.FOCUS
        val image = mochi(context, if (focusing) Mood.FOCUS else Mood.IDLE)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
        val pending = pendingCount(data.tasks)
        provideContent {
            Column(
                Card.padding(12.dp).clickable(openApp(context, "home")),
                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(image, contentDescription = "Mochi", modifier = GlanceModifier.size(110.dp, 92.dp))
                Text(hello, style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                if (next != null) {
                    val min = ((next.begin - now) / 60_000).toInt()
                    val whenText = if (min < 60) "En $min min" else SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(next.begin))
                    Text("$whenText · ${next.title}", style = TextStyle(color = Violet, fontSize = 12.sp), maxLines = 1)
                }
                Text(
                    if (pending == 0) "Sin pendientes ✓" else if (pending == 1) "1 pendiente" else "$pending pendientes",
                    style = TextStyle(color = if (pending == 0) GreenText else Dim, fontSize = 12.sp),
                )
            }
        }
    }
}

class MochiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MochiWidget()
}

// ── Respirar ──────────────────────────────────────────────────────────────────

class BreatheWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val image = mochi(context, Mood.BREATHING)
        val week = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }.timeInMillis
        val minutes = Store.state.value.sessions.filter { it.at >= week }.sumOf { it.minutes }
        provideContent {
            Column(Card.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.clickable(openApp(context, "breathe"))) {
                    Image(image, contentDescription = "Mochi", modifier = GlanceModifier.size(64.dp, 54.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text("Respirar", style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Text("$minutes min esta semana", style = TextStyle(color = Dim, fontSize = 12.sp))
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                Row {
                    for ((i, m) in listOf(1, 5, 10).withIndex()) {
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Box(
                            GlanceModifier.width(58.dp).height(40.dp).background(Chip).cornerRadius(20.dp).clickable(openApp(context, "breathe", m)),
                            contentAlignment = Alignment.Center,
                        ) { Text("$m min", style = TextStyle(color = Violet, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        }
    }
}

class BreatheWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BreatheWidget()
}

// ── Pomodoro ──────────────────────────────────────────────────────────────────

class PomodoroWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val p = Store.state.value.pomodoro
        val now = System.currentTimeMillis()
        val image = mochi(context, if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, 200, 166)
        val running = p.phase != Phase.IDLE && !p.isPaused() && p.endsAt != null
        val countdown = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
            val base = SystemClock.elapsedRealtime() + ((p.endsAt ?: now) - now)
            setChronometer(R.id.chrono, base, null, true)
            setChronometerCountDown(R.id.chrono, true)
        }
        provideContent {
            Row(Card.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(image, contentDescription = "Mochi", modifier = GlanceModifier.size(66.dp, 55.dp).clickable(openApp(context, "focus")))
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight().clickable(openApp(context, "focus"))) {
                    Text(
                        PomodoroScheduler.phaseTitle(p.phase) + if (p.isPaused()) " · en pausa" else "",
                        style = TextStyle(color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    )
                    if (running) AndroidRemoteViews(countdown)
                    else Text(if (p.phase == Phase.IDLE) "25:00" else clock(p.left(now)), style = TextStyle(color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    Text("${p.today} hoy", style = TextStyle(color = Dim, fontSize = 11.sp))
                }
                Box(
                    GlanceModifier.size(48.dp).background(Chip).cornerRadius(24.dp).clickable(actionRunCallback<PomodoroToggle>()),
                    contentAlignment = Alignment.Center,
                ) { Text(if (running) "❚❚" else "▶", style = TextStyle(color = Violet, fontSize = 18.sp, fontWeight = FontWeight.Bold)) }
            }
        }
    }
}

class PomodoroWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PomodoroWidget()
}

/** ▶ / ❚❚ on the widget: the same as the app's main button. */
class PomodoroToggle : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Store.init(context)
        PomodoroScheduler.channels(context)
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        val next = when {
            p.phase == Phase.IDLE -> p.startFocus(now)
            p.isPaused() -> p.resume(now)
            else -> p.pause(now)
        }
        Store.setPomodoro(next)
        PomodoroScheduler.sync(context, next, now)
    }
}

// ── Pendientes ────────────────────────────────────────────────────────────────

private val TaskId = ActionParameters.Key<String>("taskId")

class TasksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        val tasks = orderedTasks(Store.state.value.tasks).filter { !it.done }.take(20)
        val image = mochi(context, Mood.IDLE, 120, 100)
        provideContent { TasksContent(context, tasks.map { it.id to it.text }, image) }
    }
}

@Composable
private fun TasksContent(context: Context, tasks: List<Pair<String, String>>, image: ImageProvider) {
    Column(Card.padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(image, contentDescription = "Mochi", modifier = GlanceModifier.size(30.dp, 25.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "Pendientes · ${tasks.size}", modifier = GlanceModifier.defaultWeight().clickable(openApp(context, "tasks")),
                style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Box(
                GlanceModifier.size(34.dp).background(Chip).cornerRadius(17.dp).clickable(openApp(context, "tasks")),
                contentAlignment = Alignment.Center,
            ) { Text("+", style = TextStyle(color = Violet, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
        }
        Spacer(GlanceModifier.height(6.dp))
        if (tasks.isEmpty()) {
            Text("Nada pendiente ✓", style = TextStyle(color = GreenText, fontSize = 13.sp), modifier = GlanceModifier.padding(top = 8.dp))
        } else {
            LazyColumn {
                items(tasks, itemId = { it.first.hashCode().toLong() }) { (taskId, text) ->
                    CheckBox(
                        checked = false,
                        onCheckedChange = actionRunCallback<TaskDone>(actionParametersOf(TaskId to taskId)),
                        text = text,
                        style = TextStyle(color = White, fontSize = 14.sp),
                        maxLines = 2,
                        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

/** Ticking a task on the widget. */
class TaskDone : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskId] ?: return
        Store.init(context)
        Store.setTasks(toggleTask(Store.state.value.tasks, taskId))
    }
}
