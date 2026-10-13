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
}
