// Home screen widgets (Glance): Mochi with your day, Respirar (1·5·10 min,
// the watch joins in), the Pomodoro with its live countdown, Pendientes you
// can tick off, Sueño, ¿Cómo estás? (one tap logs your mood) and Rachas.
// Each has its own cozy gradient, and Mochi moves: Glance can't animate, so
// his frames go in a ViewFlipper (a few small bitmaps — RemoteViews have a
// size limit). They redraw on every Store change.

package mx.loperstack.mochi.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
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
import mx.loperstack.mochi.mochi.MochiEngine
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.data.addMood
import mx.loperstack.mochi.data.dayKey
import mx.loperstack.mochi.data.doneOn
import mx.loperstack.mochi.data.hhmm
import mx.loperstack.mochi.data.minutes
import mx.loperstack.mochi.data.scheduledOn
import mx.loperstack.mochi.data.streak
import mx.loperstack.mochi.data.toggle
import mx.loperstack.mochi.wellness.MOODS
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

// Solid Material You colours that follow the phone's light or dark mode.
private val White: ColorProvider @Composable get() = GlanceTheme.colors.onSurface
private val Dim: ColorProvider @Composable get() = GlanceTheme.colors.onSurfaceVariant
private val Violet: ColorProvider @Composable get() = GlanceTheme.colors.primary
private val GreenText: ColorProvider @Composable get() = GlanceTheme.colors.tertiary
private val Amber: ColorProvider @Composable get() = GlanceTheme.colors.secondary
private val Chip: ColorProvider @Composable get() = GlanceTheme.colors.secondaryContainer

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
            runCatching { SleepWidget().updateAll(app) }
            runCatching { MoodWidget().updateAll(app) }
            runCatching { StreaksWidget().updateAll(app) }
            runCatching { CompanionWidget().updateAll(app) }
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

private fun color() = Color(Store.state.value.mochiColor)

private fun mochi(context: Context, mood: Mood = Mood.IDLE, w: Int = 300, h: Int = 250, face: MochiEngine.Eye? = null) =
    ImageProvider(mochiBitmap(w, h, color(), mood, face, wear = true, headroom = 0.32f))

// ── Cozy backgrounds ──────────────────────────────────────────────────────────

/** Each section's two tones, top-left to bottom-right. */
private enum class Mood2(val a: Int, val b: Int) {
    MOCHI(0xFF3B2A63.toInt(), 0xFF1C1733.toInt()),
    BREATHE(0xFF0F4A4A.toInt(), 0xFF182A48.toInt()),
    FOCUS(0xFF4A2338.toInt(), 0xFF22193D.toInt()),
    TASKS(0xFF1F3A5A.toInt(), 0xFF1A1E33.toInt()),
    SLEEP(0xFF1E1B4B.toInt(), 0xFF070B1F.toInt()),
    FEEL(0xFF5A3A2A.toInt(), 0xFF2E1F42.toInt()),
    STREAK(0xFF5C2E12.toInt(), 0xFF2E1A33.toInt()),
}

private val bgCache = HashMap<Mood2, ImageProvider>()

private fun bg(m: Mood2): ImageProvider = bgCache.getOrPut(m) {
    val w = 160
    val h = 160
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), m.a, m.b, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = null
    // A soft glow, top right.
    p.shader = android.graphics.RadialGradient(w * 0.85f, h * 0.1f, w * 0.6f, 0x33FFFFFF, 0, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    p.shader = null
    if (m == Mood2.SLEEP) {
        val r = java.util.Random(11)
        repeat(26) {
            p.color = ((0x55 + r.nextInt(0x99)) shl 24) or 0xFFFFFF
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.7f, 0.4f + r.nextFloat() * 1.1f, p)
        }
    }
    ImageProvider(bmp)
}

@Composable
private fun card(m: Mood2) = GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(
    when (m) {
        Mood2.MOCHI, Mood2.FEEL -> GlanceTheme.colors.widgetBackground
        Mood2.BREATHE -> GlanceTheme.colors.tertiaryContainer
        Mood2.FOCUS -> GlanceTheme.colors.primaryContainer
        Mood2.STREAK -> GlanceTheme.colors.secondaryContainer
        else -> GlanceTheme.colors.surface
    },
)

// ── Mochi, moving ─────────────────────────────────────────────────────────────

/** How he moves: each is a smooth tween (res/anim) the launcher plays on every flip. */
private enum class Motion(val layout: Int, val headroom: Float) {
    HOP(R.layout.widget_flip_hop, 0.4f),
    BOB(R.layout.widget_flip_bob, 0.32f),
    BREATH(R.layout.widget_flip_breath, 0.32f),
    SNOOZE(R.layout.widget_flip_snooze, 0.3f),
}

/**
 * Mochi moving, for `AndroidRemoteViews`: a ViewFlipper whose frames are the
 * same picture (sent once — RemoteViews share a repeated bitmap) plus, if
 * given, one with other eyes (a blink, a smile). Each flip plays the motion's
 * tween, so it is fluid at the screen's own rate, not a slideshow.
 */
private fun moving(context: Context, motion: Motion, mood: Mood, w: Int, h: Int, face: MochiEngine.Eye? = null, alt: MochiEngine.Eye? = null): RemoteViews {
    val flip = RemoteViews(context.packageName, motion.layout)
    val col = color()
    val main = mochiBitmap(w, h, col, mood, face, wear = true, headroom = motion.headroom)
    val other = alt?.let { mochiBitmap(w, h, col, mood, it, wear = true, headroom = motion.headroom) }
    for (b in listOfNotNull(main, main, main, other ?: main)) {
        val frame = RemoteViews(context.packageName, R.layout.widget_frame)
        frame.setImageViewBitmap(R.id.frame, b)
        flip.addView(R.id.flip, frame)
    }
    return flip
}

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
        Store.applyLook()
        val image = moving(context, if (pending0(data) == 0) Motion.HOP else Motion.BOB, if (focusing) Mood.FOCUS else Mood.IDLE, 240, 220, alt = MochiEngine.Eye.HAPPY)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello = when (hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }.let { if (data.name.isBlank()) it else "$it, ${data.name}" }
        val pending = pendingCount(data.tasks)
        provideContent { GlanceTheme {
            Column(
                card(Mood2.MOCHI).padding(12.dp).clickable(openApp(context, "home")),
                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
            ) {
                AndroidRemoteViews(image, GlanceModifier.size(124.dp, 114.dp))
                Text(hello, style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                if (next != null) {
                    val min = ((next.begin - now) / 60_000).toInt()
                    val whenText = if (min < 60) "En $min min" else SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(next.begin))
                    Text("$whenText · ${next.title}", style = TextStyle(color = Violet, fontSize = 12.sp), maxLines = 1)
                }
                Text(
                    if (pending == 0) "Sin pendientes" else if (pending == 1) "1 pendiente" else "$pending pendientes",
                    style = TextStyle(color = if (pending == 0) GreenText else Dim, fontSize = 12.sp),
                )
            }
        } }
    }
}

private fun pending0(d: mx.loperstack.mochi.data.AppData) = pendingCount(d.tasks)

class MochiWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MochiWidget()
}

// ── Respirar ──────────────────────────────────────────────────────────────────

class BreatheWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val image = moving(context, Motion.BREATH, Mood.BREATHING, 160, 146)
        val week = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            add(Calendar.DAY_OF_YEAR, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
        }.timeInMillis
        val minutes = Store.state.value.sessions.filter { it.at >= week }.sumOf { it.minutes }
        provideContent { GlanceTheme {
            Column(card(Mood2.BREATHE).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.clickable(openApp(context, "breathe"))) {
                    AndroidRemoteViews(image, GlanceModifier.size(72.dp, 66.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text("Respirar", style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Text("$minutes min esta semana · inhala con él", style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.height(10.dp))
                Row {
                    for ((i, m) in listOf(1, 5, 10).withIndex()) {
                        if (i > 0) Spacer(GlanceModifier.width(8.dp))
                        Box(
                            GlanceModifier.width(58.dp).height(40.dp).background(Chip).cornerRadius(20.dp).clickable(openApp(context, "breathe", m)),
                            contentAlignment = Alignment.Center,
                        ) { Text("$m min", style = TextStyle(color = GlanceTheme.colors.onTertiaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
                    }
                }
            }
        } }
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
        Store.applyLook()
        val image = moving(context, if (p.phase == Phase.FOCUS && !p.isPaused()) Motion.SNOOZE else Motion.BOB, if (p.phase == Phase.FOCUS) Mood.FOCUS else Mood.IDLE, 160, 146)
        val running = p.phase != Phase.IDLE && !p.isPaused() && p.endsAt != null
        val countdown = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
            val base = SystemClock.elapsedRealtime() + ((p.endsAt ?: now) - now)
            setChronometer(R.id.chrono, base, null, true)
            setChronometerCountDown(R.id.chrono, true)
        }
        provideContent { GlanceTheme {
            Row(card(Mood2.FOCUS).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.size(72.dp, 66.dp).clickable(openApp(context, "focus"))) { AndroidRemoteViews(image, GlanceModifier.fillMaxSize()) }
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight().clickable(openApp(context, "focus"))) {
                    Text(
                        PomodoroScheduler.phaseTitle(p.phase) + if (p.isPaused()) " · en pausa" else "",
                        style = TextStyle(color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    )
                    if (running) AndroidRemoteViews(countdown)
                    else Text(if (p.phase == Phase.IDLE) "%d:00".format(mx.loperstack.mochi.wellness.PomodoroConfig.current.focusMin) else clock(p.left(now)), style = TextStyle(color = White, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                    Text("${p.today} hoy", style = TextStyle(color = Dim, fontSize = 11.sp))
                }
                Box(
                    GlanceModifier.size(48.dp).background(Chip).cornerRadius(24.dp).clickable(actionRunCallback<PomodoroToggle>()),
                    contentAlignment = Alignment.Center,
                ) { Text(if (running) "❚❚" else "▶", style = TextStyle(color = Violet, fontSize = 18.sp, fontWeight = FontWeight.Bold)) }
            }
        } }
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
        Store.applyLook()
        val image = mochi(context, Mood.IDLE, 120, 100)
        provideContent { GlanceTheme { TasksContent(context, tasks.map { it.id to it.text }, image) } }
    }
}

@Composable
private fun TasksContent(context: Context, tasks: List<Pair<String, String>>, image: ImageProvider) {
    Column(card(Mood2.TASKS).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(image, contentDescription = "Tokkip", modifier = GlanceModifier.size(30.dp, 25.dp))
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
            Text("Nada pendiente", style = TextStyle(color = GreenText, fontSize = 13.sp), modifier = GlanceModifier.padding(top = 8.dp))
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
        mx.loperstack.mochi.data.TaskSync.toggle(context, taskId)
    }
}

// ── Sueño ─────────────────────────────────────────────────────────────────────

class SleepWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val d = Store.state.value
        val now = System.currentTimeMillis()
        val last = d.sleep.maxByOrNull { it.wake }
        val lastToday = last?.takeIf { it.night == dayKey(now) }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val night = hour >= 20 || hour < 6
        val image = moving(context, Motion.SNOOZE, Mood.SLEEPY, 190, 172)
        val title = when {
            lastToday != null -> "${lastToday.minutes / 60} h ${"%02d".format(lastToday.minutes % 60)} anoche"
            night -> "Hora de descansar"
            else -> "¿Cómo dormiste?"
        }
        val sub = when {
            night -> "Meta: dormir a las ${hhmm(d.sleepGoal.bedMin)}"
            lastToday != null && lastToday.minutes >= 420 -> "¡Descansaste!"
            lastToday != null && lastToday.minutes < 360 -> "Hoy ve con calma"
            else -> "Toca para registrar tu noche"
        }
        provideContent { GlanceTheme {
            Row(card(Mood2.SLEEP).padding(12.dp).clickable(openApp(context, "sleep")), verticalAlignment = Alignment.CenterVertically) {
                AndroidRemoteViews(image, GlanceModifier.size(92.dp, 84.dp))
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text("Sueño", style = TextStyle(color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    Text(title, style = TextStyle(color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(sub, style = TextStyle(color = Dim, fontSize = 12.sp), maxLines = 2)
                }
            }
        } }
    }
}

class SleepWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SleepWidget()
}

// ── ¿Cómo estás? ──────────────────────────────────────────────────────────────

private val MoodValue = ActionParameters.Key<Int>("mood")

class MoodWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val now = System.currentTimeMillis()
        val today = Store.state.value.logs.firstOrNull { it.day == dayKey(now) }
        val latest = today?.moods?.lastOrNull()?.value ?: today?.mood?.takeIf { it > 0 }
        val col = color()
        val faces = MOODS.reversed().map { m -> m to ImageProvider(mochiBitmap(96, 80, col, Mood.IDLE, m.face, wear = false)) }
        val label = latest?.let { v -> MOODS.firstOrNull { it.value == v }?.label }
        val count = today?.moods?.size ?: 0
        provideContent { GlanceTheme {
            Column(card(Mood2.FEEL).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (label == null) "¿Cómo estás?" else "Ahora: $label · $count hoy",
                    style = TextStyle(color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    modifier = GlanceModifier.padding(start = 4.dp).clickable(openApp(context, "diary")),
                )
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for ((m, img) in faces) {
                        Box(
                            GlanceModifier.defaultWeight().height(48.dp).cornerRadius(16.dp)
                                .background(if (m.value == latest) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
                                .clickable(actionRunCallback<MoodLog>(actionParametersOf(MoodValue to m.value))),
                            contentAlignment = Alignment.Center,
                        ) { Image(img, contentDescription = m.label, modifier = GlanceModifier.size(44.dp, 37.dp)) }
                        if (m != faces.last().first) Spacer(GlanceModifier.width(4.dp))
                    }
                }
            }
        } }
    }
}

class MoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoodWidget()
}

/** A face on the widget: logs that mood now, as the diary does. */
class MoodLog : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val v = parameters[MoodValue] ?: return
        Store.init(context)
        val now = System.currentTimeMillis()
        Store.setLogs(addMood(Store.state.value.logs, dayKey(now), now, v))
        mx.loperstack.mochi.mochi.Haptics.success(context)
    }
}

// ── Rachas ────────────────────────────────────────────────────────────────────

private val HabitId = ActionParameters.Key<String>("habitId")

class StreaksWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val today = Store.state.value.habits.filter { it.scheduledOn(cal) }
            .map { Triple(it.id, it.name, it.streak(now)) to it.doneOn(now) }
        val left = today.count { !it.second }
        val image = moving(context, if (today.isNotEmpty() && left == 0) Motion.HOP else Motion.BOB, Mood.IDLE, 110, 100)
        provideContent { GlanceTheme {
            Column(card(Mood2.STREAK).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, "streaks")), verticalAlignment = Alignment.CenterVertically) {
                    AndroidRemoteViews(image, GlanceModifier.size(42.dp, 38.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(
                        when {
                            today.isEmpty() -> "Rachas"
                            left == 0 -> "¡Todo listo hoy!"
                            else -> "Rachas · faltan $left"
                        },
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1,
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                if (today.isEmpty()) {
                    Text("Toca para crear tu primera racha", style = TextStyle(color = Amber, fontSize = 13.sp), modifier = GlanceModifier.clickable(openApp(context, "streaks")))
                } else {
                    LazyColumn {
                        items(today, itemId = { it.first.first.hashCode().toLong() }) { (h, done) ->
                            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                CheckBox(
                                    checked = done,
                                    onCheckedChange = actionRunCallback<HabitToggle>(actionParametersOf(HabitId to h.first)),
                                    text = h.second,
                                    style = TextStyle(color = White, fontSize = 14.sp),
                                    maxLines = 1,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Text(if (h.third > 0) "racha ${h.third}" else "", style = TextStyle(color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        } }
    }
}

class StreaksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreaksWidget()
}

/** Ticking a habit on the widget. */
class HabitToggle : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val hid = parameters[HabitId] ?: return
        Store.init(context)
        val now = System.currentTimeMillis()
        Store.setHabits(Store.state.value.habits.map { if (it.id == hid) it.toggle(now) else it })
        mx.loperstack.mochi.mochi.Haptics.success(context)
    }
}

// ── Compañero: everything at hand, changing through the day ───────────────────

class CompanionWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Store.init(context)
        Store.applyLook()
        val d = Store.state.value
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val next = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            runCatching { nextEvent(Agenda.load(context, now), now) }.getOrNull()
        } else null
        val p = d.pomodoro
        val focusing = p.phase != Phase.IDLE && !p.isPaused() && p.endsAt != null
        val tasks = orderedTasks(d.tasks).filter { !it.done }
        val habits = d.habits.filter { it.scheduledOn(cal) }
        val habitsLeft = habits.count { !it.doneOn(now) }
        val today = d.logs.firstOrNull { it.day == dayKey(now) }
        val mood = (today?.moods?.lastOrNull()?.value ?: today?.mood?.takeIf { it > 0 })?.let { v -> MOODS.firstOrNull { it.value == v } }
        val lastNight = d.sleep.maxByOrNull { it.wake }?.takeIf { it.night == dayKey(now) }
        val hello = when (hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }.let { if (d.name.isBlank()) it else "$it, ${d.name}" }
        val allDone = tasks.isEmpty() && habitsLeft == 0
        val image = moving(
            context, if (allDone) Motion.HOP else if (focusing) Motion.SNOOZE else Motion.BOB,
            if (focusing) Mood.FOCUS else if (hour >= 22 || hour < 6) Mood.SLEEPY else Mood.IDLE, 200, 182, alt = MochiEngine.Eye.HAPPY,
        )
        val countdown = RemoteViews(context.packageName, R.layout.widget_chrono).apply {
            setChronometer(R.id.chrono, SystemClock.elapsedRealtime() + ((p.endsAt ?: now) - now), null, true)
            setChronometerCountDown(R.id.chrono, true)
        }
        // The one thing that matters most right now.
        val (nowLabel, nowText) = when {
            focusing -> PomodoroScheduler.phaseTitle(p.phase) to null
            next != null && next.begin - now < 3 * 3_600_000L -> {
                val min = ((next.begin - now) / 60_000).toInt()
                (if (min <= 0) "Ahora" else if (min < 60) "En $min min" else "A las ${SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(next.begin))}") to next.title
            }
            hour in 5..11 && lastNight != null -> "Anoche" to "Dormiste ${lastNight.minutes / 60} h ${"%02d".format(lastNight.minutes % 60)}"
            hour >= 20 -> "Antes de dormir" to "¿Qué fue lo mejor de tu día?"
            allDone -> "Hoy" to "¡Todo listo! Tokkip está orgulloso"
            else -> "Hoy" to "Un paso a la vez"
        }
        provideContent {
            GlanceTheme {
                Column(GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(GlanceTheme.colors.widgetBackground).padding(14.dp)) {
                    Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, "home")), verticalAlignment = Alignment.CenterVertically) {
                        AndroidRemoteViews(image, GlanceModifier.size(78.dp, 72.dp))
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(hello, style = TextStyle(color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(nowLabel, style = TextStyle(color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            if (focusing) AndroidRemoteViews(countdown)
                            else if (nowText != null) Text(nowText, style = TextStyle(color = White, fontSize = 15.sp), maxLines = 2)
                        }
                    }
                    Spacer(GlanceModifier.height(10.dp))
                    // Three little cards: tasks, streaks, mood.
                    Row(GlanceModifier.fillMaxWidth()) {
                        @Composable
                        fun Mini(title: String, value: String, tab: String, last: Boolean = false) {
                            Column(
                                GlanceModifier.defaultWeight().height(62.dp).cornerRadius(18.dp).background(GlanceTheme.colors.secondaryContainer)
                                    .clickable(openApp(context, tab)).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(title, style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                                Text(value, style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            }
                            if (!last) Spacer(GlanceModifier.width(6.dp))
                        }
                        Mini("Tareas", if (tasks.isEmpty()) "Al día" else "${tasks.size} · ${tasks.first().text}", "tasks")
                        Mini("Rachas", if (habits.isEmpty()) "Crear" else if (habitsLeft == 0) "Listas" else "Faltan $habitsLeft", "streaks")
                        Mini("Ánimo", mood?.label ?: "¿Cómo vas?", "diary", last = true)
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    // Quick actions.
                    Row(GlanceModifier.fillMaxWidth()) {
                        @Composable
                        fun Action(text: String, modifier: GlanceModifier, last: Boolean = false) {
                            Box(modifier.defaultWeight().height(40.dp).cornerRadius(20.dp).background(GlanceTheme.colors.primary), contentAlignment = Alignment.Center) {
                                Text(text, style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                            }
                            if (!last) Spacer(GlanceModifier.width(6.dp))
                        }
                        Action("Respirar", GlanceModifier.clickable(openApp(context, "breathe", 5)))
                        Action(if (focusing) "Pausa" else "Enfoque", GlanceModifier.clickable(actionRunCallback<PomodoroToggle>()))
                        Action("Diario", GlanceModifier.clickable(openApp(context, "diary")), last = true)
                    }
                }
            }
        }
    }
}

class CompanionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompanionWidget()
}
