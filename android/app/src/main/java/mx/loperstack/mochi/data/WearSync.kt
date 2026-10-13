// The phone's side of the watch link (Wearable Data Layer): it publishes a
// Snapshot whenever something changes, and acts on what the watch asks —
// even with the app closed, through PhoneListenerService.

package mx.loperstack.mochi.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.wellness.EventLite
import mx.loperstack.mochi.wellness.Phase
import mx.loperstack.mochi.wellness.PomodoroScheduler
import mx.loperstack.mochi.wellness.Snapshot
import mx.loperstack.mochi.wellness.SyncPaths
import mx.loperstack.mochi.wellness.addTask
import mx.loperstack.mochi.wellness.isPaused
import mx.loperstack.mochi.wellness.pause
import mx.loperstack.mochi.wellness.resume
import mx.loperstack.mochi.wellness.sessionOf
import mx.loperstack.mochi.wellness.startFocus
import mx.loperstack.mochi.wellness.toJson
import mx.loperstack.mochi.wellness.toggleTask

object WearSync {
    fun snapshot(context: Context, now: Long = System.currentTimeMillis()): Snapshot {
        val d = Store.state.value
        val bm = context.getSystemService(BatteryManager::class.java)
        val events = if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            upcoming(Agenda.load(context, now), now).take(6).map { EventLite(it.title, it.begin, it.end, it.allDay, it.meetUrl) }
        } else emptyList()
        return Snapshot(
            tasks = d.tasks,
            mochiColor = d.mochiColor,
            phoneBattery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            phoneCharging = bm.isCharging,
            events = events,
            pomodoro = d.pomodoro,
            soundOn = d.soundOn,
            at = now,
        )
    }

    /** Publishes the state for the watch; a phone without a watch just drops it. */
    fun push(context: Context) {
        val app = context.applicationContext
        Thread {
            runCatching {
                val req = PutDataMapRequest.create(SyncPaths.STATE).apply {
                    dataMap.putString("json", snapshot(app).toJson())
                    dataMap.putLong("t", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(app).putDataItem(req)
            }
        }.start()
    }

    /** What the watch asked for. */
    fun handle(context: Context, path: String, data: ByteArray) {
        Store.init(context)
        val text = data.toString(Charsets.UTF_8)
        when (path) {
            SyncPaths.TOGGLE_TASK -> Store.setTasks(toggleTask(Store.state.value.tasks, text))
            SyncPaths.ADD_TASK -> {
                val now = System.currentTimeMillis()
                Store.setTasks(addTask(Store.state.value.tasks, text, now, "${now.toString(36)}-w"))
            }
            SyncPaths.SESSION -> sessionOf(text)?.let { Store.addSession(it) }
            SyncPaths.POMODORO -> {
                val now = System.currentTimeMillis()
                val p = Store.state.value.pomodoro
                val next = when {
                    p.phase == Phase.IDLE -> p.startFocus(now)
                    p.isPaused() -> p.resume(now)
                    else -> p.pause(now)
                }
                Store.setPomodoro(next)
                PomodoroScheduler.sync(context, next, now)
            }
            SyncPaths.REQUEST -> Unit
        }
        push(context)
    }
}

/** Messages from the watch arrive here, app open or not. */
class PhoneListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        Sounds.init(this)
        WearSync.handle(this, event.path, event.data ?: ByteArray(0))
    }
}
