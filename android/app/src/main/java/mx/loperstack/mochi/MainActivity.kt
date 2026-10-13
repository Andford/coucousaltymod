package mx.loperstack.mochi

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import mx.loperstack.mochi.data.Store
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
    }

    private val startTab = mutableStateOf(Tab.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Sounds.init(this)
        Sounds.enabled = Store.state.value.soundOn
        // Every change goes to the watch, if there is one.
        Store.onChange = {
            Sounds.enabled = Store.state.value.soundOn
            WearSync.push(applicationContext)
        }
        PomodoroScheduler.channels(this)
        catchUp()
        startTab.value = tabOf(intent)
        setContent { MochiApp(startTab.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        startTab.value = tabOf(intent)
    }

    override fun onResume() {
        super.onResume()
        catchUp()
        WearSync.push(this)
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

    private fun tabOf(intent: Intent?): Tab = when (intent?.getStringExtra(EXTRA_TAB)) {
        "focus" -> Tab.FOCUS
        "breathe" -> Tab.BREATHE
        "tasks" -> Tab.TASKS
        else -> Tab.HOME
    }
}
