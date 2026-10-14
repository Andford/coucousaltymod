// What the phone and the watch tell each other (Wearable Data Layer):
// - phone → watch: a Snapshot (tasks, Mochi's colour, the phone's battery, the
//   next events, the Pomodoro), as one DataItem at STATE_PATH;
// - watch → phone: small messages (toggle or add a task, a breathing session,
//   start/pause the Pomodoro, "send me the state").
// Plain JSON, so both sides and the tests read the same thing.

package mx.loperstack.mochi.wellness

import org.json.JSONArray
import org.json.JSONObject

object SyncPaths {
    const val STATE = "/mochi/state"
    const val TOGGLE_TASK = "/mochi/task/toggle"
    const val ADD_TASK = "/mochi/task/add"
    const val SESSION = "/mochi/session"
    const val POMODORO = "/mochi/pomodoro/toggle"
    const val REQUEST = "/mochi/sync"
    /** phone → watch: "startedAt,minutes" — breathe together, the watch reads the pulse. */
    const val BREATHE_START = "/mochi/breathe/start"
    /** phone → watch: the session was cut short. */
    const val BREATHE_STOP = "/mochi/breathe/stop"
    /** watch → phone: the pulse right now, "bpm". */
    const val HEART_RATE = "/mochi/hr"
    /** watch → phone: today's mood, "1"…"5". */
    const val MOOD = "/mochi/mood"
}

/** "1700000000000,5" → a session, or null. */
fun breathingOf(text: String): Breathing? {
    val parts = text.split(",")
    val at = parts.getOrNull(0)?.toLongOrNull() ?: return null
    val min = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..60 } ?: return null
    return Breathing(at, min)
}

fun Breathing.toWire(): String = "${startedAt ?: 0},$minutes"

data class EventLite(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val meetUrl: String?)

data class Snapshot(
    val tasks: List<Task> = emptyList(),
    val mochiColor: Long = 0xFFF5F6F8,
    val phoneBattery: Int = -1,
    val phoneCharging: Boolean = false,
    val events: List<EventLite> = emptyList(),
    val pomodoro: Pomodoro = Pomodoro(),
    val soundOn: Boolean = true,
    /** Today's mood in the diary, 1…5 (0 = not logged yet). */
    val moodToday: Int = 0,
    /** Your name, for Mochi's hello. */
    val name: String = "",
    /** Taps and navigation vibrate (Ajustes on the phone). */
    val haptics: Boolean = true,
    /** Last night's sleep in minutes (0 = not logged), and its morning, yyyy-MM-dd. */
    val sleepMin: Int = 0,
    val sleepNight: String = "",
    /** Mochi drawn flat (2D) or with the PC's gradient. */
    val flat: Boolean = true,
    /** The last breathing sessions (with their pulse) and nights, for the watch's reports. */
    val sessions: List<BreathSession> = emptyList(),
    val nights: List<NightLite> = emptyList(),
    val at: Long = 0,
)

/** A breathing session, from the phone or the watch (heart rate only from the watch). */
data class BreathSession(
    val at: Long,
    val minutes: Int,
    val breaths: Int,
    /** Beats per minute; 0 = not measured. */
    val hrStart: Int = 0,
    val hrEnd: Int = 0,
    val hrMin: Int = 0,
    val device: String = "phone",
    /** The pulse through the session, one reading every HR_STEP_S seconds (0 = no reading). */
    val hr: List<Int> = emptyList(),
    /** Beat-to-beat intervals in ms, when the watch's heartbeat sensor gives them. */
    val rr: List<Int> = emptyList(),
)

/** Seconds between the session's pulse readings. */
const val HR_STEP_S = 2

/** A night for the watch's report. */
data class NightLite(val night: String, val minutes: Int, val quality: Int, val bed: Long, val wake: Long)

/** How much calmer: the drop from the first to the last beats, as a share. 0 when not measured. */
fun BreathSession.calmDrop(): Float =
    if (hrStart <= 0 || hrEnd <= 0) 0f else ((hrStart - hrEnd).toFloat() / hrStart).coerceAtLeast(0f)

fun taskJson(t: Task): JSONObject =
    JSONObject().put("id", t.id).put("text", t.text).put("done", t.done).put("createdAt", t.createdAt)

fun taskOf(o: JSONObject?): Task? {
    o ?: return null
    val id = o.optString("id", "")
    val text = o.optString("text", "").trim()
    return if (id.isEmpty() || text.isEmpty()) null else Task(id, text.take(TASK_MAX_LEN), o.optBoolean("done"), o.optLong("createdAt"))
}

fun Snapshot.toJson(): String = JSONObject()
    .put("tasks", JSONArray().apply { tasks.forEach { put(taskJson(it)) } })
    .put("color", mochiColor)
    .put("battery", phoneBattery)
    .put("charging", phoneCharging)
    .put("events", JSONArray().apply {
        events.forEach {
            put(JSONObject().put("title", it.title).put("begin", it.begin).put("end", it.end).put("allDay", it.allDay).put("meet", it.meetUrl ?: ""))
        }
    })
    .put("pomodoro", JSONObject()
        .put("phase", pomodoro.phase.name)
        .put("endsAt", pomodoro.endsAt ?: 0L)
        .put("pausedLeft", pomodoro.pausedLeft ?: -1)
        .put("today", pomodoro.today))
    .put("sound", soundOn)
    .put("mood", moodToday)
    .put("name", name)
    .put("haptics", haptics)
    .put("sleepMin", sleepMin)
    .put("sleepNight", sleepNight)
    .put("flat", flat)
    .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJsonObject()) } })
    .put("nights", JSONArray().apply {
        nights.forEach { put(JSONObject().put("night", it.night).put("min", it.minutes).put("q", it.quality).put("bed", it.bed).put("wake", it.wake)) }
    })
    .put("at", at)
    .toString()

fun snapshotOf(raw: String?): Snapshot {
    if (raw.isNullOrBlank()) return Snapshot()
    return try {
        val o = JSONObject(raw)
        val tasks = o.optJSONArray("tasks")?.let { a -> (0 until a.length()).mapNotNull { taskOf(a.optJSONObject(it)) } } ?: emptyList()
        val events = o.optJSONArray("events")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val e = a.optJSONObject(i) ?: return@mapNotNull null
                EventLite(
                    e.optString("title", ""), e.optLong("begin"), e.optLong("end"), e.optBoolean("allDay"),
                    e.optString("meet", "").takeIf { it.startsWith("https://") },
                )
            }
        } ?: emptyList()
        val p = o.optJSONObject("pomodoro")
        val phase = runCatching { Phase.valueOf(p?.optString("phase") ?: "IDLE") }.getOrDefault(Phase.IDLE)
        Snapshot(
            tasks = tasks,
            mochiColor = o.optLong("color", 0xFFF5F6F8),
            phoneBattery = o.optInt("battery", -1),
            phoneCharging = o.optBoolean("charging"),
            events = events,
            pomodoro = Pomodoro(
                phase = phase,
                endsAt = p?.optLong("endsAt")?.takeIf { it > 0 && phase != Phase.IDLE },
                pausedLeft = p?.optInt("pausedLeft", -1)?.takeIf { it >= 0 && phase != Phase.IDLE },
                today = p?.optInt("today") ?: 0,
            ),
            soundOn = o.optBoolean("sound", true),
            moodToday = o.optInt("mood", 0).coerceIn(0, 5),
            name = o.optString("name", "").take(30),
            haptics = o.optBoolean("haptics", true),
            sleepMin = o.optInt("sleepMin", 0).coerceIn(0, 24 * 60),
            sleepNight = o.optString("sleepNight", ""),
            flat = o.optBoolean("flat", true),
            sessions = o.optJSONArray("sessions")?.let { a -> (0 until a.length()).mapNotNull { sessionOf(a.optJSONObject(it)?.toString()) } } ?: emptyList(),
            nights = o.optJSONArray("nights")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val n = a.optJSONObject(i) ?: return@mapNotNull null
                    NightLite(n.optString("night"), n.optInt("min"), n.optInt("q"), n.optLong("bed"), n.optLong("wake"))
                }
            } ?: emptyList(),
            at = o.optLong("at"),
        )
    } catch (_: Exception) {
        Snapshot()
    }
}

fun BreathSession.toJsonObject(): JSONObject = JSONObject()
    .put("at", at).put("minutes", minutes).put("breaths", breaths)
    .put("hrStart", hrStart).put("hrEnd", hrEnd).put("hrMin", hrMin).put("device", device)
    .put("hr", JSONArray(hr)).put("rr", JSONArray(rr))

fun BreathSession.toJson(): String = toJsonObject().toString()

private fun ints(a: JSONArray?, max: Int, range: IntRange): List<Int> =
    (0 until minOf(a?.length() ?: 0, max)).map { a!!.optInt(it) }.map { if (it in range) it else 0 }

fun sessionOf(raw: String?): BreathSession? = try {
    val o = JSONObject(raw ?: "")
    BreathSession(
        o.getLong("at"), o.optInt("minutes"), o.optInt("breaths"),
        o.optInt("hrStart"), o.optInt("hrEnd"), o.optInt("hrMin"), o.optString("device", "phone"),
        ints(o.optJSONArray("hr"), 60 * 60 / HR_STEP_S, 0..230),
        ints(o.optJSONArray("rr"), 5000, 0..2500).filter { it > 0 },
    )
} catch (_: Exception) {
    null
}

fun sessionsJson(list: List<BreathSession>): String = JSONArray().apply { list.forEach { put(it.toJsonObject()) } }.toString()

fun sessionsOf(raw: String?): List<BreathSession> = try {
    val a = JSONArray(raw ?: "[]")
    (0 until a.length()).mapNotNull { sessionOf(a.optJSONObject(it)?.toString()) }
} catch (_: Exception) {
    emptyList()
}

/** Next events for the watch: not over, soonest first, at most `max`. */
fun nextEvents(events: List<EventLite>, now: Long, max: Int = 5) =
    events.filter { it.end > now }.sortedBy { it.begin }.take(max)

/** "En 12 min", "Ahora", or null past an hour (the caller shows the time). */
fun minutesUntil(begin: Long, now: Long): Int = ((begin - now) / 60_000).toInt()
