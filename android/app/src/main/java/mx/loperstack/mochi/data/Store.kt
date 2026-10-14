// What the app keeps on the phone: the tasks, Mochi's colour, the Pomodoro.
// SharedPreferences + JSON, read once; every change goes through here so the
// screens (which watch `state`) and the Pomodoro's alarm see the same thing.

package mx.loperstack.mochi.data

import android.content.Context
import android.content.SharedPreferences
import mx.loperstack.mochi.widget.Widgets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mx.loperstack.mochi.wellness.BreathSession
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.sessionsJson
import mx.loperstack.mochi.wellness.sessionsOf
import mx.loperstack.mochi.wellness.Pomodoro
import mx.loperstack.mochi.wellness.Task
import mx.loperstack.mochi.wellness.TASK_LIMIT
import mx.loperstack.mochi.wellness.TASK_MAX_LEN
import org.json.JSONArray
import org.json.JSONObject

data class AppData(
    val tasks: List<Task> = emptyList(),
    /** ARGB; white is the PC's own Mochi (the default), any other colour tints him. */
    val mochiColor: Long = 0xFFF5F6F8,
    val pomodoro: Pomodoro = Pomodoro(),
    /** Breathing sessions from the phone and the watch, newest last (at most 200). */
    val sessions: List<BreathSession> = emptyList(),
    val soundOn: Boolean = true,
    /** The diary, oldest day first. */
    val logs: List<DayLog> = emptyList(),
    /** What Mochi calls you ("" until the welcome). */
    val name: String = "",
    /** "pixel" (your wallpaper's colours, light or dark as the phone), "pixelDark", or "mochi" (the island's). */
    val theme: String = "pixel",
    /** The welcome has been through once. */
    val onboarded: Boolean = false,
)

object Store {
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    /** Told after every change (the app keeps its sound switch in step). */
    @Volatile var onChange: (() -> Unit)? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences("mochi", Context.MODE_PRIVATE)
        // Mochi now looks like the PC's: the old violet default becomes white, once.
        if (!prefs.getBoolean("pcLook", false)) {
            val e = prefs.edit().putBoolean("pcLook", true)
            if (prefs.getLong("mochiColor", 0xFFA78BFA) == 0xFFA78BFA) e.putLong("mochiColor", 0xFFF5F6F8)
            e.apply()
        }
        _state.value = AppData(
            tasks = parseTasks(prefs.getString("tasks", null)),
            mochiColor = prefs.getLong("mochiColor", 0xFFF5F6F8),
            pomodoro = parsePomodoro(prefs.getString("pomodoro", null)),
            sessions = sessionsOf(prefs.getString("sessions", null)),
            soundOn = prefs.getBoolean("soundOn", true),
            logs = logsOf(prefs.getString("logs", null)),
            name = prefs.getString("name", "") ?: "",
            theme = prefs.getString("theme", "pixel") ?: "pixel",
            onboarded = prefs.getBoolean("onboarded", false),
        )
    }

    /** Every change reaches the watch and the home screen widgets. */
    private fun changed() {
        onChange?.invoke()
        WearSync.push(app)
        Widgets.refresh(app)
    }

    fun setTasks(tasks: List<Task>) {
        _state.value = _state.value.copy(tasks = tasks)
        prefs.edit().putString("tasks", tasksJson(tasks)).apply()
        changed()
    }

    fun setColor(argb: Long) {
        _state.value = _state.value.copy(mochiColor = argb)
        prefs.edit().putLong("mochiColor", argb).apply()
        changed()
    }

    fun setPomodoro(p: Pomodoro) {
        _state.value = _state.value.copy(pomodoro = p)
        prefs.edit().putString("pomodoro", pomodoroJson(p)).apply()
        changed()
    }

    /**
     * A session from the phone or the watch. Breathing together, both send the
     * same start: the one with the pulse (the watch's) wins.
     */
    fun addSession(session: BreathSession) {
        val same = _state.value.sessions.firstOrNull { it.at == session.at }
        if (same != null && (same.hrEnd > 0 || session.hrEnd <= 0)) return
        val list = (_state.value.sessions.filter { it.at != session.at } + session).sortedBy { it.at }.takeLast(200)
        _state.value = _state.value.copy(sessions = list)
        prefs.edit().putString("sessions", sessionsJson(list)).apply()
        Widgets.refresh(app)
    }

    fun setLogs(logs: List<DayLog>) {
        val moodBefore = _state.value.logs.firstOrNull { it.day == dayKey(System.currentTimeMillis()) }?.mood
        _state.value = _state.value.copy(logs = logs)
        prefs.edit().putString("logs", logsJson(logs)).apply()
        // Only today's mood leaves the phone (the watch shows it).
        if (logs.firstOrNull { it.day == dayKey(System.currentTimeMillis()) }?.mood != moodBefore) changed()
    }

    fun setName(name: String) {
        val clean = name.trim().take(30)
        _state.value = _state.value.copy(name = clean)
        prefs.edit().putString("name", clean).apply()
        changed()
    }

    fun setTheme(theme: String) {
        _state.value = _state.value.copy(theme = theme)
        prefs.edit().putString("theme", theme).apply()
    }

    fun setOnboarded(done: Boolean) {
        _state.value = _state.value.copy(onboarded = done)
        prefs.edit().putBoolean("onboarded", done).apply()
    }

    fun setSound(on: Boolean) {
        _state.value = _state.value.copy(soundOn = on)
        prefs.edit().putBoolean("soundOn", on).apply()
        changed()
    }
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun tasksJson(tasks: List<Task>): String = JSONArray().apply {
    for (t in tasks) put(JSONObject().put("id", t.id).put("text", t.text).put("done", t.done).put("createdAt", t.createdAt).put("remote", t.remote))
}.toString()

/** Only well-formed tasks survive a broken or old store. */
fun parseTasks(raw: String?): List<Task> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "")
            val text = o.optString("text", "").trim()
            if (id.isEmpty() || text.isEmpty()) null
            else Task(id, text.take(TASK_MAX_LEN), o.optBoolean("done", false), o.optLong("createdAt", 0), o.optBoolean("remote", false))
        }.takeLast(TASK_LIMIT)
    } catch (_: Exception) {
        emptyList()
    }
}

fun pomodoroJson(p: Pomodoro): String = JSONObject()
    .put("phase", p.phase.name)
    .put("endsAt", p.endsAt ?: JSONObject.NULL)
    .put("pausedLeft", p.pausedLeft ?: JSONObject.NULL)
    .put("completed", p.completed)
    .put("today", p.today)
    .put("day", p.day)
    .toString()

fun parsePomodoro(raw: String?): Pomodoro {
    if (raw.isNullOrBlank()) return Pomodoro()
    return try {
        val o = JSONObject(raw)
        val phase = runCatching { Phase.valueOf(o.optString("phase", "IDLE")) }.getOrDefault(Phase.IDLE)
        Pomodoro(
            phase = phase,
            endsAt = if (phase == Phase.IDLE || o.isNull("endsAt")) null else o.optLong("endsAt"),
            pausedLeft = if (phase == Phase.IDLE || o.isNull("pausedLeft")) null else o.optInt("pausedLeft"),
            completed = o.optInt("completed", 0).coerceAtLeast(0),
            today = o.optInt("today", 0).coerceAtLeast(0),
            day = o.optString("day", ""),
        )
    } catch (_: Exception) {
        Pomodoro()
    }
}
