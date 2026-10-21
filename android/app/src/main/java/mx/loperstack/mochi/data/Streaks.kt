// Rachas: habits you want to keep, each on the days you choose (every day if
// none) and, if you like, at a time with a reminder. A day counts when you
// tick it; the streak is the run of scheduled days ticked, up to today (a
// scheduled today still to do doesn't break it).
// Pure Kotlin + org.json: tested in app/src/test.

package mx.loperstack.mochi.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Habit(
    val id: String,
    val name: String,
    val emoji: String = "⭐",
    /** Calendar.DAY_OF_WEEK values (1 = Sunday … 7 = Saturday); empty = every day. */
    val days: Set<Int> = emptySet(),
    /** Minutes of the day for the reminder; null = any time. */
    val time: Int? = null,
    val remind: Boolean = true,
    val color: Long = 0xFFA78BFA,
    /** Days ticked, yyyy-MM-dd. */
    val done: Set<String> = emptySet(),
    val createdAt: Long = 0,
)

val HABIT_EMOJIS = listOf(
    "⭐", "💧", "📚", "🏃", "🧘", "🥗", "😴", "💊", "🎸", "✍️", "🧹", "🌱",
    "🚶", "💪", "🍎", "🎨", "🧠", "📵", "☀️", "🙏", "💰", "🗣️", "🐶", "❤️",
)

val HABIT_COLORS = listOf(0xFFA78BFA, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24, 0xFFF97316, 0xFFF472B6, 0xFF22D3EE, 0xFFF87171)

/** L M X J V S D → Calendar days, Monday first. */
val WEEK = listOf(Calendar.MONDAY to "L", Calendar.TUESDAY to "M", Calendar.WEDNESDAY to "X", Calendar.THURSDAY to "J", Calendar.FRIDAY to "V", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D")

private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

fun Habit.scheduledOn(c: Calendar): Boolean = days.isEmpty() || c.get(Calendar.DAY_OF_WEEK) in days

fun Habit.doneOn(ms: Long) = dayKey(ms) in done

/** The run of scheduled days ticked, ending today (or yesterday if today is still to do). */
fun Habit.streak(now: Long): Int {
    val c = cal(now)
    var n = 0
    // Today, if scheduled, only adds when done; it never breaks the run.
    if (scheduledOn(c) && doneOn(c.timeInMillis)) n++
    c.add(Calendar.DAY_OF_YEAR, -1)
    var guard = 0
    while (guard++ < 800) {
        if (scheduledOn(c)) {
            if (doneOn(c.timeInMillis)) n++ else break
        }
        if (c.timeInMillis < createdAt - 86_400_000L) break
        c.add(Calendar.DAY_OF_YEAR, -1)
    }
    return n
}

/** The longest run ever. */
fun Habit.best(now: Long): Int {
    if (done.isEmpty()) return 0
    val first = done.minOf { it }
    val c = Calendar.getInstance().apply { timeInMillis = dayStart(first) }
    val end = cal(now)
    var run = 0
    var best = 0
    var guard = 0
    while (!c.after(end) && guard++ < 2000) {
        if (scheduledOn(c)) {
            if (doneOn(c.timeInMillis)) { run++; best = maxOf(best, run) }
            else if (dayKey(c.timeInMillis) != dayKey(now)) run = 0
        }
        c.add(Calendar.DAY_OF_YEAR, 1)
    }
    return best
}

enum class DayMark { DONE, MISSED, TODAY, OFF }

/** The last seven days, oldest first: ticked, missed, still to do today, or not scheduled. */
fun Habit.week(now: Long): List<DayMark> = (6 downTo 0).map { back ->
    val c = cal(now).apply { add(Calendar.DAY_OF_YEAR, -back) }
    when {
        !scheduledOn(c) -> DayMark.OFF
        doneOn(c.timeInMillis) -> DayMark.DONE
        back == 0 -> DayMark.TODAY
        else -> DayMark.MISSED
    }
}

fun Habit.toggle(now: Long): Habit {
    val k = dayKey(now)
    return copy(done = (if (k in done) done - k else done + k).sorted().takeLast(800).toSet())
}

/** "Todos los días · 07:30", "L X V". */
fun Habit.scheduleText(): String {
    val d = if (days.isEmpty() || days.size == 7) "Todos los días" else WEEK.filter { it.first in days }.joinToString(" ") { it.second }
    return if (time != null) "$d · ${hhmm(time)}" else d
}

/** A word of encouragement for the reminder. */
fun Habit.nudge(now: Long): String {
    val n = streak(now)
    return when {
        n == 0 -> "Hoy es un gran día para empezar «$name» $emoji"
        n == 1 -> "Ayer empezaste «$name». ¡Que sean dos! $emoji"
        n < 7 -> "🔥 Llevas $n días con «$name». ¡No rompas la racha!"
        n < 30 -> "🔥 ¡$n días seguidos con «$name»! Eres constante, sigue así."
        else -> "🏆 $n días con «$name». Esto ya es parte de ti."
    }
}

fun habitsJson(list: List<Habit>): String = JSONArray().apply {
    for (h in list) put(
        JSONObject().put("id", h.id).put("name", h.name).put("emoji", h.emoji)
            .put("days", JSONArray(h.days.toList())).put("time", h.time ?: -1).put("remind", h.remind)
            .put("color", h.color).put("done", JSONArray(h.done.toList())).put("createdAt", h.createdAt),
    )
}.toString()

fun habitsOf(raw: String?): List<Habit> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id", "")
            val name = o.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            val d = o.optJSONArray("days")
            val dn = o.optJSONArray("done")
            Habit(
                id, name.take(60), o.optString("emoji", "⭐").take(8),
                (0 until (d?.length() ?: 0)).map { d!!.optInt(it) }.filter { it in 1..7 }.toSet(),
                o.optInt("time", -1).takeIf { it in 0 until 1440 }, o.optBoolean("remind", true),
                o.optLong("color", 0xFFA78BFA),
                (0 until (dn?.length() ?: 0)).map { dn!!.optString(it) }.filter { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }.toSet(),
                o.optLong("createdAt"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
