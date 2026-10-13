// The watch's copy of the phone's state, and its way back to the phone.
// The phone publishes a Snapshot (shared/…/Sync.kt); the watch keeps the last
// one (so the tile and complications have something with the phone away) and
// sends small messages for what you do on the wrist.

package mx.loperstack.mochi.wear

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.loperstack.mochi.mochi.Sounds
import mx.loperstack.mochi.wellness.BreathSession
import mx.loperstack.mochi.wellness.Snapshot
import mx.loperstack.mochi.wellness.SyncPaths
import mx.loperstack.mochi.wellness.snapshotOf
import mx.loperstack.mochi.wellness.toJson
import mx.loperstack.mochi.wellness.toggleTask

object WatchStore {
    private lateinit var prefs: SharedPreferences
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("mochi-watch", Context.MODE_PRIVATE)
        _state.value = snapshotOf(prefs.getString("snapshot", null))
        Sounds.enabled = _state.value.soundOn
    }

    /** A new state from the phone. */
    fun receive(json: String) {
        val s = snapshotOf(json)
        if (s.at < _state.value.at) return
        _state.value = s
        Sounds.enabled = s.soundOn
        prefs.edit().putString("snapshot", json).apply()
        refreshSurfaces()
    }

    /** The tile and the complications show the new state too. */
    fun refreshSurfaces() {
        runCatching { TileService.getUpdater(app).requestUpdate(MochiTileService::class.java) }
        for (cls in listOf(PendingComplicationService::class.java, PhoneBatteryComplicationService::class.java)) {
            runCatching { ComplicationDataSourceUpdateRequester.create(app, ComponentName(app, cls)).requestUpdateAll() }
        }
    }

    /** Reads the last state the phone published (opening the app). */
    fun pull() {
        scope.launch {
            runCatching {
                val items = Wearable.getDataClient(app).dataItems.await()
                try {
                    for (item in items) {
                        if (item.uri.path == SyncPaths.STATE) {
                            DataMapItem.fromDataItem(item).dataMap.getString("json")?.let { receive(it) }
                        }
                    }
                } finally {
                    items.release()
                }
            }
            send(SyncPaths.REQUEST, "")
        }
    }

    fun send(path: String, text: String) {
        scope.launch {
            runCatching {
                val nodes = Wearable.getNodeClient(app).connectedNodes.await()
                for (n in nodes) Wearable.getMessageClient(app).sendMessage(n.id, path, text.toByteArray(Charsets.UTF_8)).await()
            }
        }
    }

    // ── What the wrist can do (shown at once, then confirmed by the phone) ────

    fun toggleTask(id: String) {
        val s = _state.value
        val was = s.tasks.firstOrNull { it.id == id } ?: return
        _state.value = s.copy(tasks = toggleTask(s.tasks, id))
        Sounds.play(if (was.done) "blip" else "approve")
        send(SyncPaths.TOGGLE_TASK, id)
    }

    fun addTask(text: String) {
        if (text.isBlank()) return
        Sounds.play("pop")
        send(SyncPaths.ADD_TASK, text.trim())
    }

    fun pomodoro() {
        Sounds.play("work")
        send(SyncPaths.POMODORO, "")
    }

    fun session(s: BreathSession) = send(SyncPaths.SESSION, s.toJson())
}

/** The phone's state arrives here, app open or not. */
class WatchListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        WatchStore.init(this)
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == SyncPaths.STATE) {
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let { WatchStore.receive(it) }
            }
        }
    }
}
