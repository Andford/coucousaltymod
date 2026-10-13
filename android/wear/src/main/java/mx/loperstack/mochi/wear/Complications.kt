// Watch face complications: how many tasks are left, and the phone's battery.
// Both read the last state the phone sent (WatchStore) and open Mochi on tap.

package mx.loperstack.mochi.wear

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import mx.loperstack.mochi.wellness.pendingCount

private fun plain(s: String) = PlainComplicationText.Builder(s).build()

private fun mochiIcon(context: Context) =
    MonochromaticImage.Builder(Icon.createWithResource(context, mx.loperstack.mochi.shared.R.drawable.ic_stat_mochi)).build()

private fun openApp(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context, 0,
        Intent(context, WearActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

/** "3" with Mochi's icon: tasks still to do. */
class PendingComplicationService : SuspendingComplicationDataSourceService() {

    private fun data(count: Int, tap: Boolean): ComplicationData =
        ShortTextComplicationData.Builder(plain(count.toString()), plain("$count pendientes"))
            .setTitle(plain("Tareas"))
            .setMonochromaticImage(mochiIcon(this))
            .apply { if (tap) setTapAction(openApp(this@PendingComplicationService)) }
            .build()

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        WatchStore.init(this)
        return data(pendingCount(WatchStore.state.value.tasks), tap = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) data(3, tap = false) else null
}

/** The phone's battery, as a ring or a short "82%". */
class PhoneBatteryComplicationService : SuspendingComplicationDataSourceService() {

    private fun data(type: ComplicationType, level: Int, charging: Boolean, tap: Boolean): ComplicationData? {
        val known = level >= 0
        val label = if (known) "$level%" else "–"
        val desc = plain(if (known) "Batería del teléfono $level%" else "Batería del teléfono desconocida")
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(if (known) level.toFloat() else 0f, 0f, 100f, desc)
                    .setText(plain(if (charging) "⚡$label" else label))
                    .setMonochromaticImage(mochiIcon(this))
                    .apply { if (tap) setTapAction(openApp(this@PhoneBatteryComplicationService)) }
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(label), desc)
                    .setTitle(plain(if (charging) "⚡ Tel" else "Tel"))
                    .setMonochromaticImage(mochiIcon(this))
                    .apply { if (tap) setTapAction(openApp(this@PhoneBatteryComplicationService)) }
                    .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        WatchStore.init(this)
        val s = WatchStore.state.value
        return data(request.complicationType, s.phoneBattery, s.phoneCharging, tap = true)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, 82, false, tap = false)
}
