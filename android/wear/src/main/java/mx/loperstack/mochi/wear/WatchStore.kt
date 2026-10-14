// The watch's copy of the phone's state, and its way back to the phone.
// The phone publishes a Snapshot (shared/…/Sync.kt); the watch keeps the last
// one (so the tile and complications have something with the phone away) and
// sends small messages for what you do on the wrist.

package mx.loperstack.mochi.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
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
import mx.loperstack.mochi.wellness.Breathing
import mx.loperstack.mochi.wellness.breathingOf
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

    /** A session the phone started: the breathe screen opens it, in step. */
    val breathRequest = MutableStateFlow<Breathing?>(null)
    /** The phone cut its session short (its start time). */
    val stopRequest = MutableStateFlow<Long?>(null)
    /** The app is on screen (no notification needed). */
    @Volatile var foreground = false

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
        runCatching { TileService.getUpdater(app).requestUpdate(MoodTileService::class.java) }
        for (cls in listOf(PendingComplicationService::class.java, PhoneBatteryComplicationService::class.java, MoodComplicationService::class.java)) {
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

    fun heartRate(bpm: Int) = send(SyncPaths.HEART_RATE, bpm.toString())

    /** Today's mood for the diary on the phone (from the app, the tile or the complication). */
    fun mood(value: Int, sound: Boolean = true) {
        // "now" marks it as today's, so the tile and complication trust it.
        _state.value = _state.value.copy(moodToday = value, at = maxOf(_state.value.at, System.currentTimeMillis()))
        prefs.edit().putString("snapshot", _state.value.toJson()).apply()
        if (sound) Sounds.play(if (value >= 4) "love" else "pop")
        send(SyncPaths.MOOD, value.toString())
        refreshSurfaces()
    }

    /** Today's mood, 0 if the last word from the phone is from another day. */
    fun todayMood(): Int {
        val s = _state.value
        val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
        return if (fmt.format(java.util.Date(s.at)) == fmt.format(java.util.Date())) s.moodToday else 0
    }

    /** A screen to open (the mood complication asks for "mood"). */
    val openScreen = MutableStateFlow<String?>(null)

    /** The phone asked to breathe together. */
    fun breatheFromPhone(context: Context, b: Breathing) {
        val start = b.startedAt ?: return
        if (System.currentTimeMillis() - start > b.minutes * 60_000L) return
        breathRequest.value = b
        if (foreground) return
        // Not on screen: open it (or, if the system says no, a tap away).
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mochi://breathe?at=$start&min=${b.minutes}"))
            .setClass(context, WearActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(context, 7, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_BREATHE, "Respirar con el teléfono", NotificationManager.IMPORTANCE_HIGH))
        val n = NotificationCompat.Builder(context, CHANNEL_BREATHE)
            .setSmallIcon(mx.loperstack.mochi.shared.R.drawable.ic_stat_mochi)
            .setContentTitle("Respira con Mochi")
            .setContentText("${b.minutes} min · toca para medir tu pulso")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setTimeoutAfter(b.minutes * 60_000L)
            .build()
        runCatching { nm.notify(NOTIFY_BREATHE, n) }
        runCatching { context.startActivity(intent) }
    }

    fun clearBreatheNotice(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFY_BREATHE) }
    }

    private const val CHANNEL_BREATHE = "breathe_phone"
    private const val NOTIFY_BREATHE = 42
}

/** The phone's state and requests arrive here, app open or not. */
class WatchListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        WatchStore.init(this)
        val text = (event.data ?: ByteArray(0)).toString(Charsets.UTF_8)
        when (event.path) {
            SyncPaths.BREATHE_START -> breathingOf(text)?.let { WatchStore.breatheFromPhone(this, it) }
            SyncPaths.BREATHE_STOP -> {
                WatchStore.stopRequest.value = System.currentTimeMillis()
                WatchStore.breathRequest.value = null
                WatchStore.clearBreatheNotice(this)
            }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        WatchStore.init(this)
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == SyncPaths.STATE) {
                DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json")?.let { WatchStore.receive(it) }
            }
        }
    }
}
