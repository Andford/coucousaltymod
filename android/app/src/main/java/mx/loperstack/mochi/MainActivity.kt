package mx.loperstack.mochi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import mx.loperstack.mochi.data.Store
import mx.loperstack.mochi.data.GoogleTasks
import mx.loperstack.mochi.data.TaskSync
import mx.loperstack.mochi.data.WearSync
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.ui.MochiApp
import mx.loperstack.mochi.ui.Tab
import mx.loperstack.mochi.wellness.PomodoroScheduler
import mx.loperstack.mochi.wellness.isRunning
import mx.loperstack.mochi.wellness.tick

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        /** From the Respirar widget: start a session of this many minutes. */
        const val EXTRA_BREATHE_MIN = "breatheMin"
    }

    private val startTab = mutableStateOf(Tab.HOME)
    /** minutes to breathe, and a nonce so the same button works twice. */
    private val breatheNow = mutableStateOf(0 to 0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        GoogleTasks.init(this)
        Sounds.init(this)
        Sounds.enabled = Store.state.value.soundOn
        // Every change already reaches the watch and the widgets (Store).
        Store.onChange = {
            Sounds.enabled = Store.state.value.soundOn
            mx.loperstack.mochi.mochi.Haptics.enabled = Store.state.value.haptics
        mx.loperstack.mochi.mochi.MochiStyle.flat = Store.state.value.mochiFlat
        }
        mx.loperstack.mochi.mochi.Haptics.enabled = Store.state.value.haptics
        PomodoroScheduler.channels(this)
        mx.loperstack.mochi.wellness.BedReminder.sync(this)
        catchUp()
        startTab.value = tabOf(intent)
        breatheNow.value = breatheOf(intent)
        setContent { MochiApp(startTab.value, breatheNow.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startTab.value = tabOf(intent)
        breatheNow.value = breatheOf(intent)
    }

    override fun onResume() {
        super.onResume()
        catchUp()
        WearSync.push(this)
        // Tasks changed on the PC or in Google: bring them in.
        if (GoogleTasks.connected) TaskSync.refresh(this)
    }

    /** A phase that ended while the alarm could not run: move on, quietly. */
    private fun catchUp() {
        val now = System.currentTimeMillis()
        val p = Store.state.value.pomodoro
        if (p.isRunning() && (p.endsAt ?: 0) <= now) {
            val (next, _) = p.tick(now)
            Store.setPomodoro(next)
            PomodoroScheduler.sync(this, next, now)
        }
    }

    private fun breatheOf(intent: Intent?): Pair<Int, Long> {
        val m = intent?.getIntExtra(EXTRA_BREATHE_MIN, 0) ?: 0
        return if (m > 0) m to System.nanoTime() else 0 to 0L
    }

    private fun tabOf(intent: Intent?): Tab = when (intent?.getStringExtra(EXTRA_TAB)) {
        "focus" -> Tab.FOCUS
        "breathe" -> Tab.BREATHE
        "tasks" -> Tab.TASKS
        "diary" -> Tab.DIARY
        "sleep" -> Tab.SLEEP
        else -> Tab.HOME
    }
}
