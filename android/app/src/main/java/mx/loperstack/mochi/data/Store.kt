// What the app keeps on the phone: the tasks, Mochi's colour, the Pomodoro.
// SharedPreferences + JSON, read once; every change goes through here so the
// screens (which watch `state`) and the Pomodoro's alarm see the same thing.

package mx.loperstack.mochi.data

import android.content.Context
import android.content.SharedPreferences
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
    /** ARGB; the violet of the PC's Bienestar pill by default. */
    val mochiColor: Long = 0xFFA78BFA,
    val pomodoro: Pomodoro = Pomodoro(),
    /** Breathing sessions from the phone and the watch, newest last (at most 200). */
    val sessions: List<BreathSession> = emptyList(),
    val soundOn: Boolean = true,
)

object Store {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(AppData())
    val state: StateFlow<AppData> = _state.asStateFlow()

    /** Told after every change (the watch sync listens). */
    @Volatile var onChange: (() -> Unit)? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("mochi", Context.MODE_PRIVATE)
        _state.value = AppData(
            tasks = parseTasks(prefs.getString("tasks", null)),
            mochiColor = prefs.getLong("mochiColor", 0xFFA78BFA),
            pomodoro = parsePomodoro(prefs.getString("pomodoro", null)),
            sessions = sessionsOf(prefs.getString("sessions", null)),
            soundOn = prefs.getBoolean("soundOn", true),
        )
    }

    fun setTasks(tasks: List<Task>) {
        _state.value = _state.value.copy(tasks = tasks)
        prefs.edit().putString("tasks", tasksJson(tasks)).apply()
        onChange?.invoke()
    }

    fun setColor(argb: Long) {
        _state.value = _state.value.copy(mochiColor = argb)
        prefs.edit().putLong("mochiColor", argb).apply()
        onChange?.invoke()
    }

    fun setPomodoro(p: Pomodoro) {
        _state.value = _state.value.copy(pomodoro = p)
        prefs.edit().putString("pomodoro", pomodoroJson(p)).apply()
        onChange?.invoke()
    }

    fun addSession(session: BreathSession) {
        if (_state.value.sessions.any { it.at == session.at && it.device == session.device }) return
        val list = (_state.value.sessions + session).sortedBy { it.at }.takeLast(200)
        _state.value = _state.value.copy(sessions = list)
        prefs.edit().putString("sessions", sessionsJson(list)).apply()
    }

    fun setSound(on: Boolean) {
        _state.value = _state.value.copy(soundOn = on)
        prefs.edit().putBoolean("soundOn", on).apply()
        onChange?.invoke()
    }
}

// ── JSON ──────────────────────────────────────────────────────────────────────

fun tasksJson(tasks: List<Task>): String = JSONArray().apply {
    for (t in tasks) put(JSONObject().put("id", t.id).put("text", t.text).put("done", t.done).put("createdAt", t.createdAt))
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
            else Task(id, text.take(TASK_MAX_LEN), o.optBoolean("done", false), o.optLong("createdAt", 0))
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
