// Mochi's notifications, each with its own illustrated card: a good morning
// (a resilient phrase, or one that knows how you slept), your events a few
// minutes before, a gentle nudge for pending tasks, a night invitation to
// write in the diary, and your streaks' reminders. All in Ajustes →
// Notificaciones. Alarms are exact and re-planned each time one rings and
// whenever the app opens.

package mx.loperstack.mochi.wellness

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import mx.loperstack.mochi.MainActivity
import mx.loperstack.mochi.data.Agenda
import mx.loperstack.mochi.data.Habit
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.dayKey
import mx.loperstack.mochi.data.doneOn
import mx.loperstack.mochi.data.minutes
import mx.loperstack.mochi.data.nudge
import mx.loperstack.mochi.data.scheduledOn
import mx.loperstack.mochi.data.upcoming
import mx.loperstack.mochi.mochi.MochiEngine
import mx.loperstack.mochi.mochi.Mood
import mx.loperstack.mochi.mochi.Rest
import mx.loperstack.mochi.mochi.mochiBitmap
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private val MORNING = listOf(
    "Hoy no tienes que poder con todo. Solo con el siguiente paso.",
    "Lo que ayer pesó, hoy lo ves con ojos nuevos.",
    "Avanzar despacio también es avanzar.",
    "Tu calma también es productividad.",
    "Eres más fuerte de lo que recuerdas en tus días difíciles.",
    "Un día a la vez, una tarea a la vez.",
    "Lo que siembras hoy, aunque no se vea, está creciendo.",
    "No necesitas un día perfecto para tener un buen día.",
    "Respira. Ya superaste el 100 % de tus días malos.",
    "Hoy es una página en blanco. Escribe algo bonito.",
    "La constancia le gana al talento cuando el talento no es constante.",
    "Haz lo que puedas, con lo que tienes, donde estás.",
    "Cada mañana es otra oportunidad de empezar.",
    "Pequeños hábitos, grandes cambios.",
    "Tu esfuerzo de hoy es el orgullo de mañana.",
)
private val MORNING_TIRED = listOf(
    "Dormiste poco: hoy sé amable contigo y baja un poco el ritmo.",
    "Noche corta. Agua, luz de sol y una pausa para respirar te van a ayudar.",
    "Hoy vamos con calma. Lo importante primero, lo demás puede esperar.",
)
private val MORNING_RESTED = listOf(
    "¡Descansaste! Hoy tienes energía para lo que te propongas.",
    "Dormiste bien: tu yo de hoy te lo agradece. ¡A brillar!",
    "Buen descanso, buen día. Aprovecha esa energía.",
)
private val NIGHT = listOf(
    "¿Qué fue lo mejor de tu día?",
    "¿Qué aprendiste hoy de ti?",
    "Tres cosas por las que hoy das gracias.",
    "¿Qué te gustaría soltar antes de dormir?",
    "¿Qué hiciste hoy que tu yo de ayer no habría hecho?",
    "¿Cómo te trataste hoy?",
    "Un momento de hoy que quieras recordar.",
    "¿Qué harías distinto mañana?",
)

private fun hm(ms: Long) = SimpleDateFormat("HH:mm", Locale("es", "MX")).format(Date(ms))

object MochiNotifier {
    const val CH_DAY = "mochi_day"
    const val CH_REMIND = "mochi_remind"
    const val CH_STREAK = "mochi_streak"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_AT = "at"
    const val EXTRA_MEET = "meet"

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DAY, "Buenos días y buenas noches", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Eventos y tareas", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_STREAK, "Rachas", NotificationManager.IMPORTANCE_DEFAULT))
    }

    // ── Planning ──────────────────────────────────────────────────────────────

    private fun alarm(context: Context, code: Int, at: Long, kind: String, extras: Intent.() -> Unit = {}) {
        val am = context.getSystemService(AlarmManager::class.java)
        val i = Intent(context, MochiAlarmReceiver::class.java).setData(Uri.parse("mochi://alarm/$kind/$code"))
            .putExtra(EXTRA_KIND, kind).apply(extras)
        val pi = PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun cancel(context: Context, code: Int, kind: String) {
        val i = Intent(context, MochiAlarmReceiver::class.java).setData(Uri.parse("mochi://alarm/$kind/$code"))
        val pi = PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) context.getSystemService(AlarmManager::class.java).cancel(pi)
    }

    /** The next time `minuteOfDay` comes, after now. */
    fun nextAt(minuteOfDay: Int, now: Long, days: Set<Int> = emptySet()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60); set(Calendar.MINUTE, minuteOfDay % 60); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while ((c.timeInMillis <= now || (days.isNotEmpty() && c.get(Calendar.DAY_OF_WEEK) !in days)) && guard++ < 14) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** Plans every alarm from the settings, today's events and the habits. */
    fun sync(context: Context) {
        runCatching {
            Store.init(context)
            val d = Store.state.value
            val n = d.notify
            val now = System.currentTimeMillis()
            if (n.morning) alarm(context, 101, nextAt(n.morningAt, now), "morning") else cancel(context, 101, "morning")
            if (n.tasks) alarm(context, 102, nextAt(n.tasksAt, now), "tasks") else cancel(context, 102, "tasks")
            if (n.night) alarm(context, 103, nextAt(n.nightAt, now), "night") else cancel(context, 103, "night")
            // Events: the next day's, a few minutes ahead.
            if (n.events && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                for (e in upcoming(Agenda.load(context, now), now).filter { !it.allDay }.take(12)) {
                    val at = e.begin - n.eventLead * 60_000L
                    if (at <= now) continue
                    alarm(context, 5000 + (e.id % 4000).toInt() + (e.begin / 60_000 % 97).toInt(), at, "event") {
                        putExtra(EXTRA_TITLE, e.title); putExtra(EXTRA_AT, e.begin); putExtra(EXTRA_MEET, e.meetUrl)
                    }
                }
            }
            // Habits with a time.
            for ((i, h) in d.habits.withIndex()) {
                val code = 9000 + i
                val t = h.time
                if (n.streaks && h.remind && t != null) alarm(context, code, nextAt(t, now, h.days), "habit") { putExtra(EXTRA_ID, h.id) }
                else cancel(context, code, "habit")
            }
        }
    }

    // ── Ringing ───────────────────────────────────────────────────────────────

    fun ring(context: Context, intent: Intent, preview: Boolean = false) {
        Store.init(context)
        Store.applyLook()
        channels(context)
        val d = Store.state.value
        val now = System.currentTimeMillis()
        val name = d.name
        when (intent.getStringExtra(EXTRA_KIND)) {
            "morning" -> {
                val rest = mx.loperstack.mochi.mochi.MochiStyle.rest
                val phrase = when (rest) {
                    Rest.TIRED -> MORNING_TIRED.random()
                    Rest.GREAT -> MORNING_RESTED.random()
                    else -> MORNING.random()
                }
                val last = d.sleep.lastOrNull()?.takeIf { it.night == dayKey(now) }
                val sleepLine = last?.let { "Dormiste ${it.minutes / 60} h ${"%02d".format(it.minutes % 60)}" }
                val pending = d.tasks.count { !it.done }
                val extra = listOfNotNull(sleepLine, if (pending > 0) "$pending pendientes" else null).joinToString(" · ")
                post(
                    context, 201, CH_DAY,
                    title = if (name.isBlank()) "Buenos días" else "Buenos días, $name",
                    text = phrase, sub = extra, theme = Theme.MORNING,
                    face = if (rest == Rest.TIRED) MochiEngine.Eye.TIRED else MochiEngine.Eye.HAPPY,
                    open = openApp(context, "home"),
                )
            }
            "tasks" -> {
                val pending = d.tasks.filter { !it.done }
                if (pending.isEmpty() && preview) {
                    post(
                        context, 202, CH_REMIND, title = "¡Sin pendientes!", text = "Hoy no te queda nada. Disfruta tu tarde",
                        sub = "Así se ve el aviso de tareas", theme = Theme.TASKS, face = MochiEngine.Eye.HAPPY, open = openApp(context, "tasks"),
                    )
                }
                if (pending.isNotEmpty()) {
                    val list = pending.take(4).joinToString("\n") { "• ${it.text}" }
                    post(
                        context, 202, CH_REMIND,
                        title = if (pending.size == 1) "Te queda una tarea" else "Te quedan ${pending.size} tareas",
                        text = list, sub = "Una a la vez, tú puedes", theme = Theme.TASKS, face = MochiEngine.Eye.WIDE,
                        open = openApp(context, "tasks"),
                    )
                }
            }
            "night" -> {
                val habitsLeft = d.habits.filter { h ->
                    val c = Calendar.getInstance()
                    h.scheduledOn(c) && !h.doneOn(now)
                }
                val sub = if (d.notify.streaks && habitsLeft.isNotEmpty()) "Aún hoy: " + habitsLeft.take(3).joinToString(", ") { it.name } else "Toca para escribir en tu diario"
                post(
                    context, 203, CH_DAY,
                    title = if (name.isBlank()) "Buenas noches" else "Buenas noches, $name",
                    text = NIGHT.random(), sub = sub, theme = Theme.NIGHT, face = MochiEngine.Eye.CLOSED,
                    open = openApp(context, "diary", compose = true),
                    action = "Escribir" to openApp(context, "diary", compose = true),
                )
            }
            "event" -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Evento"
                val at = intent.getLongExtra(EXTRA_AT, now)
                val meet = intent.getStringExtra(EXTRA_MEET)
                val minutes = ((at - now) / 60_000).toInt().coerceAtLeast(0)
                post(
                    context, 6000 + (title.hashCode() and 0x3FF), CH_REMIND,
                    title = title, text = if (minutes <= 1) "Empieza ahora" else "Empieza en $minutes min · ${hm(at)}",
                    sub = "Respira, ya casi", theme = Theme.EVENT, face = MochiEngine.Eye.DOT,
                    open = openApp(context, "home"),
                    action = meet?.let { "Unirse" to PendingIntent.getActivity(context, 7, Intent(Intent.ACTION_VIEW, Uri.parse(it)), PendingIntent.FLAG_IMMUTABLE) },
                )
            }
            "habit" -> {
                val h: Habit? = d.habits.firstOrNull { it.id == intent.getStringExtra(EXTRA_ID) }
                if (h == null && preview) {
                    post(
                        context, 8000, CH_STREAK, title = " Tu racha", text = "Hoy es un gran día para empezar una racha",
                        sub = "Crea una en Tareas → Rachas", theme = Theme.STREAK, face = MochiEngine.Eye.STAR, open = openApp(context, "streaks"),
                    )
                }
                if (h != null && (preview || (!h.doneOn(now) && h.scheduledOn(Calendar.getInstance())))) {
                    post(
                        context, 8000 + (h.id.hashCode() and 0x3FF), CH_STREAK,
                        title = h.name, text = h.nudge(now), sub = "Toca para marcarlo",
                        theme = Theme.STREAK, face = MochiEngine.Eye.STAR,
                        open = openApp(context, "streaks"),
                    )
                }
            }
        }
        sync(context)
    }

    private fun openApp(context: Context, tab: String, compose: Boolean = false): PendingIntent =
        PendingIntent.getActivity(
            context, tab.hashCode() + if (compose) 1 else 0,
            Intent(context, MainActivity::class.java).setData(Uri.parse("mochi://open/$tab/$compose"))
                .putExtra(MainActivity.EXTRA_TAB, tab).putExtra(MainActivity.EXTRA_COMPOSE, compose)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    enum class Theme(val top: Int, val bottom: Int, val accent: Int) {
        MORNING(0xFFFFC9A8.toInt(), 0xFFC4B5FD.toInt(), 0xFFF97316.toInt()),
        TASKS(0xFFA7F3D0.toInt(), 0xFF7DD3FC.toInt(), 0xFF0D9488.toInt()),
        EVENT(0xFFBFDBFE.toInt(), 0xFFC4B5FD.toInt(), 0xFF6366F1.toInt()),
        NIGHT(0xFF1E1B4B.toInt(), 0xFF4C1D95.toInt(), 0xFFA78BFA.toInt()),
        STREAK(0xFFFED7AA.toInt(), 0xFFFBCFE8.toInt(), 0xFFEA580C.toInt()),
    }

    /** The card shown when the notification opens up: a soft gradient, Mochi and the words. */
    fun card(text: String, theme: Theme, face: MochiEngine.Eye?, color: Long): Bitmap {
        val w = 1080
        val h = 540
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), theme.top, theme.bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 64f, 64f, p)
        p.shader = null
        val dark = theme == Theme.NIGHT
        if (dark) {
            // A few stars.
            p.color = 0xCCFFFFFF.toInt()
            val r = Random(7)
            repeat(28) { c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * 0.8f, 1.5f + r.nextFloat() * 3f, p) }
        }
        // A soft circle behind Mochi.
        p.color = if (dark) 0x22FFFFFF else 0x55FFFFFF
        c.drawCircle(w * 0.78f, h * 0.55f, h * 0.42f, p)
        val mochi = mochiBitmap(440, 400, Color(color), Mood.IDLE, face, wear = true, headroom = 0.3f)
        c.drawBitmap(mochi, null, RectF(w * 0.78f - 220f, h * 0.55f - 220f, w * 0.78f + 220f, h * 0.55f + 180f), Paint(Paint.FILTER_BITMAP_FLAG))
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (dark) 0xFFF5F3FF.toInt() else 0xFF1F1A2E.toInt()
            textSize = 56f
            typeface = Typeface.create(Typeface.create("google-sans", Typeface.NORMAL), 700, false)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, (w * 0.52f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(6f, 1f).setMaxLines(5).build()
        c.save()
        c.translate(72f, (h - layout.height) / 2f)
        layout.draw(c)
        c.restore()
        return bmp
    }

    private fun post(
        context: Context, id: Int, channel: String, title: String, text: String, sub: String, theme: Theme,
        face: MochiEngine.Eye?, open: PendingIntent, action: Pair<String, PendingIntent>? = null,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val color = Store.state.value.mochiColor
        val icon = mochiBitmap(256, 256, Color(color), Mood.IDLE, face, wear = true, headroom = 0.3f)
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(mx.loperstack.mochi.shared.R.drawable.ic_stat_mochi)
            .setColor(theme.accent)
            .setLargeIcon(icon)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setSubText(sub.takeIf { it.isNotBlank() })
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(card(text, theme, face, color))
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(sub),
            )
            .setContentIntent(open)
            .setAutoCancel(true)
        action?.let { (label, pi) -> b.addAction(0, label, pi) }
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, b.build()) }
    }

    /** A sample of each, from Ajustes. */
    fun preview(context: Context, kind: String) {
        Store.init(context)
        val i = Intent().putExtra(EXTRA_KIND, kind)
        when (kind) {
            "event" -> i.putExtra(EXTRA_TITLE, "Ejemplo: reunión de equipo").putExtra(EXTRA_AT, System.currentTimeMillis() + Store.state.value.notify.eventLead * 60_000L)
            "habit" -> Store.state.value.habits.firstOrNull()?.let { i.putExtra(EXTRA_ID, it.id) }
        }
        ring(context, i, preview = true)
    }
}

class MochiAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { MochiNotifier.sync(context); BedReminder.sync(context); return }
        MochiNotifier.ring(context, intent)
    }
}
