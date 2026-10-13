// The Bienestar rules, ported from the PC's windows/src/core/wellness.ts so the
// phone and the island behave the same: tasks, the Pomodoro and the breath.
// Pure Kotlin, no Android: tested in app/src/test.

package mx.loperstack.mochi.wellness

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ── Tasks ─────────────────────────────────────────────────────────────────────

data class Task(val id: String, val text: String, val done: Boolean, val createdAt: Long)

const val TASK_MAX_LEN = 200
const val TASK_LIMIT = 100

fun addTask(tasks: List<Task>, text: String, now: Long, id: String): List<Task> {
    val clean = text.replace(Regex("\\s+"), " ").trim().take(TASK_MAX_LEN)
    if (clean.isEmpty()) return tasks
    val next = (tasks + Task(id, clean, false, now)).toMutableList()
    while (next.size > TASK_LIMIT) {
        val i = next.indexOfFirst { it.done }
        if (i < 0) break
        next.removeAt(i)
    }
    return next
}

fun toggleTask(tasks: List<Task>, id: String) = tasks.map { if (it.id == id) it.copy(done = !it.done) else it }
fun removeTask(tasks: List<Task>, id: String) = tasks.filter { it.id != id }
fun clearDone(tasks: List<Task>) = tasks.filter { !it.done }
fun orderedTasks(tasks: List<Task>) = tasks.filter { !it.done } + tasks.filter { it.done }
fun pendingCount(tasks: List<Task>) = tasks.count { !it.done }

// ── Pomodoro ──────────────────────────────────────────────────────────────────

enum class Phase { IDLE, FOCUS, BREAK, LONG_BREAK }

data class PomodoroConfig(val focusMin: Int = 25, val breakMin: Int = 5, val longBreakMin: Int = 15, val cyclesBeforeLong: Int = 4)

data class Pomodoro(
    val phase: Phase = Phase.IDLE,
    /** Unix ms the running phase ends; null while paused or idle. */
    val endsAt: Long? = null,
    /** Seconds left while paused. */
    val pausedLeft: Int? = null,
    /** Focus blocks finished since the last long break. */
    val completed: Int = 0,
    /** Focus blocks finished today. */
    val today: Int = 0,
    /** The day `today` counts, yyyy-MM-dd (local). */
    val day: String = "",
)

enum class PomodoroEvent { FOCUS_DONE, BREAK_DONE }

fun dayKey(nowMs: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = nowMs }
    return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}

fun phaseMinutes(phase: Phase, cfg: PomodoroConfig = PomodoroConfig()) = when (phase) {
    Phase.FOCUS, Phase.IDLE -> cfg.focusMin
    Phase.BREAK -> cfg.breakMin
    Phase.LONG_BREAK -> cfg.longBreakMin
}

fun Pomodoro.isRunning() = phase != Phase.IDLE && endsAt != null && pausedLeft == null
fun Pomodoro.isPaused() = phase != Phase.IDLE && pausedLeft != null

/** Seconds left in the phase (the full focus block while idle). */
fun Pomodoro.left(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig()): Int = when {
    phase == Phase.IDLE -> cfg.focusMin * 60
    pausedLeft != null -> pausedLeft
    endsAt == null -> phaseMinutes(phase, cfg) * 60
    else -> max(0, ceil((endsAt - nowMs) / 1000.0).toInt())
}

/** 0…1 of the phase gone by. */
fun Pomodoro.progress(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig()): Float {
    if (phase == Phase.IDLE) return 0f
    val total = phaseMinutes(phase, cfg) * 60f
    return (1f - left(nowMs, cfg) / total).coerceIn(0f, 1f)
}

private fun Pomodoro.withDay(nowMs: Long): Pomodoro {
    val d = dayKey(nowMs)
    return if (day == d) this else copy(day = d, today = 0)
}

fun Pomodoro.startFocus(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig()) =
    withDay(nowMs).copy(phase = Phase.FOCUS, endsAt = nowMs + cfg.focusMin * 60_000L, pausedLeft = null)

fun Pomodoro.pause(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig()) =
    if (!isRunning()) this else copy(pausedLeft = left(nowMs, cfg), endsAt = null)

fun Pomodoro.resume(nowMs: Long) =
    if (!isPaused()) this else copy(endsAt = nowMs + (pausedLeft ?: 0) * 1000L, pausedLeft = null)

fun Pomodoro.reset() = copy(phase = Phase.IDLE, endsAt = null, pausedLeft = null, completed = 0)

/**
 * Ends the phase now: a focus block leads to a break — a long one every
 * `cyclesBeforeLong` — and a break back to focus. Only a block that ran out on
 * its own (`counted`) counts as done.
 */
fun Pomodoro.finishPhase(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig(), counted: Boolean = true): Pair<Pomodoro, PomodoroEvent?> {
    val base = withDay(nowMs)
    return when (phase) {
        Phase.FOCUS -> {
            val completed = if (counted) base.completed + 1 else base.completed
            val long = counted && completed >= cfg.cyclesBeforeLong
            val next = if (long) Phase.LONG_BREAK else Phase.BREAK
            base.copy(
                phase = next,
                completed = if (long) 0 else completed,
                today = if (counted) base.today + 1 else base.today,
                endsAt = nowMs + phaseMinutes(next, cfg) * 60_000L,
                pausedLeft = null,
            ) to PomodoroEvent.FOCUS_DONE
        }
        Phase.BREAK, Phase.LONG_BREAK ->
            base.copy(phase = Phase.FOCUS, endsAt = nowMs + cfg.focusMin * 60_000L, pausedLeft = null) to PomodoroEvent.BREAK_DONE
        Phase.IDLE -> this to null
    }
}

/** The alarm's job: a running phase whose time is up ends. */
fun Pomodoro.tick(nowMs: Long, cfg: PomodoroConfig = PomodoroConfig()): Pair<Pomodoro, PomodoroEvent?> =
    if (!isRunning() || (endsAt ?: 0) > nowMs) this to null else finishPhase(nowMs, cfg)

// ── Breathing ─────────────────────────────────────────────────────────────────

val BREATHE_MINUTES = listOf(1, 5, 10)
const val INHALE_S = 4.0
const val EXHALE_S = 6.0
private const val CYCLE_S = INHALE_S + EXHALE_S

data class Breathing(val startedAt: Long? = null, val minutes: Int = 1)

data class BreathFrame(
    val active: Boolean,
    val inhaling: Boolean,
    /** How full Mochi is: 0 empty … 1 full, eased. */
    val fill: Float,
    /** Seconds left in the session. */
    val left: Int,
    val breaths: Int,
    val done: Boolean,
    /** 0…1 of the session gone by. */
    val progress: Float,
)

private fun easeInOut(x: Double) = 0.5 - 0.5 * cos(PI * x.coerceIn(0.0, 1.0))

fun breathFrame(b: Breathing, nowMs: Long): BreathFrame {
    val start = b.startedAt ?: return BreathFrame(false, true, 0f, 0, 0, false, 0f)
    val total = b.minutes * 60.0
    val elapsed = max(0.0, (nowMs - start) / 1000.0)
    val done = elapsed >= total
    val t = min(elapsed, total)
    val inCycle = t % CYCLE_S
    val inhaling = inCycle < INHALE_S
    val p = if (inhaling) inCycle / INHALE_S else (inCycle - INHALE_S) / EXHALE_S
    val fill = if (done) 0.0 else if (inhaling) easeInOut(p) else 1 - easeInOut(p)
    return BreathFrame(
        active = !done,
        inhaling = inhaling,
        fill = fill.toFloat(),
        left = max(0, ceil(total - elapsed).toInt()),
        breaths = floor(t / CYCLE_S).toInt(),
        done = done,
        progress = (t / total).toFloat(),
    )
}

/** 1500 → "25:00". */
fun clock(seconds: Int): String {
    val s = max(0, seconds)
    return "%d:%02d".format(s / 60, s % 60)
}
