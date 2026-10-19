package mx.loperstack.mochi

import mx.loperstack.mochi.data.CalEvent
import mx.loperstack.mochi.data.findMeet
import mx.loperstack.mochi.data.nextEvent
import mx.loperstack.mochi.data.parsePomodoro
import mx.loperstack.mochi.data.parseTasks
import mx.loperstack.mochi.data.pomodoroJson
import mx.loperstack.mochi.data.tasksJson
import mx.loperstack.mochi.data.upcoming
import mx.loperstack.mochi.wellness.Breathing
import mx.loperstack.mochi.data.DayLog
import mx.loperstack.mochi.data.dayKey
import mx.loperstack.mochi.data.dayNumber
import mx.loperstack.mochi.data.fromGoogle
import mx.loperstack.mochi.data.gTaskOf
import mx.loperstack.mochi.data.logsJson
import mx.loperstack.mochi.data.logsOf
import mx.loperstack.mochi.data.streak
import mx.loperstack.mochi.data.withMood
import mx.loperstack.mochi.data.withNote
import mx.loperstack.mochi.data.withoutNote
import org.json.JSONObject
import mx.loperstack.mochi.data.addMood
import mx.loperstack.mochi.data.averageMood
import mx.loperstack.mochi.data.removeMood
import mx.loperstack.mochi.mochi.Outfit
import mx.loperstack.mochi.wellness.BreathSession
import mx.loperstack.mochi.wellness.hrvStats
import mx.loperstack.mochi.wellness.minuteTable
import mx.loperstack.mochi.wellness.rmssd
import mx.loperstack.mochi.wellness.sessionOf
import mx.loperstack.mochi.wellness.toJson
import mx.loperstack.mochi.data.SleepGoal
import mx.loperstack.mochi.data.mergeHealth
import mx.loperstack.mochi.data.minutes
import mx.loperstack.mochi.data.personalTips
import mx.loperstack.mochi.data.sleepEntry
import mx.loperstack.mochi.data.sleepJson
import mx.loperstack.mochi.data.sleepOf
import mx.loperstack.mochi.data.sleepStats
import mx.loperstack.mochi.wellness.breathingOf
import mx.loperstack.mochi.wellness.toWire
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.PomodoroEvent
import mx.loperstack.mochi.wellness.Pomodoro
import mx.loperstack.mochi.wellness.TASK_LIMIT
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
import mx.loperstack.mochi.wellness.resume
import mx.loperstack.mochi.wellness.startFocus
import mx.loperstack.mochi.wellness.tick
import mx.loperstack.mochi.wellness.toggleTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T0 = 1_760_000_000_000L
private const val MIN = 60_000L

class LogicTest {
    @Test fun tasksAreTrimmedEmptyOnesRefused() {
        val t = addTask(emptyList(), "  Llamar a   Bunday ", T0, "a")
        assertEquals("Llamar a Bunday", t.single().text)
        assertEquals(1, addTask(t, "   ", T0, "b").size)
    }

    @Test fun doneTasksSinkAndClear() {
        var t = listOf("uno", "dos", "tres").foldIndexed(emptyList<mx.loperstack.mochi.wellness.Task>()) { i, acc, s -> addTask(acc, s, T0 + i, "$i") }
        t = toggleTask(t, "0")
        assertEquals(listOf("dos", "tres", "uno"), orderedTasks(t).map { it.text })
        assertEquals(2, pendingCount(t))
        assertEquals(listOf("1", "2"), clearDone(t).map { it.id })
    }

    @Test fun aFullListDropsOldestDoneNeverPending() {
        var t = emptyList<mx.loperstack.mochi.wellness.Task>()
        for (i in 0 until TASK_LIMIT) t = addTask(t, "t$i", T0, "$i")
        t = toggleTask(t, "5")
        t = addTask(t, "nueva", T0, "new")
        assertEquals(TASK_LIMIT, t.size)
        assertFalse(t.any { it.id == "5" })
    }

    @Test fun focusPausesAndResumesWhereItWas() {
        var p = Pomodoro().startFocus(T0)
        assertTrue(p.isRunning())
        assertEquals(15 * 60, p.left(T0 + 10 * MIN))
        p = p.pause(T0 + 10 * MIN)
        assertTrue(p.isPaused())
        assertEquals(15 * 60, p.left(T0 + 60 * MIN))
        p = p.resume(T0 + 60 * MIN)
        assertEquals(14 * 60, p.left(T0 + 61 * MIN))
    }

    @Test fun fourthFocusLeadsToALongBreak() {
        var p = Pomodoro().startFocus(T0)
        val seen = mutableListOf<String>()
        repeat(8) {
            val (n, e) = p.tick(p.endsAt!!)
            seen += "$e:${n.phase}"
            p = n
        }
        assertEquals(
            listOf(
                "FOCUS_DONE:BREAK", "BREAK_DONE:FOCUS", "FOCUS_DONE:BREAK", "BREAK_DONE:FOCUS",
                "FOCUS_DONE:BREAK", "BREAK_DONE:FOCUS", "FOCUS_DONE:LONG_BREAK", "BREAK_DONE:FOCUS",
            ),
            seen,
        )
        assertEquals(4, p.today)
    }

    @Test fun nothingBeforeTimeSkippedFocusDoesNotCount() {
        val p = Pomodoro().startFocus(T0)
        assertNull(p.tick(T0 + MIN).second)
        val (skipped, event) = p.finishPhase(T0 + MIN, counted = false)
        assertEquals(PomodoroEvent.FOCUS_DONE, event)
        assertEquals(Phase.BREAK, skipped.phase)
        assertEquals(0, skipped.today)
    }

    @Test fun breathIsFourInSixOut() {
        val b = Breathing(T0, 1)
        fun at(s: Double) = breathFrame(b, T0 + (s * 1000).toLong())
        assertTrue(at(0.0).inhaling)
        assertEquals(0f, at(0.0).fill, 1e-6f)
        assertEquals(0.5f, at(2.0).fill, 1e-6f)
        assertFalse(at(4.0).inhaling)
        assertEquals(1f, at(4.0).fill, 1e-6f)
        assertEquals(2, at(25.0).breaths)
        assertEquals(35, at(25.0).left)
        assertTrue(at(60.0).done)
        assertFalse(breathFrame(Breathing(), T0).active)
    }

    @Test fun storeRoundTrips() {
        val tasks = addTask(emptyList(), "Propuesta", T0, "a")
        assertEquals(tasks, parseTasks(tasksJson(tasks)))
        assertEquals(emptyList<Any>(), parseTasks("nope"))
        val p = Pomodoro().startFocus(T0)
        assertEquals(p, parsePomodoro(pomodoroJson(p)))
        assertEquals(Pomodoro(), parsePomodoro("{bad"))
    }

    @Test fun agendaFindsMeetAndTheNextEvent() {
        assertEquals("https://meet.google.com/abc-defg-hij", findMeet(null, "Únete: https://meet.google.com/abc-defg-hij ok"))
        assertNull(findMeet("Sala 3", null))
        val past = CalEvent(1, "Desayuno", T0 - 2 * 3600_000, T0 - 3600_000, false, null, null)
        val allDay = CalEvent(2, "Cumple", T0 - 3600_000, T0 + 20 * 3600_000, true, null, null)
        val lunch = CalEvent(3, "Comida", T0 + 3600_000, T0 + 2 * 3600_000, false, null, null)
        assertEquals(listOf(2L, 3L), upcoming(listOf(lunch, past, allDay), T0).map { it.id })
        assertEquals(3L, nextEvent(listOf(allDay, lunch), T0)?.id)
    }

    @Test fun clockFormats() {
        assertEquals("25:00", clock(1500))
        assertEquals("0:05", clock(5))
    }

    @Test fun breathingTravelsToTheWatch() {
        val b = Breathing(1_700_000_000_000, 5)
        assertEquals(b, breathingOf(b.toWire()))
        assertEquals(null, breathingOf("nope"))
        assertEquals(null, breathingOf("1700000000000,0"))
    }

    @Test fun diaryKeepsMoodAndLines() {
        var logs = withMood(emptyList(), "2026-10-13", 4)
        logs = withNote(logs, "2026-10-13", 2000, "segunda")
        logs = withNote(logs, "2026-10-13", 1000, "primera")
        logs = withNote(logs, "2026-10-13", 3000, "   ")
        val day = logs.single()
        assertEquals(4, day.mood)
        assertEquals(listOf("primera", "segunda"), day.notes.map { it.text })
        assertEquals(logs, logsOf(logsJson(logs)))
        // A day with nothing left disappears.
        var gone = withMood(withNote(emptyList(), "2026-10-12", 1, "x"), "2026-10-12", 0)
        gone = withoutNote(gone, "2026-10-12", 1)
        assertEquals(emptyList<DayLog>(), gone)
    }

    @Test fun diaryCountsDaysAndStreaks() {
        val logs = listOf(DayLog("2026-10-01", 3), DayLog("2026-10-12", 4), DayLog("2026-10-13", 5))
        assertEquals(1, dayNumber(logs, "2026-10-01"))
        assertEquals(13, dayNumber(logs, "2026-10-13"))
        val noon13 = java.util.Calendar.getInstance().apply { set(2026, 9, 13, 12, 0) }.timeInMillis
        assertEquals("2026-10-13", dayKey(noon13))
        assertEquals(2, streak(logs, noon13))
    }

    @Test fun googleTasksBecomeRemoteTasks() {
        val a = gTaskOf(JSONObject("""{"id":"abc","title":" Comprar pan ","status":"needsAction","position":"002"}"""))!!
        val b = gTaskOf(JSONObject("""{"id":"def","title":"Llamar","status":"completed","position":"001"}"""))!!
        assertEquals(null, gTaskOf(JSONObject("""{"id":"x","title":"Borrada","deleted":true}""")))
        assertEquals(null, gTaskOf(JSONObject("""{"id":"y","title":"  "}""")))
        val tasks = fromGoogle(listOf(a, b))
        assertEquals(listOf("Llamar", "Comprar pan"), tasks.map { it.text })
        assertEquals(true, tasks.all { it.remote })
        assertEquals(true, tasks[0].done)
    }

    private fun at(day: Int, h: Int, m: Int = 0) = java.util.Calendar.getInstance().apply { set(2026, 9, day, h, m, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun sleepNightsAndNumbers() {
        val a = sleepEntry(at(12, 23, 30), at(13, 7, 0), quality = 4)
        assertEquals("2026-10-13", a.night)
        assertEquals(450, a.minutes)
        val list = listOf(sleepEntry(at(10, 23, 0), at(11, 6, 0), 2, listOf("cafe")), sleepEntry(at(11, 22, 50), at(12, 6, 10), 2, listOf("cafe")), a)
        assertEquals(list, sleepOf(sleepJson(list)))
        val s = sleepStats(list, SleepGoal(23 * 60, 7 * 60))!!
        assertEquals(3, s.nights)
        assertEquals(true, s.debt > 0)
        assertEquals(true, s.score in 0..100)
        // Short nights: the first tip is about sleeping longer.
        assertEquals("⏰", personalTips(list, SleepGoal(22 * 60, 7 * 60)).first().icon)
    }

    @Test fun watchNightsKeepYourRating() {
        val mine = sleepEntry(at(12, 23, 0), at(13, 7, 0), quality = 5, note = "bien")
        val watch = sleepEntry(at(12, 23, 20), at(13, 6, 40), source = "health").copy(deepMin = 80)
        val merged = mergeHealth(listOf(mine), listOf(watch)).single()
        assertEquals(5, merged.quality)
        assertEquals("bien", merged.note)
        assertEquals(80, merged.deepMin)
        assertEquals("health", merged.source)
    }

    @Test fun heartRateVariability() {
        assertEquals(0, rmssd(listOf(800, 800)))
        assertEquals(20, rmssd(listOf(800, 820, 800, 820, 800)))
        // Pulse readings only: an estimate, minute by minute.
        val s = BreathSession(1L, 1, 6, 72, 64, 62, "watch", hr = List(30) { if (it < 15) 72 else 64 })
        val st = hrvStats(s)!!
        assertEquals(false, st.beatToBeat)
        assertEquals(62, st.minBpm.coerceAtMost(62))
        assertEquals(1, minuteTable(s).size)
        // Real beats win, and survive the trip to the phone.
        val withBeats = s.copy(rr = List(40) { if (it % 2 == 0) 800 else 840 })
        assertEquals(true, hrvStats(withBeats)!!.beatToBeat)
        assertEquals(withBeats, sessionOf(withBeats.toJson()))
    }

    @Test fun moodsThroughTheDay() {
        var logs = addMood(emptyList(), "2026-10-19", 8 * 3600_000L, 5)
        logs = addMood(logs, "2026-10-19", 18 * 3600_000L, 2)
        val d = logs.single()
        assertEquals(2, d.mood)
        assertEquals(listOf(5, 2), d.moods.map { it.value })
        assertEquals(4, d.averageMood)
        assertEquals(logs, logsOf(logsJson(logs)))
        assertEquals(5, removeMood(logs, "2026-10-19", 18 * 3600_000L).single().mood)
    }

    @Test fun seasonalHats() {
        fun on(m: Int, d: Int) = Outfit.seasonal(java.util.Calendar.getInstance().apply { clear(); set(2026, m - 1, d) })
        assertEquals(Outfit.WITCH, on(10, 19))
        assertEquals(Outfit.FLOWERS, on(11, 2))
        assertEquals(Outfit.SANTA, on(12, 20))
        assertEquals(Outfit.PARTY, on(1, 1))
        assertEquals(Outfit.BUNNY, on(4, 5)) // Easter 2026
        assertEquals(Outfit.NONE, on(3, 10))
        assertEquals(Outfit.CROWN, Outfit.resolve("CROWN"))
    }
}
